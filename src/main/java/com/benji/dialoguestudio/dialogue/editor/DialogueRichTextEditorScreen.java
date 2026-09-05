package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.dialogue.text.DialogueMarkdown;
import com.benji.dialoguestudio.dialogue.text.DialogueRichTextUtil;
import com.benji.dialoguestudio.dialogue.text.DialogueTextRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class DialogueRichTextEditorScreen extends DialogueRetroScreen {

    private final Screen parent;
    private final DialogueEditorProject project;
    private final DialogueDefinition.Line line;
    private final String sourceText;
    private String text;
    private DialogueMarkdown.Result markdownResult = DialogueMarkdown.parse("", false);
    private final String locale;
    private final String heading;

    private int panelW;
    private int left;

    private int previewTop;
    private int previewHeight;
    private int previewLeft;
    private int previewWidth;

    private int settingsTop;
    private int settingsBottom;
    private int scrollOffset;
    private int contentHeight;

    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private final List<Label> labels = new ArrayList<>();

    private final List<Card> cards = new ArrayList<>();

    private int selectionAnchor = -1;
    private int selectionCursor = -1;
    private boolean selecting;

    private int selectedRegion = -1;
    private int previewTicks;

    private List<TextGlyph> textGlyphs = List.of();

    private int textOriginX;
    private int textOriginY;
    private int textMaxWidth;
    private int textLineHeight;

    public DialogueRichTextEditorScreen(Screen parent, DialogueEditorProject project, DialogueDefinition.Line line, String text, String locale, String heading) {
        super(Component.literal("Dialogue Studio - Rich Text"));

        this.parent = parent;
        this.project = project;
        this.line = line;
        this.sourceText = text != null ? text : "";
        this.locale = locale;
        this.heading = heading != null ? heading : "Rich Text";

        if (this.line.rich_regions == null) {
            this.line.rich_regions = new ArrayList<>();
        }

        refreshParsedText();

        selectedRegion = firstRegionForLocale();
    }


    @Override
    protected void init() {
        scrollWidgets.clear();
        labels.clear();
        cards.clear();

        refreshParsedText();

        panelW = Math.min(920, width - 16);

        left = (width - panelW) / 2;

        previewTop = 58;

        previewHeight = Mth.clamp(height / 5, 56, 104);

        previewLeft = left + 16;

        previewWidth = panelW - 32;

        settingsTop = previewTop + previewHeight + 30;

        settingsBottom = height - 36;

        textOriginX = previewLeft + 12;

        textOriginY = previewTop + 17;

        textMaxWidth = Math.max(32, previewWidth - 24);

        textLineHeight = Math.max(font.lineHeight + 4, 12);

        textGlyphs = layoutText(font, text, textMaxWidth, textLineHeight);

        buildFixedControls();

        int y = settingsTop + 8 - scrollOffset;

        y = addInfoCard(y, "LINE BASE STYLE", "Markdown, font and outline here affect the whole line. A Rich Text region can override any of them for only the selected word/phrase.", 0xFF3F6438, 0xFFE8E0C3);

        y = addTwoButtons(y, "Markdown: " + nullableBoolean(line.markdown), () -> {
            line.markdown = nextNullableBoolean(line.markdown);
            selectionAnchor = -1;
            selectionCursor = -1;
            refreshParsedText();
            rebuild();
        }, "LINE font: " + lineFontSummary(), () -> openFontPicker(line.text_font, value -> {
            line.text_font = value;
            rebuild();
        }));

        y = addFullButton(y, lineOutlineSummary(), () -> minecraft.setScreen(DialogueOutlineEditorScreen.line(this, project, line)));

        y = addFullButton(y, "Reset LINE markdown/font/outline to INHERIT", () -> {
            line.markdown = null;
            line.text_font = null;
            line.text_outline_color = null;
            line.text_outline_gradient = null;
            line.text_outline_thickness = null;
            selectionAnchor = -1;
            selectionCursor = -1;
            refreshParsedText();
            rebuild();
        });

        if (markdownEnabled()) {
            y = addInfoCard(y, "MARKDOWN", "**bold**  *italic*  ***bold italic***  ~~strike~~  __underline__  \\* escapes a marker. Rich Text selection uses the visible text without Markdown symbols.", 0xFF5B703F, 0xFFD8E36A);
        }

        DialogueDefinition.TextRegion region = currentRegion();

        if (region == null) {
            y = addInfoCard(y, "HOW TO CREATE A RICH TEXT REGION", "Drag over one or more characters in the text preview above, then press Create region from selection. The new region can override only that part of the sentence.", 0xFF3D7438, 0xFFA8F06A);
            y = addInfoCard(y, "INHERITANCE", "A region only changes the properties you set. Blank color/effects/animation values inherit the normal line/global style, so old Dialogue Engine styling still works underneath Rich Text.", 0xFF526B3D, 0xFFD2C8AA);

        } else {
            DialogueRichTextUtil.Range range = DialogueRichTextUtil.resolveRange(region, text);

            String snippet = range != null ? safeSubstring(text, range.start(), range.end()) : "<invalid range>";

            y = addInfoCard(y, "SELECTED REGION", "Characters " + (range != null ? range.start() + ".." + range.end() : "?") + "  •  \"" + printable(snippet) + "\"", 0xFF4A6F3F, 0xFFECE5C9);
            y = addTextField(y, "Region name (optional)", region.name, 96, value -> region.name = blankToNull(value));

            if (locale != null) {
                y = addFullButton(y, region.locale == null || region.locale.isBlank() || "*".equals(region.locale) ? "Scope: ALL LANGUAGES" : "Scope: THIS LANGUAGE (" + region.locale + ")", () -> {
                    if (region.locale == null || region.locale.isBlank() || "*".equals(region.locale)) {

                        region.locale = locale;

                    } else {
                        region.locale = null;
                    }

                    rebuild();
                });
            } else {
                y = addInfoCard(y, "SCOPE: LITERAL TEXT", "Literal dialogue has no language-file locale, so this region follows the literal sentence directly.", 0xFF526B3D, 0xFFD2C8AA);
            }

            y = addColorField(y, region);
            y = addGradientField(y, region);

            y = addTwoButtons(y, "Bold: " + nullableBoolean(region.bold), () -> {
                region.bold = nextNullableBoolean(region.bold);
                rebuild();
            }, "Italic: " + nullableBoolean(region.italic), () -> {
                region.italic = nextNullableBoolean(region.italic);
                rebuild();
            });

            y = addTwoButtons(y, "Underline: " + nullableBoolean(region.underline), () -> {
                region.underline = nextNullableBoolean(region.underline);
                rebuild();
            }, "Strike: " + nullableBoolean(region.strikethrough), () -> {
                region.strikethrough = nextNullableBoolean(region.strikethrough);
                rebuild();
            });

            y = addFullButton(y, "REGION font: " + regionFontSummary(region), () -> openFontPicker(region.font, value -> {
                region.font = value;
                rebuild();
            }));

            y = addFullButton(y, regionOutlineSummary(region), () -> minecraft.setScreen(DialogueOutlineEditorScreen.region(this, project, line, region)));

            y = addFullButton(y, "Combined effects: " + effectsSummary(region.effects), () -> minecraft.setScreen(new DialogueEditorTextEffectsScreen(this, region.effects, true, effects -> {
                region.effects = effects;

                rebuild();
            })));

            y = addFullButton(y, "Visual animation parameters...", () -> minecraft.setScreen(new DialogueTextAnimationEditorScreen(this, project, line, region, snippet)));
            y = addInfoCard(y, "REGION OVERRIDES", "Color, gradient and effect stack are independent. Example: one word can be red + shake while the rest of the line keeps the inherited gold wave.", 0xFF566B3E, 0xFFD8E36A);
            y = addFullButton(y, "Reset ONLY this region's style to INHERIT", () -> {
                region.color = null;

                region.gradient = null;

                region.effects = null;
                region.bold = null;
                region.italic = null;
                region.underline = null;
                region.strikethrough = null;
                region.font = null;
                region.outline_color = null;
                region.outline_gradient = null;
                region.outline_thickness = null;

                region.animation = new DialogueDefinition.TextAnimation();

                rebuild();
            });
        }

        contentHeight = Math.max(settingsBottom - settingsTop, y + scrollOffset - settingsTop + 12);

        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());

        updateScrollVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> {
            DialogueRichTextUtil.repairRegions(line, text, locale);

            DialogueEditorHistory.checkpoint(project);

            minecraft.setScreen(parent);
        }).bounds(left + 16, height - 28, panelW - 32, 20).build());
    }


    private void buildFixedControls() {
        int buttonY = 36;
        int gap = 4;
        int usable = panelW - 32;
        int createW = Math.max(122, usable / 3);

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(selectionLength() > 0 ? "Create region from selection" : "Drag-select text first"), button -> createRegionFromSelection()).bounds(left + 16, buttonY, createW, 20).build());

        int x = left + 16 + createW + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), button -> moveRegion(-1)).bounds(x, buttonY, 30, 20).build());

        x += 34;

        int navW = Math.max(110, usable - createW - 30 - 30 - 92 - gap * 4);

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(regionLabel()), button -> {
        }).bounds(x, buttonY, navW, 20).build());

        x += navW + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), button -> moveRegion(1)).bounds(x, buttonY, 30, 20).build());

        x += 34;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Delete region"), button -> deleteCurrentRegion()).bounds(x, buttonY, Math.max(84, left + panelW - 16 - x), 20).build());
    }


    private int addColorField(int y, DialogueDefinition.TextRegion region) {
        addLabel("Region color (blank = inherit)", left + 16, y - 11, 0xFFCFC6A6);

        int pickerW = 82;

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, panelW - 32 - pickerW - 4, 20, Component.literal("Color"));

        box.setMaxLength(64);

        box.setValue(region.color != null ? region.color : "");

        box.setResponder(value -> region.color = blankToNull(value));

        addScrollableWidget(box);

        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Color..."), button -> minecraft.setScreen(new DialogueEditorColorPickerScreen(this, region.color != null ? region.color : inheritedBaseColor(), color -> {
            region.color = color;

            rebuild();
        }))).bounds(left + panelW - 16 - pickerW, y, pickerW, 20).build());

        return y + 32;
    }


    private int addGradientField(int y, DialogueDefinition.TextRegion region) {
        addLabel("Region gradient: blank=inherit, none=disable, comma-separated colors", left + 16, y - 11, 0xFFCFC6A6);

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, panelW - 32, 20, Component.literal("Gradient"));

        box.setMaxLength(512);
        box.setValue(gradientText(region.gradient));
        box.setResponder(value -> region.gradient = parseGradient(value));

        addScrollableWidget(box);

        return y + 32;
    }


    private int addTextField(int y, String label, String value, int maxLength, java.util.function.Consumer<String> responder) {
        addLabel(label, left + 16, y - 11, 0xFFCFC6A6);

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, panelW - 32, 20, Component.literal(label));

        box.setMaxLength(maxLength);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addScrollableWidget(box);

        return y + 32;
    }


    private int addFullButton(int y, String text, Runnable action) {
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal(text), button -> action.run()).bounds(left + 16, y, panelW - 32, 20).build());

        return y + 28;
    }


    private int addInfoCard(int y, String title, String text, int accent, int titleColor) {
        List<String> wrapped = wrap(text, panelW - 58);

        int h = 28 + wrapped.size() * 11;

        cards.add(new Card(left + 16, y, panelW - 32, h, 0xD0171D24, accent));

        addLabel(title, left + 28, y + 8, titleColor);

        int ty = y + 24;

        for (String row : wrapped) {

            addLabel(row, left + 28, ty, 0xFFF0E8D0);

            ty += 11;
        }

        return y + h + 8;
    }


    private void addLabel(String text, int x, int y, int color) {
        labels.add(new Label(text, x, y, color));
    }


    private <T extends AbstractWidget> T addScrollableWidget(T widget) {
        scrollWidgets.add(widget);
        return addRenderableWidget(widget);
    }


    private void updateScrollVisibility() {
        for (AbstractWidget widget : scrollWidgets) {

            widget.visible = widget.getY() + widget.getHeight() >= settingsTop && widget.getY() <= settingsBottom;
        }
    }


    @Override
    public void tick() {
        previewTicks++;
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 10, left + panelW, height - 8, 0xF0121710);
        graphics.drawString(font, "RICH TEXT EDITOR", left + 16, 18, 0xFFB8FF72, false);
        graphics.drawString(font, trimToWidth(heading + (locale != null ? "  •  " + locale : "  •  literal"), panelW - 32), left + 16, 29, DialogueRetroTheme.TEXT_HINT, false);
        renderTextCanvas(graphics, mouseX, mouseY, partialTick);
        String selectionInfo = selectionLength() > 0 ? "Selection " + selectionStart() + ".." + selectionEnd() + ": \"" + printable(safeSubstring(text, selectionStart(), selectionEnd())) + "\"" : "Drag over text to select a word/phrase. Existing region ranges are underlined.";
        graphics.drawString(font, trimToWidth(selectionInfo, previewWidth), previewLeft, previewTop + previewHeight + 4, selectionLength() > 0 ? 0xFFFFD45A : DialogueRetroTheme.TEXT_HINT, false);
        graphics.enableScissor(left, settingsTop, left + panelW, settingsBottom);

        for (Card card : cards) {

            if (card.y + card.h < settingsTop || card.y > settingsBottom) {

                continue;
            }

            graphics.fill(card.x, card.y, card.x + card.w, card.y + card.h, card.background);
            graphics.fill(card.x, card.y, card.x + 4, card.y + card.h, card.accent);
        }

        for (Label label : labels) {

            if (label.y >= settingsTop && label.y <= settingsBottom - 9) {

                graphics.drawString(font, label.text, label.x, label.y, label.color, false);
            }
        }

        graphics.disableScissor();
        renderScrollbar(graphics);

        graphics.fill(left, settingsBottom + 1, left + panelW, height - 8, 0xF0090C08);
        graphics.fill(left, settingsBottom, left + panelW, settingsBottom + 1, 0xFF445438);

        super.render(graphics, mouseX, mouseY, partialTick);
    }


    private void renderTextCanvas(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, 0xFF0B1009);

        graphics.fill(previewLeft + 1, previewTop + 1, previewLeft + previewWidth - 1, previewTop + previewHeight - 1, 0xFF141C11);

        graphics.enableScissor(previewLeft + 1, previewTop + 1, previewLeft + previewWidth - 1, previewTop + previewHeight - 1);

        int selStart = selectionStart();
        int selEnd = selectionEnd();

        for (TextGlyph glyph : textGlyphs) {

            int gx = textOriginX + glyph.x;
            int gy = textOriginY + glyph.y;

            if (glyph.index >= selStart && glyph.index < selEnd) {

                graphics.fill(gx - 1, gy - 2, gx + Math.max(2, glyph.width) + 1, gy + font.lineHeight + 2, 0x704D84A8);
            }

            int regionIndex = topRegionAt(glyph.index);

            if (regionIndex >= 0) {
                int underlineColor = regionIndex == selectedRegion ? 0xFFFFD45A : 0xFF93E85D;
                graphics.fill(gx, gy + font.lineHeight + 1, gx + Math.max(1, glyph.width), gy + font.lineHeight + 2, underlineColor);
            }
        }

        String activeLocale = locale;

        for (TextGlyph glyph : textGlyphs) {

            DialogueRichTextUtil.ResolvedStyle rich = DialogueRichTextUtil.resolve(line, text, glyph.index, activeLocale);
            List<String> effects = rich.effects != null ? normalizeEffects(rich.effects) : baseEffects();

            DialogueDefinition.TextAnimation animation = rich.animation;

            float x = textOriginX + glyph.x;
            float y = textOriginY + glyph.y;

            float scale = 1.0F;

            float simulationAge = (previewTicks + partialTick + glyph.index * 1.5F) % 24.0F;
            for (String effect : effects) {

                if (effect == null) {
                    continue;
                }

                switch (effect.toLowerCase(Locale.ROOT)) {
                    case "wave" -> {
                        float amplitude = value(animation.wave_amplitude, 0.85F);

                        float speed = value(animation.wave_speed, 5.0F);
                        float frequency = value(animation.wave_frequency, 0.55F);

                        y += Mth.sin((previewTicks + partialTick) * 0.044F * speed + glyph.index * frequency) * amplitude;
                    }

                    case "shake" -> {
                        float strength = value(animation.shake_strength, 1.0F);
                        long seed = glyph.index * 734287L + previewTicks * 912271L;

                        x += hashOffset(seed) * strength;
                        y += hashOffset(seed + 19L) * strength;
                    }

                    case "explode" -> {
                        int duration = intValue(animation.explode_ticks, 6);

                        float amount = value(animation.explode_amount, 0.85F);
                        float progress = smooth(simulationAge / Math.max(1, duration));

                        scale *= 1.0F + (1.0F - progress) * amount;
                    }

                    case "slide", "linear" -> {

                        int duration = intValue(animation.slide_ticks, 6);

                        float distance = value(animation.slide_distance, 13.0F);
                        float progress = smooth(simulationAge / Math.max(1, duration));

                        x -= (1.0F - progress) * distance;
                    }
                }
            }

            int rgb = resolvedColor(glyph, rich);

            PoseStack pose = graphics.pose();

            pose.pushPose();

            pose.translate(x, y, 0);

            if (scale != 1.0F) {
                pose.translate(glyph.width * 0.5F, font.lineHeight * 0.5F, 0);
                pose.scale(scale, scale, 1);
                pose.translate(-glyph.width * 0.5F, -font.lineHeight * 0.5F, 0);
            }

            DialogueTextRenderUtil.GlyphStyle glyphStyle = effectiveGlyphStyle(glyph.index, rich);
            int outlineRgb = resolvedOutlineColor(glyph, rich);
            float outlineThickness = resolvedOutlineThickness(rich);

            DialogueTextRenderUtil.drawGlyph(graphics, font, glyph.character, 0xFF000000 | rgb, 0xFF000000 | outlineRgb, outlineThickness, glyphStyle, scale);

            pose.popPose();
        }

        graphics.disableScissor();
    }


    private int resolvedColor(TextGlyph glyph, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.gradient != null) {

            if (rich.gradient.size() >= 2) {
                int span = Math.max(1, rich.gradientEnd - rich.gradientStart - 1);

                float t = Mth.clamp((glyph.index - rich.gradientStart) / (float) span, 0.0F, 1.0F);
                return gradientColor(rich.gradient, t);
            }

        } else {
            List<String> gradient = line.text_gradient != null ? line.text_gradient : project.definition.text_gradient;

            if (gradient != null && gradient.size() >= 2) {

                return gradientColor(gradient, Mth.clamp(glyph.x / (float) Math.max(1, textMaxWidth), 0.0F, 1.0F));
            }
        }

        String color = rich.color != null ? rich.color : (line.text_color != null ? line.text_color : project.definition.text_color);

        if ("rainbow".equalsIgnoreCase(color)) {
            float hue = (glyph.index * 0.095F + previewTicks * 0.003F) % 1.0F;

            return Color.HSBtoRGB(hue, 0.76F, 1.0F) & 0xFFFFFF;
        }

        return DialogueEditorPreview.parseColor(color);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideTextCanvas(mouseX, mouseY)) {

            int boundary = boundaryAt(mouseX, mouseY);

            selectionAnchor = boundary;
            selectionCursor = boundary;
            selecting = true;

            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && selecting) {

            selectionCursor = boundaryAt(mouseX, mouseY);

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }


    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && selecting) {

            selecting = false;

            if (selectionLength() == 0) {
                int index = Math.max(0, Math.min(Math.max(0, text.length() - 1), selectionStart()));

                List<Integer> regions = DialogueRichTextUtil.regionIndicesAt(line, text, index, locale);

                if (!regions.isEmpty()) {
                    selectedRegion = regions.get(regions.size() - 1);
                }
            }
            rebuild();

            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelW && mouseY >= previewTop && mouseY <= settingsBottom) {

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


    private boolean insideTextCanvas(double mouseX, double mouseY) {
        return mouseX >= previewLeft && mouseX <= previewLeft + previewWidth && mouseY >= previewTop && mouseY <= previewTop + previewHeight;
    }


    private int boundaryAt(double mouseX, double mouseY) {
        if (text.isEmpty()) {
            return 0;
        }

        double localX = mouseX - textOriginX;
        double localY = mouseY - textOriginY;

        TextGlyph nearest = null;

        double best = Double.MAX_VALUE;

        for (TextGlyph glyph : textGlyphs) {

            double cx = glyph.x + glyph.width * 0.5D;
            double cy = glyph.y + font.lineHeight * 0.5D;
            double dx = localX - cx;
            double dy = localY - cy;
            double distance = dx * dx + dy * dy * 2.0D;

            if (distance < best) {
                best = distance;

                nearest = glyph;
            }
        }

        if (nearest == null) {
            return 0;
        }

        boolean after = localX > nearest.x + nearest.width * 0.5D;

        return Mth.clamp(nearest.index + (after ? 1 : 0), 0, text.length());
    }


    private void createRegionFromSelection() {
        if (selectionLength() <= 0) {
            return;
        }

        int start = selectionStart();

        int end = selectionEnd();

        DialogueDefinition.TextRegion region = new DialogueDefinition.TextRegion();

        region.start = start;
        region.end = end;
        region.match = safeSubstring(text, start, end);
        region.name = region.match.length() <= 24 ? printable(region.match) : "Region " + (line.rich_regions.size() + 1);
        region.locale = locale;

        line.rich_regions.add(region);

        selectedRegion = line.rich_regions.size() - 1;

        DialogueEditorHistory.checkpoint(project);

        rebuild();
    }


    private void deleteCurrentRegion() {
        if (selectedRegion < 0 || selectedRegion >= line.rich_regions.size()) {
            return;
        }

        line.rich_regions.remove(selectedRegion);

        if (line.rich_regions.isEmpty()) {
            selectedRegion = -1;

        } else {
            selectedRegion = Math.min(selectedRegion, line.rich_regions.size() - 1);
        }

        DialogueEditorHistory.checkpoint(project);

        rebuild();
    }


    private void moveRegion(int direction) {
        List<Integer> visible = visibleRegionIndices();

        if (visible.isEmpty()) {
            selectedRegion = -1;

            rebuild();
            return;
        }

        int current = visible.indexOf(selectedRegion);

        if (current < 0) {
            selectedRegion = visible.get(0);

        } else {
            selectedRegion = visible.get(Math.floorMod(current + direction, visible.size()));
        }

        rebuild();
    }


    private int firstRegionForLocale() {
        List<Integer> visible = visibleRegionIndices();

        return visible.isEmpty() ? -1 : visible.get(0);
    }


    private List<Integer> visibleRegionIndices() {
        List<Integer> result = new ArrayList<>();

        if (line.rich_regions == null) {
            return result;
        }

        for (int i = 0; i < line.rich_regions.size(); i++) {

            DialogueDefinition.TextRegion region = line.rich_regions.get(i);

            if (region != null && DialogueRichTextUtil.localeMatches(region.locale, locale)) {

                result.add(i);
            }
        }

        return result;
    }


    private DialogueDefinition.TextRegion currentRegion() {
        if (line.rich_regions == null || selectedRegion < 0 || selectedRegion >= line.rich_regions.size()) {

            return null;
        }

        DialogueDefinition.TextRegion region = line.rich_regions.get(selectedRegion);

        return DialogueRichTextUtil.localeMatches(region.locale, locale) ? region : null;
    }


    private int topRegionAt(int characterIndex) {
        List<Integer> values = DialogueRichTextUtil.regionIndicesAt(line, text, characterIndex, locale);

        return values.isEmpty() ? -1 : values.get(values.size() - 1);
    }


    private String regionLabel() {
        List<Integer> visible = visibleRegionIndices();

        if (visible.isEmpty()) {
            return "Regions: 0";
        }

        int local = visible.indexOf(selectedRegion);

        if (local < 0) {
            local = 0;
        }

        return "Region " + (local + 1) + " / " + visible.size();
    }


    private int selectionStart() {
        if (selectionAnchor < 0 || selectionCursor < 0) {
            return 0;
        }

        return Math.min(selectionAnchor, selectionCursor);
    }


    private int selectionEnd() {
        if (selectionAnchor < 0 || selectionCursor < 0) {
            return 0;
        }

        return Math.max(selectionAnchor, selectionCursor);
    }


    private int selectionLength() {
        return Math.max(0, selectionEnd() - selectionStart());
    }


    private int maxScroll() {
        return Math.max(0, contentHeight - (settingsBottom - settingsTop));
    }


    private void renderScrollbar(GuiGraphics graphics) {
        int max = maxScroll();

        if (max <= 0) {
            return;
        }

        int trackX = left + panelW - 7;

        int trackTop = settingsTop + 2;

        int trackBottom = settingsBottom - 2;

        int trackH = Math.max(1, trackBottom - trackTop);

        int viewportH = Math.max(1, settingsBottom - settingsTop);

        int thumbH = Math.max(18, Math.round(trackH * (viewportH / (float) contentHeight)));

        int travel = Math.max(1, trackH - thumbH);

        int thumbY = trackTop + Math.round(travel * (scrollOffset / (float) max));

        graphics.fill(trackX, trackTop, trackX + 2, trackBottom, 0x555B664C);

        graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB8FF72);
    }


    private void refreshParsedText() {
        markdownResult = DialogueMarkdown.parse(sourceText, markdownEnabled());
        text = markdownResult.text();
        DialogueRichTextUtil.repairRegions(line, text, locale);
    }

    private boolean markdownEnabled() {
        return line.markdown != null ? line.markdown : project.definition.markdown;
    }

    private DialogueTextRenderUtil.GlyphStyle effectiveGlyphStyle(int index, DialogueRichTextUtil.ResolvedStyle rich) {
        DialogueMarkdown.CharStyle md = markdownResult.styleAt(index);
        String fontId = rich.font != null ? rich.font : line.text_font != null ? line.text_font : project.definition.text_font;
        return new DialogueTextRenderUtil.GlyphStyle(fontId, rich.bold != null ? rich.bold : md.bold(), rich.italic != null ? rich.italic : md.italic(), rich.underline != null ? rich.underline : md.underline(), rich.strikethrough != null ? rich.strikethrough : md.strikethrough());
    }

    private float resolvedOutlineThickness(DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.outlineThickness != null) return Math.max(0.0F, rich.outlineThickness);
        if (line.text_outline_thickness != null) return Math.max(0.0F, line.text_outline_thickness);
        return Math.max(0.0F, project.definition.text_outline_thickness);
    }

    private int resolvedOutlineColor(TextGlyph glyph, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.outlineGradient != null && rich.outlineGradient.size() >= 2) {
            int span = Math.max(1, rich.outlineGradientEnd - rich.outlineGradientStart - 1);
            float t = Mth.clamp((glyph.index - rich.outlineGradientStart) / (float) span, 0.0F, 1.0F);
            return gradientColor(rich.outlineGradient, t);
        }

        List<String> gradient = line.text_outline_gradient != null ? line.text_outline_gradient : project.definition.text_outline_gradient;
        if (rich.outlineGradient == null && gradient != null && gradient.size() >= 2) {
            return gradientColor(gradient, Mth.clamp(glyph.x / (float) Math.max(1, textMaxWidth), 0.0F, 1.0F));
        }

        String color = rich.outlineColor != null ? rich.outlineColor : line.text_outline_color != null ? line.text_outline_color : project.definition.text_outline_color;
        if (color == null || color.isBlank()) color = "black";
        if ("rainbow".equalsIgnoreCase(color)) {
            float hue = (glyph.index * 0.095F + previewTicks * 0.003F) % 1.0F;
            return Color.HSBtoRGB(hue, 0.76F, 1.0F) & 0xFFFFFF;
        }
        return DialogueEditorPreview.parseColor(color);
    }

    private int addTwoButtons(int y, String leftText, Runnable leftAction, String rightText, Runnable rightAction) {
        int gap = 4;
        int w = (panelW - 32 - gap) / 2;
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal(leftText), b -> leftAction.run()).bounds(left + 16, y, w, 20).build());
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal(rightText), b -> rightAction.run()).bounds(left + 16 + w + gap, y, w, 20).build());
        return y + 28;
    }

    private String lineOutlineSummary() {
        if (line.text_outline_thickness == null) {
            float inherited = Math.max(0.0F, project.definition.text_outline_thickness);

            return "LINE outline: INHERIT  •  effective " + String.format(Locale.ROOT, "%.2f", inherited) + " px  •  Configure...";
        }

        if (line.text_outline_thickness <= 0.01F) {
            return "LINE outline: OFF  •  Configure...";
        }

        return "LINE outline: " + String.format(Locale.ROOT, "%.2f", line.text_outline_thickness) + " px  •  Configure...";
    }


    private String regionOutlineSummary(DialogueDefinition.TextRegion region) {
        if (region.outline_thickness == null) {
            float inherited = line.text_outline_thickness != null ? Math.max(0.0F, line.text_outline_thickness) : Math.max(0.0F, project.definition.text_outline_thickness);

            return "REGION outline: INHERIT  •  effective " + String.format(Locale.ROOT, "%.2f", inherited) + " px  •  Configure...";
        }

        if (region.outline_thickness <= 0.01F) {
            return "REGION outline: OFF  •  Configure...";
        }

        return "REGION outline: " + String.format(Locale.ROOT, "%.2f", region.outline_thickness) + " px  •  Configure...";
    }


    private int addLineOutlineFields(int y) {
        int pickerW = 82;
        addLabel("Line outline color (blank = inherit global)", left + 16, y - 11, 0xFFCFC6A6);
        EditBox color = new DialogueRetroEditBox(font, left + 16, y, panelW - 32 - pickerW - 4, 20, Component.literal("Outline color"));
        color.setMaxLength(64);
        color.setValue(line.text_outline_color != null ? line.text_outline_color : "");
        color.setResponder(value -> line.text_outline_color = blankToNull(value));
        addScrollableWidget(color);
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Color..."), b -> minecraft.setScreen(new DialogueEditorColorPickerScreen(this, line.text_outline_color != null ? line.text_outline_color : "black", picked -> {
            line.text_outline_color = picked;
            rebuild();
        }))).bounds(left + panelW - 16 - pickerW, y, pickerW, 20).build());
        y += 32;

        y = addTextField(y, "Line outline gradient: blank=inherit, none=disable", gradientText(line.text_outline_gradient), 512, value -> line.text_outline_gradient = parseGradient(value));
        y = addTextField(y, "Line outline thickness: blank=inherit, 0=off, 0..4", line.text_outline_thickness != null ? String.valueOf(line.text_outline_thickness) : "", 32, value -> line.text_outline_thickness = nullableFloat(value));
        return y;
    }

    private int addRegionOutlineFields(int y, DialogueDefinition.TextRegion region) {
        int pickerW = 82;
        addLabel("Region outline color (blank = inherit)", left + 16, y - 11, 0xFFCFC6A6);
        EditBox color = new DialogueRetroEditBox(font, left + 16, y, panelW - 32 - pickerW - 4, 20, Component.literal("Region outline color"));
        color.setMaxLength(64);
        color.setValue(region.outline_color != null ? region.outline_color : "");
        color.setResponder(value -> region.outline_color = blankToNull(value));
        addScrollableWidget(color);
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Color..."), b -> minecraft.setScreen(new DialogueEditorColorPickerScreen(this, region.outline_color != null ? region.outline_color : "black", picked -> {
            region.outline_color = picked;
            rebuild();
        }))).bounds(left + panelW - 16 - pickerW, y, pickerW, 20).build());
        y += 32;

        y = addTextField(y, "Region outline gradient: blank=inherit, none=disable", gradientText(region.outline_gradient), 512, value -> region.outline_gradient = parseGradient(value));
        y = addTextField(y, "Region outline thickness: blank=inherit, 0=off, 0..4", region.outline_thickness != null ? String.valueOf(region.outline_thickness) : "", 32, value -> region.outline_thickness = nullableFloat(value));
        return y;
    }

    private void openFontPicker(String current, java.util.function.Consumer<String> setter) {
        minecraft.setScreen(new DialogueEditorFontPickerScreen(this, project, current, true, setter));
    }

    private String lineFontSummary() {
        if (line.text_font == null || line.text_font.isBlank()) {

            return "INHERIT → " + displayFont(project.definition.text_font);
        }

        if (isVanillaFont(line.text_font)) {
            return "VANILLA (override)";
        }

        return displayFont(line.text_font) + " (override)";
    }

    private String regionFontSummary(DialogueDefinition.TextRegion region) {
        if (region.font == null || region.font.isBlank()) {

            String inherited = line.text_font != null && !line.text_font.isBlank() ? line.text_font : project.definition.text_font;

            return "INHERIT → " + displayFont(inherited);
        }

        if (isVanillaFont(region.font)) {
            return "VANILLA (override)";
        }

        return displayFont(region.font) + " (override)";
    }

    private static String displayFont(String value) {
        if (isVanillaFont(value)) {
            return "VANILLA";
        }

        return value.length() <= 28 ? value : "…" + value.substring(value.length() - 27);
    }

    private static boolean isVanillaFont(String value) {
        return value == null || value.isBlank() || "minecraft:default".equalsIgnoreCase(value) || "default".equalsIgnoreCase(value) || "vanilla".equalsIgnoreCase(value);
    }

    private static String nullableBoolean(Boolean value) {
        return value == null ? "INHERIT" : value ? "ON" : "OFF";
    }

    private static Boolean nextNullableBoolean(Boolean value) {
        if (value == null) return Boolean.TRUE;
        if (value) return Boolean.FALSE;
        return null;
    }

    private static Float nullableFloat(String value) {
        try {
            return value == null || value.isBlank() ? null : Mth.clamp(Float.parseFloat(value.trim()), 0.0F, 4.0F);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String inheritedBaseColor() {
        if (line.text_color != null && !line.text_color.isBlank()) {

            return line.text_color;
        }

        return project.definition.text_color != null ? project.definition.text_color : "white";
    }


    private List<String> baseEffects() {
        if (line.text_effects != null) {
            return normalizeEffects(line.text_effects);
        }

        if (project.definition.text_effects != null) {
            return normalizeEffects(project.definition.text_effects);
        }

        String legacy = line.text_effect != null ? line.text_effect : project.definition.text_effect;

        if (legacy == null || legacy.isBlank() || "normal".equalsIgnoreCase(legacy)) {

            return List.of();
        }

        return List.of(legacy.toLowerCase(Locale.ROOT));
    }


    private List<String> normalizeEffects(List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (String effect : effects) {

            if (effect == null || effect.isBlank() || "normal".equalsIgnoreCase(effect)) {

                continue;
            }

            result.add(effect.trim().toLowerCase(Locale.ROOT));
        }

        return List.copyOf(result);
    }


    private String effectsSummary(List<String> effects) {
        if (effects == null) {
            return "INHERIT";
        }

        if (effects.isEmpty()) {
            return "NONE";
        }

        return String.join(" + ", effects);
    }


    private String gradientText(List<String> gradient) {
        if (gradient == null) {
            return "";
        }

        if (gradient.isEmpty()) {
            return "none";
        }

        return String.join(", ", gradient);
    }


    private List<String> parseGradient(String value) {
        if (value == null || value.isBlank()) {

            return null;
        }

        if ("none".equalsIgnoreCase(value.trim())) {

            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        for (String part : value.split(",")) {

            String clean = part.trim();

            if (!clean.isEmpty()) {
                result.add(clean);
            }
        }

        return result;
    }


    private int gradientColor(List<String> colors, float t) {
        int sections = colors.size() - 1;

        float scaled = t * sections;

        int index = Mth.clamp((int) Math.floor(scaled), 0, sections - 1);

        float local = scaled - index;

        int a = DialogueEditorPreview.parseColor(colors.get(index));
        int b = DialogueEditorPreview.parseColor(colors.get(index + 1));
        int r = Math.round(Mth.lerp(local, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(local, (a >> 8) & 255, (b >> 8) & 255));
        int blue = Math.round(Mth.lerp(local, a & 255, b & 255));

        return (r << 16) | (g << 8) | blue;
    }


    private List<TextGlyph> layoutText(Font font, String text, int maxWidth, int lineHeight) {
        List<TextGlyph> result = new ArrayList<>();
        int x = 0;
        int y = 0;
        int i = 0;

        while (i < text.length()) {
            char character = text.charAt(i);

            if (character == '\n') {
                x = 0;
                y += lineHeight;
                i++;
                continue;
            }

            if (Character.isWhitespace(character)) {
                int glyphWidth = Math.max(2, styledWidth(i, character));
                if (x + glyphWidth > maxWidth) {
                    x = 0;
                    y += lineHeight;
                } else {
                    result.add(new TextGlyph(i, character, x, y, glyphWidth));
                    x += glyphWidth;
                }
                i++;
                continue;
            }

            int wordEnd = i;
            while (wordEnd < text.length()) {
                char next = text.charAt(wordEnd);
                if (Character.isWhitespace(next) || next == '\n') break;
                wordEnd++;
            }

            int wordWidth = 0;
            for (int index = i; index < wordEnd; index++) {
                wordWidth += Math.max(1, styledWidth(index, text.charAt(index)));
            }

            if (x > 0 && x + wordWidth > maxWidth) {
                x = 0;
                y += lineHeight;
            }

            for (int index = i; index < wordEnd; index++) {
                char letter = text.charAt(index);
                int glyphWidth = Math.max(1, styledWidth(index, letter));
                if (x > 0 && x + glyphWidth > maxWidth) {
                    x = 0;
                    y += lineHeight;
                }
                result.add(new TextGlyph(index, letter, x, y, glyphWidth));
                x += glyphWidth;
            }

            i = wordEnd;
        }

        return result;
    }

    private int styledWidth(int index, char character) {
        DialogueRichTextUtil.ResolvedStyle rich = DialogueRichTextUtil.resolve(line, text, index, locale);
        return DialogueTextRenderUtil.width(font, character, effectiveGlyphStyle(index, rich));
    }


    private List<String> wrap(String text, int pixelWidth) {
        List<String> result = new ArrayList<>();

        if (text == null || text.isBlank()) {

            result.add("");
            return result;
        }

        String[] words = text.split("\\s+");

        String current = "";

        for (String word : words) {

            String candidate = current.isEmpty() ? word : current + " " + word;

            if (font.width(candidate) <= pixelWidth) {

                current = candidate;

                continue;
            }

            if (!current.isEmpty()) {
                result.add(current);
            }

            if (font.width(word) > pixelWidth) {

                String remaining = word;

                while (!remaining.isEmpty()) {
                    String part = font.plainSubstrByWidth(remaining, pixelWidth);

                    if (part.isEmpty()) {
                        break;
                    }

                    result.add(part);

                    remaining = remaining.substring(part.length());
                }

                current = "";

                continue;
            }

            current = word;
        }

        if (!current.isEmpty()) {
            result.add(current);
        }

        return result;
    }


    private String trimToWidth(String value, int maxWidth) {
        if (value == null) {
            return "";
        }

        if (font.width(value) <= maxWidth) {
            return value;
        }

        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }


    private static String safeSubstring(String value, int start, int end) {
        if (value == null) {
            return "";
        }

        int safeStart = Math.max(0, Math.min(start, value.length()));

        int safeEnd = Math.max(safeStart, Math.min(end, value.length()));

        return value.substring(safeStart, safeEnd);
    }


    private static String printable(String value) {
        return value == null ? "" : value.replace('\n', ' ');
    }


    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private void rebuild() {
        minecraft.setScreen(this);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    private record TextGlyph(int index, char character, int x, int y, int width) {
    }


    private record Label(String text, int x, int y, int color) {
    }


    private record Card(int x, int y, int w, int h, int background, int accent) {
    }
}
