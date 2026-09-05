package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.dialogue.text.DialogueTextRenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogueOutlineEditorScreen extends DialogueRetroScreen {

    private enum Scope {
        GLOBAL, LINE, REGION
    }

    private final Screen parent;
    private final DialogueEditorProject project;
    private final DialogueDefinition.Line line;
    private final DialogueDefinition.TextRegion region;
    private final Scope scope;

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    private final List<Label> contentLabels = new ArrayList<>();

    private int left;
    private int panelW;

    private int previewTop;
    private int previewBottom;

    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int scrollOffset;

    public static DialogueOutlineEditorScreen global(Screen parent, DialogueEditorProject project) {
        return new DialogueOutlineEditorScreen(parent, project, null, null, Scope.GLOBAL);
    }

    public static DialogueOutlineEditorScreen line(Screen parent, DialogueEditorProject project, DialogueDefinition.Line line) {
        return new DialogueOutlineEditorScreen(parent, project, line, null, Scope.LINE);
    }

    public static DialogueOutlineEditorScreen region(Screen parent, DialogueEditorProject project, DialogueDefinition.Line line, DialogueDefinition.TextRegion region) {
        return new DialogueOutlineEditorScreen(parent, project, line, region, Scope.REGION);
    }

    private DialogueOutlineEditorScreen(Screen parent, DialogueEditorProject project, DialogueDefinition.Line line, DialogueDefinition.TextRegion region, Scope scope) {
        super(Component.literal("Dialogue Studio - Text Outline"));

        this.parent = parent;

        this.project = project;

        this.line = line;

        this.region = region;

        this.scope = scope;
    }

    @Override
    protected void init() {
        contentWidgets.clear();
        contentLabels.clear();

        panelW = Math.min(700, width - 16);

        left = (width - panelW) / 2;

        int innerW = panelW - 32;

        previewTop = 42;

        int previewH = Mth.clamp(height / 5, 46, 72);

        previewBottom = previewTop + previewH;

        contentTop = previewBottom + 8;

        contentBottom = height - 42;

        int y = contentTop + 6 - scrollOffset;

        y = addContentButton(y, modeLabel(), () -> {
            cycleMode();
            rebuild();
        });

        y += 2;

        y = addColorRow(y, innerW);
        y = addGradientRow(y, innerW);
        y = addThicknessRow(y, innerW);
        y = addContentButton(y, scope == Scope.GLOBAL ? "Reset GLOBAL outline to OFF" : "Reset to INHERIT", () -> {
            reset();
            rebuild();
        });

        contentHeight = Math.max(contentBottom - contentTop, y + scrollOffset - contentTop + 6);

        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));

        updateContentVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> {
            DialogueEditorHistory.checkpoint(project);

            minecraft.setScreen(parent);
        }).bounds(left + 16, height - 30, innerW, 20).build());
    }

    private int addContentButton(int y, String title, Runnable action) {
        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal(title), button -> action.run()).bounds(left + 16, y, panelW - 32, 20).build());

        return y + 30;
    }

    private int addColorRow(int y, int innerW) {
        addContentLabel(scope == Scope.GLOBAL ? "Color (blank = black)" : "Color (blank = INHERIT)", left + 16, y, 0xFFCFC6A6);

        y += 11;

        int pickerW = 86;

        EditBox color = new DialogueRetroEditBox(font, left + 16, y, innerW - pickerW - 4, 20, Component.literal("Outline color"));

        color.setMaxLength(64);
        color.setValue(colorValue());

        color.setResponder(this::setColor);

        addContentWidget(color);

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("Color..."), button -> minecraft.setScreen(new DialogueEditorColorPickerScreen(this, colorValue().isBlank() ? effectiveColor() : colorValue(), picked -> {
            setColor(picked);
            rebuild();
        }))).bounds(left + 16 + innerW - pickerW, y, pickerW, 20).build());
        return y + 30;
    }

    private int addGradientRow(int y, int innerW) {
        addContentLabel(scope == Scope.GLOBAL ? "Gradient: blank = use color" : "Gradient: blank = INHERIT, none = disable", left + 16, y, 0xFFCFC6A6);

        y += 11;

        EditBox gradient = new DialogueRetroEditBox(font, left + 16, y, innerW, 20, Component.literal("Outline gradient"));

        gradient.setMaxLength(512);
        gradient.setValue(gradientText(gradientValue()));

        gradient.setResponder(value -> setGradient(parseGradient(value)));

        addContentWidget(gradient);

        return y + 30;
    }

    private int addThicknessRow(int y, int innerW) {
        addContentLabel(scope == Scope.GLOBAL ? "Thickness: 0 = OFF, 1..4 = crisp pixel radius" : "Thickness: blank = INHERIT, 0 = OFF, 1..4 = crisp", left + 16, y, 0xFFCFC6A6);

        y += 11;

        int small = 66;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("- 0.25"), button -> {
            setThickness(Math.max(0.0F, resolvedEditableThickness() - 0.25F));

            rebuild();
        }).bounds(left + 16, y, small, 20).build());

        EditBox thickness = new DialogueRetroEditBox(font, left + 16 + small + 4, y, innerW - small * 2 - 8, 20, Component.literal("Outline thickness"));

        thickness.setValue(thicknessText());

        thickness.setResponder(value -> {
            if (scope != Scope.GLOBAL && (value == null || value.isBlank())) {

                setThicknessNullable(null);

                return;
            }

            try {
                setThickness(Mth.clamp(Float.parseFloat(value.trim()), 0.0F, 4.0F));

            } catch (Exception ignored) {
            }
        });

        addContentWidget(thickness);

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("+ 0.25"), button -> {
            setThickness(Math.min(4.0F, resolvedEditableThickness() + 0.25F));

            rebuild();
        }).bounds(left + panelW - 16 - small, y, small, 20).build());

        return y + 30;
    }

    private <T extends AbstractWidget> T addContentWidget(T widget) {
        contentWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private void addContentLabel(String text, int x, int y, int color) {
        contentLabels.add(new Label(text, x, y, color));
    }

    private void updateContentVisibility() {
        for (AbstractWidget widget : contentWidgets) {

            widget.visible = widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= contentBottom;
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom - contentTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelW && mouseY >= contentTop && mouseY <= contentBottom) {

            int old = scrollOffset;

            if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - 30);

            } else if (delta < 0) {
                scrollOffset = Math.min(maxScroll(), scrollOffset + 30);
            }

            if (old != scrollOffset) {
                rebuild();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void rebuild() {
        minecraft.setScreen(this);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 8, left + panelW, height - 8, 0xF0121710);
        graphics.drawString(font, "TEXT OUTLINE • " + scope.name(), left + 16, 16, 0xFFB8FF72, false);
        graphics.drawString(font, scopeHelp(), left + 16, 29, DialogueRetroTheme.TEXT_HINT, false);
        graphics.fill(left + 16, previewTop, left + panelW - 16, previewBottom, 0xFF0B1009);
        graphics.drawString(font, "LIVE PREVIEW", left + 26, previewTop + 8, DialogueRetroTheme.TEXT_HINT, false);

        renderPreviewText(graphics);

        graphics.enableScissor(left, contentTop, left + panelW, contentBottom);

        for (Label label : contentLabels) {

            if (label.y + font.lineHeight >= contentTop && label.y <= contentBottom) {

                graphics.drawString(font, label.text, label.x, label.y, label.color, false);
            }
        }

        graphics.disableScissor();

        if (maxScroll() > 0) {
            renderScrollbar(graphics);
        }

        graphics.fill(left, contentBottom + 1, left + panelW, height - 8, 0xF0090C08);
        graphics.fill(left, contentBottom, left + panelW, contentBottom + 1, 0xFF445438);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreviewText(GuiGraphics graphics) {
        String sample = scope == Scope.REGION ? "selected region" : scope == Scope.LINE ? "whole line" : "whole dialogue";

        DialogueTextRenderUtil.GlyphStyle style = new DialogueTextRenderUtil.GlyphStyle(effectiveFont(), false, false, false, false);

        int x = left + 26;
        int y = previewTop + 28;

        List<String> gradient = effectiveGradient();

        for (int i = 0; i < sample.length(); i++) {

            char c = sample.charAt(i);

            int outlineRgb = previewOutlineColor(i, sample.length(), gradient);

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);

            DialogueTextRenderUtil.drawGlyph(graphics, font, c, 0xFFFFD45A, 0xFF000000 | outlineRgb, effectiveThickness(), style);
            graphics.pose().popPose();

            x += DialogueTextRenderUtil.width(font, c, style);
        }
    }

    private int previewOutlineColor(int index, int length, List<String> gradient) {
        if (gradient != null && gradient.size() >= 2) {

            float t = length <= 1 ? 0.0F : index / (float) (length - 1);

            return gradientColor(gradient, t);
        }

        String value = effectiveColor();

        if ("rainbow".equalsIgnoreCase(value)) {

            float hue = length <= 1 ? 0.0F : index / (float) length;

            return Color.HSBtoRGB(hue, 0.82F, 1.0F) & 0xFFFFFF;
        }

        return DialogueEditorPreview.parseColor(value);
    }

    private static int gradientColor(List<String> colors, float t) {
        int sections = colors.size() - 1;

        float scaled = Mth.clamp(t, 0.0F, 1.0F) * sections;

        int index = Mth.clamp((int) Math.floor(scaled), 0, sections - 1);

        float local = scaled - index;

        int a = DialogueEditorPreview.parseColor(colors.get(index));
        int b = DialogueEditorPreview.parseColor(colors.get(index + 1));
        int r = Math.round(Mth.lerp(local, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(local, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(local, a & 255, b & 255));

        return (r << 16) | (g << 8) | bl;
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
        int thumbH = Math.max(18, Math.round(trackH * viewportH / (float) contentHeight));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = trackTop + Math.round(travel * scrollOffset / (float) max);

        graphics.fill(trackX, trackTop, trackX + 2, trackBottom, 0x555B664C);

        graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB8FF72);
    }

    private String modeLabel() {
        Float own = ownThickness();

        if (scope == Scope.GLOBAL) {
            return project.definition.text_outline_thickness > 0.01F ? "Outline: ON  •  " + format(project.definition.text_outline_thickness) + "  (crisp " + crispRadius(project.definition.text_outline_thickness) + " px)" : "Outline: OFF";
        }

        if (own == null) {
            return "Outline: INHERIT  •  effective " + format(effectiveThickness()) + "  (crisp " + crispRadius(effectiveThickness()) + " px)";
        }

        return own > 0.01F ? "Outline: ON  •  " + format(own) + "  (crisp " + crispRadius(own) + " px)" : "Outline: OFF";
    }

    private static int crispRadius(float requested) {
        if (requested <= 0.01F) {
            return 0;
        }

        return Math.max(1, Math.min(4, Math.round(requested)));
    }

    private void cycleMode() {
        if (scope == Scope.GLOBAL) {
            project.definition.text_outline_thickness = project.definition.text_outline_thickness > 0.01F ? 0.0F : 1.0F;

            return;
        }

        Float own = ownThickness();

        if (own == null) {
            setThicknessNullable(1.0F);

        } else if (own > 0.01F) {
            setThicknessNullable(0.0F);

        } else {
            setThicknessNullable(null);
        }
    }

    private String scopeHelp() {
        return switch (scope) {
            case GLOBAL -> "GLOBAL affects every line unless a Line/Region overrides it.";

            case LINE -> "LINE affects this whole line unless a Rich Text region overrides it.";

            case REGION -> "REGION affects only the selected Rich Text word/phrase.";
        };
    }

    private String colorValue() {
        return switch (scope) {
            case GLOBAL -> nullToEmpty(project.definition.text_outline_color);

            case LINE -> nullToEmpty(line.text_outline_color);

            case REGION -> nullToEmpty(region.outline_color);
        };
    }

    private void setColor(String value) {
        String clean = blankToNull(value);

        switch (scope) {
            case GLOBAL -> project.definition.text_outline_color = clean;

            case LINE -> line.text_outline_color = clean;

            case REGION -> region.outline_color = clean;
        }
    }

    private List<String> gradientValue() {
        return switch (scope) {
            case GLOBAL -> project.definition.text_outline_gradient;

            case LINE -> line.text_outline_gradient;

            case REGION -> region.outline_gradient;
        };
    }

    private List<String> effectiveGradient() {
        if (scope == Scope.REGION && region.outline_gradient != null) {

            return region.outline_gradient;
        }

        if (scope != Scope.GLOBAL && line != null && line.text_outline_gradient != null) {

            return line.text_outline_gradient;
        }

        return project.definition.text_outline_gradient;
    }

    private void setGradient(List<String> value) {
        switch (scope) {
            case GLOBAL -> project.definition.text_outline_gradient = value;

            case LINE -> line.text_outline_gradient = value;

            case REGION -> region.outline_gradient = value;
        }
    }

    private Float ownThickness() {
        return switch (scope) {
            case GLOBAL -> project.definition.text_outline_thickness;

            case LINE -> line.text_outline_thickness;

            case REGION -> region.outline_thickness;
        };
    }

    private float effectiveThickness() {
        if (scope == Scope.GLOBAL) {
            return Math.max(0.0F, project.definition.text_outline_thickness);
        }

        if (scope == Scope.LINE) {
            return line.text_outline_thickness != null ? Math.max(0.0F, line.text_outline_thickness) : Math.max(0.0F, project.definition.text_outline_thickness);
        }

        if (region.outline_thickness != null) {
            return Math.max(0.0F, region.outline_thickness);
        }

        if (line != null && line.text_outline_thickness != null) {

            return Math.max(0.0F, line.text_outline_thickness);
        }

        return Math.max(0.0F, project.definition.text_outline_thickness);
    }

    private float resolvedEditableThickness() {
        Float own = ownThickness();

        return own != null ? Math.max(0.0F, own) : Math.max(0.0F, effectiveThickness());
    }

    private void setThickness(float value) {
        value = Mth.clamp(value, 0.0F, 4.0F);

        if (scope == Scope.GLOBAL) {
            project.definition.text_outline_thickness = value;

        } else {
            setThicknessNullable(value);
        }
    }

    private void setThicknessNullable(Float value) {
        switch (scope) {
            case GLOBAL -> {
                if (value != null) {
                    project.definition.text_outline_thickness = value;
                }
            }

            case LINE -> line.text_outline_thickness = value;

            case REGION -> region.outline_thickness = value;
        }
    }

    private String thicknessText() {
        Float own = ownThickness();

        if (scope != Scope.GLOBAL && own == null) {
            return "";
        }

        return format(own != null ? own : effectiveThickness());
    }

    private String effectiveColor() {
        String own = colorValue();

        if (!own.isBlank()) {
            return own;
        }

        if (scope == Scope.REGION && line != null && line.text_outline_color != null && !line.text_outline_color.isBlank()) {

            return line.text_outline_color;
        }

        if (project.definition.text_outline_color != null && !project.definition.text_outline_color.isBlank()) {

            return project.definition.text_outline_color;
        }

        return "black";
    }

    private String effectiveFont() {
        if (scope == Scope.REGION && region != null && region.font != null && !region.font.isBlank()) {

            return region.font;
        }

        if (line != null && line.text_font != null && !line.text_font.isBlank()) {

            return line.text_font;
        }

        return project.definition.text_font;
    }

    private void reset() {
        switch (scope) {
            case GLOBAL -> {
                project.definition.text_outline_color = null;

                project.definition.text_outline_gradient = null;

                project.definition.text_outline_thickness = 0.0F;
            }

            case LINE -> {
                line.text_outline_color = null;

                line.text_outline_gradient = null;

                line.text_outline_thickness = null;
            }

            case REGION -> {
                region.outline_color = null;

                region.outline_gradient = null;

                region.outline_thickness = null;
            }
        }
    }

    private static String gradientText(List<String> gradient) {
        if (gradient == null) {
            return "";
        }

        if (gradient.isEmpty()) {
            return "none";
        }

        return String.join(", ", gradient);
    }

    private static List<String> parseGradient(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        if ("none".equalsIgnoreCase(value.trim())) {

            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        for (String part : value.split(",")) {

            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }

        return result.isEmpty() ? null : result;
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Label(String text, int x, int y, int color) {
    }
}
