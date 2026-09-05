package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueZoneWorldEditRenderer {

    private static final int X_COLOR = 0xFF4D55;
    private static final int Y_COLOR = 0x55E878;
    private static final int Z_COLOR = 0x4AA3FF;
    private static final int SELECTED_COLOR = 0xFFF08A;
    private static final int ANCHOR_COLOR = 0xFFFFFF;
    private static final int GRID_COLOR = 0x6B7C8D;
    private static final int CURSOR_COLOR = 0xFFD45A;
    private static final int MARKER_CURSOR_COLOR = 0xFF75D8;
    private static final int XY_COLOR = 0xFFD45A;
    private static final int XZ_COLOR = 0xB76CFF;
    private static final int YZ_COLOR = 0x42F2E1;

    private DialogueZoneWorldEditRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DialogueZoneWorldEditScreen editor) || minecraft.level == null || minecraft.player == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        renderCustomTexture(editor, poseStack, minecraft);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        drawZoneVisualFill(editor, poseStack.last().pose(), minecraft);
        drawZoneDefaultVisual(editor, poseStack.last().pose(), minecraft);
        drawEditorFills(editor, poseStack.last().pose());

        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(1.0F);
        drawEditorLines(editor, poseStack.last().pose(), 0.24F, true);

        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(2.0F);
        drawEditorLines(editor, poseStack.last().pose(), 1.0F, false);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        poseStack.popPose();
    }

    private static void drawZoneDefaultVisual(DialogueZoneWorldEditScreen editor, Matrix4f matrix, Minecraft minecraft) {
        DialogueDefinition.Trigger trigger = editor.trigger();
        DialogueDefinition.ZoneVisual visual = trigger.visual;

        if (visual == null || !visual.show_default_zone) {
            return;
        }

        LocalAnimation animation = localAnimation(trigger, visual, minecraft);
        Vec3 center = editor.center();
        double baseY = center.y + visual.y_offset + animation.bob;
        double height = markerHeight(trigger, visual);
        int color = DialogueEditorPreview.parseColor(visual.color);
        int alpha = Mth.clamp(Math.round(visual.alpha * animation.alphaMultiplier * 255.0F), 0, 255);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        String style = visual.style != null ? visual.style.toLowerCase(Locale.ROOT) : "auto";
        if ("auto".equals(style)) {
            style = "cylinder".equals(normalizeShape(trigger.shape)) ? "ring" : "outline";
        }

        double rotation = animation.rotation;

        if ("ring".equals(style)) {
            double radius = markerRadius(trigger, visual) * animation.scale;
            circleXZ(buffer, matrix, center.x, baseY, center.z, radius, 64, color, alpha);
            circleXZ(buffer, matrix, center.x, baseY + 0.004D, center.z, radius * 0.94D, 64, color, Math.max(20, alpha / 2));
        } else if ("pillar".equals(style)) {
            double radius = markerRadius(trigger, visual) * animation.scale;
            circleXZ(buffer, matrix, center.x, baseY, center.z, radius, 64, color, alpha);
            circleXZ(buffer, matrix, center.x, baseY + height, center.z, radius, 64, color, Math.max(20, alpha * 2 / 3));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0D * i / 8.0D + rotation;
                double px = center.x + Math.cos(a) * radius;
                double pz = center.z + Math.sin(a) * radius;
                line(buffer, matrix, new Vec3(px, baseY, pz), new Vec3(px, baseY + height, pz), color, Math.max(20, alpha / 2));
            }
        } else {
            String shape = normalizeShape(trigger.shape);
            if ("box".equals(shape)) {
                double hx = markerHalfX(trigger, visual) * animation.scale;
                double hz = markerHalfZ(trigger, visual) * animation.scale;
                Vec3[] b = new Vec3[]{rotateXZ(center.x, baseY, center.z, -hx, -hz, rotation), rotateXZ(center.x, baseY, center.z, hx, -hz, rotation), rotateXZ(center.x, baseY, center.z, hx, hz, rotation), rotateXZ(center.x, baseY, center.z, -hx, hz, rotation)};
                Vec3[] t = new Vec3[]{new Vec3(b[0].x, baseY + height, b[0].z), new Vec3(b[1].x, baseY + height, b[1].z), new Vec3(b[2].x, baseY + height, b[2].z), new Vec3(b[3].x, baseY + height, b[3].z)};
                for (int i = 0; i < 4; i++) {
                    int n = (i + 1) % 4;
                    line(buffer, matrix, b[i], b[n], color, alpha);
                    line(buffer, matrix, t[i], t[n], color, Math.max(20, alpha * 2 / 3));
                    line(buffer, matrix, b[i], t[i], color, Math.max(20, alpha / 2));
                }
            } else if ("sphere".equals(shape)) {
                double radius = markerRadius(trigger, visual) * animation.scale;
                circleXZ(buffer, matrix, center.x, baseY, center.z, radius, 64, color, alpha);
                circleXY(buffer, matrix, center.x, baseY, center.z, radius, 48, color, Math.max(20, alpha * 2 / 3));
                circleYZ(buffer, matrix, center.x, baseY, center.z, radius, 48, color, Math.max(20, alpha * 2 / 3));
            } else {
                double radius = markerRadius(trigger, visual) * animation.scale;
                circleXZ(buffer, matrix, center.x, baseY, center.z, radius, 64, color, alpha);
                circleXZ(buffer, matrix, center.x, baseY + height, center.z, radius, 64, color, Math.max(20, alpha * 2 / 3));
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI * 2.0D * i / 4.0D + rotation;
                    double px = center.x + Math.cos(a) * radius;
                    double pz = center.z + Math.sin(a) * radius;
                    line(buffer, matrix, new Vec3(px, baseY, pz), new Vec3(px, baseY + height, pz), color, Math.max(20, alpha / 2));
                }
            }
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawEditorFills(DialogueZoneWorldEditScreen editor, Matrix4f matrix) {
        boolean hasPlanes = editor.mode() == DialogueZoneWorldEditScreen.EditMode.MOVE || editor.mode() == DialogueZoneWorldEditScreen.EditMode.TEXTURE_MOVE;
        boolean hasBlockAnchor = !editor.uiHidden() && editor.resolvedBlockAnchor() != null;
        boolean hasEntityAnchor = !editor.uiHidden() && editor.resolvedEntityAnchor() != null;

        if (!hasPlanes && !hasBlockAnchor && !hasEntityAnchor) {
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        if (hasPlanes) {
            drawPlaneFill(buffer, matrix, editor, DialogueZoneWorldEditScreen.GizmoPlane.XY, XY_COLOR);
            drawPlaneFill(buffer, matrix, editor, DialogueZoneWorldEditScreen.GizmoPlane.XZ, XZ_COLOR);
            drawPlaneFill(buffer, matrix, editor, DialogueZoneWorldEditScreen.GizmoPlane.YZ, YZ_COLOR);
        }

        BlockPos block = editor.resolvedBlockAnchor();
        if (block != null) {
            fillBox(buffer, matrix, new AABB(block.getX(), block.getY(), block.getZ(), block.getX() + 1.0D, block.getY() + 1.0D, block.getZ() + 1.0D), ANCHOR_COLOR, 30);
        }

        if (editor.resolvedEntityAnchor() != null) {
            fillBox(buffer, matrix, editor.resolvedEntityAnchor().getBoundingBox().inflate(0.025D), ANCHOR_COLOR, 22);
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawPlaneFill(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, DialogueZoneWorldEditScreen.GizmoPlane plane, int color) {
        Vec3[] corners = editor.planeCorners(plane);
        if (corners.length != 4) return;

        boolean selected = editor.dragPlane() == plane || editor.hoveredPlane() == plane;
        int actualColor = selected ? SELECTED_COLOR : color;
        int alpha = selected ? 88 : 34;
        quad(buffer, matrix, corners[0], corners[1], corners[2], corners[3], actualColor, alpha);
    }

    private static void drawEditorLines(DialogueZoneWorldEditScreen editor, Matrix4f matrix, float alphaScale, boolean xray) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        DialogueDefinition.Trigger trigger = editor.trigger();
        Vec3 center = editor.center();
        Vec3 anchorBase = editor.anchorBase();

        int zoneColor = trigger.visual != null ? DialogueEditorPreview.parseColor(trigger.visual.color) : 0x42F2E1;

        int zoneAlpha = Math.round((xray ? 92.0F : 235.0F) * alphaScale);
        int gridAlpha = Math.round((xray ? 34.0F : 82.0F) * alphaScale);
        int helperAlpha = Math.round((xray ? 90.0F : 220.0F) * alphaScale);

        if (!editor.uiHidden()) {
            drawGroundGrid(buffer, matrix, center, trigger, gridAlpha);
            drawGameplayShape(buffer, matrix, center, trigger, zoneColor, zoneAlpha);

            if (anchorBase != null) {
                drawWireCube(buffer, matrix, anchorBase, editor.handleSize() * 0.55D, ANCHOR_COLOR, Math.round(helperAlpha * 0.75F));

                if (anchorBase.distanceToSqr(center) > 0.0001D) {
                    line(buffer, matrix, anchorBase, center, ANCHOR_COLOR, Math.round(helperAlpha * 0.55F));
                }
            }

            drawResolvedAnchorBounds(buffer, matrix, editor, helperAlpha);
            drawCenterMarker(buffer, matrix, center, zoneColor, helperAlpha, editor.handleSize());

            Vec3 cursor = editor.cursorHit();
            if (cursor != null) {
                int cursorColor = editor.markerPlacementMode() ? MARKER_CURSOR_COLOR : CURSOR_COLOR;
                double cursorScale = editor.markerPlacementMode() ? 1.35D : 0.9D;
                drawCursorCross(buffer, matrix, cursor, editor.handleSize() * cursorScale, cursorColor, Math.round(helperAlpha * 0.9F));
            }
        }

        if (editor.mode() == DialogueZoneWorldEditScreen.EditMode.MOVE || editor.mode() == DialogueZoneWorldEditScreen.EditMode.TEXTURE_MOVE) {
            drawPlaneOutlines(buffer, matrix, editor, helperAlpha);
        }

        Vec3 gizmoCenter = editor.gizmoCenter();
        if (!editor.uiHidden() && gizmoCenter.distanceToSqr(center) > 0.0001D) {
            line(buffer, matrix, center, gizmoCenter, SELECTED_COLOR, Math.max(80, helperAlpha / 2));
        }
        drawCenterMarker(buffer, matrix, gizmoCenter, SELECTED_COLOR, helperAlpha, editor.handleSize() * 0.8D);
        drawGizmo(buffer, matrix, editor, helperAlpha);

        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawGroundGrid(BufferBuilder buffer, Matrix4f matrix, Vec3 center, DialogueDefinition.Trigger trigger, int alpha) {
        double extent = Math.min(16.0D, Math.max(3.0D, horizontalExtent(trigger) + 2.0D));
        int whole = Mth.clamp((int) Math.ceil(extent), 3, 16);
        double y = center.y + 0.012D;

        for (int i = -whole; i <= whole; i++) {
            int lineAlpha = i == 0 ? Math.min(255, alpha * 2) : alpha;

            line(buffer, matrix, new Vec3(center.x - whole, y, center.z + i), new Vec3(center.x + whole, y, center.z + i), GRID_COLOR, lineAlpha);
            line(buffer, matrix, new Vec3(center.x + i, y, center.z - whole), new Vec3(center.x + i, y, center.z + whole), GRID_COLOR, lineAlpha);
        }
    }

    private static void drawGameplayShape(BufferBuilder buffer, Matrix4f matrix, Vec3 center, DialogueDefinition.Trigger trigger, int color, int alpha) {
        switch (normalizeShape(trigger.shape)) {
            case "sphere" -> drawSphere(buffer, matrix, center, Math.max(0.1D, trigger.radius), color, alpha);
            case "box" ->
                    drawBox(buffer, matrix, center, Math.max(0.1D, trigger.size_x), Math.max(0.1D, trigger.size_y), Math.max(0.1D, trigger.size_z), color, alpha);
            default ->
                    drawCylinder(buffer, matrix, center, Math.max(0.1D, trigger.radius), Math.max(0.1D, trigger.height), color, alpha);
        }
    }

    private static void drawCylinder(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, double height, int color, int alpha) {
        final int segments = 64;
        double bottom = center.y;
        double top = center.y + height;

        circleXZ(buffer, matrix, center.x, bottom, center.z, radius, segments, color, alpha);
        circleXZ(buffer, matrix, center.x, top, center.z, radius, segments, color, alpha);
        circleXZ(buffer, matrix, center.x, center.y + height * 0.5D, center.z, radius, segments, color, Math.max(30, alpha / 3));

        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0D * i / 8.0D;
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;
            line(buffer, matrix, new Vec3(x, bottom, z), new Vec3(x, top, z), color, Math.max(50, alpha / 2));
        }
    }

    private static void drawSphere(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double radius, int color, int alpha) {
        circleXZ(buffer, matrix, center.x, center.y, center.z, radius, 64, color, alpha);
        circleXY(buffer, matrix, center.x, center.y, center.z, radius, 64, color, alpha);
        circleYZ(buffer, matrix, center.x, center.y, center.z, radius, 64, color, alpha);

        double diagonal = radius * 0.70710678118D;
        circleXZ(buffer, matrix, center.x, center.y + diagonal, center.z, diagonal, 48, color, Math.max(35, alpha / 3));
        circleXZ(buffer, matrix, center.x, center.y - diagonal, center.z, diagonal, 48, color, Math.max(35, alpha / 3));
    }

    private static void drawBox(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double sx, double sy, double sz, int color, int alpha) {
        double hx = sx * 0.5D;
        double hz = sz * 0.5D;
        double y0 = center.y;
        double y1 = center.y + sy;

        Vec3 a = new Vec3(center.x - hx, y0, center.z - hz);
        Vec3 b = new Vec3(center.x + hx, y0, center.z - hz);
        Vec3 c = new Vec3(center.x + hx, y0, center.z + hz);
        Vec3 d = new Vec3(center.x - hx, y0, center.z + hz);

        Vec3 e = new Vec3(center.x - hx, y1, center.z - hz);
        Vec3 f = new Vec3(center.x + hx, y1, center.z - hz);
        Vec3 g = new Vec3(center.x + hx, y1, center.z + hz);
        Vec3 h = new Vec3(center.x - hx, y1, center.z + hz);

        line(buffer, matrix, a, b, color, alpha);
        line(buffer, matrix, b, c, color, alpha);
        line(buffer, matrix, c, d, color, alpha);
        line(buffer, matrix, d, a, color, alpha);

        line(buffer, matrix, e, f, color, alpha);
        line(buffer, matrix, f, g, color, alpha);
        line(buffer, matrix, g, h, color, alpha);
        line(buffer, matrix, h, e, color, alpha);

        line(buffer, matrix, a, e, color, alpha);
        line(buffer, matrix, b, f, color, alpha);
        line(buffer, matrix, c, g, color, alpha);
        line(buffer, matrix, d, h, color, alpha);

        double midY = center.y + sy * 0.5D;
        line(buffer, matrix, new Vec3(center.x - hx, midY, center.z - hz), new Vec3(center.x + hx, midY, center.z - hz), color, Math.max(35, alpha / 3));
        line(buffer, matrix, new Vec3(center.x + hx, midY, center.z - hz), new Vec3(center.x + hx, midY, center.z + hz), color, Math.max(35, alpha / 3));
        line(buffer, matrix, new Vec3(center.x + hx, midY, center.z + hz), new Vec3(center.x - hx, midY, center.z + hz), color, Math.max(35, alpha / 3));
        line(buffer, matrix, new Vec3(center.x - hx, midY, center.z + hz), new Vec3(center.x - hx, midY, center.z - hz), color, Math.max(35, alpha / 3));
    }

    private static void drawPlaneOutlines(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, int alpha) {
        drawPlaneOutline(buffer, matrix, editor, DialogueZoneWorldEditScreen.GizmoPlane.XY, XY_COLOR, alpha);
        drawPlaneOutline(buffer, matrix, editor, DialogueZoneWorldEditScreen.GizmoPlane.XZ, XZ_COLOR, alpha);
        drawPlaneOutline(buffer, matrix, editor, DialogueZoneWorldEditScreen.GizmoPlane.YZ, YZ_COLOR, alpha);
    }

    private static void drawPlaneOutline(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, DialogueZoneWorldEditScreen.GizmoPlane plane, int baseColor, int alpha) {
        Vec3[] c = editor.planeCorners(plane);
        if (c.length != 4) return;
        boolean selected = editor.dragPlane() == plane || editor.hoveredPlane() == plane;
        int color = selected ? SELECTED_COLOR : baseColor;
        int a = selected ? 255 : Math.max(90, alpha / 2);
        line(buffer, matrix, c[0], c[1], color, a);
        line(buffer, matrix, c[1], c[2], color, a);
        line(buffer, matrix, c[2], c[3], color, a);
        line(buffer, matrix, c[3], c[0], color, a);
    }

    private static void drawResolvedAnchorBounds(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, int alpha) {
        BlockPos block = editor.resolvedBlockAnchor();
        if (block != null) {
            drawAabb(buffer, matrix, new AABB(block.getX(), block.getY(), block.getZ(), block.getX() + 1.0D, block.getY() + 1.0D, block.getZ() + 1.0D), ANCHOR_COLOR, Math.max(120, alpha));
        }

        if (editor.resolvedEntityAnchor() != null) {
            drawAabb(buffer, matrix, editor.resolvedEntityAnchor().getBoundingBox().inflate(0.025D), ANCHOR_COLOR, Math.max(110, alpha));
        }
    }

    private static void drawAabb(BufferBuilder buffer, Matrix4f matrix, AABB box, int color, int alpha) {
        Vec3 a = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 b = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 c = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 d = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 e = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 f = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 g = new Vec3(box.maxX, box.maxY, box.maxZ);
        Vec3 h = new Vec3(box.minX, box.maxY, box.maxZ);

        line(buffer, matrix, a, b, color, alpha);
        line(buffer, matrix, b, c, color, alpha);
        line(buffer, matrix, c, d, color, alpha);
        line(buffer, matrix, d, a, color, alpha);
        line(buffer, matrix, e, f, color, alpha);
        line(buffer, matrix, f, g, color, alpha);
        line(buffer, matrix, g, h, color, alpha);
        line(buffer, matrix, h, e, color, alpha);
        line(buffer, matrix, a, e, color, alpha);
        line(buffer, matrix, b, f, color, alpha);
        line(buffer, matrix, c, g, color, alpha);
        line(buffer, matrix, d, h, color, alpha);
    }

    private static void drawGizmo(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, int alpha) {
        Vec3 center = editor.gizmoCenter();

        if (editor.mode() == DialogueZoneWorldEditScreen.EditMode.TEXTURE_ROTATE) {
            drawRotationRing(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.X, X_COLOR, alpha);
            drawRotationRing(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.Y, Y_COLOR, alpha);
            drawRotationRing(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.Z, Z_COLOR, alpha);
            return;
        }

        if (editor.mode() == DialogueZoneWorldEditScreen.EditMode.MOVE || editor.mode() == DialogueZoneWorldEditScreen.EditMode.TEXTURE_MOVE) {
            drawMoveAxis(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.X, X_COLOR, alpha);
            drawMoveAxis(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.Y, Y_COLOR, alpha);
            drawMoveAxis(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.Z, Z_COLOR, alpha);
            return;
        }

        drawSizeAxis(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.X, X_COLOR, alpha);
        drawSizeAxis(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.Y, Y_COLOR, alpha);
        drawSizeAxis(buffer, matrix, editor, center, DialogueZoneWorldEditScreen.GizmoAxis.Z, Z_COLOR, alpha);
    }

    private static void drawRotationRing(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, Vec3 center, DialogueZoneWorldEditScreen.GizmoAxis axis, int baseColor, int alpha) {
        boolean selected = editor.dragRotationAxis() == axis || editor.hoveredRotationAxis() == axis;
        int color = selected ? SELECTED_COLOR : baseColor;
        int ringAlpha = selected ? 255 : Math.max(110, alpha);
        double radius = editor.rotationRingRadius();

        switch (axis) {
            case X -> circleYZ(buffer, matrix, center.x, center.y, center.z, radius, 72, color, ringAlpha);
            case Y -> circleXZ(buffer, matrix, center.x, center.y, center.z, radius, 72, color, ringAlpha);
            case Z -> circleXY(buffer, matrix, center.x, center.y, center.z, radius, 72, color, ringAlpha);
        }

        DialogueDefinition.ZoneVisual visual = editor.trigger().visual;
        double angle = Math.toRadians(switch (axis) {
            case X -> visual.texture_rotation_x;
            case Y -> visual.texture_rotation;
            case Z -> visual.texture_rotation_z;
            default -> 0.0D;
        });

        Vec3 pointer = switch (axis) {
            case X -> center.add(0.0D, Math.cos(angle) * radius, Math.sin(angle) * radius);
            case Y -> center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            case Z -> center.add(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
            default -> center;
        };

        line(buffer, matrix, center, pointer, color, ringAlpha);
        drawWireCube(buffer, matrix, pointer, editor.handleSize() * 0.75D, color, ringAlpha);
    }

    private static void drawMoveAxis(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, Vec3 start, DialogueZoneWorldEditScreen.GizmoAxis axis, int baseColor, int alpha) {
        Vec3 end = editor.gizmoEnd(axis, 1);
        boolean selected = (editor.dragAxis() == axis && editor.dragAxisSign() > 0) || (editor.hoveredAxis() == axis && editor.hoveredAxisSign() > 0);
        int color = selected ? SELECTED_COLOR : baseColor;
        int a = selected ? 255 : alpha;

        line(buffer, matrix, start, end, color, a);

        double handle = editor.handleSize();
        drawWireCube(buffer, matrix, end, handle, color, a);
        drawArrowHead(buffer, matrix, start, end, axis, color, a, handle * 2.1D);
    }

    private static void drawSizeAxis(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, Vec3 start, DialogueZoneWorldEditScreen.GizmoAxis axis, int baseColor, int alpha) {
        drawSizeHandle(buffer, matrix, editor, start, axis, 1, baseColor, alpha);
        if (editor.supportsNegativeSizeHandle(axis)) {
            drawSizeHandle(buffer, matrix, editor, start, axis, -1, baseColor, alpha);
        }
    }

    private static void drawSizeHandle(BufferBuilder buffer, Matrix4f matrix, DialogueZoneWorldEditScreen editor, Vec3 start, DialogueZoneWorldEditScreen.GizmoAxis axis, int sign, int baseColor, int alpha) {
        Vec3 end = editor.gizmoEnd(axis, sign);
        boolean selected = (editor.dragAxis() == axis && editor.dragAxisSign() == sign) || (editor.hoveredAxis() == axis && editor.hoveredAxisSign() == sign);
        int color = selected ? SELECTED_COLOR : baseColor;
        int a = selected ? 255 : alpha;

        line(buffer, matrix, start, end, color, Math.max(90, a));
        double handle = editor.handleSize() * (selected ? 1.25D : 1.05D);
        drawWireCube(buffer, matrix, end, handle, color, a);
        Vec3 axisVec = switch (axis) {
            case X -> new Vec3(0, handle * 1.6D, 0);
            case Y -> new Vec3(handle * 1.6D, 0, 0);
            case Z -> new Vec3(0, handle * 1.6D, 0);
            default -> Vec3.ZERO;
        };
        line(buffer, matrix, end.subtract(axisVec), end.add(axisVec), color, a);
    }

    private static void drawArrowHead(BufferBuilder buffer, Matrix4f matrix, Vec3 start, Vec3 end, DialogueZoneWorldEditScreen.GizmoAxis axis, int color, int alpha, double size) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 back = end.subtract(direction.scale(size));

        switch (axis) {
            case X -> {
                line(buffer, matrix, end, back.add(0, size * 0.45D, 0), color, alpha);
                line(buffer, matrix, end, back.add(0, -size * 0.45D, 0), color, alpha);
                line(buffer, matrix, end, back.add(0, 0, size * 0.45D), color, alpha);
                line(buffer, matrix, end, back.add(0, 0, -size * 0.45D), color, alpha);
            }
            case Y -> {
                line(buffer, matrix, end, back.add(size * 0.45D, 0, 0), color, alpha);
                line(buffer, matrix, end, back.add(-size * 0.45D, 0, 0), color, alpha);
                line(buffer, matrix, end, back.add(0, 0, size * 0.45D), color, alpha);
                line(buffer, matrix, end, back.add(0, 0, -size * 0.45D), color, alpha);
            }
            case Z -> {
                line(buffer, matrix, end, back.add(size * 0.45D, 0, 0), color, alpha);
                line(buffer, matrix, end, back.add(-size * 0.45D, 0, 0), color, alpha);
                line(buffer, matrix, end, back.add(0, size * 0.45D, 0), color, alpha);
                line(buffer, matrix, end, back.add(0, -size * 0.45D, 0), color, alpha);
            }
        }
    }

    private static void drawCenterMarker(BufferBuilder buffer, Matrix4f matrix, Vec3 center, int color, int alpha, double handleSize) {
        double s = handleSize * 0.75D;
        drawWireCube(buffer, matrix, center, s, color, alpha);

        line(buffer, matrix, center.add(-s * 1.8D, 0, 0), center.add(s * 1.8D, 0, 0), color, alpha);
        line(buffer, matrix, center.add(0, -s * 1.8D, 0), center.add(0, s * 1.8D, 0), color, alpha);
        line(buffer, matrix, center.add(0, 0, -s * 1.8D), center.add(0, 0, s * 1.8D), color, alpha);
    }

    private static void drawCursorCross(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double size, int color, int alpha) {
        line(buffer, matrix, center.add(-size, 0, 0), center.add(size, 0, 0), color, alpha);
        line(buffer, matrix, center.add(0, -size, 0), center.add(0, size, 0), color, alpha);
        line(buffer, matrix, center.add(0, 0, -size), center.add(0, 0, size), color, alpha);
        circleXZ(buffer, matrix, center.x, center.y + 0.01D, center.z, size * 1.4D, 20, color, alpha);
    }

    private static void drawWireCube(BufferBuilder buffer, Matrix4f matrix, Vec3 center, double half, int color, int alpha) {
        double x0 = center.x - half;
        double x1 = center.x + half;
        double y0 = center.y - half;
        double y1 = center.y + half;
        double z0 = center.z - half;
        double z1 = center.z + half;

        Vec3 a = new Vec3(x0, y0, z0);
        Vec3 b = new Vec3(x1, y0, z0);
        Vec3 c = new Vec3(x1, y0, z1);
        Vec3 d = new Vec3(x0, y0, z1);
        Vec3 e = new Vec3(x0, y1, z0);
        Vec3 f = new Vec3(x1, y1, z0);
        Vec3 g = new Vec3(x1, y1, z1);
        Vec3 h = new Vec3(x0, y1, z1);

        line(buffer, matrix, a, b, color, alpha);
        line(buffer, matrix, b, c, color, alpha);
        line(buffer, matrix, c, d, color, alpha);
        line(buffer, matrix, d, a, color, alpha);
        line(buffer, matrix, e, f, color, alpha);
        line(buffer, matrix, f, g, color, alpha);
        line(buffer, matrix, g, h, color, alpha);
        line(buffer, matrix, h, e, color, alpha);
        line(buffer, matrix, a, e, color, alpha);
        line(buffer, matrix, b, f, color, alpha);
        line(buffer, matrix, c, g, color, alpha);
        line(buffer, matrix, d, h, color, alpha);
    }

    private static void fillBox(BufferBuilder buffer, Matrix4f matrix, AABB box, int color, int alpha) {
        Vec3 a = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 b = new Vec3(box.maxX, box.minY, box.minZ);
        Vec3 c = new Vec3(box.maxX, box.minY, box.maxZ);
        Vec3 d = new Vec3(box.minX, box.minY, box.maxZ);
        Vec3 e = new Vec3(box.minX, box.maxY, box.minZ);
        Vec3 f = new Vec3(box.maxX, box.maxY, box.minZ);
        Vec3 g = new Vec3(box.maxX, box.maxY, box.maxZ);
        Vec3 h = new Vec3(box.minX, box.maxY, box.maxZ);

        quad(buffer, matrix, a, b, c, d, color, alpha);
        quad(buffer, matrix, e, h, g, f, color, alpha);
        quad(buffer, matrix, a, e, f, b, color, alpha);
        quad(buffer, matrix, b, f, g, c, color, alpha);
        quad(buffer, matrix, c, g, h, d, color, alpha);
        quad(buffer, matrix, d, h, e, a, color, alpha);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int bl = rgb & 255;
        int clampedAlpha = Mth.clamp(alpha, 0, 255);

        buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, clampedAlpha).endVertex();
        buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, clampedAlpha).endVertex();
        buffer.vertex(matrix, (float) c.x, (float) c.y, (float) c.z).color(r, g, bl, clampedAlpha).endVertex();
        buffer.vertex(matrix, (float) d.x, (float) d.y, (float) d.z).color(r, g, bl, clampedAlpha).endVertex();
    }

    private static void circleXZ(BufferBuilder buffer, Matrix4f matrix, double cx, double cy, double cz, double radius, int segments, int color, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            line(buffer, matrix, new Vec3(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius), new Vec3(cx + Math.cos(b) * radius, cy, cz + Math.sin(b) * radius), color, alpha);
        }
    }

    private static void circleXY(BufferBuilder buffer, Matrix4f matrix, double cx, double cy, double cz, double radius, int segments, int color, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            line(buffer, matrix, new Vec3(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, cz), new Vec3(cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, cz), color, alpha);
        }
    }

    private static void circleYZ(BufferBuilder buffer, Matrix4f matrix, double cx, double cy, double cz, double radius, int segments, int color, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            line(buffer, matrix, new Vec3(cx, cy + Math.sin(a) * radius, cz + Math.cos(a) * radius), new Vec3(cx, cy + Math.sin(b) * radius, cz + Math.cos(b) * radius), color, alpha);
        }
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, int rgb, int alpha) {
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int bl = rgb & 255;
        int clampedAlpha = Mth.clamp(alpha, 0, 255);

        buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, clampedAlpha).endVertex();
        buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, clampedAlpha).endVertex();
    }

    private static void renderCustomTexture(DialogueZoneWorldEditScreen editor, PoseStack poseStack, Minecraft minecraft) {
        DialogueDefinition.Trigger trigger = editor.trigger();
        DialogueDefinition.ZoneVisual visual = trigger.visual;
        if (visual == null || visual.texture == null || visual.texture.isBlank()) {
            return;
        }

        ResourceLocation texture = DialogueEditorTextureCache.resolve(editor.project(), visual.texture, null);
        if (texture == null) {
            return;
        }

        LocalAnimation animation = localAnimation(trigger, visual, minecraft);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(texture);
        VertexConsumer consumer = buffers.getBuffer(type);

        double seconds = (minecraft.level.getGameTime() + minecraft.getFrameTime()) / 20.0D;
        double repeatU = "repeat".equalsIgnoreCase(visual.texture_fit) ? Math.max(0.01D, visual.texture_repeat_x) : 1.0D;
        double repeatV = "repeat".equalsIgnoreCase(visual.texture_fit) ? Math.max(0.01D, visual.texture_repeat_y) : 1.0D;
        double scrollU = seconds * visual.texture_scroll_u;
        double scrollV = seconds * visual.texture_scroll_v;

        String mode = visual.texture_mode != null ? visual.texture_mode.toLowerCase(Locale.ROOT) : "plane";
        switch (mode) {
            case "cylinder_wrap" ->
                    drawEditorTexturedCylinder(consumer, poseStack, editor.center(), trigger, visual, animation, repeatU, repeatV, scrollU, scrollV);
            case "box_wrap" ->
                    drawEditorTexturedBox(consumer, poseStack, editor.center(), trigger, visual, animation, repeatU, repeatV, scrollU, scrollV);
            default ->
                    drawEditorTexturedPlane(consumer, poseStack, editor.center(), trigger, visual, animation, repeatU, repeatV, scrollU, scrollV);
        }

        buffers.endBatch(type);
    }

    private static void drawZoneVisualFill(DialogueZoneWorldEditScreen editor, Matrix4f matrix, Minecraft minecraft) {
        DialogueDefinition.Trigger trigger = editor.trigger();
        DialogueDefinition.ZoneVisual visual = trigger.visual;
        if (visual == null || !visual.fill_enabled) {
            return;
        }

        String shape = normalizeShape(trigger.shape);
        if (!"cylinder".equals(shape) && !"box".equals(shape)) {
            return;
        }

        LocalAnimation animation = localAnimation(trigger, visual, minecraft);
        int bottomColor = DialogueEditorPreview.parseColor(visual.fill_color_bottom);
        int topColor = "solid".equalsIgnoreCase(visual.fill_mode) ? bottomColor : DialogueEditorPreview.parseColor(visual.fill_color_top);

        int bottomAlpha = Mth.clamp(Math.round(visual.alpha * visual.fill_alpha_bottom * animation.alphaMultiplier * 255.0F), 0, 255);
        float rawTopAlpha = "solid".equalsIgnoreCase(visual.fill_mode) ? visual.fill_alpha_bottom : visual.fill_alpha_top;
        int topAlpha = Mth.clamp(Math.round(visual.alpha * rawTopAlpha * animation.alphaMultiplier * 255.0F), 0, 255);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vec3 center = editor.center();
        double baseY = center.y + visual.y_offset + animation.bob;
        double height = markerHeight(trigger, visual);

        if ("box".equals(shape)) {
            double halfX = markerHalfX(trigger, visual) * animation.scale;
            double halfZ = markerHalfZ(trigger, visual) * animation.scale;
            Vec3[] bottom = new Vec3[]{rotateXZ(center.x, baseY, center.z, -halfX, -halfZ, animation.rotation), rotateXZ(center.x, baseY, center.z, halfX, -halfZ, animation.rotation), rotateXZ(center.x, baseY, center.z, halfX, halfZ, animation.rotation), rotateXZ(center.x, baseY, center.z, -halfX, halfZ, animation.rotation)};
            Vec3[] top = new Vec3[]{new Vec3(bottom[0].x, baseY + height, bottom[0].z), new Vec3(bottom[1].x, baseY + height, bottom[1].z), new Vec3(bottom[2].x, baseY + height, bottom[2].z), new Vec3(bottom[3].x, baseY + height, bottom[3].z)};
            for (int i = 0; i < 4; i++) {
                int n = (i + 1) % 4;
                gradientQuad(buffer, matrix, bottom[i], bottom[n], top[n], top[i], bottomColor, topColor, bottomAlpha, topAlpha);
            }
        } else {
            int segments = 64;
            double radius = markerRadius(trigger, visual) * animation.scale;
            for (int i = 0; i < segments; i++) {
                double a = Math.PI * 2.0D * i / segments + animation.rotation;
                double b = Math.PI * 2.0D * (i + 1) / segments + animation.rotation;
                Vec3 p0 = new Vec3(center.x + Math.cos(a) * radius, baseY, center.z + Math.sin(a) * radius);
                Vec3 p1 = new Vec3(center.x + Math.cos(b) * radius, baseY, center.z + Math.sin(b) * radius);
                Vec3 p2 = new Vec3(center.x + Math.cos(b) * radius, baseY + height, center.z + Math.sin(b) * radius);
                Vec3 p3 = new Vec3(center.x + Math.cos(a) * radius, baseY + height, center.z + Math.sin(a) * radius);
                gradientQuad(buffer, matrix, p0, p1, p2, p3, bottomColor, topColor, bottomAlpha, topAlpha);
            }
        }

        BufferUploader.drawWithShader(buffer.end());
    }

    private static void gradientQuad(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int bottomColor, int topColor, int bottomAlpha, int topAlpha) {
        colorVertex(buffer, matrix, a, bottomColor, bottomAlpha);
        colorVertex(buffer, matrix, b, bottomColor, bottomAlpha);
        colorVertex(buffer, matrix, c, topColor, topAlpha);
        colorVertex(buffer, matrix, d, topColor, topAlpha);

        colorVertex(buffer, matrix, d, topColor, topAlpha);
        colorVertex(buffer, matrix, c, topColor, topAlpha);
        colorVertex(buffer, matrix, b, bottomColor, bottomAlpha);
        colorVertex(buffer, matrix, a, bottomColor, bottomAlpha);
    }

    private static void colorVertex(BufferBuilder buffer, Matrix4f matrix, Vec3 position, int color, int alpha) {
        buffer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z).color(color >> 16 & 255, color >> 8 & 255, color & 255, Mth.clamp(alpha, 0, 255)).endVertex();
    }

    private static void drawEditorTexturedPlane(VertexConsumer consumer, PoseStack poseStack, Vec3 center, DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual, LocalAnimation animation, double repeatU, double repeatV, double scrollU, double scrollV) {
        double size = visual.size > 0.0D ? visual.size : horizontalExtent(trigger) * 2.0D;
        double halfX = Math.max(0.05D, size * animation.scale * Math.max(0.05D, visual.texture_scale_x) * 0.5D);
        double halfZ = Math.max(0.05D, size * animation.scale * Math.max(0.05D, visual.texture_scale_y) * 0.5D);

        Vec3 textureCenter = new Vec3(center.x + visual.texture_offset_x, center.y + visual.y_offset + animation.bob + visual.texture_offset_y + 0.006D, center.z + visual.texture_offset_z);

        double rotationX = Math.toRadians(visual.texture_rotation_x);
        double rotationY = animation.rotation + Math.toRadians(visual.texture_rotation);
        double rotationZ = Math.toRadians(visual.texture_rotation_z);

        Vec3 p0 = transformTexturePoint(textureCenter, new Vec3(-halfX, 0.0D, -halfZ), rotationX, rotationY, rotationZ);
        Vec3 p1 = transformTexturePoint(textureCenter, new Vec3(-halfX, 0.0D, halfZ), rotationX, rotationY, rotationZ);
        Vec3 p2 = transformTexturePoint(textureCenter, new Vec3(halfX, 0.0D, halfZ), rotationX, rotationY, rotationZ);
        Vec3 p3 = transformTexturePoint(textureCenter, new Vec3(halfX, 0.0D, -halfZ), rotationX, rotationY, rotationZ);

        Vec3 normal = rotateTextureVector(new Vec3(0.0D, 1.0D, 0.0D), rotationX, rotationY, rotationZ).normalize();

        texturedQuad(consumer, poseStack, p0, p1, p2, p3, scrollU, scrollV + repeatV, scrollU, scrollV, scrollU + repeatU, scrollV, scrollU + repeatU, scrollV + repeatV, visual.alpha * animation.alphaMultiplier, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void drawEditorTexturedCylinder(VertexConsumer consumer, PoseStack poseStack, Vec3 center, DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual, LocalAnimation animation, double repeatU, double repeatV, double scrollU, double scrollV) {
        int segments = 64;
        double radius = markerRadius(trigger, visual) * animation.scale * Math.max(0.05D, visual.texture_scale_x);
        double height = markerHeight(trigger, visual) * Math.max(0.05D, visual.texture_scale_y);
        double baseY = center.y + visual.y_offset + animation.bob + visual.texture_offset_y;

        Vec3 textureCenter = new Vec3(center.x + visual.texture_offset_x, baseY + height * 0.5D, center.z + visual.texture_offset_z);

        double halfHeight = height * 0.5D;
        double rotationX = Math.toRadians(visual.texture_rotation_x);
        double rotationY = animation.rotation + Math.toRadians(visual.texture_rotation);
        double rotationZ = Math.toRadians(visual.texture_rotation_z);

        for (int i = 0; i < segments; i++) {
            double fa = i / (double) segments;
            double fb = (i + 1) / (double) segments;
            double a = Math.PI * 2.0D * fa;
            double b = Math.PI * 2.0D * fb;

            Vec3 p0 = transformTexturePoint(textureCenter, new Vec3(Math.cos(a) * radius, -halfHeight, Math.sin(a) * radius), rotationX, rotationY, rotationZ);
            Vec3 p1 = transformTexturePoint(textureCenter, new Vec3(Math.cos(b) * radius, -halfHeight, Math.sin(b) * radius), rotationX, rotationY, rotationZ);
            Vec3 p2 = transformTexturePoint(textureCenter, new Vec3(Math.cos(b) * radius, halfHeight, Math.sin(b) * radius), rotationX, rotationY, rotationZ);
            Vec3 p3 = transformTexturePoint(textureCenter, new Vec3(Math.cos(a) * radius, halfHeight, Math.sin(a) * radius), rotationX, rotationY, rotationZ);

            double middle = (a + b) * 0.5D;
            Vec3 normal = rotateTextureVector(new Vec3(Math.cos(middle), 0.0D, Math.sin(middle)), rotationX, rotationY, rotationZ).normalize();

            texturedQuad(consumer, poseStack, p0, p1, p2, p3, scrollU + fa * repeatU, scrollV + repeatV, scrollU + fb * repeatU, scrollV + repeatV, scrollU + fb * repeatU, scrollV, scrollU + fa * repeatU, scrollV, visual.alpha * animation.alphaMultiplier, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private static void drawEditorTexturedBox(VertexConsumer consumer, PoseStack poseStack, Vec3 center, DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual, LocalAnimation animation, double repeatU, double repeatV, double scrollU, double scrollV) {
        double halfX = markerHalfX(trigger, visual) * animation.scale * Math.max(0.05D, visual.texture_scale_x);
        double halfZ = markerHalfZ(trigger, visual) * animation.scale * Math.max(0.05D, visual.texture_scale_x);
        double height = markerHeight(trigger, visual) * Math.max(0.05D, visual.texture_scale_y);
        double baseY = center.y + visual.y_offset + animation.bob + visual.texture_offset_y;

        Vec3 textureCenter = new Vec3(center.x + visual.texture_offset_x, baseY + height * 0.5D, center.z + visual.texture_offset_z);

        double halfHeight = height * 0.5D;
        double rotationX = Math.toRadians(visual.texture_rotation_x);
        double rotationY = animation.rotation + Math.toRadians(visual.texture_rotation);
        double rotationZ = Math.toRadians(visual.texture_rotation_z);

        Vec3[] bottom = new Vec3[]{transformTexturePoint(textureCenter, new Vec3(-halfX, -halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(textureCenter, new Vec3(halfX, -halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(textureCenter, new Vec3(halfX, -halfHeight, halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(textureCenter, new Vec3(-halfX, -halfHeight, halfZ), rotationX, rotationY, rotationZ)};

        Vec3[] top = new Vec3[]{transformTexturePoint(textureCenter, new Vec3(-halfX, halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(textureCenter, new Vec3(halfX, halfHeight, -halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(textureCenter, new Vec3(halfX, halfHeight, halfZ), rotationX, rotationY, rotationZ), transformTexturePoint(textureCenter, new Vec3(-halfX, halfHeight, halfZ), rotationX, rotationY, rotationZ)};

        double[] lengths = {halfX * 2.0D, halfZ * 2.0D, halfX * 2.0D, halfZ * 2.0D};
        double perimeter = Math.max(0.001D, (halfX + halfZ) * 4.0D);
        double u = 0.0D;

        for (int i = 0; i < 4; i++) {
            int n = (i + 1) % 4;
            double nextU = u + lengths[i] / perimeter * repeatU;

            Vec3 edge = bottom[n].subtract(bottom[i]);
            Vec3 vertical = top[i].subtract(bottom[i]);
            Vec3 normal = vertical.cross(edge).normalize();

            texturedQuad(consumer, poseStack, bottom[i], bottom[n], top[n], top[i], scrollU + u, scrollV + repeatV, scrollU + nextU, scrollV + repeatV, scrollU + nextU, scrollV, scrollU + u, scrollV, visual.alpha * animation.alphaMultiplier, (float) normal.x, (float) normal.y, (float) normal.z);
            u = nextU;
        }
    }

    private static void texturedQuad(VertexConsumer consumer, PoseStack poseStack, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u0, double v0, double u1, double v1, double u2, double v2, double u3, double v3, float alpha, float nx, float ny, float nz) {
        textureVertex(consumer, poseStack, p0, u0, v0, alpha, nx, ny, nz);
        textureVertex(consumer, poseStack, p1, u1, v1, alpha, nx, ny, nz);
        textureVertex(consumer, poseStack, p2, u2, v2, alpha, nx, ny, nz);
        textureVertex(consumer, poseStack, p3, u3, v3, alpha, nx, ny, nz);
        textureVertex(consumer, poseStack, p3, u3, v3, alpha, -nx, -ny, -nz);
        textureVertex(consumer, poseStack, p2, u2, v2, alpha, -nx, -ny, -nz);
        textureVertex(consumer, poseStack, p1, u1, v1, alpha, -nx, -ny, -nz);
        textureVertex(consumer, poseStack, p0, u0, v0, alpha, -nx, -ny, -nz);
    }

    private static void textureVertex(VertexConsumer consumer, PoseStack poseStack, Vec3 p, double u, double v, float alpha, float nx, float ny, float nz) {
        PoseStack.Pose pose = poseStack.last();
        consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, Mth.clamp(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), 0, 255)).uv((float) u, (float) v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private static LocalAnimation localAnimation(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual, Minecraft minecraft) {
        double seconds = (minecraft.level.getGameTime() + minecraft.getFrameTime()) / 20.0D;
        double scale = 1.0D;
        if (visual.pulse) {
            scale += Math.sin(seconds * Math.PI * 2.0D * Math.max(0.0D, visual.pulse_speed)) * Math.max(0.0D, visual.pulse_amplitude);
        }
        scale = Math.max(0.05D, scale);

        double bob = visual.bob ? Math.sin(seconds * Math.PI * 2.0D * Math.max(0.0D, visual.bob_speed)) * Math.max(0.0D, visual.bob_amplitude) : 0.0D;
        double rotation = visual.rotate ? Math.toRadians(seconds * visual.rotate_speed) : 0.0D;

        double alphaMultiplier = 1.0D;
        if (visual.alpha_breathe) {
            double amount = Mth.clamp(visual.alpha_breathe_amount, 0.0D, 1.0D);
            double wave = 0.5D + 0.5D * Math.sin(seconds * Math.PI * 2.0D * Math.max(0.0D, visual.alpha_breathe_speed));
            alphaMultiplier = 1.0D - amount + wave * amount;
        }

        return new LocalAnimation(scale, bob, rotation, (float) alphaMultiplier);
    }

    private static double markerRadius(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual) {
        return visual.size > 0.0D ? Math.max(0.05D, visual.size * 0.5D) : Math.max(0.1D, trigger.radius);
    }

    private static double markerHalfX(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual) {
        return visual.size > 0.0D ? Math.max(0.05D, visual.size * 0.5D) : Math.max(0.05D, trigger.size_x * 0.5D);
    }

    private static double markerHalfZ(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual) {
        return visual.size > 0.0D ? Math.max(0.05D, visual.size * 0.5D) : Math.max(0.05D, trigger.size_z * 0.5D);
    }

    private static double markerHeight(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual) {
        if (visual.visual_height > 0.0D) {
            return Math.max(0.05D, visual.visual_height);
        }
        return "box".equals(normalizeShape(trigger.shape)) ? Math.max(0.1D, trigger.size_y) : Math.max(0.1D, trigger.height);
    }

    private static Vec3 transformTexturePoint(Vec3 center, Vec3 local, double rotationX, double rotationY, double rotationZ) {
        return center.add(rotateTextureVector(local, rotationX, rotationY, rotationZ));
    }

    private static Vec3 rotateTextureVector(Vec3 vector, double rotationX, double rotationY, double rotationZ) {
        double x = vector.x;
        double y = vector.y;
        double z = vector.z;

        double cosX = Math.cos(rotationX);
        double sinX = Math.sin(rotationX);
        double yX = y * cosX - z * sinX;
        double zX = y * sinX + z * cosX;
        y = yX;
        z = zX;

        double cosY = Math.cos(rotationY);
        double sinY = Math.sin(rotationY);
        double xY = x * cosY - z * sinY;
        double zY = x * sinY + z * cosY;
        x = xY;
        z = zY;

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

    private record LocalAnimation(double scale, double bob, double rotation, float alphaMultiplier) {
    }

    private static double horizontalExtent(DialogueDefinition.Trigger trigger) {
        return switch (normalizeShape(trigger.shape)) {
            case "box" -> Math.max(trigger.size_x, trigger.size_z) * 0.5D;
            default -> Math.max(0.1D, trigger.radius);
        };
    }

    private static String normalizeShape(String shape) {
        if (shape == null) {
            return "cylinder";
        }

        String normalized = shape.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sphere", "box", "cylinder" -> normalized;
            default -> "cylinder";
        };
    }
}
