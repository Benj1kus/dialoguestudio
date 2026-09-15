package com.benji.dialoguestudio.client.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.network.dialogueengine.DialogueInteractiveMarkerS2CPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueInteractiveMarkerRenderer {

    private static final String DEFAULT_TEXTURE = "dlgstd:textures/gui/dialogue/interactive_arrow.png";
    private static final double TAU = Math.PI * 2.0D;

    private static List<DialogueInteractiveMarkerS2CPacket.Marker> markers = List.of();

    private DialogueInteractiveMarkerRenderer() {
    }

    public static void setMarkers(List<DialogueInteractiveMarkerS2CPacket.Marker> newMarkers) {
        markers = newMarkers != null ? List.copyOf(newMarkers) : List.of();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null) {
            markers = List.of();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || markers.isEmpty()) {
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

        buffers.endBatch();
        poseStack.popPose();
    }

    private static void renderMarker(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueInteractiveMarkerS2CPacket.Marker marker, float partialTick, float timeSeconds) {
        double x = marker.x();
        double y = marker.y();
        double z = marker.z();

        if (marker.entityId() >= 0) {
            Entity entity = minecraft.level.getEntity(marker.entityId());

            if (entity == null || entity.isRemoved()) {
                return;
            }

            x = Mth.lerp(partialTick, entity.xOld, entity.getX());
            y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight();
            z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        }

        double maxDistance = Math.max(1.0D, marker.previewDistance());

        if (minecraft.player.distanceToSqr(x, y, z) > maxDistance * maxDistance) {
            return;
        }

        float phase = (marker.key().hashCode() & 0xFFFF) / 65535.0F * (float) (Math.PI * 2.0D);
        double bob = 0.0D;
        double scale = Math.max(0.10D, marker.size());
        float sway = 0.0F;

        if (marker.animated()) {
            if (marker.bob()) {
                bob = Math.sin(timeSeconds * TAU * marker.bobSpeed() + phase) * Math.max(0.0D, marker.bobAmplitude());
            }

            if (marker.pulse()) {
                double pulse = Math.sin(timeSeconds * TAU * marker.pulseSpeed() + phase * 0.73F);
                scale *= Math.max(0.05D, 1.0D + pulse * Math.max(0.0D, marker.pulseAmount()));
            }

            if (marker.sway()) {
                sway = (float) (Math.sin(timeSeconds * TAU * marker.swaySpeed() + phase * 1.37F) * Math.max(0.0D, marker.swayDegrees()));
            }
        }

        String textureId = marker.texture();
        ResourceLocation texture = ResourceLocation.tryParse(textureId == null || textureId.isBlank() ? DEFAULT_TEXTURE : textureId);

        if (texture == null) {
            texture = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/interactive_arrow.png");
        }

        poseStack.pushPose();
        poseStack.translate(x, y + marker.yOffset() + bob, z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway));

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

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }
}
