package com.benji.dialoguestudio.dialogue.trigger;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.dialogue.DialogueRegistry;
import com.benji.dialoguestudio.dialogue.DialogueSessionManager;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.network.dialogueengine.DialogueNetwork;
import com.benji.dialoguestudio.network.dialogueengine.DialogueZonePreviewS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueTriggerEngine {

    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    private static final int ZONE_SYNC_INTERVAL = 10;
    private static final int MAX_ZONE_PREVIEWS = 96;

    private DialogueTriggerEngine() {
    }


    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity target = event.getTarget();

        forEachTrigger("right_click_entity", (id, definition, trigger) -> {

            if (!matchesEntity(target, trigger.target)) {
                return false;
            }

            boolean started = start(player, target, id, definition, trigger);

            if (started && trigger.consume) {
                event.setCanceled(true);

                event.setCancellationResult(InteractionResult.SUCCESS);
            }

            return started;
        });
    }


    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState state = level.getBlockState(event.getPos());

        forEachTrigger("right_click_block", (id, definition, trigger) -> {

            if (!matchesBlock(state, trigger.target)) {
                return false;
            }

            boolean started = start(player, null, id, definition, trigger);

            if (started && trigger.consume) {
                event.setCanceled(true);

                event.setCancellationResult(InteractionResult.SUCCESS);
            }

            return started;
        });
    }


    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity target = event.getEntity();

        forEachTrigger("hit_entity", (id, definition, trigger) -> {

            if (!matchesEntity(target, trigger.target)) {
                return false;
            }

            boolean started = start(player, target, id, definition, trigger);

            if (started && trigger.consume) {
                event.setCanceled(true);
            }

            return started;
        });
    }


    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity killed = event.getEntity();

        forEachTrigger("kill_entity", (id, definition, trigger) -> {

            if (!matchesEntity(killed, trigger.target)) {
                return false;
            }

            return start(player, null, id, definition, trigger);
        });
    }


    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (player.tickCount % ZONE_SYNC_INTERVAL == 0) {
            syncZonePreviews(player);
        }

        if (DialogueSessionManager.isActive(player)) {
            return;
        }

        checkTickTriggers(player);
    }


    private static void checkTickTriggers(ServerPlayer player) {
        for (Map.Entry<ResourceLocation, DialogueDefinition> entry : DialogueRegistry.entries().entrySet()) {

            ResourceLocation id = entry.getKey();

            DialogueDefinition definition = entry.getValue();

            if (definition.triggers == null) {
                continue;
            }

            for (DialogueDefinition.Trigger trigger : definition.triggers) {

                if (trigger == null || trigger.type == null) {
                    continue;
                }

                int interval = Math.max(1, trigger.check_interval);

                if (player.tickCount % interval != 0) {
                    continue;
                }

                String type = trigger.type.toLowerCase(Locale.ROOT);

                boolean started = switch (type) {
                    case "proximity_entity" -> checkNearbyEntity(player, id, definition, trigger, false);

                    case "shift_near_entity" ->
                            player.isShiftKeyDown() && checkNearbyEntity(player, id, definition, trigger, false);

                    case "look_at_entity" -> checkNearbyEntity(player, id, definition, trigger, true);

                    case "proximity_block" -> checkNearbyBlock(player, id, definition, trigger);

                    case "enter_area" -> checkArea(player, id, definition, trigger);

                    case "zone" -> checkZone(player, id, definition, trigger);

                    default -> false;
                };

                if (started) {
                    return;
                }
            }
        }
    }


    private static boolean checkNearbyEntity(ServerPlayer player, ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger, boolean requireLook) {
        double radius = Math.max(0.5D, trigger.radius);

        AABB box = player.getBoundingBox().inflate(radius);

        for (Entity entity : player.serverLevel().getEntities(player, box, candidate -> candidate.isAlive() && matchesEntity(candidate, trigger.target))) {

            if (requireLook && !isLookingAt(player, entity, trigger.look_angle)) {
                continue;
            }

            if (start(player, entity, id, definition, trigger)) {
                return true;
            }
        }

        return false;
    }


    private static boolean checkNearbyBlock(ServerPlayer player, ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger) {
        ServerLevel level = player.serverLevel();

        int radius = Mth.clamp((int) Math.ceil(trigger.radius), 1, 16);

        BlockPos center = player.blockPosition();

        double radiusSqr = trigger.radius * trigger.radius;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {

            if (center.distSqr(pos) > radiusSqr) {
                continue;
            }

            if (!matchesBlock(level.getBlockState(pos), trigger.target)) {
                continue;
            }

            return start(player, null, id, definition, trigger);
        }

        return false;
    }

    private static boolean checkArea(ServerPlayer player, ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger) {
        if (!dimensionMatches(player, trigger)) {
            return false;
        }

        Vec3 pos = player.position();

        boolean inside;

        if (trigger.min_x != null && trigger.min_y != null && trigger.min_z != null && trigger.max_x != null && trigger.max_y != null && trigger.max_z != null) {

            inside = pos.x >= trigger.min_x && pos.x <= trigger.max_x && pos.y >= trigger.min_y && pos.y <= trigger.max_y && pos.z >= trigger.min_z && pos.z <= trigger.max_z;

        } else if (trigger.x != null && trigger.y != null && trigger.z != null) {

            double dx = pos.x - trigger.x;

            double dy = pos.y - trigger.y;

            double dz = pos.z - trigger.z;

            inside = dx * dx + dy * dy + dz * dz <= trigger.radius * trigger.radius;

        } else {
            return false;
        }

        return inside && start(player, null, id, definition, trigger);
    }

    private static boolean checkZone(ServerPlayer player, ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger) {
        if (!dimensionMatches(player, trigger)) {
            return false;
        }

        List<ResolvedZone> zones = resolveZoneAnchors(player, trigger, false);

        Vec3 playerPos = player.position();

        for (ResolvedZone zone : zones) {

            if (!isInsideZone(playerPos, zone.center, trigger)) {
                continue;
            }

            if (start(player, zone.source, id, definition, trigger, zone.key)) {
                return true;
            }
        }

        return false;
    }

    private static void syncZonePreviews(ServerPlayer player) {
        if (DialogueSessionManager.isActive(player)) {
            DialogueNetwork.syncZones(player, List.of());

            return;
        }

        List<DialogueZonePreviewS2CPacket.Zone> previews = new ArrayList<>();

        for (Map.Entry<ResourceLocation, DialogueDefinition> entry : DialogueRegistry.entries().entrySet()) {

            DialogueDefinition definition = entry.getValue();

            if (definition.triggers == null) {
                continue;
            }

            for (int triggerIndex = 0; triggerIndex < definition.triggers.size(); triggerIndex++) {

                DialogueDefinition.Trigger trigger = definition.triggers.get(triggerIndex);

                if (trigger == null || trigger.type == null || !"zone".equalsIgnoreCase(trigger.type) || !dimensionMatches(player, trigger)) {
                    continue;
                }

                DialogueDefinition.ZoneVisual visual = trigger.visual != null ? trigger.visual : new DialogueDefinition.ZoneVisual();

                if (!visual.enabled) {
                    continue;
                }

                double previewDistance = Math.max(1.0D, visual.preview_distance);

                List<ResolvedZone> resolved = resolveZoneAnchors(player, trigger, true);

                for (ResolvedZone zone : resolved) {

                    if (player.position().distanceToSqr(zone.center) > previewDistance * previewDistance) {
                        continue;
                    }

                    String key = entry.getKey() + "|" + triggerIndex + "|" + zone.key;

                    previews.add(new DialogueZonePreviewS2CPacket.Zone(key,
                            normalizeShape(trigger.shape),
                            zone.center.x, zone.center.y, zone.center.z,
                            Math.max(0.1D, trigger.radius),
                            Math.max(0.1D, trigger.height),
                            Math.max(0.1D, trigger.size_x),
                            Math.max(0.1D, trigger.size_y),
                            Math.max(0.1D, trigger.size_z),
                            visual.style != null ? visual.style : "auto",
                            visual.show_default_zone,
                            visual.texture,
                            visual.texture_mode != null ? visual.texture_mode : "plane",
                            visual.texture_fit != null ? visual.texture_fit : "stretch",
                            Math.max(0.01D, visual.texture_repeat_x),
                            Math.max(0.01D, visual.texture_repeat_y),
                            visual.texture_scroll_u,
                            visual.texture_scroll_v,
                            visual.texture_offset_x,
                            visual.texture_offset_y,
                            visual.texture_offset_z,
                            Math.max(0.05D, visual.texture_scale_x),
                            Math.max(0.05D, visual.texture_scale_y),
                            visual.texture_rotation_x,
                            visual.texture_rotation,
                            visual.texture_rotation_z,
                            visual.color != null ? visual.color : "cyan",
                            Mth.clamp(visual.alpha, 0.0F, 1.0F),
                            visual.y_offset,
                            Math.max(0.0D, visual.size),
                            Math.max(0.0D, visual.visual_height),
                            visual.fill_enabled,
                            visual.fill_mode != null ? visual.fill_mode : "gradient",
                            visual.fill_color_bottom != null ? visual.fill_color_bottom : "cyan",
                            visual.fill_color_top != null ? visual.fill_color_top : "cyan",
                            Mth.clamp(visual.fill_alpha_bottom, 0.0F, 1.0F),
                            Mth.clamp(visual.fill_alpha_top, 0.0F, 1.0F),
                            visual.pulse,
                            Math.max(0.0D, visual.pulse_amplitude),
                            Math.max(0.0D, visual.pulse_speed),
                            visual.bob,
                            Math.max(0.0D, visual.bob_amplitude),
                            Math.max(0.0D, visual.bob_speed),
                            visual.rotate,
                            visual.rotate_speed,
                            visual.alpha_breathe,
                            Mth.clamp(visual.alpha_breathe_amount, 0.0D, 1.0D),
                            Math.max(0.0D, visual.alpha_breathe_speed),
                            previewDistance));

                    if (previews.size() >= MAX_ZONE_PREVIEWS) {
                        DialogueNetwork.syncZones(player, previews);

                        return;
                    }
                }
            }
        }

        DialogueNetwork.syncZones(player, previews);
    }


    private static List<ResolvedZone> resolveZoneAnchors(ServerPlayer player, DialogueDefinition.Trigger trigger, boolean preview) {
        DialogueDefinition.ZoneAnchor anchor = trigger.anchor;

        if (anchor == null) {
            if (trigger.x != null && trigger.y != null && trigger.z != null) {

                return List.of(new ResolvedZone(new Vec3(trigger.x, trigger.y, trigger.z), null, "absolute:" + trigger.x + ":" + trigger.y + ":" + trigger.z));
            }

            return List.of();
        }

        String type = anchor.type != null ? anchor.type.toLowerCase(Locale.ROOT) : "absolute";

        return switch (type) {
            case "block" -> resolveBlockAnchors(player, trigger, anchor, preview);

            case "entity" -> resolveEntityAnchors(player, trigger, anchor, preview);

            case "absolute" -> resolveAbsoluteAnchor(trigger, anchor);

            default -> List.of();
        };
    }


    private static List<ResolvedZone> resolveAbsoluteAnchor(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneAnchor anchor) {
        Double x = anchor.x != null ? anchor.x : trigger.x;

        Double y = anchor.y != null ? anchor.y : trigger.y;

        Double z = anchor.z != null ? anchor.z : trigger.z;

        if (x == null || y == null || z == null) {
            return List.of();
        }

        Vec3 center = applyOffset(new Vec3(x, y, z), anchor);

        return List.of(new ResolvedZone(center, null, "absolute:" + x + ":" + y + ":" + z));
    }


    private static List<ResolvedZone> resolveEntityAnchors(ServerPlayer player, DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneAnchor anchor, boolean preview) {
        double horizontal = preview ? Math.max(1.0D, getVisual(trigger).preview_distance) : zoneHorizontalExtent(trigger) + 2.0D;

        double vertical = Math.max(zoneVerticalExtent(trigger) + 2.0D, 6.0D);

        AABB searchBox = player.getBoundingBox().inflate(horizontal, vertical, horizontal);

        String target = anchor.target != null ? anchor.target : trigger.target;

        List<Entity> entities = player.serverLevel().getEntities(player, searchBox, entity -> entity.isAlive() && matchesEntity(entity, target) && matchesEntityTag(entity, anchor.entity_tag));

        if (entities.isEmpty()) {
            return List.of();
        }

        entities.sort(Comparator.comparingDouble(player::distanceToSqr));

        boolean all = "all".equalsIgnoreCase(anchor.pick);

        int count = all ? Math.min(entities.size(), 64) : 1;

        List<ResolvedZone> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Entity entity = entities.get(i);

            Vec3 center = applyOffset(new Vec3(entity.getX(), entity.getY(), entity.getZ()), anchor);

            result.add(new ResolvedZone(center, entity, "entity:" + entity.getUUID()));
        }

        return result;
    }


    private static List<ResolvedZone> resolveBlockAnchors(ServerPlayer player, DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneAnchor anchor, boolean preview) {
        ServerLevel level = player.serverLevel();

        double searchDistance = preview ? Math.max(1.0D, getVisual(trigger).preview_distance) : zoneHorizontalExtent(trigger) + 2.0D;

        int horizontal = Mth.clamp((int) Math.ceil(searchDistance), 1, 32);

        int vertical = preview ? Mth.clamp((int) Math.ceil(Math.max(1.0D, anchor.search_height)), 1, 16) : Mth.clamp((int) Math.ceil(zoneVerticalExtent(trigger) + 2.0D), 1, 16);

        BlockPos playerPos = player.blockPosition();

        String target = anchor.target != null ? anchor.target : trigger.target;

        boolean all = "all".equalsIgnoreCase(anchor.pick);

        ResolvedZone nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        List<ResolvedZone> allZones = all ? new ArrayList<>() : null;

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-horizontal, -vertical, -horizontal), playerPos.offset(horizontal, vertical, horizontal))) {

            BlockState state = level.getBlockState(pos);

            if (!matchesBlock(state, target)) {
                continue;
            }
            Vec3 center = applyOffset(new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D), anchor);

            if (preview && player.position().distanceToSqr(center) > searchDistance * searchDistance) {
                continue;
            }

            ResolvedZone zone = new ResolvedZone(center, null, "block:" + pos.asLong());

            if (all) {
                allZones.add(zone);

                if (allZones.size() >= 64) {
                    break;
                }

                continue;
            }

            double distance = player.position().distanceToSqr(center);

            if (distance < nearestDistance) {
                nearestDistance = distance;

                nearest = zone;
            }
        }

        if (all) {
            allZones.sort(Comparator.comparingDouble(zone -> player.position().distanceToSqr(zone.center)));

            return allZones;
        }

        return nearest != null ? List.of(nearest) : List.of();
    }


    private static boolean isInsideZone(Vec3 playerPos, Vec3 center, DialogueDefinition.Trigger trigger) {
        String shape = normalizeShape(trigger.shape);

        double dx = playerPos.x - center.x;

        double dy = playerPos.y - center.y;

        double dz = playerPos.z - center.z;

        return switch (shape) {

            case "sphere" -> {
                double radius = Math.max(0.1D, trigger.radius);

                yield dx * dx + dy * dy + dz * dz <= radius * radius;
            }

            case "box" -> {
                double halfX = Math.max(0.1D, trigger.size_x) * 0.5D;

                double halfZ = Math.max(0.1D, trigger.size_z) * 0.5D;

                double height = Math.max(0.1D, trigger.size_y);

                yield Math.abs(dx) <= halfX && Math.abs(dz) <= halfZ && dy >= 0.0D && dy <= height;
            }

            default -> {
                double radius = Math.max(0.1D, trigger.radius);

                double height = Math.max(0.1D, trigger.height);

                yield dx * dx + dz * dz <= radius * radius && dy >= 0.0D && dy <= height;
            }
        };
    }


    private static double zoneHorizontalExtent(DialogueDefinition.Trigger trigger) {
        return switch (normalizeShape(trigger.shape)) {
            case "box" -> Math.max(trigger.size_x, trigger.size_z) * 0.5D;

            default -> Math.max(0.1D, trigger.radius);
        };
    }


    private static double zoneVerticalExtent(DialogueDefinition.Trigger trigger) {
        return switch (normalizeShape(trigger.shape)) {
            case "sphere" -> Math.max(0.1D, trigger.radius);

            case "box" -> Math.max(0.1D, trigger.size_y);

            default -> Math.max(0.1D, trigger.height);
        };
    }


    private static String normalizeShape(String shape) {
        if (shape == null) {
            return "cylinder";
        }

        shape = shape.trim().toLowerCase(Locale.ROOT);

        return switch (shape) {
            case "sphere", "box", "cylinder" -> shape;

            default -> "cylinder";
        };
    }


    private static Vec3 applyOffset(Vec3 center, DialogueDefinition.ZoneAnchor anchor) {
        return center.add(anchor.offset_x, anchor.offset_y, anchor.offset_z);
    }


    private static DialogueDefinition.ZoneVisual getVisual(DialogueDefinition.Trigger trigger) {
        return trigger.visual != null ? trigger.visual : new DialogueDefinition.ZoneVisual();
    }


    private static boolean matchesEntityTag(Entity entity, String tag) {
        return tag == null || tag.isBlank() || entity.getTags().contains(tag);
    }


    private static boolean dimensionMatches(ServerPlayer player, DialogueDefinition.Trigger trigger) {
        return trigger.dimension == null || trigger.dimension.isBlank() || player.level().dimension().location().toString().equals(trigger.dimension);
    }


    public static boolean fireExternal(ServerPlayer player, Entity source, String event) {
        for (Map.Entry<ResourceLocation, DialogueDefinition> entry : DialogueRegistry.entries().entrySet()) {

            DialogueDefinition definition = entry.getValue();

            if (definition.triggers == null) {
                continue;
            }

            for (DialogueDefinition.Trigger trigger : definition.triggers) {

                if (trigger == null || !"external".equalsIgnoreCase(trigger.type)) {
                    continue;
                }

                if (trigger.event == null || !trigger.event.equals(event)) {
                    continue;
                }

                if (source != null && !matchesEntity(source, trigger.target)) {
                    continue;
                }

                if (start(player, source, entry.getKey(), definition, trigger)) {
                    return true;
                }
            }
        }

        return false;
    }


    private static boolean start(ServerPlayer player, Entity source, ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger) {
        return start(player, source, id, definition, trigger, null);
    }

    private static boolean start(ServerPlayer player, Entity source, ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger, String instanceKey) {
        if (DialogueSessionManager.isActive(player)) {
            return false;
        }

        String sourceKey;

        if (instanceKey != null) {
            sourceKey = instanceKey;

        } else if (source != null) {
            sourceKey = source.getUUID().toString();

        } else {
            sourceKey = "none";
        }

        String cooldownKey = player.getUUID() + "|" + id + "|" + sourceKey;

        long now = player.serverLevel().getGameTime();

        long last = COOLDOWNS.getOrDefault(cooldownKey, Long.MIN_VALUE / 2);

        if (now - last < Math.max(0, trigger.cooldown_ticks)) {
            return false;
        }

        boolean started = DialogueSessionManager.start(player, source, id, trigger.once, null);

        if (started) {
            COOLDOWNS.put(cooldownKey, now);
        }

        return started;
    }


    private static boolean matchesEntity(Entity entity, String target) {
        if (target == null || target.isBlank() || target.equals("*")) {
            return true;
        }

        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));

            if (id == null) {
                return false;
            }

            TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, id);

            return entity.getType().is(tag);
        }

        ResourceLocation id = ResourceLocation.tryParse(target);

        return id != null && id.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }


    private static boolean matchesBlock(BlockState state, String target) {
        if (target == null || target.isBlank() || target.equals("*")) {
            return true;
        }

        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));

            if (id == null) {
                return false;
            }

            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);

            return state.is(tag);
        }

        ResourceLocation id = ResourceLocation.tryParse(target);

        return id != null && id.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }


    private static boolean isLookingAt(ServerPlayer player, Entity target, double angleDegrees) {
        if (!player.hasLineOfSight(target)) {
            return false;
        }

        Vec3 look = player.getLookAngle().normalize();

        Vec3 direction = target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();

        double minimumDot = Math.cos(Math.toRadians(Math.max(1.0D, angleDegrees)));

        return look.dot(direction) >= minimumDot;
    }


    private static void forEachTrigger(String type, TriggerVisitor visitor) {
        for (Map.Entry<ResourceLocation, DialogueDefinition> entry : DialogueRegistry.entries().entrySet()) {

            DialogueDefinition definition = entry.getValue();

            if (definition.triggers == null) {
                continue;
            }

            for (DialogueDefinition.Trigger trigger : definition.triggers) {

                if (trigger == null || trigger.type == null || !trigger.type.equalsIgnoreCase(type)) {
                    continue;
                }

                if (visitor.visit(entry.getKey(), definition, trigger)) {
                    return;
                }
            }
        }
    }


    private record ResolvedZone(Vec3 center, Entity source, String key) {
    }


    @FunctionalInterface
    private interface TriggerVisitor {
        boolean visit(ResourceLocation id, DialogueDefinition definition, DialogueDefinition.Trigger trigger);
    }
}