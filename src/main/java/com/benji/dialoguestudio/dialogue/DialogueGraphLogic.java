package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

public final class DialogueGraphLogic {

    private DialogueGraphLogic() {
    }

    public static boolean conditionsPass(ServerPlayer player, Entity source, ResourceLocation dialogueId, List<DialogueDefinition.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (DialogueDefinition.Condition condition : conditions) {
            if (condition == null) continue;

            boolean value = evaluateCondition(player, source, dialogueId, condition);

            if (condition.invert) {
                value = !value;
            }

            if (!value) {
                return false;
            }
        }

        return true;
    }

    public static void runActions(ServerPlayer player, Entity source, ResourceLocation dialogueId, String nodeId, List<DialogueDefinition.Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        for (DialogueDefinition.Action action : actions) {
            if (action == null || action.type == null) {
                continue;
            }

            String rawType = action.type.trim();

            String type = rawType.toLowerCase(Locale.ROOT);

            try {
                boolean handled = switch (type) {
                    case "external", "fire_external" -> {
                        fireExternal(player, source, dialogueId, nodeId, action);
                        yield true;
                    }

                    case "give_item" -> {
                        giveItem(player, action);
                        yield true;
                    }

                    case "take_item" -> {
                        takeItem(player, action);
                        yield true;
                    }

                    case "add_player_tag" -> {
                        Entity target = actionTarget(player, source, action.target);

                        if (target != null && action.id != null && !action.id.isBlank()) {
                            target.addTag(action.id);
                        }

                        yield true;
                    }

                    case "remove_player_tag" -> {
                        Entity target = actionTarget(player, source, action.target);

                        if (target != null && action.id != null && !action.id.isBlank()) {
                            target.removeTag(action.id);
                        }

                        yield true;
                    }

                    case "set_score" -> {
                        setScore(player, source, action, false);
                        yield true;
                    }

                    case "add_score" -> {
                        setScore(player, source, action, true);
                        yield true;
                    }

                    case "run_command" -> {
                        runCommand(player, source, dialogueId, nodeId, action);
                        yield true;
                    }

                    case "play_sound" -> {
                        playSound(player, source, action);
                        yield true;
                    }

                    case "particle" -> {
                        spawnParticle(player, source, action);
                        yield true;
                    }

                    case "teleport" -> {
                        teleport(player, source, action);
                        yield true;
                    }

                    case "kill" -> {
                        Entity target = actionTarget(player, source, action.target);

                        if (target != null) {
                            target.kill();
                        }

                        yield true;
                    }

                    case "quest_start" -> {
                        DialogueQuestState.set(player, action.id, "active");
                        yield true;
                    }

                    case "quest_complete" -> {
                        DialogueQuestState.set(player, action.id, "completed");
                        yield true;
                    }

                    case "quest_fail" -> {
                        DialogueQuestState.set(player, action.id, "failed");
                        yield true;
                    }

                    case "quest_reset" -> {
                        DialogueQuestState.reset(player, action.id);
                        yield true;
                    }

                    default -> false;
                };

                if (!handled) {
                    DialogueActionRegistry.execute(rawType, player, source, dialogueId, nodeId, action);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void fireExternal(ServerPlayer player, Entity source, ResourceLocation dialogueId, String nodeId, DialogueDefinition.Action action) {
        String eventId = action.event;

        if ((eventId == null || eventId.isBlank()) && action.id != null) {
            eventId = action.id;
        }

        if (eventId == null || eventId.isBlank()) {
            return;
        }

        MinecraftForge.EVENT_BUS.post(new DialogueNodeExternalEvent(player, source, dialogueId, nodeId, eventId, action));
    }

    private static void giveItem(ServerPlayer player, DialogueDefinition.Action action) {
        if (action.id == null || action.id.isBlank()) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(action.id);

        Item item = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;

        if (item == null) {
            return;
        }

        ItemStack stack = new ItemStack(item, Math.max(1, action.count));

        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void takeItem(ServerPlayer player, DialogueDefinition.Action action) {
        String wanted = action.id;

        if (wanted == null || wanted.isBlank()) {
            return;
        }

        int remaining = Math.max(1, action.count);

        ResourceLocation directId = null;
        TagKey<Item> tag = null;

        if (wanted.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(wanted.substring(1));

            if (tagId == null) {
                return;
            }

            tag = TagKey.create(Registries.ITEM, tagId);
        } else {
            directId = ResourceLocation.tryParse(wanted);

            if (directId == null) {
                return;
            }
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {

            ItemStack stack = player.getInventory().getItem(slot);

            if (stack.isEmpty()) {
                continue;
            }

            boolean matches;

            if (tag != null) {
                matches = stack.is(tag);
            } else {
                ResourceLocation actual = ForgeRegistries.ITEMS.getKey(stack.getItem());

                matches = directId.equals(actual);
            }

            if (!matches) {
                continue;
            }

            int remove = Math.min(remaining, stack.getCount());

            stack.shrink(remove);
            remaining -= remove;
        }
    }

    private static void setScore(ServerPlayer player, Entity source, DialogueDefinition.Action action, boolean add) {
        String objectiveName = action.objective;

        if (!validObjectiveName(objectiveName)) {
            return;
        }
        var scoreboard = player.getScoreboard();

        var objective = scoreboard.getObjective(objectiveName);

        if (objective == null) {
            executeRawCommand(player.createCommandSourceStack().withPermission(2).withSuppressedOutput(), "scoreboard objectives add " + objectiveName + " dummy");

            objective = scoreboard.getObjective(objectiveName);
        }

        if (objective == null) {
            return;
        }

        Entity target = actionTarget(player, source, action.target);

        if (target == null) {
            return;
        }

        var score = scoreboard.getOrCreatePlayerScore(target.getScoreboardName(), objective);

        if (add) {
            score.setScore(score.getScore() + action.value);
        } else {
            score.setScore(action.value);
        }
    }

    private static void runCommand(ServerPlayer player, Entity source, ResourceLocation dialogueId, String nodeId, DialogueDefinition.Action action) {
        String command = action.command;

        if (command == null || command.isBlank()) {
            return;
        }

        Entity contextEntity = actionTarget(player, source, action.target);

        CommandSourceStack stack = contextEntity != null ? contextEntity.createCommandSourceStack() : player.createCommandSourceStack();

        stack = stack.withPermission(2).withSuppressedOutput();

        String replaced = command.replace("{player}", player.getGameProfile().getName()).replace("{player_uuid}", player.getUUID().toString()).replace("{dialogue}", dialogueId != null ? dialogueId.toString() : "").replace("{node}", nodeId != null ? nodeId : "");

        if (source != null) {
            replaced = replaced.replace("{source_uuid}", source.getUUID().toString()).replace("{source_name}", source.getName().getString());
        } else {
            replaced = replaced.replace("{source_uuid}", "").replace("{source_name}", "");
        }

        executeRawCommand(stack, stripSlash(replaced));
    }

    private static void playSound(ServerPlayer player, Entity source, DialogueDefinition.Action action) {
        ResourceLocation sound = ResourceLocation.tryParse(action.id);

        if (sound == null) {
            return;
        }

        String category = validSoundSource(action.sound_source);

        float volume = Math.max(0.0F, action.volume);

        float pitch = Math.max(0.01F, action.sound_pitch);

        CommandSourceStack stack = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();

        executeRawCommand(stack, "playsound " + sound + " " + category + " " + player.getGameProfile().getName() + " ~ ~ ~ " + fmt(volume) + " " + fmt(pitch) + " 0");
    }

    private static void spawnParticle(ServerPlayer player, Entity source, DialogueDefinition.Action action) {
        ResourceLocation particle = ResourceLocation.tryParse(action.id);

        if (particle == null) {
            return;
        }

        Entity origin = actionTarget(player, source, action.target);

        if (origin == null) {
            origin = player;
        }

        CommandSourceStack stack = origin.createCommandSourceStack().withPermission(2).withSuppressedOutput();

        executeRawCommand(stack, "particle " + particle + " " + relative(action.x) + " " + relative(action.y) + " " + relative(action.z) + " " + fmt(Math.abs(action.spread_x)) + " " + fmt(Math.abs(action.spread_y)) + " " + fmt(Math.abs(action.spread_z)) + " " + fmt(Math.max(0.0D, action.speed)) + " " + Math.max(1, action.count) + " force " + player.getGameProfile().getName());
    }

    private static void teleport(ServerPlayer player, Entity source, DialogueDefinition.Action action) {
        Entity target = actionTarget(player, source, action.target);

        if (target == null) {
            return;
        }

        double x = action.relative ? target.getX() + action.x : action.x;

        double y = action.relative ? target.getY() + action.y : action.y;

        double z = action.relative ? target.getZ() + action.z : action.z;

        float yaw = action.yaw != null ? action.yaw : target.getYRot();

        float pitch = action.teleport_pitch != null ? action.teleport_pitch : target.getXRot();

        if (target instanceof ServerPlayer targetPlayer) {
            ServerLevel level = targetPlayer.serverLevel();

            if (action.dimension != null && !action.dimension.isBlank()) {

                ResourceLocation dimensionId = ResourceLocation.tryParse(action.dimension);

                if (dimensionId != null && targetPlayer.getServer() != null) {

                    ServerLevel requested = targetPlayer.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));

                    if (requested != null) {
                        level = requested;
                    }
                }
            }

            targetPlayer.teleportTo(level, x, y, z, yaw, pitch);

            return;
        }

        target.teleportTo(x, y, z);

        target.setYRot(yaw);
        target.setXRot(pitch);
    }

    private static Entity actionTarget(ServerPlayer player, Entity source, String value) {
        return "source".equalsIgnoreCase(value) ? source : player;
    }

    private static boolean evaluateCondition(ServerPlayer player, Entity source, ResourceLocation dialogueId, DialogueDefinition.Condition condition) {
        String rawType = condition.type != null ? condition.type.trim() : "always";

        String type = rawType.toLowerCase(Locale.ROOT);

        return switch (type) {
            case "always" -> true;

            case "player_tag" -> condition.id != null && player.getTags().contains(condition.id);

            case "source_tag" -> source != null && condition.id != null && source.getTags().contains(condition.id);

            case "dimension" ->
                    condition.id != null && player.level().dimension().location().toString().equals(condition.id);

            case "mod_loaded" -> condition.id != null && ModList.get().isLoaded(condition.id);

            case "source_type" -> matchesSourceType(source, condition.id);

            case "has_item" -> hasItem(player, condition.id, Math.max(1, condition.count));

            case "score" -> scoreMatches(player, condition.objective, condition.operator, condition.value);

            case "quest_state" -> DialogueQuestState.matches(player, condition.id, condition.state);

            default -> DialogueConditionRegistry.evaluate(rawType, player, source, dialogueId, condition);
        };
    }

    private static boolean matchesSourceType(Entity source, String value) {
        if (source == null || value == null || value.isBlank()) {
            return false;
        }

        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));

            if (tagId == null) {
                return false;
            }

            return source.getType().is(TagKey.create(Registries.ENTITY_TYPE, tagId));
        }

        ResourceLocation wanted = ResourceLocation.tryParse(value);

        ResourceLocation actual = ForgeRegistries.ENTITY_TYPES.getKey(source.getType());

        return wanted != null && wanted.equals(actual);
    }

    private static boolean hasItem(ServerPlayer player, String value, int wantedCount) {
        if (value == null || value.isBlank()) {
            return false;
        }

        int found = 0;

        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));

            if (tagId == null) {
                return false;
            }

            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);

            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {

                ItemStack stack = player.getInventory().getItem(slot);

                if (!stack.isEmpty() && stack.is(tag)) {
                    found += stack.getCount();

                    if (found >= wantedCount) {
                        return true;
                    }
                }
            }

            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(value);

        Item item = id != null ? ForgeRegistries.ITEMS.getValue(id) : null;

        if (item == null) {
            return false;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {

            ItemStack stack = player.getInventory().getItem(slot);

            if (!stack.isEmpty() && stack.is(item)) {
                found += stack.getCount();

                if (found >= wantedCount) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean scoreMatches(ServerPlayer player, String objectiveName, String operator, int expected) {
        if (objectiveName == null || objectiveName.isBlank()) {
            return false;
        }

        var scoreboard = player.getScoreboard();

        var objective = scoreboard.getObjective(objectiveName);

        if (objective == null) {
            return false;
        }

        int actual = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore();

        String op = operator != null ? operator.trim() : ">=";

        return switch (op) {
            case "=", "==" -> actual == expected;
            case "!=" -> actual != expected;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            default -> actual >= expected;
        };
    }

    private static void executeRawCommand(CommandSourceStack stack, String command) {
        if (stack == null || command == null || command.isBlank() || stack.getServer() == null) {
            return;
        }

        stack.getServer().getCommands().performPrefixedCommand(stack, stripSlash(command));
    }

    private static boolean validObjectiveName(String value) {
        return value != null && value.matches("[A-Za-z0-9._+\\-]{1,16}");
    }

    private static String validSoundSource(String value) {
        String source = value != null ? value.toLowerCase(Locale.ROOT) : "master";

        return switch (source) {
            case "master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient",
                 "voice" -> source;

            default -> "master";
        };
    }

    private static String relative(double value) {
        if (Math.abs(value) < 0.000001D) {
            return "~";
        }

        return "~" + fmt(value);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String stripSlash(String command) {
        String value = command != null ? command.trim() : "";

        while (value.startsWith("/")) {
            value = value.substring(1).trim();
        }

        return value;
    }
}
