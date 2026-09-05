package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DialogueConditionListScreen extends DialogueRetroScreen {

    private static final List<String> TYPES = List.of("always", "player_tag", "source_tag", "score", "has_item", "dimension", "source_type", "mod_loaded", "quest_state");

    private static final List<String> OPERATORS = List.of(">=", ">", "==", "!=", "<=", "<");

    private final Screen parent;
    private final DialogueEditorProject project;
    private final List<DialogueDefinition.Condition> conditions;
    private final String heading;

    private int selected;
    private int scrollOffset;
    private int contentHeight;

    private int panelW;
    private int left;
    private int innerW;

    private int contentTop;
    private int contentBottom;

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    private final List<Label> labels = new ArrayList<>();

    private final List<Card> cards = new ArrayList<>();

    public DialogueConditionListScreen(Screen parent, DialogueEditorProject project, List<DialogueDefinition.Condition> conditions, String heading) {
        super(Component.literal("Dialogue Studio - Conditions"));

        this.parent = parent;
        this.project = project;
        this.conditions = conditions;
        this.heading = heading != null ? heading : "Conditions";

        if (this.conditions.isEmpty()) {
            this.conditions.add(new DialogueDefinition.Condition());
        }
    }

    @Override
    protected void init() {
        contentWidgets.clear();
        labels.clear();
        cards.clear();

        selected = Math.max(0, Math.min(selected, conditions.size() - 1));

        DialogueDefinition.Condition condition = conditions.get(selected);

        panelW = Math.min(620, width - 24);

        left = (width - panelW) / 2;

        innerW = panelW - 32;

        contentTop = 118;
        contentBottom = height - 46;

        buildFixedHeader(condition);

        int y = contentTop + 10 - scrollOffset;

        y = addInfoCard(y, "THIS RULE MEANS:", humanSentence(condition), 0xFFD8E36A, 0xFFFFD45A);

        y = addInfoCard(y, "HOW THIS RULE IS USED", typeHelp(normalizeType(condition.type)), 0xFF526B3D, 0xFFD2C8AA);

        if (conditions.size() > 1) {
            y = addInfoCard(y, "AND GROUP", "All " + conditions.size() + " rules in this list must be TRUE. If even one rule is FALSE, this branch/choice is unavailable.", 0xFF4B7840, 0xFFA8F06A);
        }

        y = buildFieldsForType(condition, y);

        contentHeight = Math.max(contentBottom - contentTop, y + scrollOffset - contentTop + 14);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));

        updateContentVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> {
            DialogueEditorHistory.checkpoint(project);

            minecraft.setScreen(parent);
        }).bounds(left + 16, height - 30, innerW, 20).build());
    }

    private void buildFixedHeader(DialogueDefinition.Condition condition) {
        int y = 54;

        buildNavigator(y);

        y += 34;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Rule type: " + friendlyType(condition.type)), button -> {
            condition.type = next(normalizeType(condition.type), TYPES);

            normalizeForType(condition);

            scrollOffset = 0;
            rebuild();
        }).bounds(left + 16, y, innerW / 2 - 4, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(condition.invert ? "NOT / Invert: ON" : "NOT / Invert: OFF"), button -> {
            condition.invert = !condition.invert;

            scrollOffset = 0;
            rebuild();
        }).bounds(left + 20 + innerW / 2, y, innerW / 2 - 4, 20).build());
    }

    private void buildNavigator(int y) {
        int x = left + 16;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), button -> {
            selected = Math.max(0, selected - 1);

            scrollOffset = 0;
            rebuild();
        }).bounds(x, y, 40, 20).build());

        x += 44;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Rule " + (selected + 1) + " / " + conditions.size()), button -> {
        }).bounds(x, y, 146, 20).build());

        x += 150;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), button -> {
            selected = Math.min(conditions.size() - 1, selected + 1);

            scrollOffset = 0;
            rebuild();
        }).bounds(x, y, 40, 20).build());

        x += 48;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("+ Add rule"), button -> {
            conditions.add(new DialogueDefinition.Condition());

            selected = conditions.size() - 1;

            scrollOffset = 0;
            rebuild();
        }).bounds(x, y, 94, 20).build());

        x += 98;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("- Remove"), button -> {
            if (conditions.size() > 1) {
                conditions.remove(selected);

                selected = Math.min(selected, conditions.size() - 1);
            } else {
                DialogueDefinition.Condition only = conditions.get(0);

                only.type = "always";
                only.id = null;
                only.objective = null;
                only.invert = false;
            }

            scrollOffset = 0;
            rebuild();
        }).bounds(x, y, 94, 20).build());
    }

    private int buildFieldsForType(DialogueDefinition.Condition condition, int y) {
        String type = normalizeType(condition.type);

        if ("always".equals(type)) {
            return addInfoCard(y, "NO INPUT REQUIRED", "Always true has no extra fields. This rule never blocks the path unless NOT / Invert is enabled.", 0xFF526B3D, 0xFFD2C8AA);
        }

        switch (type) {
            case "score" -> {
                y = addField(y, "Scoreboard objective", condition.objective, 128, value -> condition.objective = blankToNull(value));

                int rowY = y;

                addLabel("Comparison operator", left + 16, rowY - 11, 0xFFCFC6A6);

                addLabel("Score value", left + 20 + innerW / 2, rowY - 11, 0xFFCFC6A6);

                addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("Compare: " + nullToDefault(condition.operator, ">=")), button -> {
                    condition.operator = next(nullToDefault(condition.operator, ">="), OPERATORS);

                    rebuild();
                }).bounds(left + 16, rowY, innerW / 2 - 4, 20).build());

                EditBox value = new DialogueRetroEditBox(font, left + 20 + innerW / 2, rowY, innerW / 2 - 4, 20, Component.literal("Score value"));

                value.setValue(String.valueOf(condition.value));

                value.setResponder(text -> {
                    try {
                        condition.value = Integer.parseInt(text.trim());
                    } catch (Exception ignored) {
                    }
                });

                addContentWidget(value);

                y += 36;
            }

            case "has_item" -> {
                y = addField(y, "Item registry id or #item_tag", condition.id, 256, value -> condition.id = blankToNull(value));

                y = addIntegerField(y, "Minimum amount in player's inventory", condition.count, value -> condition.count = Math.max(1, value));
            }

            case "player_tag" ->
                    y = addField(y, "Player tag", condition.id, 256, value -> condition.id = blankToNull(value));

            case "source_tag" ->
                    y = addField(y, "Dialogue source entity tag", condition.id, 256, value -> condition.id = blankToNull(value));

            case "dimension" ->
                    y = addField(y, "Dimension id  (example: minecraft:overworld)", condition.id, 256, value -> condition.id = blankToNull(value));

            case "source_type" ->
                    y = addField(y, "Source entity id or #entity_type tag", condition.id, 256, value -> condition.id = blankToNull(value));

            case "mod_loaded" ->
                    y = addField(y, "Mod id  (example: netherman)", condition.id, 128, value -> condition.id = blankToNull(value));

            case "quest_state" -> {
                y = addField(y, "Quest id  (example: mydialogues:temple_quest)", condition.id, 256, value -> condition.id = blankToNull(value));

                int rowY = y;

                addLabel("Required quest state", left + 16, rowY - 11, 0xFFCFC6A6);

                addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("State: " + nullToDefault(condition.state, "active")), button -> {
                    condition.state = next(nullToDefault(condition.state, "active"), List.of("not_started", "active", "completed", "failed", "started"));

                    rebuild();
                }).bounds(left + 16, rowY, innerW, 20).build());

                y += 36;
            }

            default -> {
            }
        }

        return y;
    }

    private int addInfoCard(int y, String title, String text, int accent, int titleColor) {
        List<String> wrapped = wrap(text, innerW - 28);

        int h = 30 + wrapped.size() * 12;

        cards.add(new Card(left + 16, y, innerW, h, 0xD0171D24, accent));

        addLabel(title, left + 28, y + 8, titleColor);

        int ty = y + 24;

        for (String line : wrapped) {

            addLabel(line, left + 28, ty, 0xFFF0E8D0);

            ty += 12;
        }

        return y + h + 10;
    }

    private int addField(int y, String label, String value, int maxLength, Consumer<String> responder) {
        addLabel(label, left + 16, y - 11, 0xFFCFC6A6);

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, innerW, 20, Component.literal(label));

        box.setMaxLength(maxLength);
        box.setValue(value != null ? value : "");

        box.setResponder(responder);

        addContentWidget(box);

        return y + 36;
    }

    private int addIntegerField(int y, String label, int initial, java.util.function.IntConsumer responder) {
        return addField(y, label, String.valueOf(initial), 32, text -> {
            try {
                responder.accept(Integer.parseInt(text.trim()));
            } catch (Exception ignored) {
            }
        });
    }

    private void addLabel(String text, int x, int y, int color) {
        labels.add(new Label(text, x, y, color));
    }

    private <T extends AbstractWidget> T addContentWidget(T widget) {
        contentWidgets.add(widget);
        return addRenderableWidget(widget);
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

            int max = maxScroll();

            int old = scrollOffset;

            if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - 30);
            } else if (delta < 0) {
                scrollOffset = Math.min(max, scrollOffset + 30);
            }

            if (old != scrollOffset) {
                rebuild();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void normalizeForType(DialogueDefinition.Condition condition) {
        if (condition.operator == null || condition.operator.isBlank()) {

            condition.operator = ">=";
        }

        condition.count = Math.max(1, condition.count);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "always";
        }

        return type.toLowerCase(Locale.ROOT);
    }

    private void rebuild() {
        minecraft.setScreen(this);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 16, left + panelW, height - 8, 0xF0121710);

        graphics.drawString(font, heading, left + 16, 24, 0xFFB8FF72, false);
        graphics.enableScissor(left, contentTop, left + panelW, contentBottom);

        for (Card card : cards) {

            if (card.y + card.h < contentTop || card.y > contentBottom) {
                continue;
            }

            graphics.fill(card.x, card.y, card.x + card.w, card.y + card.h, card.background);

            graphics.fill(card.x, card.y, card.x + 4, card.y + card.h, card.accent);
        }

        for (Label label : labels) {

            if (label.y >= contentTop && label.y <= contentBottom - 9) {

                graphics.drawString(font, label.text, label.x, label.y, label.color, false);
            }
        }

        graphics.disableScissor();

        if (maxScroll() > 0) {
            graphics.drawString(font, "mouse wheel: scroll", left + panelW - 118, 24, DialogueRetroTheme.TEXT_HINT, false);

            renderScrollbar(graphics);
        }
        graphics.fill(left, contentBottom + 1, left + panelW, height - 8, 0xF0090C08);

        graphics.fill(left, contentBottom, left + panelW, contentBottom + 1, 0xFF445438);

        super.render(graphics, mouseX, mouseY, partialTick);
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

    private String humanSentence(DialogueDefinition.Condition condition) {
        if (condition == null) {
            return "ALWAYS TRUE";
        }

        String type = normalizeType(condition.type);

        String sentence = switch (type) {
            case "player_tag" -> "player has tag \"" + safe(condition.id) + "\"";

            case "source_tag" -> "dialogue source has tag \"" + safe(condition.id) + "\"";

            case "score" ->
                    "player score " + safe(condition.objective) + " " + nullToDefault(condition.operator, ">=") + " " + condition.value;

            case "has_item" -> "player has at least " + Math.max(1, condition.count) + " × " + safe(condition.id);

            case "dimension" -> "player is in " + safe(condition.id);

            case "source_type" -> "dialogue source entity is " + safe(condition.id);

            case "mod_loaded" -> "mod \"" + safe(condition.id) + "\" is loaded";

            case "quest_state" -> "quest " + safe(condition.id) + " is " + nullToDefault(condition.state, "active");

            default -> "always true";
        };

        return condition.invert ? "NOT ( " + sentence + " )" : sentence;
    }

    private String typeHelp(String type) {
        return switch (type) {
            case "player_tag" -> "PLAYER TAG: useful for story flags set with vanilla entity/scoreboard tags.";

            case "source_tag" -> "SOURCE TAG: checks the NPC/boss/entity that owns this dialogue session.";

            case "score" -> "SCORE: compares a vanilla scoreboard objective with the number you enter.";

            case "has_item" ->
                    "HAS ITEM: checks the player's full inventory. Registry ids and #item tags are supported.";

            case "dimension" -> "DIMENSION: true only in the specified dimension, e.g. minecraft:overworld.";

            case "source_type" -> "SOURCE TYPE: matches the dialogue source EntityType or #entity_type tag.";

            case "mod_loaded" -> "MOD LOADED: useful for compatibility branches when another mod is installed.";

            case "quest_state" ->
                    "QUEST STATE: checks Dialogue Engine's persistent quest lifecycle: not_started, active, completed, failed, or started.";

            default -> "ALWAYS: this rule never blocks the path. Use it when you want an unconditional option/branch.";
        };
    }

    private String friendlyType(String type) {
        return switch (normalizeType(type)) {
            case "player_tag" -> "Player has tag";

            case "source_tag" -> "Source has tag";

            case "score" -> "Score comparison";

            case "has_item" -> "Player has item";

            case "dimension" -> "Player dimension";

            case "source_type" -> "Source entity type";

            case "mod_loaded" -> "Mod is installed";

            case "quest_state" -> "Quest state";

            default -> "Always true";
        };
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

    private static String next(String current, List<String> values) {
        int index = values.indexOf(current);

        return values.get(index < 0 ? 0 : (index + 1) % values.size());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static String safe(String value) {
        return value != null && !value.isBlank() ? value : "?";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Label(String text, int x, int y, int color) {
    }

    private record Card(int x, int y, int w, int h, int background, int accent) {
    }
}
