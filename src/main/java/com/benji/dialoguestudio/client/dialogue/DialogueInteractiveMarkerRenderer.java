package com.benji.dialoguestudio.client.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.network.dialogueengine.DialogueInteractiveMarkerS2CPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueInteractiveMarkerRenderer {

    private static final String DEFAULT_TEXTURE = "dlgstd:textures/gui/dialogue/interactive_arrow.png";
    private static final double TAU = Math.PI * 2.0D;

    private static List<DialogueInteractiveMarkerS2CPacket.Marker> markers = List.of();
    private static List<DialogueInteractiveMarkerS2CPacket.MarkerText> texts = List.of();

    private DialogueInteractiveMarkerRenderer() {
    }

    public static void setMarkers(List<DialogueInteractiveMarkerS2CPacket.Marker> newMarkers) {
        markers = newMarkers != null ? List.copyOf(newMarkers) : List.of();
        texts = List.of();
    }

    public static void setEntries(List<DialogueInteractiveMarkerS2CPacket.Marker> newMarkers, List<DialogueInteractiveMarkerS2CPacket.MarkerText> newTexts) {
        markers = newMarkers != null ? List.copyOf(newMarkers) : List.of();
        texts = newTexts != null ? List.copyOf(newTexts) : List.of();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null) {
            markers = List.of();
            texts = List.of();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || (markers.isEmpty() && texts.isEmpty())) {
            return;
        }

        float partialTick = minecraft.getFrameTime();
        float timeSeconds = (minecraft.level.getGameTime() + partialTick) / 20.0F;
        Vec3 camera = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        for (DialogueInteractiveMarkerS2CPacket.Marker marker : markers) {
            renderMarker(minecraft, poseStack, buffers, marker, partialTick, timeSeconds);
        }

        for (DialogueInteractiveMarkerS2CPacket.MarkerText text : texts) {
            renderText(minecraft, poseStack, buffers, text, partialTick, timeSeconds);
        }

        buffers.endBatch();
        poseStack.popPose();
    }

    private static void renderMarker(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueInteractiveMarkerS2CPacket.Marker marker, float partialTick, float timeSeconds) {
        Vec3 position = resolvePosition(minecraft, marker.entityId(), marker.x(), marker.y(), marker.z(), partialTick);

        if (position == null || !withinDistance(minecraft, position, marker.previewDistance())) {
            return;
        }

        AnimationState animation = animation(marker.key(), marker.animated(), marker.bob(), marker.bobAmplitude(), marker.bobSpeed(), marker.pulse(), marker.pulseAmount(), marker.pulseSpeed(), marker.sway(), marker.swayDegrees(), marker.swaySpeed(), timeSeconds);

        String textureId = marker.texture();
        ResourceLocation texture = ResourceLocation.tryParse(textureId == null || textureId.isBlank() ? DEFAULT_TEXTURE : textureId);

        if (texture == null) {
            texture = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/interactive_arrow.png");
        }

        double scale = Math.max(0.10D, marker.size()) * animation.scale;

        poseStack.pushPose();
        poseStack.translate(position.x, position.y + marker.yOffset() + animation.bob, position.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(animation.sway));

        float half = (float) scale * 0.5F;
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));

        vertex(consumer, matrix, normal, -half, -half, 0.0F, 0.0F, 1.0F);
        vertex(consumer, matrix, normal, -half, half, 0.0F, 0.0F, 0.0F);
        vertex(consumer, matrix, normal, half, half, 0.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, normal, half, -half, 0.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }

    private static void renderText(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueInteractiveMarkerS2CPacket.MarkerText text, float partialTick, float timeSeconds) {
        if (text.text() == null || text.text().isBlank()) {
            return;
        }

        Vec3 position = resolvePosition(minecraft, text.entityId(), text.x(), text.y(), text.z(), partialTick);

        if (position == null || !withinDistance(minecraft, position, text.previewDistance())) {
            return;
        }

        AnimationState animation = animation(text.key(), text.animated(), text.bob(), text.bobAmplitude(), text.bobSpeed(), text.pulse(), text.pulseAmount(), text.pulseSpeed(), text.sway(), text.swayDegrees(), text.swaySpeed(), timeSeconds);

        float scale = (float) (0.025D * Math.max(0.25D, text.scale()) * animation.scale);
        int color = parseColor(text.color());
        int background = text.background() ? (Mth.clamp(Math.round(text.backgroundAlpha() * 255.0F), 0, 255) << 24) : 0;

        poseStack.pushPose();
        poseStack.translate(position.x, position.y + text.yOffset() + animation.bob, position.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(animation.sway));
        poseStack.scale(-scale, -scale, scale);

        Font font = minecraft.font;
        float x = -font.width(text.text()) / 2.0F;
        
        if (text.shadow()) {
            font.drawInBatch(text.text(), x + 1.0F, 1.0F, shadowColor(color), false, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        }

        font.drawInBatch(text.text(), x, 0.0F, color, false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);

        poseStack.popPose();
    }


    private static int shadowColor(int color) {
        int alpha = color & 0xFF000000;
        int red = ((color >> 16) & 0xFF) / 4;
        int green = ((color >> 8) & 0xFF) / 4;
        int blue = (color & 0xFF) / 4;

        return alpha | (red << 16) | (green << 8) | blue;
    }

    private static Vec3 resolvePosition(Minecraft minecraft, int entityId, double x, double y, double z, float partialTick) {
        if (entityId < 0) {
            return new Vec3(x, y, z);
        }

        Entity entity = minecraft.level.getEntity(entityId);

        if (entity == null || entity.isRemoved()) {
            return null;
        }

        return new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()), Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight(), Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    private static boolean withinDistance(Minecraft minecraft, Vec3 position, double previewDistance) {
        double maxDistance = Math.max(1.0D, previewDistance);
        return minecraft.player.distanceToSqr(position.x, position.y, position.z) <= maxDistance * maxDistance;
    }

    private static AnimationState animation(String key, boolean animated, boolean bobEnabled, double bobAmplitude, double bobSpeed, boolean pulseEnabled, double pulseAmount, double pulseSpeed, boolean swayEnabled, double swayDegrees, double swaySpeed, float timeSeconds) {
        if (!animated) {
            return AnimationState.IDENTITY;
        }

        float phase = (key.hashCode() & 0xFFFF) / 65535.0F * (float) TAU;

        double bob = bobEnabled ? Math.sin(timeSeconds * TAU * bobSpeed + phase) * Math.max(0.0D, bobAmplitude) : 0.0D;

        double scale = 1.0D;

        if (pulseEnabled) {
            double pulse = Math.sin(timeSeconds * TAU * pulseSpeed + phase * 0.73F);
            scale = Math.max(0.05D, 1.0D + pulse * Math.max(0.0D, pulseAmount));
        }

        float sway = swayEnabled ? (float) (Math.sin(timeSeconds * TAU * swaySpeed + phase * 1.37F) * Math.max(0.0D, swayDegrees)) : 0.0F;

        return new AnimationState(bob, scale, sway);
    }

    private static int parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0xFFFFFFFF;
        }

        String value = raw.trim();

        if (value.startsWith("#")) {
            try {
                int rgb = Integer.parseUnsignedInt(value.substring(1), 16);
                if (value.length() <= 7) {
                    rgb |= 0xFF000000;
                }
                return rgb;
            } catch (NumberFormatException ignored) {
            }
        }

        ChatFormatting formatting = ChatFormatting.getByName(value.toLowerCase(Locale.ROOT));

        if (formatting != null && formatting.getColor() != null) {
            return 0xFF000000 | formatting.getColor();
        }

        return 0xFFFFFFFF;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }

    private record AnimationState(double bob, double scale, float sway) {
        private static final AnimationState IDENTITY = new AnimationState(0.0D, 1.0D, 0.0F);
    }
}
