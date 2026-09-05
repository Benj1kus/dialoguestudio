package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class DialogueZoneFxGuiPreview {

    private static final double TAU = Math.PI * 2.0D;

    private DialogueZoneFxGuiPreview() {
    }

    public static void render(DialogueEditorProject project, GuiGraphics graphics, int x, int y, int width, int height, int previewTicks, float partialTick) {
        DialogueDefinition.Trigger trigger = project.currentTrigger();
        if (trigger.visual == null) {
            trigger.visual = new DialogueDefinition.ZoneVisual();
        }

        DialogueDefinition.ZoneVisual visual = trigger.visual;

        DialogueRetroTheme.drawPanel(graphics, x, y, x + width, y + height);
        DialogueRetroTheme.drawTitleBar(graphics, x + 3, y + 3, x + width - 3, 24);
        DialogueRetroTheme.drawDarkInset(graphics, x + 8, y + 28, x + width - 8, y + height - 48);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawString(font, "LIVE PREVIEW", x + 10, y + 9, 0xFFB8FF72, false);

        String shape = normalized(trigger.shape, "cylinder");
        String textureMode = normalized(visual.texture_mode, "plane");
        String preset = visual.preset != null ? visual.preset : "custom";

        String subtitle = shape + " | " + textureMode + " | " + preset;
        graphics.drawString(font, fitText(font, subtitle, width - 20), x + 10, y + 18, DialogueRetroTheme.TEXT_LIGHT, false);

        int vx = x + 8;
        int vy = y + 28;
        int vw = Math.max(20, width - 16);
        int vh = Math.max(30, height - 76);

        double seconds = (previewTicks + partialTick) / 20.0D;
        PreviewAnimation animation = animation(visual, seconds);

        double rawWidth = markerWidth(trigger, visual);
        double rawHeight = markerHeight(trigger, visual);
        double maxWidthPx = vw * 0.60D;
        double maxHeightPx = vh * 0.58D;
        double unit = Math.min(maxWidthPx / Math.max(0.1D, rawWidth), maxHeightPx / Math.max(0.1D, rawHeight));
        unit = Mth.clamp(unit, 7.0D, 48.0D);

        int zoneCenterX = vx + vw / 2;
        int groundY = vy + (int) (vh * 0.74D);

        int markerW = Math.max(12, (int) Math.round(rawWidth * unit * animation.scale));
        int markerH = Math.max(8, (int) Math.round(rawHeight * unit));

        int zoneX0 = zoneCenterX - markerW / 2;
        int zoneX1 = zoneCenterX + markerW / 2;
        int zoneY1 = groundY - (int) Math.round((visual.y_offset + animation.bob) * unit);
        int zoneY0 = zoneY1 - markerH;

        graphics.enableScissor(vx, vy, vx + vw, vy + vh);
        drawGroundGrid(graphics, vx, vy, vw, vh, groundY);

        if (visual.fill_enabled && ("cylinder".equals(shape) || "box".equals(shape))) {
            drawFill(graphics, shape, zoneX0, zoneY0, zoneX1, zoneY1, visual, animation.alphaMultiplier);
        }

        if (visual.texture != null && !visual.texture.isBlank()) {
            ResourceLocation texture = DialogueEditorTextureCache.resolve(project, visual.texture, null);
            if (texture != null) {
                drawTexture(project, graphics, texture, textureMode, zoneCenterX, groundY, markerW, markerH, visual, animation, seconds, unit);
            } else {
                graphics.drawString(font, "Texture could not be resolved", vx + 8, vy + 8, 0xFFFF7B7B, false);
            }
        }

        if (visual.show_default_zone) {
            drawDefaultGeometry(graphics, shape, normalized(visual.style, "auto"), zoneX0, zoneY0, zoneX1, zoneY1, DialogueEditorPreview.parseColor(visual.color), Mth.clamp(visual.alpha * animation.alphaMultiplier, 0.0F, 1.0F));
        }

        graphics.disableScissor();

        int infoY = y + height - 40;

        int infoWidth = Math.max(20, width - 20);

        String layerInfo = "default " + (visual.show_default_zone ? "ON" : "OFF") + " | fill " + (visual.fill_enabled ? visual.fill_mode : "OFF") + " | tex " + (visual.texture != null && !visual.texture.isBlank() ? "ON" : "OFF");
        graphics.drawString(font, fitText(font, layerInfo, infoWidth), x + 10, infoY, DialogueRetroTheme.TEXT_PATH, false);

        String animationInfo = String.format(Locale.ROOT,
                "pulse %s | bob %s | rot %.0f/%.0f/%.0f | alpha %s",
                visual.pulse ? "ON" : "OFF",
                visual.bob ? "ON" : "OFF",
                visual.texture_rotation_x,
                visual.texture_rotation + (visual.rotate ? seconds * visual.rotate_speed : 0.0D),
                visual.texture_rotation_z,
                visual.alpha_breathe ? "ON" : "OFF");

        graphics.drawString(font, fitText(font, animationInfo, infoWidth), x + 10, infoY + 10, DialogueRetroTheme.TEXT_HINT, false);
        graphics.drawString(font, fitText(font, "World gizmo = exact transform", infoWidth), x + 10, infoY + 20, DialogueRetroTheme.TEXT_PATH, false);
    }

    private static String fitText(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int available = Math.max(1, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(text, available) + suffix;
    }

    private static PreviewAnimation animation(DialogueDefinition.ZoneVisual visual, double seconds) {
        double scale = 1.0D;
        if (visual.pulse) {
            scale += Math.sin(seconds * TAU * Math.max(0.0D, visual.pulse_speed)) * Math.max(0.0D, visual.pulse_amplitude);
        }
        scale = Math.max(0.05D, scale);

        double bob = visual.bob ? Math.sin(seconds * TAU * Math.max(0.0D, visual.bob_speed)) * Math.max(0.0D, visual.bob_amplitude) : 0.0D;

        double alphaMultiplier = 1.0D;
        if (visual.alpha_breathe) {
            double amount = Mth.clamp(visual.alpha_breathe_amount, 0.0D, 1.0D);
            double wave = 0.5D + 0.5D * Math.sin(seconds * TAU * Math.max(0.0D, visual.alpha_breathe_speed));
            alphaMultiplier = 1.0D - amount + wave * amount;
        }

        double animationRotation = visual.rotate ? seconds * visual.rotate_speed : 0.0D;
        return new PreviewAnimation(scale, bob, animationRotation, (float) alphaMultiplier);
    }

    private static void drawGroundGrid(GuiGraphics graphics, int x, int y, int w, int h, int groundY) {
        int left = x + 10;
        int right = x + w - 10;
        int top = Math.max(y + 8, groundY - Math.max(32, h / 3));

        for (int i = 0; i <= 8; i++) {
            int gx = left + Math.round((right - left) * (i / 8.0F));
            graphics.fill(gx, top, gx + 1, groundY + 1, 0x222B4151);
        }

        for (int i = 0; i <= 4; i++) {
            int gy = top + Math.round((groundY - top) * (i / 4.0F));
            graphics.fill(left, gy, right, gy + 1, 0x222B4151);
        }
    }

    private static void drawFill(GuiGraphics graphics, String shape, int x0, int y0, int x1, int y1, DialogueDefinition.ZoneVisual visual, float alphaMultiplier) {
        int bottom = DialogueEditorPreview.parseColor(visual.fill_color_bottom);
        int top = "solid".equalsIgnoreCase(visual.fill_mode) ? bottom : DialogueEditorPreview.parseColor(visual.fill_color_top);

        float bottomAlpha = Mth.clamp(visual.alpha * visual.fill_alpha_bottom * alphaMultiplier, 0.0F, 1.0F);
        float topRaw = "solid".equalsIgnoreCase(visual.fill_mode) ? visual.fill_alpha_bottom : visual.fill_alpha_top;
        float topAlpha = Mth.clamp(visual.alpha * topRaw * alphaMultiplier, 0.0F, 1.0F);

        int h = Math.max(1, y1 - y0);
        for (int yy = 0; yy < h; yy++) {
            float t = yy / (float) Math.max(1, h - 1);
            int rgb = lerpColor(top, bottom, t);
            float alpha = Mth.lerp(t, topAlpha, bottomAlpha);
            int argb = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | rgb;

            if ("cylinder".equals(shape)) {
                double ny = (yy / (double) h) * 2.0D - 1.0D;
                double curve = Math.sqrt(Math.max(0.0D, 1.0D - ny * ny));
                int inset = (int) Math.round((1.0D - curve) * (x1 - x0) * 0.05D);
                graphics.fill(x0 + inset, y0 + yy, x1 - inset, y0 + yy + 1, argb);
            } else {
                graphics.fill(x0, y0 + yy, x1, y0 + yy + 1, argb);
            }
        }
    }

    private static void drawTexture(DialogueEditorProject project, GuiGraphics graphics, ResourceLocation texture, String mode, int zoneCenterX, int groundY, int markerW, int markerH, DialogueDefinition.ZoneVisual visual, PreviewAnimation animation, double seconds, double unit) {
        int offsetX = (int) Math.round((visual.texture_offset_x - visual.texture_offset_z * 0.35D) * unit);
        int offsetY = (int) Math.round(visual.texture_offset_y * unit);

        int centerX = zoneCenterX + offsetX;
        int baseY = groundY - (int) Math.round((visual.y_offset + animation.bob) * unit) - offsetY;

        double scaleX = Math.max(0.05D, visual.texture_scale_x);
        double scaleY = Math.max(0.05D, visual.texture_scale_y);

        int tw;
        int th;

        if ("plane".equals(mode)) {
            tw = Math.max(8, (int) Math.round(markerW * scaleX));
            th = Math.max(8, (int) Math.round(markerW * scaleY));
        } else {
            tw = Math.max(8, (int) Math.round(markerW * scaleX));
            th = Math.max(8, (int) Math.round(markerH * scaleY));
        }

        int tx = centerX - tw / 2;
        int ty = "plane".equals(mode) ? baseY - th / 2 : baseY - th;

        float alpha = Mth.clamp(visual.alpha * animation.alphaMultiplier, 0.0F, 1.0F);
        double rotation = visual.texture_rotation + animation.animationRotation;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        graphics.pose().pushPose();
        if ("plane".equals(mode)) {
            graphics.pose().translate(centerX, baseY - th / 2.0D, 0.0D);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) rotation));
            drawTextureTiles(graphics, texture, -tw / 2, -th / 2, tw, th, visual, seconds);
        } else {
            drawTextureTiles(graphics, texture, tx, ty, tw, th, visual, seconds);
        }
        graphics.pose().popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (!"plane".equals(mode)) {
            double angle = Math.toRadians(rotation);
            int px = centerX + (int) Math.round(Math.cos(angle) * tw * 0.42D);
            int py = ty + 4 + (int) Math.round(Math.sin(angle) * 4.0D);
            drawLine(graphics, centerX, ty + 4, px, py, 0xFFFFD45A);
        }
    }

    private static void drawTextureTiles(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h, DialogueDefinition.ZoneVisual visual, double seconds) {
        boolean repeat = "repeat".equalsIgnoreCase(visual.texture_fit);
        if (!repeat) {
            graphics.blit(texture, x, y, 0.0F, 0.0F, w, h, w, h);
            return;
        }

        int repeatX = Mth.clamp((int) Math.ceil(Math.max(1.0D, visual.texture_repeat_x)), 1, 8);
        int repeatY = Mth.clamp((int) Math.ceil(Math.max(1.0D, visual.texture_repeat_y)), 1, 8);
        int tileW = Math.max(1, (int) Math.ceil(w / (double) repeatX));
        int tileH = Math.max(1, (int) Math.ceil(h / (double) repeatY));

        int scrollX = (int) Math.round(seconds * visual.texture_scroll_u * tileW);
        int scrollY = (int) Math.round(seconds * visual.texture_scroll_v * tileH);

        graphics.enableScissor(x, y, x + w, y + h);
        for (int iy = -1; iy <= repeatY; iy++) {
            for (int ix = -1; ix <= repeatX; ix++) {
                int dx = x + ix * tileW - Math.floorMod(scrollX, tileW);
                int dy = y + iy * tileH - Math.floorMod(scrollY, tileH);
                graphics.blit(texture, dx, dy, 0.0F, 0.0F, tileW, tileH, tileW, tileH);
            }
        }
        graphics.disableScissor();
    }

    private static void drawDefaultGeometry(GuiGraphics graphics, String shape, String style, int x0, int y0, int x1, int y1, int rgb, float alpha) {
        if ("auto".equals(style)) {
            style = "cylinder".equals(shape) ? "ring" : "outline";
        }

        int color = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | rgb;

        if ("ring".equals(style)) {
            ellipse(graphics, (x0 + x1) / 2, y1, Math.max(4, (x1 - x0) / 2), Math.max(2, (x1 - x0) / 8), color);
            return;
        }

        if ("pillar".equals(style) || "cylinder".equals(shape)) {
            ellipse(graphics, (x0 + x1) / 2, y0, Math.max(4, (x1 - x0) / 2), Math.max(2, (x1 - x0) / 8), color);
            ellipse(graphics, (x0 + x1) / 2, y1, Math.max(4, (x1 - x0) / 2), Math.max(2, (x1 - x0) / 8), color);
            graphics.fill(x0, y0, x0 + 1, y1 + 1, color);
            graphics.fill(x1 - 1, y0, x1, y1 + 1, color);
            return;
        }

        if ("sphere".equals(shape)) {
            ellipse(graphics, (x0 + x1) / 2, (y0 + y1) / 2, Math.max(4, (x1 - x0) / 2), Math.max(4, (y1 - y0) / 2), color);
            ellipse(graphics, (x0 + x1) / 2, (y0 + y1) / 2, Math.max(4, (x1 - x0) / 2), Math.max(2, (y1 - y0) / 7), color);
            return;
        }

        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void ellipse(GuiGraphics graphics, int cx, int cy, int rx, int ry, int color) {
        int previousX = cx + rx;
        int previousY = cy;

        for (int i = 1; i <= 72; i++) {
            double angle = TAU * i / 72.0D;
            int px = cx + (int) Math.round(Math.cos(angle) * rx);
            int py = cy + (int) Math.round(Math.sin(angle) * ry);
            drawLine(graphics, previousX, previousY, px, py, color);
            previousX = px;
            previousY = py;
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = error * 2;
            if (e2 >= dy) {
                error += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;

        int r = Mth.clamp(Math.round(Mth.lerp(t, ar, br)), 0, 255);
        int g = Mth.clamp(Math.round(Mth.lerp(t, ag, bg)), 0, 255);
        int bl = Mth.clamp(Math.round(Mth.lerp(t, ab, bb)), 0, 255);
        return r << 16 | g << 8 | bl;
    }

    private static double markerWidth(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual) {
        if (visual.size > 0.0D) {
            return Math.max(0.1D, visual.size);
        }

        return switch (normalized(trigger.shape, "cylinder")) {
            case "box" -> Math.max(0.1D, trigger.size_x);
            case "sphere" -> Math.max(0.1D, trigger.radius * 2.0D);
            default -> Math.max(0.1D, trigger.radius * 2.0D);
        };
    }

    private static double markerHeight(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual) {
        if (visual.visual_height > 0.0D) {
            return Math.max(0.1D, visual.visual_height);
        }

        return switch (normalized(trigger.shape, "cylinder")) {
            case "box" -> Math.max(0.1D, trigger.size_y);
            case "sphere" -> Math.max(0.1D, trigger.radius * 2.0D);
            default -> Math.max(0.1D, trigger.height);
        };
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toLowerCase(Locale.ROOT);
    }

    private record PreviewAnimation(double scale, double bob, double animationRotation, float alphaMultiplier) {
    }
}
