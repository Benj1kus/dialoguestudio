package com.benji.dialoguestudio.dialogue.text;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

public final class DialogueNpcNameRenderer {
    private DialogueNpcNameRenderer() {}

    public static float scale(DialogueDefinition.Layout layout) {
        return Float.isFinite(layout.npc_name_scale) ? Math.max(.1F, Math.min(4, layout.npc_name_scale)) : .72F;
    }
    public static float x(DialogueDefinition.Layout layout) {
        return layout.npc_name_x != null && Float.isFinite(layout.npc_name_x) ? layout.npc_name_x : layout.frame_x + layout.frame_width * .5F;
    }
    public static float y(DialogueDefinition.Layout layout, int fontHeight) {
        return layout.npc_name_y != null && Float.isFinite(layout.npc_name_y) ? layout.npc_name_y : layout.frame_y - fontHeight * scale(layout) - 2;
    }

    public static void render(GuiGraphics graphics, Font font, DialogueDefinition definition, String source,
                              String locale, float seconds, float alpha, float canvasScale) {
        DialogueDefinition.Line name = definition.npc_name;
        int opacity = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        if (name == null || source == null || source.isBlank() || opacity < 4) return;
        var tags = DialogueTextTags.expand(DialogueMarkdown.parse(source, name.markdown != null ? name.markdown : definition.markdown));
        var markdown = tags.markdown();
        String text = markdown.text();
        if (text.isBlank()) return;
        var frame = new DialogueTextEffects.Frame(text.length(), i -> tags.resolve(name, i, locale));
        var styles = new DialogueTextRenderUtil.GlyphStyle[text.length()];
        int[] widths = new int[text.length()];
        int totalWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            var rich = frame.style(i);
            var md = markdown.styleAt(i);
            styles[i] = new DialogueTextRenderUtil.GlyphStyle(
                    rich.font != null ? rich.font : name.text_font != null ? name.text_font : definition.text_font,
                    rich.bold != null ? rich.bold : md.bold(), rich.italic != null ? rich.italic : md.italic(),
                    rich.underline != null ? rich.underline : md.underline(), rich.strikethrough != null ? rich.strikethrough : md.strikethrough());
            widths[i] = DialogueTextRenderUtil.width(font, character(text, i), styles[i]);
            totalWidth += widths[i];
        }
        float scale = scale(definition.layout);
        graphics.pose().pushPose();
        graphics.pose().translate(x(definition.layout) - totalWidth * scale * .5F, y(definition.layout, font.lineHeight), 12);
        graphics.pose().scale(scale, scale, 1);
        List<String> baseEffects = DialogueTextEffects.baseEffects(definition, name);
        int advance = 0;
        for (int i = 0; i < text.length(); i++) {
            var rich = frame.style(i);
            List<String> palette = DialogueTextEffects.palette(definition, name, rich);
            List<String> effects = rich.effects != null ? rich.effects : baseEffects;
            String baseColor = rich.color != null ? rich.color : name.text_color != null ? name.text_color : definition.text_color;
            if ((baseColor == null || "white".equals(baseColor)) && rich.color == null) {
                String legacy = name.text_style != null ? name.text_style : definition.text_style;
                if (legacy != null) baseColor = legacy;
            }
            int rgb = animatedColor(baseColor, i, seconds);
            if (palette != null && palette.size() >= 2) {
                double fraction = rich.gradient != null ? (i - rich.gradientStart) / (double) Math.max(1, rich.gradientEnd - rich.gradientStart - 1)
                        : advance / (double) Math.max(1, totalWidth - widths[text.length() - 1]);
                rgb = DialogueTextEffects.gradient(palette, fraction, DialogueNpcNameRenderer::parseColor);
            }
            rgb = DialogueTextEffects.color(rgb, effects, palette, seconds, i, frame.start(i), frame.length(i), DialogueNpcNameRenderer::parseColor);
            float outline = rich.outlineThickness != null ? rich.outlineThickness : name.text_outline_thickness != null ? name.text_outline_thickness : definition.text_outline_thickness;
            String outlineValue = rich.outlineColor != null ? rich.outlineColor : name.text_outline_color != null ? name.text_outline_color : definition.text_outline_color;
            int outlineRgb = animatedColor(outlineValue == null ? "black" : outlineValue, i, seconds);
            List<String> outlinePalette = rich.outlineGradient != null ? rich.outlineGradient : name.text_outline_gradient != null ? name.text_outline_gradient : definition.text_outline_gradient;
            if (outlinePalette != null && outlinePalette.size() >= 2) {
                double fraction = rich.outlineGradient != null ? (i - rich.outlineGradientStart) / (double) Math.max(1, rich.outlineGradientEnd - rich.outlineGradientStart - 1)
                        : advance / (double) Math.max(1, totalWidth - widths[text.length() - 1]);
                outlineRgb = DialogueTextEffects.gradient(outlinePalette, fraction, DialogueNpcNameRenderer::parseColor);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(advance, 0, 0);
            DialogueTextRenderUtil.drawGlyph(graphics, font, character(text, i), opacity << 24 | rgb,
                    opacity << 24 | outlineRgb, outline, styles[i], canvasScale * scale, DialogueTextEffects.has(effects, "glow"));
            graphics.pose().popPose();
            advance += widths[i];
        }
        graphics.pose().popPose();
    }

    private static char character(String text, int index) {
        char c = text.charAt(index);
        return c == '\n' || c == '\r' ? ' ' : c;
    }
    private static int animatedColor(String value, int index, float seconds) {
        if ("rainbow".equalsIgnoreCase(value)) return Color.HSBtoRGB((index * .095F + seconds * .055F) % 1, .76F, 1) & 0xFFFFFF;
        return parseColor(value);
    }
    private static int parseColor(String value) {
        if (value == null || value.isBlank()) return 0xFFFFFF;
        String clean = value.trim().toLowerCase(Locale.ROOT);

        switch (clean) {
            case "blue": return 0x4AA3FF;
            case "red": return 0xFF4D55;
            case "gold", "golden": return 0xFFD45A;
            case "green": return 0x55E878;
            case "black": return 0;
            case "purple": return 0xB76CFF;
            case "cyan": return 0x42F2E1;
        }
        try {
            if (clean.startsWith("#")) clean = clean.substring(1);
            if (clean.startsWith("0x")) clean = clean.substring(2);
            if (clean.length() == 3) clean = "" + clean.charAt(0) + clean.charAt(0) + clean.charAt(1) + clean.charAt(1) + clean.charAt(2) + clean.charAt(2);
            return Integer.parseInt(clean, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) { return 0xFFFFFF; }
    }
}
