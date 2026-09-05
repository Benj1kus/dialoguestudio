package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.dialogue.text.DialogueMarkdown;
import com.benji.dialoguestudio.dialogue.text.DialogueRichTextUtil;
import com.benji.dialoguestudio.dialogue.text.DialogueTextRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogueEditorPreview {

    private static final ResourceLocation DEFAULT_FRAME = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/frame_dialog.png");
    private static final ResourceLocation DEFAULT_BACKGROUND = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/dithering_gradient.png");
    private static final ResourceLocation DEFAULT_SPRITE = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/default_sprite.png");

    private DialogueEditorPreview() {
    }

    public record Transform(float originX, float originY, float scale, int canvasWidth, int canvasHeight) {
        public float screenX(float canvasX) {
            return originX + canvasX * scale;
        }

        public float screenY(float canvasY) {
            return originY + canvasY * scale;
        }

        public float canvasX(double screenX) {
            return (float) ((screenX - originX) / scale);
        }

        public float canvasY(double screenY) {
            return (float) ((screenY - originY) / scale);
        }
    }

    public static Transform render(DialogueEditorProject project, GuiGraphics graphics, int x, int y, int width, int height, int ticks, float partialTick) {
        return render(project, graphics, x, y, width, height, ticks, partialTick, false);
    }

    public static Transform render(DialogueEditorProject project, GuiGraphics graphics, int x, int y, int width, int height, int ticks, float partialTick, boolean forceSelectedLegacyLine) {
        project.normalize();

        DialogueDefinition definition = project.definition;

        DialogueDefinition.Layout layout = definition.layout;

        boolean nodePreview = !forceSelectedLegacyLine && definition.graph_enabled && project.selected_node != null;

        DialogueDefinition.Line line = forceSelectedLegacyLine ? project.currentLine() : project.previewLine();

        DialogueRetroTheme.drawPanel(graphics, x, y, x + width, y + height);
        DialogueRetroTheme.drawTitleBar(graphics, x + 3, y + 3, x + width - 3, 28);

        int previewTop = y + 36;
        int previewBottom = y + height - 6;

        DialogueRetroTheme.drawDarkInset(graphics, x + 6, previewTop, x + width - 6, previewBottom);

        float availableHeight = Math.max(1.0F, previewBottom - previewTop - 8.0F);
        float scale = Math.min((width - 28) / (float) layout.canvas_width, availableHeight / (float) layout.canvas_height);
        scale = Math.max(0.2F, scale);

        float originX = x + (width - layout.canvas_width * scale) * 0.5F;
        float originY = previewTop + (previewBottom - previewTop - layout.canvas_height * scale) * 0.5F;

        Transform transform = new Transform(originX, originY, scale, layout.canvas_width, layout.canvas_height);

        graphics.drawString(Minecraft.getInstance().font, "LIVE DIALOGUE PREVIEW", x + 10, y + 9, 0xFFA8F06A, false);
        String previewLabel = nodePreview ? "Node " + project.selected_node + "  |  " + project.dialogueId() : "Line " + (project.selected_line + 1) + "/" + definition.lines.size() + "  |  " + project.dialogueId();

        Font headerFont = Minecraft.getInstance().font;

        previewLabel = ellipsize(headerFont, previewLabel, Math.max(20, width - 20));

        graphics.drawString(headerFont, previewLabel, x + 10, y + 21, DialogueRetroTheme.TEXT_LIGHT, false);
        graphics.enableScissor(x + 1, previewTop, x + width - 1, y + height - 1);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0.0F);
        pose.scale(scale, scale, 1.0F);

        renderBackground(project, graphics, definition);
        renderSprite(project, graphics, definition, line, ticks + partialTick);
        renderFrame(project, graphics, definition, line);
        renderText(project, graphics, definition, line, ticks + partialTick, nodePreview, scale);

        if (nodePreview) {
            DialogueDefinition.Node selectedNode = definition.nodes.get(project.selected_node);

            if (selectedNode != null && "choice".equalsIgnoreCase(selectedNode.type)) {
                renderChoicePreview(project, graphics, definition, selectedNode);
            }
        }

        pose.popPose();
        graphics.disableScissor();

        return transform;
    }

    private static void renderBackground(DialogueEditorProject project, GuiGraphics graphics, DialogueDefinition definition) {
        ResourceLocation texture = DialogueEditorTextureCache.resolve(project, definition.background, DEFAULT_BACKGROUND);
        if (texture == null) return;
        DialogueDefinition.Layout layout = definition.layout;
        graphics.setColor(1F, 1F, 1F, Mth.clamp(definition.background_alpha, 0F, 1F));
        graphics.blit(texture, 0, 0, 0, 0, layout.canvas_width, layout.canvas_height, layout.canvas_width, layout.canvas_height);
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    private static void renderFrame(DialogueEditorProject project, GuiGraphics graphics, DialogueDefinition definition, DialogueDefinition.Line line) {
        String declared = line.frame != null ? line.frame : definition.frame;
        ResourceLocation texture = DialogueEditorTextureCache.resolve(project, declared, DEFAULT_FRAME);
        if (texture == null) return;
        DialogueDefinition.Layout l = definition.layout;
        graphics.blit(texture, l.frame_x, l.frame_y, 0, 0, l.frame_width, l.frame_height, l.frame_width, l.frame_height);
    }

    private static void renderSprite(DialogueEditorProject project, GuiGraphics graphics, DialogueDefinition definition, DialogueDefinition.Line line, float time) {
        DialogueDefinition.Layout layout = definition.layout;
        int width = line.sprite_width != null ? line.sprite_width : layout.sprite_width;
        int height = line.sprite_height != null ? line.sprite_height : layout.sprite_height;
        float x = resolveSpriteX(definition, line);
        float y = layout.sprite_y;

        ResourceLocation texture;
        if (line.sprite == null || line.sprite.isBlank()) {
            texture = DEFAULT_SPRITE;
        } else {
            texture = DialogueEditorTextureCache.resolve(project, line.sprite, DEFAULT_SPRITE);
        }
        if (texture == null) return;

        String transition = line.sprite_transition != null ? line.sprite_transition : definition.sprite_transition;
        if (transition == null) transition = "none";
        transition = transition.toLowerCase(Locale.ROOT);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        float cx = x + width * 0.5F;
        float cy = y + height * 0.5F;

        switch (transition) {
            case "bounce" -> {
                float bounce = Mth.sin(time * 0.16F) * 1.6F;
                pose.translate(x, y + bounce, 0);
            }
            case "sway" -> {
                float sway = Mth.sin(time * 0.12F) * 2.0F;
                pose.translate(cx, cy, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(sway));
                pose.translate(-width * 0.5F, -height * 0.5F, 0);
            }
            case "fade", "fade_up" -> {
                float a = 0.72F + 0.28F * (0.5F + 0.5F * Mth.sin(time * 0.10F));
                graphics.setColor(1, 1, 1, a);
                pose.translate(x, y, 0);
            }
            default -> pose.translate(x, y, 0);
        }

        graphics.blit(texture, 0, 0, 0, 0, width, height, width, height);
        graphics.setColor(1, 1, 1, 1);
        pose.popPose();
    }

    private static float resolveSpriteX(DialogueDefinition definition, DialogueDefinition.Line line) {
        if (line.sprite_x != null) return line.sprite_x;
        String position = line.sprite_position != null ? line.sprite_position : definition.sprite_position;
        if (position == null) position = "center";
        return switch (position.toLowerCase(Locale.ROOT)) {
            case "left" -> definition.layout.sprite_left_x;
            case "right" -> definition.layout.sprite_right_x;
            default -> definition.layout.sprite_center_x;
        };
    }

    private static void renderText(DialogueEditorProject project, GuiGraphics graphics, DialogueDefinition definition, DialogueDefinition.Line line, float time, boolean nodePreview, float previewCanvasScale) {
        Font font = Minecraft.getInstance().font;

        DialogueDefinition.Layout layout = definition.layout;

        String text;

        String locale;

        if (line.literal != null) {
            text = line.literal;

            locale = null;

        } else if (nodePreview && project.selected_node != null) {

            text = project.getLocalizedNodeText(project.preview_locale, line, project.selected_node);

            locale = project.preview_locale;

        } else {
            text = project.getLocalizedText(project.preview_locale, line, project.selected_line);

            locale = project.preview_locale;
        }

        if (text == null || text.isEmpty()) {

            text = line.text != null ? "<" + line.text + ">" : "<empty line>";
        }

        DialogueMarkdown.Result markdown = DialogueMarkdown.parse(text, markdownEnabled(definition, line));
        text = markdown.text();

        int maxWidth = Math.max(1, Mth.floor(layout.text_width / layout.text_scale));

        List<Glyph> glyphs = layoutGlyphs(font, definition, line, text, locale, markdown, maxWidth, layout.line_height);

        List<String> baseEffects = resolveEffects(definition, line);

        int charTicks = line.char_ticks != null ? Math.max(1, line.char_ticks) : Math.max(1, definition.char_ticks);

        int visible = text.length();

        if (project.animate_preview && !text.isEmpty()) {

            int loop = text.length() * charTicks + 30;

            int phase = Math.floorMod((int) time, Math.max(1, loop));

            visible = Math.min(text.length(), phase / charTicks + 1);
        }

        PoseStack pose = graphics.pose();

        pose.pushPose();

        pose.translate(layout.text_x, layout.text_y, 10);

        pose.scale(layout.text_scale, layout.text_scale, 1);

        for (Glyph glyph : glyphs) {

            if (glyph.index >= visible) {
                continue;
            }

            DialogueRichTextUtil.ResolvedStyle rich = DialogueRichTextUtil.resolve(line, text, glyph.index, locale);

            List<String> effects = rich.effects != null ? normalizeEffects(rich.effects) : baseEffects;

            DialogueDefinition.TextAnimation animation = rich.animation;

            float gx = glyph.x;

            float gy = glyph.y;

            float glyphScale = 1.0F;

            float age = Math.max(0, visible - glyph.index);

            for (String effect : effects) {

                if (effect == null) {
                    continue;
                }

                switch (effect.toLowerCase(Locale.ROOT)) {
                    case "wave" -> {
                        float amplitude = animation.wave_amplitude != null ? animation.wave_amplitude : 0.85F;

                        float speed = animation.wave_speed != null ? animation.wave_speed : 5.0F;
                        float frequency = animation.wave_frequency != null ? animation.wave_frequency : 0.55F;

                        gy += Mth.sin(time * 0.044F * speed + glyph.index * frequency) * amplitude;
                    }

                    case "shake" -> {
                        float strength = animation.shake_strength != null ? animation.shake_strength : 1.0F;

                        long seed = glyph.index * 734287L + (long) time * 912271L;

                        gx += hashOffset(seed) * strength;

                        gy += hashOffset(seed + 19L) * strength;
                    }

                    case "explode" -> {
                        int duration = animation.explode_ticks != null ? Math.max(1, animation.explode_ticks) : 6;

                        float amount = animation.explode_amount != null ? Math.max(0.0F, animation.explode_amount) : 0.85F;

                        glyphScale *= 1.0F + Math.max(0, 1.0F - age / duration) * amount;
                    }

                    case "slide", "linear" -> {

                        int duration = animation.slide_ticks != null ? Math.max(1, animation.slide_ticks) : 6;

                        float distance = animation.slide_distance != null ? animation.slide_distance : 13.0F;

                        gx -= Math.max(0, 1.0F - age / duration) * distance;
                    }
                }
            }

            int rgb = letterColor(definition, line, glyph, maxWidth, time, rich);

            PoseStack glyphPose = graphics.pose();

            glyphPose.pushPose();

            glyphPose.translate(gx, gy, 0);

            if (glyphScale != 1.0F) {
                int glyphWidth = glyph.width;

                glyphPose.translate(glyphWidth * 0.5F, font.lineHeight * 0.5F, 0);

                glyphPose.scale(glyphScale, glyphScale, 1);

                glyphPose.translate(-glyphWidth * 0.5F, -font.lineHeight * 0.5F, 0);
            }

            DialogueTextRenderUtil.GlyphStyle glyphStyle = effectiveGlyphStyle(definition, line, markdown, glyph.index, rich);
            int outlineRgb = outlineColor(definition, line, glyph, maxWidth, time, rich);
            float outlineThickness = outlineThickness(definition, line, rich);

            float glyphToGuiScale = previewCanvasScale * layout.text_scale * glyphScale;

            DialogueTextRenderUtil.drawGlyph(graphics, font, glyph.character, 0xFF000000 | rgb, 0xFF000000 | outlineRgb, outlineThickness, glyphStyle, glyphToGuiScale);

            glyphPose.popPose();
        }

        pose.popPose();
    }

    private static List<String> resolveEffects(DialogueDefinition definition, DialogueDefinition.Line line) {
        if (line.text_effects != null) {
            return normalizeEffects(line.text_effects);
        }

        if (definition.text_effects != null) {
            return normalizeEffects(definition.text_effects);
        }

        String legacy = line.text_effect != null ? line.text_effect : definition.text_effect;
        if (legacy == null || legacy.isBlank() || "normal".equalsIgnoreCase(legacy)) {
            return List.of();
        }

        return List.of(legacy.toLowerCase(Locale.ROOT));
    }

    private static List<String> normalizeEffects(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();

        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();

        for (String value : values) {
            if (value == null || value.isBlank() || "normal".equalsIgnoreCase(value)) continue;
            result.add(value.trim().toLowerCase(Locale.ROOT));
        }

        return List.copyOf(result);
    }

    private static void renderChoicePreview(DialogueEditorProject project, GuiGraphics graphics, DialogueDefinition definition, DialogueDefinition.Node node) {
        if (node.choices == null || node.choices.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        DialogueDefinition.Layout layout = definition.layout;

        int count = node.choices.size();
        int rowHeight = Math.max(7, layout.choice_line_height);
        int startY = Math.min(layout.choice_y, layout.canvas_height - count * rowHeight - 2);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(layout.choice_x, startY, 15.0F);
        pose.scale(layout.choice_scale, layout.choice_scale, 1.0F);

        for (int i = 0; i < node.choices.size(); i++) {
            DialogueDefinition.Choice choice = node.choices.get(i);
            if (choice == null) continue;

            String text;
            if (choice.literal != null) {
                text = choice.literal;
            } else {
                text = project.getLocalizedChoiceText(project.preview_locale, choice, project.selected_node, i);
            }

            if (text == null || text.isBlank()) {
                text = choice.text != null ? "<" + choice.text + ">" : "<choice>";
            }

            int color = i == 0 ? parseColor(layout.choice_selected_color) : parseColor(layout.choice_color);

            graphics.drawString(font, (i == 0 ? "> " : "  ") + (i + 1) + ". " + text, 0, i * rowHeight, 0xFF000000 | color, false);
        }

        pose.popPose();
    }


    private static int letterColor(DialogueDefinition definition, DialogueDefinition.Line line, Glyph glyph, int maxWidth, float time, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.gradient != null) {

            if (rich.gradient.size() >= 2) {
                int span = Math.max(1, rich.gradientEnd - rich.gradientStart - 1);

                float t = Mth.clamp((glyph.index - rich.gradientStart) / (float) span, 0.0F, 1.0F);

                return gradientColor(rich.gradient, t);
            }

        } else {
            List<String> gradient = line.text_gradient != null ? line.text_gradient : definition.text_gradient;

            if (gradient != null && gradient.size() >= 2) {

                float t = Mth.clamp(glyph.x / (float) Math.max(1, maxWidth), 0F, 1F);

                return gradientColor(gradient, t);
            }
        }

        String value = rich.color != null ? rich.color : (line.text_color != null ? line.text_color : definition.text_color);

        if ("rainbow".equalsIgnoreCase(value)) {
            float hue = (glyph.index * 0.095F + time * 0.0028F) % 1.0F;

            return Color.HSBtoRGB(hue, 0.76F, 1.0F) & 0xFFFFFF;
        }

        return parseColor(value);
    }

    private static int gradientColor(List<String> colors, float t) {
        int sections = colors.size() - 1;
        float scaled = t * sections;
        int index = Mth.clamp((int) Math.floor(scaled), 0, sections - 1);
        float local = scaled - index;
        int a = parseColor(colors.get(index));
        int b = parseColor(colors.get(index + 1));
        int r = Math.round(Mth.lerp(local, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(local, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(local, a & 255, b & 255));
        return (r << 16) | (g << 8) | bl;
    }

    private static String ellipsize(Font font, String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (font.width(value) <= maxWidth) {
            return value;
        }

        String suffix = "…";

        int allowed = Math.max(0, maxWidth - font.width(suffix));

        return font.plainSubstrByWidth(value, allowed) + suffix;
    }


    public static int parseColor(String value) {
        if (value == null) return 0xFFFFFF;
        value = value.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "blue" -> 0x4AA3FF;
            case "red" -> 0xFF4D55;
            case "gold", "golden" -> 0xFFD45A;
            case "green" -> 0x55E878;
            case "black" -> 0x000000;
            case "purple" -> 0xB76CFF;
            case "cyan" -> 0x42F2E1;
            default -> parseHex(value);
        };
    }

    private static int parseHex(String value) {
        try {
            if (value.startsWith("#")) value = value.substring(1);
            if (value.startsWith("0x")) value = value.substring(2);
            if (value.length() == 3)
                value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2);
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }

    private static float hashOffset(long value) {
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        return ((value & 1023L) / 1023.0F - 0.5F) * 1.6F;
    }

    private static List<Glyph> layoutGlyphs(Font font, DialogueDefinition definition, DialogueDefinition.Line line, String text, String locale, DialogueMarkdown.Result markdown, int maxWidth, int lineHeight) {
        List<Glyph> result = new ArrayList<>();
        int x = 0;
        int y = 0;
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                x = 0;
                y += lineHeight;
                i++;
                continue;
            }

            if (Character.isWhitespace(c)) {
                int cw = styledWidth(font, definition, line, text, locale, markdown, i, c);
                if (x + cw > maxWidth) {
                    x = 0;
                    y += lineHeight;
                } else {
                    result.add(new Glyph(i, c, x, y, cw));
                    x += cw;
                }
                i++;
                continue;
            }

            int wordEnd = i;
            while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd)) && text.charAt(wordEnd) != '\n')
                wordEnd++;

            int wordWidth = 0;
            for (int j = i; j < wordEnd; j++) {
                wordWidth += styledWidth(font, definition, line, text, locale, markdown, j, text.charAt(j));
            }

            if (x > 0 && x + wordWidth > maxWidth) {
                x = 0;
                y += lineHeight;
            }

            for (int j = i; j < wordEnd; j++) {
                char letter = text.charAt(j);
                int cw = styledWidth(font, definition, line, text, locale, markdown, j, letter);
                if (x > 0 && x + cw > maxWidth) {
                    x = 0;
                    y += lineHeight;
                }
                result.add(new Glyph(j, letter, x, y, cw));
                x += cw;
            }
            i = wordEnd;
        }
        return result;
    }

    private static int styledWidth(Font font, DialogueDefinition definition, DialogueDefinition.Line line, String text, String locale, DialogueMarkdown.Result markdown, int index, char character) {
        DialogueRichTextUtil.ResolvedStyle rich = DialogueRichTextUtil.resolve(line, text, index, locale);
        return DialogueTextRenderUtil.width(font, character, effectiveGlyphStyle(definition, line, markdown, index, rich));
    }

    private static DialogueTextRenderUtil.GlyphStyle effectiveGlyphStyle(DialogueDefinition definition, DialogueDefinition.Line line, DialogueMarkdown.Result markdown, int index, DialogueRichTextUtil.ResolvedStyle rich) {
        DialogueMarkdown.CharStyle md = markdown.styleAt(index);
        return new DialogueTextRenderUtil.GlyphStyle(rich.font != null ? rich.font : line.text_font != null ? line.text_font : definition.text_font, rich.bold != null ? rich.bold : md.bold(), rich.italic != null ? rich.italic : md.italic(), rich.underline != null ? rich.underline : md.underline(), rich.strikethrough != null ? rich.strikethrough : md.strikethrough());
    }

    private static boolean markdownEnabled(DialogueDefinition definition, DialogueDefinition.Line line) {
        return line.markdown != null ? line.markdown : definition.markdown;
    }

    private static float outlineThickness(DialogueDefinition definition, DialogueDefinition.Line line, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.outlineThickness != null) return Math.max(0.0F, rich.outlineThickness);
        if (line.text_outline_thickness != null) return Math.max(0.0F, line.text_outline_thickness);
        return Math.max(0.0F, definition.text_outline_thickness);
    }

    private static int outlineColor(DialogueDefinition definition, DialogueDefinition.Line line, Glyph glyph, int maxWidth, float time, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.outlineGradient != null && rich.outlineGradient.size() >= 2) {
            int span = Math.max(1, rich.outlineGradientEnd - rich.outlineGradientStart - 1);
            float t = Mth.clamp((glyph.index - rich.outlineGradientStart) / (float) span, 0.0F, 1.0F);
            return gradientColor(rich.outlineGradient, t);
        }

        List<String> gradient = line.text_outline_gradient != null ? line.text_outline_gradient : definition.text_outline_gradient;
        if (rich.outlineGradient == null && gradient != null && gradient.size() >= 2) {
            float t = Mth.clamp(glyph.x / (float) Math.max(1, maxWidth), 0.0F, 1.0F);
            return gradientColor(gradient, t);
        }

        String value = rich.outlineColor != null ? rich.outlineColor : line.text_outline_color != null ? line.text_outline_color : definition.text_outline_color;
        if (value == null || value.isBlank()) value = "black";
        if ("rainbow".equalsIgnoreCase(value)) {
            float hue = (glyph.index * 0.095F + time * 0.0028F) % 1.0F;
            return Color.HSBtoRGB(hue, 0.76F, 1.0F) & 0xFFFFFF;
        }
        return parseColor(value);
    }


    private record Glyph(int index, char character, int x, int y, int width) {
    }
}
