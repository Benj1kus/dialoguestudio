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

public final class DialogueActionListScreen extends DialogueRetroScreen {

    private static final List<String> TARGETS = List.of("player", "source");

    private static final List<String> SOUND_SOURCES = List.of("master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient", "voice");

    private final Screen parent;
    private final DialogueEditorProject project;
    private final List<DialogueDefinition.Action> actions;
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

    public DialogueActionListScreen(Screen parent, DialogueEditorProject project, List<DialogueDefinition.Action> actions, String heading) {
        super(Component.literal("Dialogue Studio - Actions"));

        this.parent = parent;
        this.project = project;
        this.actions = actions;
        this.heading = heading != null ? heading : "Actions";
    }

    @Override
    protected void init() {
        contentWidgets.clear();
        labels.clear();
        cards.clear();

        panelW = Math.min(720, width - 24);

        left = (width - panelW) / 2;

        innerW = panelW - 32;

        contentTop = 108;
        contentBottom = height - 46;

        buildFixedHeader();

        if (actions.isEmpty()) {
            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("+ Add first action"), button -> {
                DialogueDefinition.Action action = new DialogueDefinition.Action();

                action.type = "give_item";
                action.id = "minecraft:diamond";
                actions.add(action);
                selected = 0;
                rebuild();
            }).bounds(left + 16, contentTop + 44, innerW, 20).build());

            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> saveAndBack()).bounds(left + 16, height - 30, innerW, 20).build());

            return;
        }

        selected = Math.max(0, Math.min(selected, actions.size() - 1));

        DialogueDefinition.Action action = actions.get(selected);

        normalize(action);

        int y = contentTop + 12 - scrollOffset;

        y = addInfoCard(y, "THIS ACTION WILL:", humanSentence(action), actionColor(action.type));

        y = addFullButton(y, "Action type: " + friendlyType(action.type), () -> minecraft.setScreen(new DialogueActionTypePickerScreen(this, type -> {
            action.type = type;
            normalize(action);
            scrollOffset = 0;
        })));

        y += 4;

        y = buildFields(action, y);

        contentHeight = Math.max(contentBottom - contentTop, y + scrollOffset - contentTop + 14);

        updateContentVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> saveAndBack()).bounds(left + 16, height - 30, innerW, 20).build());
    }

    private void buildFixedHeader() {
        int y = 18;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), button -> {
            if (!actions.isEmpty()) {
                selected = Math.max(0, selected - 1);
                scrollOffset = 0;
                rebuild();
            }
        }).bounds(left + 16, y + 28, 40, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(actions.isEmpty() ? "No actions" : "Action " + (selected + 1) + " / " + actions.size()), button -> {
        }).bounds(left + 60, y + 28, 150, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), button -> {
            if (!actions.isEmpty()) {
                selected = Math.min(actions.size() - 1, selected + 1);
                scrollOffset = 0;
                rebuild();
            }
        }).bounds(left + 214, y + 28, 40, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("+ Add"), button -> {
            DialogueDefinition.Action action = new DialogueDefinition.Action();

            action.type = "give_item";
            action.id = "minecraft:diamond";

            actions.add(action);
            selected = actions.size() - 1;
            scrollOffset = 0;
            rebuild();
        }).bounds(left + 262, y + 28, 82, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("- Remove"), button -> {
            if (!actions.isEmpty()) {
                actions.remove(selected);

                selected = Math.max(0, Math.min(selected, actions.size() - 1));

                scrollOffset = 0;
                rebuild();
            }
        }).bounds(left + 348, y + 28, 92, 20).build());
    }

    private int buildFields(DialogueDefinition.Action action, int y) {
        String type = normalizeType(action.type);

        switch (type) {
            case "give_item" -> {
                y = addItemField(y, "Item to give", action.id, value -> action.id = blankToNull(value));
                y = addIntegerField(y, "Amount", action.count, value -> action.count = Math.max(1, value));
            }

            case "take_item" -> {
                y = addItemField(y, "Item id or #item_tag to remove", action.id, value -> action.id = blankToNull(value));
                y = addIntegerField(y, "Amount to remove", action.count, value -> action.count = Math.max(1, value));
                y = addInfoCard(y, "TIP", "take_item supports #item tags. give_item intentionally requires one concrete item id.", 0xFF4C6B3F);
            }

            case "add_player_tag", "remove_player_tag" -> {

                y = addTargetButton(y, action);
                y = addTextField(y, "Vanilla entity tag", action.id, 256, value -> action.id = blankToNull(value));
            }

            case "set_score", "add_score" -> {

                y = addTargetButton(y, action);

                y = addTextField(y, "Scoreboard objective", action.objective, 64, value -> action.objective = blankToNull(value));
                y = addIntegerField(y, "Value  (negative values work with Add score)", action.value, value -> action.value = value);
                y = addInfoCard(y, "QUEST PROGRESS", "If the objective does not exist, Dialogue Engine creates it as a dummy objective. Scores are good for stages, counters and progress.", 0xFF6E7447);
            }

            case "run_command" -> {
                y = addTargetButton(y, action);
                y = addTextField(y, "Server command  (leading / is optional)", action.command, 2048, value -> action.command = blankToNull(value));
                y = addInfoCard(y, "PLACEHOLDERS", "{player}, {player_uuid}, {source_uuid}, {source_name}, {dialogue}, {node}. Command runs server-side with trusted datapack permission at the selected target position.", 0xFF694D32);
            }

            case "play_sound" -> {
                y = addTextField(y, "Sound id  (resource-pack-only ids also work)", action.id, 256, value -> action.id = blankToNull(value));
                y = addCycleButton(y, "Sound category", action.sound_source, SOUND_SOURCES, value -> action.sound_source = value);
                y = addTwoFloatFields(y, "Volume", action.volume, value -> action.volume = value, "Pitch", action.sound_pitch, value -> action.sound_pitch = value);
            }

            case "particle" -> {
                y = addTargetButton(y, action);
                y = addTextField(y, "Particle id", action.id, 256, value -> action.id = blankToNull(value));
                y = addIntegerField(y, "Particle count", action.count, value -> action.count = Math.max(1, value));
                y = addTripleDoubleFields(y, "Offset X", action.x, value -> action.x = value, "Y", action.y, value -> action.y = value, "Z", action.z, value -> action.z = value);
                y = addTripleDoubleFields(y, "Spread X", action.spread_x, value -> action.spread_x = value, "Y", action.spread_y, value -> action.spread_y = value, "Z", action.spread_z, value -> action.spread_z = value);
                y = addDoubleField(y, "Particle speed", action.speed, value -> action.speed = Math.max(0.0D, value));
            }

            case "teleport" -> {
                y = addTargetButton(y, action);

                y = addFullButton(y, "Coordinates: " + (action.relative ? "RELATIVE TO TARGET" : "ABSOLUTE"), () -> {
                    action.relative = !action.relative;
                    rebuild();
                });

                y = addTripleDoubleFields(y, "X", action.x, value -> action.x = value, "Y", action.y, value -> action.y = value, "Z", action.z, value -> action.z = value);
                y = addTextField(y, "Dimension  (blank = current; cross-dimension supported for player)", action.dimension, 256, value -> action.dimension = blankToNull(value));
                y = addTwoNullableFloatFields(y, "Yaw (blank = keep)", action.yaw, value -> action.yaw = value, "Pitch (blank = keep)", action.teleport_pitch, value -> action.teleport_pitch = value);
            }

            case "fire_external" -> {
                y = addTextField(y, "External event id", action.event != null ? action.event : action.id, 256, value -> {
                    action.event = blankToNull(value);
                    action.id = null;
                });

                y = addInfoCard(y, "JAVA / MOD INTEGRATION", "Posts DialogueNodeExternalEvent. Another mod may listen to it. If nobody listens, the graph simply continues.", 0xFF536246);
            }

            case "kill" -> {
                y = addTargetButton(y, action);
                y = addInfoCard(y, "DANGER", "PLAYER kills the dialogue player. SOURCE kills the NPC/boss/entity that started the dialogue. Use explicit Conditions if this must be restricted.", 0xFF772E34);
            }

            case "quest_start", "quest_complete", "quest_fail", "quest_reset" -> {

                y = addTextField(y, "Quest id", action.id, 256, value -> action.id = blankToNull(value));
                y = addInfoCard(y, "QUEST LIFECYCLE", questHelp(type), 0xFF745D2F);
            }

            default -> {
                y = addTextField(y, "Custom action id", action.type, 256, value -> action.type = blankToNull(value));
                y = addInfoCard(y, "CUSTOM ACTION", "Namespaced custom actions can be handled by another mod through DialogueActionRegistry.", 0xFF536246);
            }
        }

        return y;
    }

    private int addInfoCard(int y, String title, String text, int color) {
        List<String> wrapped = wrap(text, innerW - 36);

        int h = 28 + wrapped.size() * 12;

        cards.add(new Card(left + 16, y, innerW, h, 0xD0171D24, color));

        labels.add(new Label(title, left + 28, y + 8, 0xFFFFFFFF));

        int ty = y + 23;

        for (String line : wrapped) {
            labels.add(new Label(line, left + 28, ty, 0xFFF0E8D0));
            ty += 12;
        }

        return y + h + 10;
    }

    private int addTargetButton(int y, DialogueDefinition.Action action) {
        String target = "source".equalsIgnoreCase(action.target) ? "source" : "player";

        return addFullButton(y, "Target: " + target.toUpperCase(Locale.ROOT), () -> {
            action.target = next(target, TARGETS);
            rebuild();
        });
    }

    private int addItemField(int y, String label, String value, Consumer<String> responder) {
        labels.add(new Label(label, left + 16, y - 10, 0xFFE8E0C3));

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, innerW - 92, 20, Component.literal(label));

        box.setMaxLength(256);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);

        addContentWidget(box);

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("Registry"), button -> minecraft.setScreen(new DialogueEditorRegistryPickerScreen(this, DialogueEditorRegistryPickerScreen.Kind.ITEM, box.getValue(), id -> {
            responder.accept(id);
            rebuild();
        }))).bounds(left + 20 + innerW - 92, y, 88, 20).build());

        return y + 36;
    }

    private int addFullButton(int y, String text, Runnable action) {
        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal(text), button -> action.run()).bounds(left + 16, y, innerW, 20).build());

        return y + 32;
    }

    private int addCycleButton(int y, String label, String current, List<String> values, Consumer<String> responder) {
        String now = current != null && values.contains(current) ? current : values.get(0);

        return addFullButton(y, label + ": " + now, () -> {
            responder.accept(next(now, values));
            rebuild();
        });
    }

    private int addTextField(int y, String label, String value, int maxLength, Consumer<String> responder) {
        labels.add(new Label(label, left + 16, y - 10, 0xFFE8E0C3));

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, innerW, 20, Component.literal(label));

        box.setMaxLength(maxLength);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);

        addContentWidget(box);

        return y + 36;
    }

    private int addIntegerField(int y, String label, int initial, java.util.function.IntConsumer responder) {
        return addTextField(y, label, String.valueOf(initial), 32, text -> {
            try {
                responder.accept(Integer.parseInt(text.trim()));
            } catch (Exception ignored) {
            }
        });
    }

    private int addDoubleField(int y, String label, double initial, java.util.function.DoubleConsumer responder) {
        return addTextField(y, label, String.valueOf(initial), 48, text -> {
            try {
                responder.accept(Double.parseDouble(text.trim()));
            } catch (Exception ignored) {
            }
        });
    }

    private int addTwoFloatFields(int y, String labelA, float valueA, Consumer<Float> responderA, String labelB, float valueB, Consumer<Float> responderB) {
        int w = (innerW - 8) / 2;

        labels.add(new Label(labelA, left + 16, y - 10, 0xFFE8E0C3));

        labels.add(new Label(labelB, left + 24 + w, y - 10, 0xFFE8E0C3));

        EditBox a = new DialogueRetroEditBox(font, left + 16, y, w, 20, Component.literal(labelA));

        a.setValue(String.valueOf(valueA));
        a.setResponder(text -> {
            try {
                responderA.accept(Float.parseFloat(text.trim()));
            } catch (Exception ignored) {
            }
        });

        EditBox b = new DialogueRetroEditBox(font, left + 24 + w, y, w, 20, Component.literal(labelB));

        b.setValue(String.valueOf(valueB));
        b.setResponder(text -> {
            try {
                responderB.accept(Float.parseFloat(text.trim()));
            } catch (Exception ignored) {
            }
        });

        addContentWidget(a);
        addContentWidget(b);

        return y + 36;
    }

    private int addTwoNullableFloatFields(int y, String labelA, Float valueA, Consumer<Float> responderA, String labelB, Float valueB, Consumer<Float> responderB) {
        int w = (innerW - 8) / 2;

        labels.add(new Label(labelA, left + 16, y - 10, 0xFFE8E0C3));
        labels.add(new Label(labelB, left + 24 + w, y - 10, 0xFFE8E0C3));

        EditBox a = new DialogueRetroEditBox(font, left + 16, y, w, 20, Component.literal(labelA));

        a.setValue(valueA != null ? String.valueOf(valueA) : "");
        a.setResponder(text -> responderA.accept(nullableFloat(text)));

        EditBox b = new DialogueRetroEditBox(font, left + 24 + w, y, w, 20, Component.literal(labelB));

        b.setValue(valueB != null ? String.valueOf(valueB) : "");
        b.setResponder(text -> responderB.accept(nullableFloat(text)));

        addContentWidget(a);
        addContentWidget(b);

        return y + 36;
    }

    private int addTripleDoubleFields(int y, String labelA, double valueA, java.util.function.DoubleConsumer responderA, String labelB, double valueB, java.util.function.DoubleConsumer responderB, String labelC, double valueC, java.util.function.DoubleConsumer responderC) {
        int gap = 6;
        int w = (innerW - gap * 2) / 3;

        String[] labelsArray = {labelA, labelB, labelC};

        double[] values = {valueA, valueB, valueC};

        java.util.function.DoubleConsumer[] responders = new java.util.function.DoubleConsumer[]{responderA, responderB, responderC};

        for (int i = 0; i < 3; i++) {
            int x = left + 16 + i * (w + gap);

            labels.add(new Label(labelsArray[i], x, y - 10, 0xFFE8E0C3));

            EditBox box = new DialogueRetroEditBox(font, x, y, w, 20, Component.literal(labelsArray[i]));

            box.setValue(String.valueOf(values[i]));

            int index = i;

            box.setResponder(text -> {
                try {
                    responders[index].accept(Double.parseDouble(text.trim()));
                } catch (Exception ignored) {
                }
            });

            addContentWidget(box);
        }

        return y + 36;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelW && mouseY >= contentTop && mouseY <= contentBottom) {

            int max = Math.max(0, contentHeight - (contentBottom - contentTop));

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

    private void saveAndBack() {
        DialogueEditorHistory.checkpoint(project);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 10, left + panelW, height - 8, 0xF0121710);

        graphics.drawString(font, heading, left + 16, 18, 0xFFB8FF72, false);

        if (actions.isEmpty()) {
            graphics.drawString(font, "No actions yet. Add one to change the world, quest state, inventory or story flow.", left + 16, contentTop + 12, 0xFFE8E0C3, false);
        }

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

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String humanSentence(DialogueDefinition.Action action) {
        String type = normalizeType(action.type);

        String target = "source".equalsIgnoreCase(action.target) ? "dialogue source" : "player";

        return switch (type) {
            case "give_item" -> "Give " + Math.max(1, action.count) + " × " + safe(action.id) + " to the player.";

            case "take_item" ->
                    "Remove " + Math.max(1, action.count) + " × " + safe(action.id) + " from the player's inventory.";

            case "add_player_tag" -> "Add tag \"" + safe(action.id) + "\" to " + target + ".";

            case "remove_player_tag" -> "Remove tag \"" + safe(action.id) + "\" from " + target + ".";

            case "set_score" -> "Set " + target + " score " + safe(action.objective) + " = " + action.value + ".";

            case "add_score" -> "Add " + action.value + " to " + target + " score " + safe(action.objective) + ".";

            case "run_command" -> "Run server command: " + safe(action.command);

            case "play_sound" -> "Play " + safe(action.id) + " for the player.";

            case "particle" ->
                    "Spawn " + Math.max(1, action.count) + " × " + safe(action.id) + " around " + target + ".";

            case "teleport" ->
                    "Teleport " + target + (action.relative ? " by offset " : " to ") + format(action.x) + ", " + format(action.y) + ", " + format(action.z) + ".";

            case "kill" -> "Kill " + target + ".";

            case "fire_external" ->
                    "Fire external event " + safe(action.event != null ? action.event : action.id) + ".";

            case "quest_start" -> "Start quest " + safe(action.id) + " → ACTIVE.";

            case "quest_complete" -> "Complete quest " + safe(action.id) + " → COMPLETED.";

            case "quest_fail" -> "Fail quest " + safe(action.id) + " → FAILED.";

            case "quest_reset" -> "Reset quest " + safe(action.id) + " → NOT STARTED.";

            default -> "Run custom action " + safe(action.type) + ".";
        };
    }

    private String friendlyType(String type) {
        return switch (normalizeType(type)) {
            case "give_item" -> "Give item";
            case "take_item" -> "Take item";
            case "add_player_tag" -> "Add tag";
            case "remove_player_tag" -> "Remove tag";
            case "set_score" -> "Set score";
            case "add_score" -> "Add score";
            case "run_command" -> "Run command";
            case "play_sound" -> "Play sound";
            case "particle" -> "Particle";
            case "teleport" -> "Teleport";
            case "kill" -> "Kill entity/player";
            case "quest_start" -> "Start quest";
            case "quest_complete" -> "Complete quest";
            case "quest_fail" -> "Fail quest";
            case "quest_reset" -> "Reset quest";
            case "fire_external" -> "Fire external event";
            default -> type != null ? type : "Custom";
        };
    }

    private int actionColor(String type) {
        return switch (normalizeType(type)) {
            case "give_item", "take_item" -> 0xFF668F48;
            case "quest_start", "quest_complete", "quest_fail", "quest_reset" -> 0xFFB88A37;
            case "kill" -> 0xFFB14E55;
            case "fire_external" -> 0xFF5D8D49;
            case "play_sound", "particle" -> 0xFF566B3E;
            default -> 0xFF6F8C56;
        };
    }

    private String questHelp(String type) {
        return switch (type) {
            case "quest_start" ->
                    "This is the semantic equivalent of ACCEPT QUEST. It persists on the player as ACTIVE. Use quest_state conditions later to branch on it.";
            case "quest_complete" ->
                    "Marks the quest COMPLETED. Numeric objective progress can stay in scoreboard values.";
            case "quest_fail" -> "Marks the quest FAILED. You may restart it later with Start quest.";
            case "quest_reset" -> "Removes the stored lifecycle state. quest_state=not_started becomes true again.";
            default -> "";
        };
    }

    private void normalize(DialogueDefinition.Action action) {
        if (action.type == null || action.type.isBlank()) {
            action.type = "give_item";
        }

        if ("external".equalsIgnoreCase(action.type)) {
            action.type = "fire_external";
        }

        if (action.target == null || action.target.isBlank()) {
            action.target = "player";
        }

        action.count = Math.max(1, action.count);

        if (action.sound_source == null || action.sound_source.isBlank()) {
            action.sound_source = "master";
        }

        if (action.sound_pitch <= 0.0F) {
            action.sound_pitch = 1.0F;
        }
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return "give_item";
        }

        String value = type.trim().toLowerCase(Locale.ROOT);

        return "external".equals(value) ? "fire_external" : value;
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

    private static Float nullableFloat(String value) {
        try {
            return value == null || value.isBlank() ? null : Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safe(String value) {
        return value != null && !value.isBlank() ? value : "?";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void rebuild() {
        minecraft.setScreen(this);
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
