package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.dialogue.text.DialogueTextRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;

public final class DialogueTextAnimationEditorScreen extends DialogueRetroScreen {

    private final Screen parent;
    private final DialogueEditorProject project;
    private final DialogueDefinition.Line line;
    private final DialogueDefinition.TextRegion region;
    private final String sample;

    private int panelW;
    private int left;

    private int previewTop;
    private int previewBottom;

    private int contentTop;
    private int contentBottom;

    private int scrollOffset;
    private int contentHeight;

    private int previewTicks;

    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private final List<Label> labels = new ArrayList<>();

    public DialogueTextAnimationEditorScreen(Screen parent, DialogueEditorProject project, DialogueDefinition.Line line, DialogueDefinition.TextRegion region, String sample) {
        super(Component.literal("Dialogue Studio - Text Animation"));

        this.parent = parent;
        this.project = project;
        this.line = line;
        this.region = region;
        this.sample = sample != null && !sample.isBlank() ? sample : "Animated text";

        if (this.region.animation == null) {
            this.region.animation = new DialogueDefinition.TextAnimation();
        }
    }


    @Override
    protected void init() {
        scrollWidgets.clear();
        labels.clear();

        panelW = Math.min(700, width - 24);

        left = (width - panelW) / 2;

        previewTop = 48;

        previewBottom = Math.min(118, Math.max(92, height / 3));

        contentTop = previewBottom + 14;

        contentBottom = height - 44;

        int y = contentTop + 8 - scrollOffset;

        DialogueDefinition.TextAnimation animation = region.animation;

        y = addSlider(y, "Wave amplitude", value(animation.wave_amplitude, 0.85F), 0.0D, 4.0D, 2, v -> animation.wave_amplitude = (float) v);
        y = addSlider(y, "Wave speed", value(animation.wave_speed, 5.0F), 0.0D, 12.0D, 2, v -> animation.wave_speed = (float) v);
        y = addSlider(y, "Wave spacing / frequency", value(animation.wave_frequency, 0.55F), 0.05D, 1.50D, 2, v -> animation.wave_frequency = (float) v);
        y = addSlider(y, "Shake strength", value(animation.shake_strength, 1.0F), 0.0D, 5.0D, 2, v -> animation.shake_strength = (float) v);
        y = addSlider(y, "Explode amount", value(animation.explode_amount, 0.85F), 0.0D, 3.0D, 2, v -> animation.explode_amount = (float) v);
        y = addSlider(y, "Explode duration (ticks)", intValue(animation.explode_ticks, 6), 1.0D, 30.0D, 0, v -> animation.explode_ticks = Math.max(1, (int) Math.round(v)));
        y = addSlider(y, "Slide distance (pixels)", value(animation.slide_distance, 13.0F), 0.0D, 48.0D, 1, v -> animation.slide_distance = (float) v);
        y = addSlider(y, "Slide duration (ticks)", intValue(animation.slide_ticks, 6), 1.0D, 30.0D, 0, v -> animation.slide_ticks = Math.max(1, (int) Math.round(v)));

        y += 4;

        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Reset animation parameters to engine defaults"), button -> {
            region.animation = new DialogueDefinition.TextAnimation();

            rebuild();
        }).bounds(left + 16, y, panelW - 32, 20).build());

        y += 30;

        contentHeight = Math.max(contentBottom - contentTop, y + scrollOffset - contentTop + 12);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());

        updateVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> {
            DialogueEditorHistory.checkpoint(project);

            minecraft.setScreen(parent);
        }).bounds(left + 16, height - 30, panelW - 32, 20).build());
    }


    private int addSlider(int y, String label, double initial, double min, double max, int decimals, DoubleConsumer consumer) {
        addLabel(label, left + 16, y - 10, 0xFFCFC6A6);

        RichSlider slider = new RichSlider(left + 16, y, panelW - 32, 20, label, initial, min, max, decimals, consumer);
        addScrollableWidget(slider);

        return y + 34;
    }


    private void addLabel(String text, int x, int y, int color) {
        labels.add(new Label(text, x, y, color));
    }


    private <T extends AbstractWidget> T addScrollableWidget(T widget) {
        scrollWidgets.add(widget);
        return addRenderableWidget(widget);
    }


    private void updateVisibility() {
        for (AbstractWidget widget : scrollWidgets) {
            widget.visible = widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= contentBottom;
        }
    }


    @Override
    public void tick() {
        previewTicks++;
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelW && mouseY >= contentTop && mouseY <= contentBottom) {

            int old = scrollOffset;

            if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - 28);

            } else if (delta < 0) {
                scrollOffset = Math.min(maxScroll(), scrollOffset + 28);
            }

            if (old != scrollOffset) {
                rebuild();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 10, left + panelW, height - 8, 0xF0121710);
        graphics.drawString(font, "TEXT ANIMATION • REGION", left + 16, 18, 0xFFB8FF72, false);
        graphics.drawString(font, "Drag sliders and watch the selected phrase react immediately.", left + 16, 30, DialogueRetroTheme.TEXT_HINT, false);

        renderAnimationPreview(graphics, partialTick);
        graphics.enableScissor(left, contentTop, left + panelW, contentBottom);

        for (Label label : labels) {

            if (label.y >= contentTop && label.y <= contentBottom - 9) {

                graphics.drawString(font, label.text, label.x, label.y, label.color, false);
            }
        }

        graphics.disableScissor();

        renderScrollbar(graphics);

        graphics.fill(left, contentBottom + 1, left + panelW, height - 8, 0xF0090C08);

        graphics.fill(left, contentBottom, left + panelW, contentBottom + 1, 0xFF445438);

        super.render(graphics, mouseX, mouseY, partialTick);
    }


    private void renderAnimationPreview(GuiGraphics graphics, float partialTick) {
        int x = left + 16;
        int y = previewTop;
        int w = panelW - 32;
        int h = Math.max(38, previewBottom - previewTop - 4);

        graphics.fill(x, y, x + w, y + h, 0xFF0E130C);

        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF172013);

        List<String> effects = effectiveEffects();

        String summary = effects.isEmpty() ? "NO EFFECTS • parameters are stored for later" : String.join(" + ", effects).toUpperCase(Locale.ROOT);

        graphics.drawString(font, summary, x + 8, y + 6, effects.isEmpty() ? 0xFF9D967D : 0xFFFFD45A, false);

        int baseX = x + Math.max(12, (w - sampleWidth()) / 2);

        int baseY = y + h / 2 + 4;

        DialogueDefinition.TextAnimation animation = region.animation != null ? region.animation : new DialogueDefinition.TextAnimation();

        float simulationAge = (previewTicks + partialTick) % 24.0F;

        int cursorX = baseX;

        for (int i = 0; i < sample.length(); i++) {

            char character = sample.charAt(i);

            DialogueTextRenderUtil.GlyphStyle glyphStyle = effectiveGlyphStyle();
            int glyphWidth = Math.max(1, DialogueTextRenderUtil.width(font, character, glyphStyle));

            float gx = cursorX;
            float gy = baseY;
            float glyphScale = 1.0F;

            for (String effect : effects) {

                switch (effect.toLowerCase(Locale.ROOT)) {
                    case "wave" -> {
                        float amplitude = value(animation.wave_amplitude, 0.85F);
                        float speed = value(animation.wave_speed, 5.0F);
                        float frequency = value(animation.wave_frequency, 0.55F);

                        gy += Mth.sin((previewTicks + partialTick) * 0.044F * speed + i * frequency) * amplitude;
                    }

                    case "shake" -> {
                        float strength = value(animation.shake_strength, 1.0F);
                        long seed = i * 734287L + previewTicks * 912271L;

                        gx += hashOffset(seed) * strength;

                        gy += hashOffset(seed + 19L) * strength;
                    }

                    case "explode" -> {
                        int duration = intValue(animation.explode_ticks, 6);

                        float amount = value(animation.explode_amount, 0.85F);
                        float progress = smooth(simulationAge / Math.max(1, duration));

                        glyphScale *= 1.0F + (1.0F - progress) * amount;
                    }

                    case "slide", "linear" -> {

                        int duration = intValue(animation.slide_ticks, 6);

                        float distance = value(animation.slide_distance, 13.0F);
                        float progress = smooth(simulationAge / Math.max(1, duration));

                        gx -= (1.0F - progress) * distance;
                    }
                }
            }

            PoseStack pose = graphics.pose();

            pose.pushPose();

            pose.translate(gx, gy, 0);

            if (glyphScale != 1.0F) {
                pose.translate(glyphWidth * 0.5F, font.lineHeight * 0.5F, 0);
                pose.scale(glyphScale, glyphScale, 1);
                pose.translate(-glyphWidth * 0.5F, -font.lineHeight * 0.5F, 0);
            }

            int color = 0xFF000000 | previewColor(i);
            int outline = 0xFF000000 | previewOutlineColor(i);

            DialogueTextRenderUtil.drawGlyph(graphics, font, character, color, outline, previewOutlineThickness(), glyphStyle, glyphScale);

            pose.popPose();

            cursorX += glyphWidth;
        }
    }


    private DialogueTextRenderUtil.GlyphStyle effectiveGlyphStyle() {
        String fontId = region.font != null ? region.font : line.text_font != null ? line.text_font : project.definition.text_font;
        return new DialogueTextRenderUtil.GlyphStyle(fontId, region.bold != null && region.bold, region.italic != null && region.italic, region.underline != null && region.underline, region.strikethrough != null && region.strikethrough);
    }

    private int sampleWidth() {
        DialogueTextRenderUtil.GlyphStyle style = effectiveGlyphStyle();
        int result = 0;
        for (int i = 0; i < sample.length(); i++) {
            result += DialogueTextRenderUtil.width(font, sample.charAt(i), style);
        }
        return result;
    }

    private int previewColor(int index) {
        List<String> gradient = region.gradient != null ? region.gradient : line.text_gradient != null ? line.text_gradient : project.definition.text_gradient;
        if (gradient != null && gradient.size() >= 2) {
            return gradientColor(gradient, sample.length() <= 1 ? 0.0F : index / (float) (sample.length() - 1));
        }
        String color = region.color != null ? region.color : line.text_color != null ? line.text_color : project.definition.text_color;
        return DialogueEditorPreview.parseColor(color);
    }

    private int previewOutlineColor(int index) {
        List<String> gradient = region.outline_gradient != null ? region.outline_gradient : line.text_outline_gradient != null ? line.text_outline_gradient : project.definition.text_outline_gradient;
        if (gradient != null && gradient.size() >= 2) {
            return gradientColor(gradient, sample.length() <= 1 ? 0.0F : index / (float) (sample.length() - 1));
        }
        String color = region.outline_color != null ? region.outline_color : line.text_outline_color != null ? line.text_outline_color : project.definition.text_outline_color;
        return DialogueEditorPreview.parseColor(color != null ? color : "black");
    }

    private float previewOutlineThickness() {
        if (region.outline_thickness != null) return Math.max(0.0F, region.outline_thickness);
        if (line.text_outline_thickness != null) return Math.max(0.0F, line.text_outline_thickness);
        return Math.max(0.0F, project.definition.text_outline_thickness);
    }

    private int gradientColor(List<String> colors, float t) {
        int sections = colors.size() - 1;
        float scaled = Mth.clamp(t, 0.0F, 1.0F) * sections;
        int index = Mth.clamp((int) Math.floor(scaled), 0, sections - 1);
        float local = scaled - index;
        int a = DialogueEditorPreview.parseColor(colors.get(index));
        int b = DialogueEditorPreview.parseColor(colors.get(index + 1));
        int r = Math.round(Mth.lerp(local, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(local, (a >> 8) & 255, (b >> 8) & 255));
        int blue = Math.round(Mth.lerp(local, a & 255, b & 255));
        return (r << 16) | (g << 8) | blue;
    }

    private List<String> effectiveEffects() {
        if (region.effects != null) {
            return normalize(region.effects);
        }

        if (line.text_effects != null) {
            return normalize(line.text_effects);
        }

        if (project.definition.text_effects != null) {
            return normalize(project.definition.text_effects);
        }

        String legacy = line.text_effect != null ? line.text_effect : project.definition.text_effect;

        if (legacy == null || legacy.isBlank() || "normal".equalsIgnoreCase(legacy)) {

            return List.of();
        }

        return List.of(legacy.toLowerCase(Locale.ROOT));
    }


    private List<String> normalize(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();

        if (values != null) {
            for (String value : values) {

                if (value == null || value.isBlank() || "normal".equalsIgnoreCase(value)) {

                    continue;
                }

                result.add(value.toLowerCase(Locale.ROOT));
            }
        }

        return List.copyOf(result);
    }


    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom - contentTop));
    }


    private void renderScrollbar(GuiGraphics graphics) {
        int max = maxScroll();

        if (max <= 0) {
            return;
        }

        int trackX = left + panelW - 7;

        int trackTop = contentTop + 2;
        int trackBottom = contentBottom - 2;
        int trackH = Math.max(1, trackBottom - trackTop);
        int viewportH = Math.max(1, contentBottom - contentTop);
        int thumbH = Math.max(18, Math.round(trackH * (viewportH / (float) contentHeight)));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = trackTop + Math.round(travel * (scrollOffset / (float) max));

        graphics.fill(trackX, trackTop, trackX + 2, trackBottom, 0x555B664C);

        graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB8FF72);
    }


    private void rebuild() {
        minecraft.setScreen(this);
    }


    private static float value(Float value, float fallback) {
        return value != null ? value : fallback;
    }


    private static int intValue(Integer value, int fallback) {
        return value != null ? Math.max(1, value) : fallback;
    }


    private static float hashOffset(long seed) {
        seed ^= seed >>> 33;

        seed *= 0xff51afd7ed558ccdL;

        seed ^= seed >>> 33;

        return ((seed >>> 40) & 255) / 255.0F - 0.5F;
    }


    private static float smooth(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);

        return value * value * (3.0F - 2.0F * value);
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }


    private record Label(String text, int x, int y, int color) {
    }


    private static final class RichSlider extends DialogueRetroSlider {

        private final String label;
        private final double min;
        private final double max;
        private final int decimals;
        private final DoubleConsumer consumer;

        private RichSlider(int x, int y, int width, int height, String label, double initial, double min, double max, int decimals, DoubleConsumer consumer) {
            super(x, y, width, height, Component.empty(), normalize(initial, min, max));

            this.label = label;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.consumer = consumer;

            updateMessage();
        }


        @Override
        protected void updateMessage() {
            double actual = actual();

            String format = "%." + decimals + "f";

            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, format, actual)));
        }


        @Override
        protected void applyValue() {
            double actual = actual();

            consumer.accept(actual);

            updateMessage();
        }


        private double actual() {
            return min + (max - min) * value;
        }


        private static double normalize(double value, double min, double max) {
            if (max <= min) {
                return 0.0D;
            }
            return Mth.clamp((value - min) / (max - min), 0.0D, 1.0D);
        }
    }
}
