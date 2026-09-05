package com.benji.dialoguestudio.client.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.network.dialogueengine.DialogueZonePreviewS2CPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueZonePreviewRenderer {

    private static final double TAU = Math.PI * 2.0D;
    private static List<DialogueZonePreviewS2CPacket.Zone> zones = List.of();

    private DialogueZonePreviewRenderer() {
    }

    public static void setZones(List<DialogueZonePreviewS2CPacket.Zone> newZones) {
        zones = newZones != null ? List.copyOf(newZones) : List.of();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (Minecraft.getInstance().level == null) {
            zones = List.of();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || zones.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float timeTicks = minecraft.level.getGameTime() + minecraft.getFrameTime();
        Vec3 playerPos = minecraft.player.position();

        for (DialogueZonePreviewS2CPacket.Zone zone : zones) {
            renderZone(poseStack, buffers, zone, playerPos, timeTicks);
        }

        buffers.endBatch();
        poseStack.popPose();
    }

    private static void renderZone(PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueZonePreviewS2CPacket.Zone zone, Vec3 playerPos, float timeTicks) {
        Vec3 originalCenter = new Vec3(zone.x(), zone.y(), zone.z());
        float distanceFade = distanceFade(playerPos.distanceTo(originalCenter), zone.previewDistance());

        if (distanceFade <= 0.001F) {
            return;
        }

        AnimationState animation = animation(zone, timeTicks, distanceFade);

        if (zone.fillEnabled()) {
            renderFill(poseStack, buffers, zone, animation);
        }

        if (zone.texture() != null && !zone.texture().isBlank()) {
            renderTexture(poseStack, buffers, zone, animation, timeTicks);
        }

        String style = resolveStyle(zone);
        boolean legacySprite = "sprite".equals(style);

        if (zone.showDefaultZone() && !legacySprite) {
            int color = parseColor(zone.color());

            switch (style) {
                case "pillar" -> renderPillar(poseStack, buffers, zone, color, animation);
                case "outline" -> renderOutline(poseStack, buffers, zone, color, animation);
                default -> renderRing(poseStack, buffers, zone, color, animation);
            }
        }
    }

    private static AnimationState animation(DialogueZonePreviewS2CPacket.Zone zone, float timeTicks, float distanceFade) {
        double seconds = timeTicks / 20.0D;

        double scale = 1.0D;
        if (zone.pulse()) {
            scale += Math.sin(seconds * TAU * Math.max(0.0D, zone.pulseSpeed())) * Math.max(0.0D, zone.pulseAmplitude());
        }
        scale = Math.max(0.05D, scale);

        double bob = 0.0D;
        if (zone.bob()) {
            bob = Math.sin(seconds * TAU * Math.max(0.0D, zone.bobSpeed())) * Math.max(0.0D, zone.bobAmplitude());
        }

        double rotation = 0.0D;
        if (zone.rotate()) {
            rotation = Math.toRadians(seconds * zone.rotateSpeed());
        }

        double alphaMultiplier = 1.0D;
        if (zone.alphaBreathe()) {
            double amount = Mth.clamp(zone.alphaBreatheAmount(), 0.0D, 1.0D);
            double wave = 0.5D + 0.5D * Math.sin(seconds * TAU * Math.max(0.0D, zone.alphaBreatheSpeed()));
            alphaMultiplier = 1.0D - amount + wave * amount;
        }

        float alpha = Mth.clamp((float) (zone.alpha() * distanceFade * alphaMultiplier), 0.0F, 1.0F);
        double baseY = zone.y() + zone.yOffset() + bob;

        return new AnimationState(scale, baseY, rotation, alpha);
    }

    private static String resolveStyle(DialogueZonePreviewS2CPacket.Zone zone) {
        String style = normalized(zone.style(), "auto");

        if (!"auto".equals(style)) {
            return style;
        }

        String shape = normalized(zone.shape(), "cylinder");
        return "cylinder".equals(shape) ? "ring" : "outline";
    }

    private static void renderFill(PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation) {
        String shape = normalized(zone.shape(), "cylinder");
        if (!"cylinder".equals(shape) && !"box".equals(shape)) {
            return;
        }

        int bottomColor = parseColor(zone.fillColorBottom());
        int topColor = "solid".equals(normalized(zone.fillMode(), "gradient")) ? bottomColor : parseColor(zone.fillColorTop());

        float bottomAlpha = animation.alpha * Mth.clamp(zone.fillAlphaBottom(), 0.0F, 1.0F);
        float topAlpha = animation.alpha * Mth.clamp("solid".equals(normalized(zone.fillMode(), "gradient")) ? zone.fillAlphaBottom() : zone.fillAlphaTop(), 0.0F, 1.0F);

        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());

        if ("box".equals(shape)) {
            drawBoxSidesFilled(consumer, poseStack, zone, animation, bottomColor, topColor, bottomAlpha, topAlpha);
        } else {
            drawCylinderSidesFilled(consumer, poseStack, zone, animation, bottomColor, topColor, bottomAlpha, topAlpha);
        }
    }

    private static void renderTexture(PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation, float timeTicks) {
        ResourceLocation texture = ResourceLocation.tryParse(zone.texture());
        if (texture == null) {
            return;
        }

        String mode = normalized(zone.textureMode(), "plane");
        RenderType renderType = RenderType.entityTranslucent(texture);
        VertexConsumer consumer = buffers.getBuffer(renderType);

        double seconds = timeTicks / 20.0D;
        double scrollU = seconds * zone.textureScrollU();
        double scrollV = seconds * zone.textureScrollV();

        double repeatU = "repeat".equals(normalized(zone.textureFit(), "stretch")) ? Math.max(0.01D, zone.textureRepeatX()) : 1.0D;
        double repeatV = "repeat".equals(normalized(zone.textureFit(), "stretch")) ? Math.max(0.01D, zone.textureRepeatY()) : 1.0D;

        switch (mode) {
            case "cylinder_wrap" ->
                    drawTexturedCylinder(consumer, poseStack, zone, animation, repeatU, repeatV, scrollU, scrollV);
            case "box_wrap" ->
                    drawTexturedBox(consumer, poseStack, zone, animation, repeatU, repeatV, scrollU, scrollV);
            default -> drawTexturedPlane(consumer, poseStack, zone, animation, repeatU, repeatV, scrollU, scrollV);
        }
    }

    private static void renderRing(PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueZonePreviewS2CPacket.Zone zone, int color, AnimationState animation) {
        double radius = markerRadius(zone) * animation.scale;
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        drawCircleXZ(consumer, poseStack, zone.x(), animation.baseY, zone.z(), radius, color, animation.alpha);
        drawCircleXZ(consumer, poseStack, zone.x(), animation.baseY + 0.004D, zone.z(), radius * 0.94D, color, animation.alpha * 0.45F);
    }

    private static void renderOutline(PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueZonePreviewS2CPacket.Zone zone, int color, AnimationState animation) {
        String shape = normalized(zone.shape(), "cylinder");
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        switch (shape) {
            case "sphere" -> drawSphereOutline(consumer, poseStack, zone, color, animation);
            case "box" -> drawBoxOutline(consumer, poseStack, zone, color, animation);
            default -> drawCylinderOutline(consumer, poseStack, zone, color, animation);
        }
    }

    private static void renderPillar(PoseStack poseStack, MultiBufferSource.BufferSource buffers, DialogueZonePreviewS2CPacket.Zone zone, int color, AnimationState animation) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        double radius = markerRadius(zone) * animation.scale;
        double topY = animation.baseY + markerHeight(zone);

        drawCircleXZ(consumer, poseStack, zone.x(), animation.baseY, zone.z(), radius, color, animation.alpha);
        drawCircleXZ(consumer, poseStack, zone.x(), topY, zone.z(), radius, color, animation.alpha * 0.65F);

        for (int i = 0; i < 8; i++) {
            double angle = TAU * i / 8.0D + animation.rotation;
            double x = zone.x() + Math.cos(angle) * radius;
            double z = zone.z() + Math.sin(angle) * radius;
            line(consumer, poseStack, x, animation.baseY, z, x, topY, z, color, animation.alpha * 0.40F);
        }
    }

    private static void drawCylinderOutline(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, int color, AnimationState animation) {
        double radius = markerRadius(zone) * animation.scale;
        double topY = animation.baseY + markerHeight(zone);

        drawCircleXZ(consumer, poseStack, zone.x(), animation.baseY, zone.z(), radius, color, animation.alpha);
        drawCircleXZ(consumer, poseStack, zone.x(), topY, zone.z(), radius, color, animation.alpha * 0.65F);

        for (int i = 0; i < 4; i++) {
            double angle = TAU * i / 4.0D + animation.rotation;
            double x = zone.x() + Math.cos(angle) * radius;
            double z = zone.z() + Math.sin(angle) * radius;
            line(consumer, poseStack, x, animation.baseY, z, x, topY, z, color, animation.alpha * 0.45F);
        }
    }

    private static void drawSphereOutline(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, int color, AnimationState animation) {
        double radius = markerRadius(zone) * animation.scale;
        drawCircleXZ(consumer, poseStack, zone.x(), animation.baseY, zone.z(), radius, color, animation.alpha);
        drawCircleXY(consumer, poseStack, zone.x(), animation.baseY, zone.z(), radius, color, animation.alpha * 0.65F);
        drawCircleYZ(consumer, poseStack, zone.x(), animation.baseY, zone.z(), radius, color, animation.alpha * 0.65F);
    }

    private static void drawBoxOutline(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, int color, AnimationState animation) {
        double halfX = markerHalfX(zone) * animation.scale;
        double halfZ = markerHalfZ(zone) * animation.scale;
        double topY = animation.baseY + markerHeight(zone);

        Vec3 b0 = rotateXZ(zone.x(), animation.baseY, zone.z(), -halfX, -halfZ, animation.rotation);
        Vec3 b1 = rotateXZ(zone.x(), animation.baseY, zone.z(), halfX, -halfZ, animation.rotation);
        Vec3 b2 = rotateXZ(zone.x(), animation.baseY, zone.z(), halfX, halfZ, animation.rotation);
        Vec3 b3 = rotateXZ(zone.x(), animation.baseY, zone.z(), -halfX, halfZ, animation.rotation);

        Vec3 t0 = new Vec3(b0.x, topY, b0.z);
        Vec3 t1 = new Vec3(b1.x, topY, b1.z);
        Vec3 t2 = new Vec3(b2.x, topY, b2.z);
        Vec3 t3 = new Vec3(b3.x, topY, b3.z);

        line(consumer, poseStack, b0, b1, color, animation.alpha);
        line(consumer, poseStack, b1, b2, color, animation.alpha);
        line(consumer, poseStack, b2, b3, color, animation.alpha);
        line(consumer, poseStack, b3, b0, color, animation.alpha);

        line(consumer, poseStack, t0, t1, color, animation.alpha * 0.65F);
        line(consumer, poseStack, t1, t2, color, animation.alpha * 0.65F);
        line(consumer, poseStack, t2, t3, color, animation.alpha * 0.65F);
        line(consumer, poseStack, t3, t0, color, animation.alpha * 0.65F);

        line(consumer, poseStack, b0, t0, color, animation.alpha * 0.45F);
        line(consumer, poseStack, b1, t1, color, animation.alpha * 0.45F);
        line(consumer, poseStack, b2, t2, color, animation.alpha * 0.45F);
        line(consumer, poseStack, b3, t3, color, animation.alpha * 0.45F);
    }

    private static void drawCylinderSidesFilled(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation, int bottomColor, int topColor, float bottomAlpha, float topAlpha) {
        final int segments = 64;
        double radius = markerRadius(zone) * animation.scale;
        double topY = animation.baseY + markerHeight(zone);

        for (int i = 0; i < segments; i++) {
            double a = TAU * i / segments + animation.rotation;
            double b = TAU * (i + 1) / segments + animation.rotation;

            Vec3 p0 = new Vec3(zone.x() + Math.cos(a) * radius, animation.baseY, zone.z() + Math.sin(a) * radius);
            Vec3 p1 = new Vec3(zone.x() + Math.cos(b) * radius, animation.baseY, zone.z() + Math.sin(b) * radius);
            Vec3 p2 = new Vec3(zone.x() + Math.cos(b) * radius, topY, zone.z() + Math.sin(b) * radius);
            Vec3 p3 = new Vec3(zone.x() + Math.cos(a) * radius, topY, zone.z() + Math.sin(a) * radius);

            gradientQuad(consumer, poseStack, p0, p1, p2, p3, bottomColor, bottomColor, topColor, topColor, bottomAlpha, bottomAlpha, topAlpha, topAlpha);
        }
    }

    private static void drawBoxSidesFilled(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation, int bottomColor, int topColor, float bottomAlpha, float topAlpha) {
        double halfX = markerHalfX(zone) * animation.scale;
        double halfZ = markerHalfZ(zone) * animation.scale;
        double topY = animation.baseY + markerHeight(zone);

        Vec3[] b = new Vec3[]{rotateXZ(zone.x(), animation.baseY, zone.z(), -halfX, -halfZ, animation.rotation), rotateXZ(zone.x(), animation.baseY, zone.z(), halfX, -halfZ, animation.rotation), rotateXZ(zone.x(), animation.baseY, zone.z(), halfX, halfZ, animation.rotation), rotateXZ(zone.x(), animation.baseY, zone.z(), -halfX, halfZ, animation.rotation)};

        Vec3[] t = new Vec3[]{new Vec3(b[0].x, topY, b[0].z), new Vec3(b[1].x, topY, b[1].z), new Vec3(b[2].x, topY, b[2].z), new Vec3(b[3].x, topY, b[3].z)};

        for (int i = 0; i < 4; i++) {
            int n = (i + 1) % 4;
            gradientQuad(consumer, poseStack, b[i], b[n], t[n], t[i], bottomColor, bottomColor, topColor, topColor, bottomAlpha, bottomAlpha, topAlpha, topAlpha);
        }
    }

    private static void drawTexturedPlane(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation, double repeatU, double repeatV, double scrollU, double scrollV) {
        double size = zone.visualSize() > 0.0D ? zone.visualSize() : defaultRadius(zone) * 2.0D;
        double halfX = Math.max(0.05D, size * animation.scale * Math.max(0.05D, zone.textureScaleX()) * 0.5D);
        double halfZ = Math.max(0.05D, size * animation.scale * Math.max(0.05D, zone.textureScaleY()) * 0.5D);

        Vec3 center = new Vec3(zone.x() + zone.textureOffsetX(), animation.baseY + zone.textureOffsetY() + 0.006D, zone.z() + zone.textureOffsetZ());

        double rotationX = Math.toRadians(zone.textureRotationX());
        double rotationY = animation.rotation + Math.toRadians(zone.textureRotationY());
        double rotationZ = Math.toRadians(zone.textureRotationZ());

        Vec3 p0 = transformTexturePoint(center, new Vec3(-halfX, 0.0D, -halfZ), rotationX, rotationY, rotationZ);
        Vec3 p1 = transformTexturePoint(center, new Vec3(-halfX, 0.0D, halfZ), rotationX, rotationY, rotationZ);
        Vec3 p2 = transformTexturePoint(center, new Vec3(halfX, 0.0D, halfZ), rotationX, rotationY, rotationZ);
        Vec3 p3 = transformTexturePoint(center, new Vec3(halfX, 0.0D, -halfZ), rotationX, rotationY, rotationZ);

        Vec3 normal = rotateTextureVector(new Vec3(0.0D, 1.0D, 0.0D), rotationX, rotationY, rotationZ).normalize();

        texturedQuad(consumer, poseStack, p0, p1, p2, p3, scrollU, scrollV + repeatV, scrollU, scrollV, scrollU + repeatU, scrollV, scrollU + repeatU, scrollV + repeatV, animation.alpha, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void drawTexturedCylinder(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation, double repeatU, double repeatV, double scrollU, double scrollV) {
        final int segments = 64;
        double radius = markerRadius(zone) * animation.scale * Math.max(0.05D, zone.textureScaleX());
        double height = markerHeight(zone) * Math.max(0.05D, zone.textureScaleY());
        double baseY = animation.baseY + zone.textureOffsetY();

        Vec3 center = new Vec3(zone.x() + zone.textureOffsetX(), baseY + height * 0.5D, zone.z() + zone.textureOffsetZ());

        double halfHeight = height * 0.5D;
        double rotationX = Math.toRadians(zone.textureRotationX());
        double rotationY = animation.rotation + Math.toRadians(zone.textureRotationY());
        double rotationZ = Math.toRadians(zone.textureRotationZ());

        for (int i = 0; i < segments; i++) {
            double fractionA = i / (double) segments;
            double fractionB = (i + 1) / (double) segments;
            double a = TAU * fractionA;
            double b = TAU * fractionB;

            Vec3 p0 = transformTexturePoint(center, new Vec3(Math.cos(a) * radius, -halfHeight, Math.sin(a) * radius), rotationX, rotationY, rotationZ);
            Vec3 p1 = transformTexturePoint(center, new Vec3(Math.cos(b) * radius, -halfHeight, Math.sin(b) * radius), rotationX, rotationY, rotationZ);
            Vec3 p2 = transformTexturePoint(center, new Vec3(Math.cos(b) * radius, halfHeight, Math.sin(b) * radius), rotationX, rotationY, rotationZ);
            Vec3 p3 = transformTexturePoint(center, new Vec3(Math.cos(a) * radius, halfHeight, Math.sin(a) * radius), rotationX, rotationY, rotationZ);

            double middle = (a + b) * 0.5D;
            Vec3 normal = rotateTextureVector(new Vec3(Math.cos(middle), 0.0D, Math.sin(middle)), rotationX, rotationY, rotationZ).normalize();

            texturedQuad(consumer, poseStack, p0, p1, p2, p3, scrollU + fractionA * repeatU, scrollV + repeatV, scrollU + fractionB * repeatU, scrollV + repeatV, scrollU + fractionB * repeatU, scrollV, scrollU + fractionA * repeatU, scrollV, animation.alpha, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private static void drawTexturedBox(VertexConsumer consumer, PoseStack poseStack, DialogueZonePreviewS2CPacket.Zone zone, AnimationState animation, double repeatU, double repeatV, double scrollU, double scrollV) {
        double halfX = markerHalfX(zone) * animation.scale * Math.max(0.05D, zone.textureScaleX());
        double halfZ = markerHalfZ(zone) * animation.scale * Math.max(0.05D, zone.textureScaleX());
        double height = markerHeight(zone) * Math.max(0.05D, zone.textureScaleY());
        double baseY = animation.baseY + zone.textureOffsetY();

        Vec3 center = new Vec3(zone.x() + zone.textureOffsetX(), baseY + height * 0.5D, zone.z() + zone.textureOffsetZ());

        double halfHeight = height * 0.5D;
        double rotationX = Math.toRadians(zone.textureRotationX());
        double rotationY = animation.rotation + Math.toRadians(zone.textureRotationY());
        double rotationZ = Math.toRadians(zone.textureRotationZ());

        Vec3[] b = new Vec3[]{transformTexturePoint(center, new Vec3(-halfX, -halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(center, new Vec3(halfX, -halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(center, new Vec3(halfX, -halfHeight, halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(center, new Vec3(-halfX, -halfHeight, halfZ), rotationX, rotationY, rotationZ)};

        Vec3[] t = new Vec3[]{transformTexturePoint(center, new Vec3(-halfX, halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(center, new Vec3(halfX, halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(center, new Vec3(halfX, halfHeight, halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(center, new Vec3(-halfX, halfHeight, halfZ), rotationX, rotationY, rotationZ)};

        double[] lengths = {halfX * 2.0D, halfZ * 2.0D, halfX * 2.0D, halfZ * 2.0D};
        double perimeter = Math.max(0.001D, (halfX + halfZ) * 4.0D);
        double uCursor = 0.0D;

        for (int i = 0; i < 4; i++) {
            int n = (i + 1) % 4;
            double nextU = uCursor + lengths[i] / perimeter * repeatU;

            Vec3 edge = b[n].subtract(b[i]);
            Vec3 vertical = t[i].subtract(b[i]);
            Vec3 normal = vertical.cross(edge).normalize();

            texturedQuad(consumer, poseStack, b[i], b[n], t[n], t[i], scrollU + uCursor, scrollV + repeatV, scrollU + nextU, scrollV + repeatV, scrollU + nextU, scrollV, scrollU + uCursor, scrollV, animation.alpha, (float) normal.x, (float) normal.y, (float) normal.z);

            uCursor = nextU;
        }
    }

    private static void texturedQuad(VertexConsumer consumer, PoseStack poseStack, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3, float alpha, float nx, float ny, float nz) {
        texturedVertex(consumer, poseStack, p0, u0, v0, alpha, nx, ny, nz);
        texturedVertex(consumer, poseStack, p1, u1, v1, alpha, nx, ny, nz);
        texturedVertex(consumer, poseStack, p2, u2, v2, alpha, nx, ny, nz);
        texturedVertex(consumer, poseStack, p3, u3, v3, alpha, nx, ny, nz);

        texturedVertex(consumer, poseStack, p3, u3, v3, alpha, -nx, -ny, -nz);
        texturedVertex(consumer, poseStack, p2, u2, v2, alpha, -nx, -ny, -nz);
        texturedVertex(consumer, poseStack, p1, u1, v1, alpha, -nx, -ny, -nz);
        texturedVertex(consumer, poseStack, p0, u0, v0, alpha, -nx, -ny, -nz);
    }

    private static void texturedVertex(VertexConsumer consumer, PoseStack poseStack, Vec3 position, double u, double v, float alpha, float nx, float ny, float nz) {
        PoseStack.Pose pose = poseStack.last();
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);

        consumer.vertex(pose.pose(), (float) position.x, (float) position.y, (float) position.z).color(255, 255, 255, alphaByte).uv((float) u, (float) v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private static void gradientQuad(VertexConsumer consumer, PoseStack poseStack, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int c0, int c1, int c2, int c3, float a0, float a1, float a2, float a3) {
        colorVertex(consumer, poseStack, p0, c0, a0);
        colorVertex(consumer, poseStack, p1, c1, a1);
        colorVertex(consumer, poseStack, p2, c2, a2);
        colorVertex(consumer, poseStack, p3, c3, a3);

        colorVertex(consumer, poseStack, p3, c3, a3);
        colorVertex(consumer, poseStack, p2, c2, a2);
        colorVertex(consumer, poseStack, p1, c1, a1);
        colorVertex(consumer, poseStack, p0, c0, a0);
    }

    private static void colorVertex(VertexConsumer consumer, PoseStack poseStack, Vec3 position, int color, float alpha) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);

        consumer.vertex(poseStack.last().pose(), (float) position.x, (float) position.y, (float) position.z).color(red, green, blue, alphaByte).endVertex();
    }

    private static Vec3 transformTexturePoint(Vec3 center, Vec3 local, double rotationX, double rotationY, double rotationZ) {
        return center.add(rotateTextureVector(local, rotationX, rotationY, rotationZ));
    }

    private static Vec3 rotateTextureVector(Vec3 vector, double rotationX, double rotationY, double rotationZ) {
        double x = vector.x;
        double y = vector.y;
        double z = vector.z;

        //X
        double cosX = Math.cos(rotationX);
        double sinX = Math.sin(rotationX);
        double yX = y * cosX - z * sinX;
        double zX = y * sinX + z * cosX;
        y = yX;
        z = zX;

        //Y
        double cosY = Math.cos(rotationY);
        double sinY = Math.sin(rotationY);
        double xY = x * cosY - z * sinY;
        double zY = x * sinY + z * cosY;
        x = xY;
        z = zY;

        //Z
        double cosZ = Math.cos(rotationZ);
        double sinZ = Math.sin(rotationZ);
        double xZ = x * cosZ - y * sinZ;
        double yZ = x * sinZ + y * cosZ;

        return new Vec3(xZ, yZ, z);
    }

    private static Vec3 rotateXZ(double centerX, double y, double centerZ, double localX, double localZ, double rotation) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        return new Vec3(centerX + localX * cos - localZ * sin, y, centerZ + localX * sin + localZ * cos);
    }

    private static void drawCircleXZ(VertexConsumer consumer, PoseStack poseStack, double centerX, double centerY, double centerZ, double radius, int color, float alpha) {
        final int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a = TAU * i / segments;
            double b = TAU * (i + 1) / segments;
            line(consumer, poseStack, centerX + Math.cos(a) * radius, centerY, centerZ + Math.sin(a) * radius, centerX + Math.cos(b) * radius, centerY, centerZ + Math.sin(b) * radius, color, alpha);
        }
    }

    private static void drawCircleXY(VertexConsumer consumer, PoseStack poseStack, double centerX, double centerY, double centerZ, double radius, int color, float alpha) {
        final int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a = TAU * i / segments;
            double b = TAU * (i + 1) / segments;
            line(consumer, poseStack, centerX + Math.cos(a) * radius, centerY + Math.sin(a) * radius, centerZ, centerX + Math.cos(b) * radius, centerY + Math.sin(b) * radius, centerZ, color, alpha);
        }
    }

    private static void drawCircleYZ(VertexConsumer consumer, PoseStack poseStack, double centerX, double centerY, double centerZ, double radius, int color, float alpha) {
        final int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a = TAU * i / segments;
            double b = TAU * (i + 1) / segments;
            line(consumer, poseStack, centerX, centerY + Math.sin(a) * radius, centerZ + Math.cos(a) * radius, centerX, centerY + Math.sin(b) * radius, centerZ + Math.cos(b) * radius, color, alpha);
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, int color, float alpha) {
        line(consumer, poseStack, a.x, a.y, a.z, b.x, b.y, b.z, color, alpha);
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, double x1, double y1, double z1, double x2, double y2, double z2, int color, float alpha) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        PoseStack.Pose pose = poseStack.last();

        consumer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1).color(red, green, blue, alphaByte).normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();

        consumer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2).color(red, green, blue, alphaByte).normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }

    private static double markerRadius(DialogueZonePreviewS2CPacket.Zone zone) {
        return zone.visualSize() > 0.0D ? Math.max(0.05D, zone.visualSize() * 0.5D) : Math.max(0.1D, zone.radius());
    }

    private static double markerHalfX(DialogueZonePreviewS2CPacket.Zone zone) {
        return zone.visualSize() > 0.0D ? Math.max(0.05D, zone.visualSize() * 0.5D) : Math.max(0.05D, zone.sizeX() * 0.5D);
    }

    private static double markerHalfZ(DialogueZonePreviewS2CPacket.Zone zone) {
        return zone.visualSize() > 0.0D ? Math.max(0.05D, zone.visualSize() * 0.5D) : Math.max(0.05D, zone.sizeZ() * 0.5D);
    }

    private static double markerHeight(DialogueZonePreviewS2CPacket.Zone zone) {
        if (zone.visualHeight() > 0.0D) {
            return Math.max(0.05D, zone.visualHeight());
        }

        return "box".equalsIgnoreCase(zone.shape()) ? Math.max(0.1D, zone.sizeY()) : Math.max(0.1D, zone.height());
    }

    private static double defaultRadius(DialogueZonePreviewS2CPacket.Zone zone) {
        if ("box".equalsIgnoreCase(zone.shape())) {
            return Math.max(zone.sizeX(), zone.sizeZ()) * 0.5D;
        }
        return Math.max(0.1D, zone.radius());
    }

    private static float distanceFade(double distance, double previewDistance) {
        if (previewDistance <= 0.0D) {
            return 1.0F;
        }
        if (distance >= previewDistance) {
            return 0.0F;
        }

        double fadeStart = previewDistance * 0.78D;
        if (distance <= fadeStart) {
            return 1.0F;
        }

        return (float) Mth.clamp(1.0D - (distance - fadeStart) / Math.max(0.001D, previewDistance - fadeStart), 0.0D, 1.0D);
    }

    private static String normalized(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static int parseColor(String value) {
        if (value == null) {
            return 0x42F2E1;
        }

        value = value.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "blue" -> 0x4AA3FF;
            case "red" -> 0xFF4D55;
            case "gold", "golden" -> 0xFFD45A;
            case "green" -> 0x55E878;
            case "white" -> 0xFFFFFF;
            case "black" -> 0x000000;
            case "purple" -> 0xB76CFF;
            case "cyan" -> 0x42F2E1;
            default -> parseHex(value);
        };
    }

    private static int parseHex(String value) {
        try {
            if (value.startsWith("#")) {
                value = value.substring(1);
            }
            if (value.startsWith("0x")) {
                value = value.substring(2);
            }
            if (value.length() == 3) {
                value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2);
            }
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0x42F2E1;
        }
    }

    private record AnimationState(double scale, double baseY, double rotation, float alpha) {
    }
}
