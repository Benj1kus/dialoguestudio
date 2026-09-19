package com.benji.dialoguestudio.dialogue.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;


public final class DialogueTextRenderUtil {

    private DialogueTextRenderUtil() {
    }

    public record GlyphStyle(String font, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
    }

    public static MutableComponent glyphComponent(char character, GlyphStyle style) {
        MutableComponent component = Component.literal(String.valueOf(character));

        component.setStyle(component.getStyle().withBold(style.bold()).withItalic(style.italic()).withUnderlined(style.underline()).withStrikethrough(style.strikethrough()));

        ResourceLocation fontId = fontId(style.font());
        if (fontId != null && isFontResourceAvailable(fontId)) {

            component.setStyle(component.getStyle().withFont(fontId));
        }

        return component;
    }

    public static int width(Font font, char character, GlyphStyle style) {
        return font.width(glyphComponent(character, style));
    }

    public static int width(Font font, String value, java.util.function.IntFunction<GlyphStyle> styles) {
        if (value == null || value.isEmpty()) {
            return 0;
        }

        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            width += width(font, value.charAt(i), styles.apply(i));
        }
        return width;
    }
    public static void drawGlyph(GuiGraphics graphics, Font font, char character, int color, int outlineColor, float outlineThickness, GlyphStyle style) {
        drawGlyph(graphics, font, character, color, outlineColor, outlineThickness, style, 1.0F);
    }

    public static void drawGlyph(GuiGraphics graphics, Font font, char character, int color, int outlineColor, float outlineThickness, GlyphStyle style, float localToGuiScale) {
        drawGlyph(graphics, font, character, color, outlineColor, outlineThickness, style, localToGuiScale, false);
    }

    public static void drawGlyph(GuiGraphics graphics, Font font, char character, int color, int outlineColor, float outlineThickness, GlyphStyle style, float localToGuiScale, boolean glow) {
        MutableComponent component = glyphComponent(character, style);

        if (glow && !Character.isWhitespace(character)) {
            float scale = Math.max(0.001F, Math.abs(localToGuiScale));
            int opacity = color >>> 24;
            for (int ring = 0; ring < 2; ring++) {
                int count = ring == 0 ? 8 : 4;
                float radius = (ring == 0 ? 1.7F : 0.85F) / scale;
                int auraAlpha = Math.round(opacity * (ring == 0 ? 0.14F : 0.20F));
                if (auraAlpha < 26) continue;
                int auraColor = (auraAlpha << 24) | (color & 0xFFFFFF);
                for (int sample = 0; sample < count; sample++) {
                    double angle = sample * Math.PI * 2 / count;
                    graphics.pose().pushPose();
                    graphics.pose().translate(Math.cos(angle) * radius, Math.sin(angle) * radius, -0.05F);
                    graphics.drawString(font, component, 0, 0, auraColor, false);
                    graphics.pose().popPose();
                }
            }
        }

        float requested = Math.max(0.0F, Math.min(4.0F, outlineThickness));

        if (requested > 0.01F) {
            int radiusGuiPixels = Math.max(1, Math.min(4, Math.round(requested)));

            float safeScale = Math.max(0.001F, Math.abs(localToGuiScale));
            float limit = radiusGuiPixels + 0.45F;
            float limitSq = limit * limit;

            for (int dy = -radiusGuiPixels; dy <= radiusGuiPixels; dy++) {

                for (int dx = -radiusGuiPixels; dx <= radiusGuiPixels; dx++) {

                    if (dx == 0 && dy == 0) {
                        continue;
                    }

                    float distanceSq = dx * dx + dy * dy;

                    if (distanceSq > limitSq) {
                        continue;
                    }

                    float localX = dx / safeScale;
                    float localY = dy / safeScale;
                    var pose = graphics.pose();

                    pose.pushPose();
                    pose.translate(localX, localY, 0.0F);
                    graphics.drawString(font, component, 0, 0, outlineColor, false);
                    pose.popPose();
                }
            }
        }
        graphics.drawString(font, component, 0, 0, color, false);
    }


    private static boolean isFontResourceAvailable(ResourceLocation fontId) {
        try {
            ResourceLocation definition = ResourceLocation.fromNamespaceAndPath(fontId.getNamespace(), "font/" + fontId.getPath() + ".json");

            return Minecraft.getInstance().getResourceManager().getResource(definition).isPresent();

        } catch (Exception ignored) {
            return false;
        }
    }


    public static ResourceLocation fontId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String clean = value.trim().toLowerCase(Locale.ROOT);

        if (clean.equals("default") || clean.equals("vanilla") || clean.equals("minecraft:default")) {
            return null;
        }

        return ResourceLocation.tryParse(clean);
    }
}
