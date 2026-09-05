package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DialogueNodeEditorScreen extends DialogueRetroScreen {

    private static final List<String> NODE_TYPES = List.of("line", "choice", "condition", "event", "end");

    private static final List<String> UNAVAILABLE_MODES = List.of("hide", "disable");

    private static final List<String> TEXT_EFFECTS = List.of("normal", "wave", "shake", "explode", "linear");
    private static final List<String> SPRITE_POSITIONS = List.of("left", "center", "right");
    private static final List<String> SPRITE_TRANSITIONS = List.of("none", "bounce", "sway", "fade_up");
    private static final List<String> VOICE_SOURCES = List.of("master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient", "voice");

    private final Screen parent;
    private final DialogueEditorProject project;
    private String nodeId;

    private int selectedChoice;
    private int scrollOffset;
    private int contentHeight;

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();

    private int panelW;
    private int left;
    private int innerW;
    private int contentTop;
    private int contentBottom;

    public DialogueNodeEditorScreen(Screen parent, DialogueEditorProject project, String nodeId) {
        super(Component.literal("Dialogue Studio - Node Editor"));
        this.parent = parent;
        this.project = project;
        this.nodeId = nodeId;
        this.project.normalize();
    }

    @Override
    protected void init() {
        project.normalize();
        contentWidgets.clear();
        labels.clear();
        cards.clear();

        DialogueDefinition.Node node = project.definition.nodes.get(nodeId);

        if (node == null) {
            minecraft.setScreen(parent);
            return;
        }

        panelW = Math.min(760, width - 24);
        left = (width - panelW) / 2;
        innerW = panelW - 32;

        contentTop = 126;
        contentBottom = height - 46;

        buildFixedHeader(node);

        String type = nodeType(node);
        int y = contentTop + 12 - scrollOffset;

        y = addSectionTitle(y, sectionTitle(type), typeColor(type));
        y += 4;

        switch (type) {
            case "choice" -> y = buildChoiceNode(node, y);
            case "condition" -> y = buildConditionNode(node, y);
            case "event" -> y = buildEventNode(node, y);
            case "end" -> y = buildEndNode(node, y);
            default -> y = buildLineNode(node, y);
        }

        contentHeight = Math.max(contentBottom - contentTop, y + scrollOffset - contentTop + 14);

        updateContentVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Done"), button -> saveAndBack()).bounds(left + 16, height - 30, innerW, 20).build());
    }

    private void buildFixedHeader(DialogueDefinition.Node node) {
        graphicsSafeNoop();

        EditBox idBox = new DialogueRetroEditBox(font, left + 16, 42, innerW - 92, 20, Component.literal("Node ID"));

        idBox.setMaxLength(96);
        idBox.setValue(nodeId);
        addRenderableWidget(idBox);

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Rename"), button -> {
            String requested = idBox.getValue();

            if (project.renameNode(nodeId, requested)) {
                nodeId = project.selected_node;
                rebuild();
            }
        }).bounds(left + 20 + innerW - 92, 42, 88, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Type: " + node.type), button -> {
            node.type = next(node.type, NODE_TYPES);
            normalizeNodeForType(node);
            selectedChoice = 0;
            scrollOffset = 0;
            rebuild();
        }).bounds(left + 16, 72, innerW / 2 - 4, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(nodeId.equals(project.definition.start_node) ? "START NODE ✓" : "Set as START"), button -> {
            project.definition.start_node = nodeId;
            project.definition.graph_enabled = true;
            rebuild();
        }).bounds(left + 20 + innerW / 2, 72, innerW / 2 - 4, 20).build());
    }

    private void graphicsSafeNoop() {
    }

    private int buildLineNode(DialogueDefinition.Node node, int y) {
        if (node.line == null) {
            node.line = new DialogueDefinition.Line();
            node.line.literal = "New node line";
        }

        DialogueDefinition.Line line = node.line;
        boolean literal = line.literal != null;

        y = addInfoCard(y, "SHOW TEXT → NEXT", "The player sees one normal dialogue line. After hold_ticks, the server follows NEXT.", 0xFF3F6438);

        y = addFullButton(y, "Text mode: " + (literal ? "LITERAL" : "LANG"), () -> {
            if (literal) {
                String old = nullToEmpty(line.literal);
                line.literal = null;
                project.ensureNodeLangKey(line, nodeId);
                project.setLocalizedNodeText(project.preview_locale, line, nodeId, old);
            } else {
                line.literal = project.getLocalizedNodeText(project.preview_locale, line, nodeId);
                line.text = null;
            }

            rebuild();
        });

        y = addTextField(y, literal ? "Dialogue text shown to the player" : "Translation text (" + project.preview_locale + ")", literal ? nullToEmpty(line.literal) : project.getLocalizedNodeText(project.preview_locale, line, nodeId), 2048, value -> {
            if (line.literal != null) {
                line.literal = value;
            } else {
                project.setLocalizedNodeText(project.preview_locale, line, nodeId, value);
            }
        });

        if (!literal) {
            y = addTextField(y, "Translation key", project.ensureNodeLangKey(line, nodeId), 256, value -> line.text = blankToNull(value));
        }

        y = addAssetField(y, "Character sprite", line.sprite, id -> line.sprite = blankToNull(id));
        y = addSectionTitle(y + 4, "LINE SETTINGS / OVERRIDES", 0xFFE9C46A);
        y = buildLineOverrides(line, y);

        y = addFullButton(y, "Combined text effects: " + effectsSummary(line), () -> minecraft.setScreen(new DialogueEditorTextEffectsScreen(this, line.text_effects, true, effects -> {
            line.text_effects = effects;
            rebuild();
        })));

        y = addFullButton(y, "Rich Text regions: " + richRegionCount(line) + "  •  visual editor", () -> {
            String resolved = line.literal != null ? nullToEmpty(line.literal) : project.getLocalizedNodeText(project.preview_locale, line, nodeId);

            String locale = line.literal != null ? null : project.preview_locale;

            minecraft.setScreen(new DialogueRichTextEditorScreen(this, project, line, resolved, locale, "Node " + nodeId + " • line"));
        });

        if (node.actions == null) node.actions = new ArrayList<>();

        y = addFullButton(y, "Actions when this line starts (" + node.actions.size() + ")", () -> minecraft.setScreen(new DialogueActionListScreen(this, project, node.actions, "LINE actions • " + nodeId)));

        y = addTextField(y, "NEXT → node id  (or connect the grey port in Node Graph)", nullToEmpty(node.next), 96, value -> node.next = blankToNull(value));

        return y;
    }

    private int buildChoiceNode(DialogueDefinition.Node node, int y) {
        if (node.line == null) {
            node.line = new DialogueDefinition.Line();
            node.line.literal = "What will you do?";
        }

        if (node.choices == null) node.choices = new ArrayList<>();

        if (node.choices.isEmpty()) {
            DialogueDefinition.Choice choice = new DialogueDefinition.Choice();
            choice.literal = "Choice 1";
            node.choices.add(choice);
        }

        selectedChoice = Math.max(0, Math.min(selectedChoice, node.choices.size() - 1));

        DialogueDefinition.Line prompt = node.line;
        boolean promptLiteral = prompt.literal != null;

        y = addInfoCard(y, "PLAYER CHOOSES", "The dialogue pauses here. The prompt is shown first, then the player selects one answer. Each answer goes to its own node.", 0xFF3D7438);

        y = addSectionTitle(y, "QUESTION / PROMPT", 0xFFA8F06A);

        y = addFullButton(y, "Prompt mode: " + (promptLiteral ? "LITERAL" : "LANG"), () -> {
            if (promptLiteral) {
                String old = nullToEmpty(prompt.literal);
                prompt.literal = null;
                project.ensureNodeLangKey(prompt, nodeId);
                project.setLocalizedNodeText(project.preview_locale, prompt, nodeId, old);
            } else {
                prompt.literal = project.getLocalizedNodeText(project.preview_locale, prompt, nodeId);
                prompt.text = null;
            }

            rebuild();
        });

        y = addTextField(y, "Prompt text shown above the answer list", prompt.literal != null ? prompt.literal : project.getLocalizedNodeText(project.preview_locale, prompt, nodeId), 2048, value -> {
            if (prompt.literal != null) {
                prompt.literal = value;
            } else {
                project.setLocalizedNodeText(project.preview_locale, prompt, nodeId, value);
            }
        });

        if (!promptLiteral) {
            y = addTextField(y, "Prompt translation key", project.ensureNodeLangKey(prompt, nodeId), 256, value -> prompt.text = blankToNull(value));
        }

        y = addAssetField(y, "Prompt character sprite", prompt.sprite, id -> prompt.sprite = blankToNull(id));

        y = addSectionTitle(y + 4, "PROMPT SETTINGS / OVERRIDES", 0xFFE9C46A);
        y = buildLineOverrides(prompt, y);

        y = addFullButton(y, "Prompt effects: " + effectsSummary(prompt), () -> minecraft.setScreen(new DialogueEditorTextEffectsScreen(this, prompt.text_effects, true, effects -> {
            prompt.text_effects = effects;
            rebuild();
        })));

        y = addFullButton(y, "Prompt Rich Text regions: " + richRegionCount(prompt) + "  •  visual editor", () -> {
            String resolved = prompt.literal != null ? nullToEmpty(prompt.literal) : project.getLocalizedNodeText(project.preview_locale, prompt, nodeId);

            String locale = prompt.literal != null ? null : project.preview_locale;

            minecraft.setScreen(new DialogueRichTextEditorScreen(this, project, prompt, resolved, locale, "Node " + nodeId + " • choice prompt"));
        });

        if (node.actions == null) node.actions = new ArrayList<>();

        y = addFullButton(y, "Actions before answers appear (" + node.actions.size() + ")", () -> minecraft.setScreen(new DialogueActionListScreen(this, project, node.actions, "CHOICE enter actions • " + nodeId)));

        y += 4;
        y = addSectionTitle(y, "ANSWERS", 0xFFA8F06A);

        y = addChoiceNavigator(node, y);

        DialogueDefinition.Choice choice = node.choices.get(selectedChoice);

        boolean literal = choice.literal != null;

        y = addChoicePreviewCard(node, choice, y);

        y = addFullButton(y, "Answer text mode: " + (literal ? "LITERAL" : "LANG"), () -> {
            if (literal) {
                String old = nullToEmpty(choice.literal);
                choice.literal = null;

                project.ensureChoiceLangKey(choice, nodeId, selectedChoice);

                project.setLocalizedChoiceText(project.preview_locale, choice, nodeId, selectedChoice, old);
            } else {
                choice.literal = project.getLocalizedChoiceText(project.preview_locale, choice, nodeId, selectedChoice);

                choice.text = null;
            }

            rebuild();
        });

        y = addTextField(y, "Answer button text", literal ? nullToEmpty(choice.literal) : project.getLocalizedChoiceText(project.preview_locale, choice, nodeId, selectedChoice), 1024, value -> {
            if (choice.literal != null) {
                choice.literal = value;
            } else {
                project.setLocalizedChoiceText(project.preview_locale, choice, nodeId, selectedChoice, value);
            }
        });

        y = addTextField(y, "GOES TO → node id  (or connect this choice port in Node Graph)", nullToEmpty(choice.goto_node), 96, value -> choice.goto_node = blankToNull(value));

        int conditions = choice.conditions != null ? choice.conditions.size() : 0;

        int rowY = y;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("If unavailable: " + nullToDefault(choice.when_unavailable, "hide")), b -> {
            choice.when_unavailable = next(nullToDefault(choice.when_unavailable, "hide"), UNAVAILABLE_MODES);
            rebuild();
        }).bounds(left + 16, rowY, innerW / 2 - 4, 20).build());

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("Conditions (" + conditions + ")"), b -> {
            if (choice.conditions == null) {
                choice.conditions = new ArrayList<>();
            }

            minecraft.setScreen(new DialogueConditionListScreen(this, project, choice.conditions, "Choice availability"));
        }).bounds(left + 20 + innerW / 2, rowY, innerW / 2 - 4, 20).build());

        labels.add(new Label("hide = remove the answer; disable = show it greyed out", left + 16, rowY + 23, DialogueRetroTheme.TEXT_HINT));

        y += 42;

        if (choice.actions == null) {
            choice.actions = new ArrayList<>();
        }

        y = addFullButton(y, "Actions when THIS answer is chosen (" + choice.actions.size() + ")", () -> minecraft.setScreen(new DialogueActionListScreen(this, project, choice.actions, "Answer " + (selectedChoice + 1) + " actions • " + nodeId)));

        y = addInfoCard(y, "ORDER OF EXECUTION", "The server checks this answer's Conditions again, runs all Actions in order, then follows GOES TO.", 0xFF4A6F3F);

        return y;
    }

    private int addChoiceNavigator(DialogueDefinition.Node node, int y) {
        int x = left + 16;
        int gap = 4;
        int h = 20;

        int prevW = 38;
        int labelW = 150;
        int nextW = 38;
        int addW = 74;
        int removeW = 74;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), b -> {
            selectedChoice = Math.max(0, selectedChoice - 1);
            rebuild();
        }).bounds(x, y, prevW, h).build());

        x += prevW + gap;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("Answer " + (selectedChoice + 1) + " / " + node.choices.size()), b -> {
        }).bounds(x, y, labelW, h).build());

        x += labelW + gap;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), b -> {
            selectedChoice = Math.min(node.choices.size() - 1, selectedChoice + 1);
            rebuild();
        }).bounds(x, y, nextW, h).build());

        x += nextW + gap;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("+ Add"), b -> {
            DialogueDefinition.Choice choice = new DialogueDefinition.Choice();

            choice.literal = "New choice";

            node.choices.add(choice);
            selectedChoice = node.choices.size() - 1;
            rebuild();
        }).bounds(x, y, addW, h).build());

        x += addW + gap;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("- Remove"), b -> {
            if (node.choices.size() > 1) {
                node.choices.remove(selectedChoice);

                selectedChoice = Math.min(selectedChoice, node.choices.size() - 1);

                rebuild();
            }
        }).bounds(x, y, removeW, h).build());

        return y + 32;
    }

    private int addChoicePreviewCard(DialogueDefinition.Node node, DialogueDefinition.Choice choice, int y) {
        String answer = choiceDisplayText(choice, selectedChoice);

        String target = choice.goto_node != null && !choice.goto_node.isBlank() ? choice.goto_node : "<not connected>";

        int conditionCount = choice.conditions != null ? choice.conditions.size() : 0;

        labels.add(new Label("PLAYER SEES:  [" + (selectedChoice + 1) + "] " + answer, left + 24, y + 8, 0xFFECE5C9));
        labels.add(new Label("when clicked  →  " + target, left + 24, y + 22, 0xFFFFFFFF));
        labels.add(new Label(conditionCount == 0 ? "availability: always" : "availability: " + conditionCount + " condition" + (conditionCount == 1 ? "" : "s"), left + 24, y + 36, conditionCount == 0 ? 0xFFBDB497 : 0xFFFFD45A));

        cards.add(new Card(left + 16, y, innerW, 50, 0xC0152516, 0xFF3D7438));

        return y + 54;
    }

    private int buildConditionNode(DialogueDefinition.Node node, int y) {
        if (node.conditions == null) node.conditions = new ArrayList<>();

        if (node.conditions.isEmpty()) {
            node.conditions.add(new DialogueDefinition.Condition());
        }

        y = addInfoCard(y, "AUTOMATIC IF / ELSE", "This node is invisible in-game. The server checks the rules immediately: TRUE follows the green output, FALSE follows the red output.", 0xFF778E43);
        y = addConditionPreviewCard(node, y);

        if (node.actions == null) node.actions = new ArrayList<>();

        y = addFullButton(y, "Actions BEFORE the IF check (" + node.actions.size() + ")", () -> minecraft.setScreen(new DialogueActionListScreen(this, project, node.actions, "CONDITION pre-check actions • " + nodeId)));
        y = addFullButton(y, "Edit IF conditions (" + node.conditions.size() + ")", () -> minecraft.setScreen(new DialogueConditionListScreen(this, project, node.conditions, "Condition branch")));
        y = addTextField(y, "TRUE ✓ → node id  (green port in Node Graph)", nullToEmpty(node.next), 96, value -> node.next = blankToNull(value));
        y = addTextField(y, "FALSE ✕ → node id  (red port in Node Graph)", nullToEmpty(node.else_node), 96, value -> node.else_node = blankToNull(value));

        return y;
    }

    private int addConditionPreviewCard(DialogueDefinition.Node node, int y) {
        labels.add(new Label("IF  " + conditionsSummary(node.conditions), left + 24, y + 8, 0xFFD8E36A));
        labels.add(new Label("TRUE  →  " + destination(node.next), left + 24, y + 24, 0xFF86D955));
        labels.add(new Label("FALSE →  " + destination(node.else_node), left + 24, y + 40, 0xFFFF777D));

        cards.add(new Card(left + 16, y, innerW, 56, 0xC01B2114, 0xFF778E43));

        return y + 60;
    }

    private int buildEventNode(DialogueDefinition.Node node, int y) {
        if (node.actions == null) node.actions = new ArrayList<>();

        y = addInfoCard(y, "DO ACTIONS → THEN", "This node is invisible. It runs its Actions from top to bottom on the server, then immediately follows THEN. Use it for rewards, quest state, scores, sounds, particles, commands, teleports or Java events.", 0xFF566B3E);
        y = addFullButton(y, "Edit actions (" + node.actions.size() + ")", () -> minecraft.setScreen(new DialogueActionListScreen(this, project, node.actions, "ACTION node • " + nodeId)));
        y = addInfoCard(y, "CURRENT ACTIONS", actionsSummary(node.actions), 0xFF49563B);
        y = addTextField(y, "THEN → node id  (grey output in Node Graph)", nullToEmpty(node.next), 96, value -> node.next = blankToNull(value));

        return y;
    }


    private int buildEndNode(DialogueDefinition.Node node, int y) {
        if (node.actions == null) node.actions = new ArrayList<>();

        y = addInfoCard(y, "END DIALOGUE", "No connection is needed. Reaching this node runs any final Actions, closes the dialogue session, finishes the fade and releases/unfreezes the source.", 0xFF7A3438);
        y = addFullButton(y, "Final actions before END (" + node.actions.size() + ")", () -> minecraft.setScreen(new DialogueActionListScreen(this, project, node.actions, "END actions • " + nodeId)));

        return y;
    }

    private int addInfoCard(int y, String title, String text, int color) {
        List<String> wrapped = wrap(text, innerW - 32);
        int height = Math.max(52, 34 + wrapped.size() * 12);

        cards.add(new Card(left + 16, y, innerW, height, 0xD01B2517, color));

        labels.add(new Label(title, left + 24, y + 8, 0xFFFFFFFF));

        int textY = y + 24;

        for (String line : wrapped) {
            labels.add(new Label(line, left + 24, textY, 0xFFE8E0C3));
            textY += 12;
        }

        return y + height + 8;
    }

    private int addSectionTitle(int y, String text, int color) {
        labels.add(new Label(text, left + 16, y, color));

        return y + 20;
    }

    private int addFullButton(int y, String text, Runnable action) {
        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal(text), b -> action.run()).bounds(left + 16, y, innerW, 20).build());

        return y + 32;
    }

    private int addTextField(int y, String label, String value, int maxLength, Consumer<String> responder) {
        labels.add(new Label(label, left + 16, y, 0xFFCFC6A6));

        y += 12;

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, innerW, 20, Component.literal(label));

        box.setMaxLength(maxLength);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);

        addContentWidget(box);

        return y + 32;
    }

    private int buildLineOverrides(DialogueDefinition.Line line, int y) {
        y = addTextField(y, "Text color override (blank = inherit)", nullToEmpty(line.text_color), 64, value -> line.text_color = blankToNull(value));
        y = addTextField(y, "Gradient override: blank=inherit, none=disable", gradientOverride(line.text_gradient), 256, value -> line.text_gradient = parseGradient(value, true));
        y = addNullableCycleButton(y, "Legacy text effect override", line.text_effect, TEXT_EFFECTS, value -> line.text_effect = value);

        y = addAssetField(y, "Voice override (blank = global)", line.voice, ".ogg", true, id -> line.voice = blankToNull(id));
        y = addTextField(y, "Voice pitch override (blank = inherit)", nullable(line.voice_pitch), 32, value -> line.voice_pitch = nf(value));
        y = addTextField(y, "Voice volume override (blank = inherit)", nullable(line.voice_volume), 32, value -> line.voice_volume = nf(value));
        y = addTextField(y, "Voice every override (blank = inherit)", nullable(line.voice_every), 32, value -> line.voice_every = ni(value));
        y = addNullableCycleButton(y, "Voice source override", line.voice_source, VOICE_SOURCES, value -> line.voice_source = value);

        y = addTextField(y, "Char ticks override (blank = inherit)", nullable(line.char_ticks), 32, value -> line.char_ticks = ni(value));
        y = addTextField(y, "Hold ticks override (blank = inherit)", nullable(line.hold_ticks), 32, value -> line.hold_ticks = ni(value));

        y = addAssetField(y, "Frame override", line.frame, id -> line.frame = blankToNull(id));
        y = addAssetField(y, "Background override", line.background, id -> line.background = blankToNull(id));

        y = addNullableCycleButton(y, "Sprite position override", line.sprite_position, SPRITE_POSITIONS, value -> line.sprite_position = value);
        y = addTextField(y, "Exact sprite X (blank = position preset)", nullable(line.sprite_x), 32, value -> line.sprite_x = nf(value));
        y = addNullableCycleButton(y, "Sprite transition override", line.sprite_transition, SPRITE_TRANSITIONS, value -> line.sprite_transition = value);
        y = addTextField(y, "Sprite move ticks (blank = inherit)", nullable(line.sprite_move_ticks), 32, value -> line.sprite_move_ticks = ni(value));
        y = addTextField(y, "Sprite transition ticks (blank = inherit)", nullable(line.sprite_transition_ticks), 32, value -> line.sprite_transition_ticks = ni(value));
        y = addTextField(y, "Sprite width (blank = inherit)", nullable(line.sprite_width), 32, value -> line.sprite_width = ni(value));
        y = addTextField(y, "Sprite height (blank = inherit)", nullable(line.sprite_height), 32, value -> line.sprite_height = ni(value));

        return y;
    }

    private int addNullableCycleButton(int y, String label, String current, List<String> values, Consumer<String> setter) {
        String shown = current == null || current.isBlank() ? "INHERIT" : current;

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal(label + ": " + shown), b -> {
            if (current == null || current.isBlank()) {
                setter.accept(values.get(0));
            } else {
                int index = values.indexOf(current);

                if (index < 0 || index >= values.size() - 1) {
                    setter.accept(null);
                } else {
                    setter.accept(values.get(index + 1));
                }
            }

            rebuild();
        }).bounds(left + 16, y, innerW, 20).build());

        return y + 32;
    }

    private int addAssetField(int y, String label, String value, Consumer<String> setter) {
        return addAssetField(y, label, value, ".png", false, setter);
    }

    private int addAssetField(int y, String label, String value, String extension, boolean sound, Consumer<String> setter) {
        labels.add(new Label(label, left + 16, y, 0xFFCFC6A6));

        y += 12;

        EditBox box = new DialogueRetroEditBox(font, left + 16, y, innerW - 88, 20, Component.literal(label));

        box.setMaxLength(512);
        box.setValue(value != null ? value : "");
        box.setResponder(setter);

        addContentWidget(box);

        addContentWidget(DialogueRetroButton.retroBuilder(Component.literal("Browse"), b -> minecraft.setScreen(new DialogueEditorFilePickerScreen(this, minecraft.gameDirectory.toPath(), extension, path -> importAsset(path, sound, setter)))).bounds(left + innerW - 68, y, 84, 20).build());

        return y + 32;
    }

    private void importAsset(Path path, boolean sound, Consumer<String> setter) {
        try {
            String id = sound ? DialogueEditorWorkspace.importSound(project, path) : DialogueEditorWorkspace.importTexture(project, path);

            setter.accept(id);
            DialogueEditorWorkspace.save(project);

        } catch (Exception ignored) {
        }

        rebuild();
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
        if (mouseX < left || mouseX > left + panelW || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        int max = Math.max(0, contentHeight - (contentBottom - contentTop));

        int old = scrollOffset;
        int step = 28;

        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - step);
        } else if (delta < 0) {
            scrollOffset = Math.min(max, scrollOffset + step);
        }

        if (old != scrollOffset) {
            rebuild();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void saveAndBack() {
        project.selected_node = nodeId;

        DialogueEditorHistory.checkpoint(project);

        try {
            DialogueEditorWorkspace.save(project);
        } catch (Exception ignored) {
        }

        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 16, left + panelW, height - 8, 0xF0121710);
        graphics.fill(left, 104, left + panelW, 105, 0xFF445438);
        graphics.fill(left, contentBottom, left + panelW, contentBottom + 1, 0xFF445438);

        graphics.drawString(font, "NODE EDITOR  •  " + nodeId, left + 16, 24, 0xFFB8FF72, false);

        DialogueDefinition.Node node = project.definition.nodes.get(nodeId);

        if (node != null) {
            String type = nodeType(node);

            graphics.drawString(font, shortHelp(type), left + 16, 104 + 8, typeColor(type), false);
        }

        graphics.enableScissor(left, contentTop, left + panelW, contentBottom);

        for (Card card : cards) {
            int top = card.y;
            int bottom = card.y + card.h;

            if (bottom < contentTop || top > contentBottom) continue;

            graphics.fill(card.x, top, card.x + card.w, bottom, card.background);
            graphics.fill(card.x, top, card.x + 4, bottom, card.accent);
        }

        for (Label label : labels) {
            if (label.y < contentTop || label.y > contentBottom - 8) {
                continue;
            }

            graphics.drawString(font, trim(label.text, innerW - 16), label.x, label.y, label.color, false);
        }

        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, partialTick);

        if (contentHeight > contentBottom - contentTop) {
            String hint = "wheel: scroll  " + Math.round(100.0D * scrollOffset / Math.max(1, contentHeight - (contentBottom - contentTop))) + "%";

            graphics.drawString(font, hint, left + panelW - font.width(hint) - 18, height - 42, DialogueRetroTheme.TEXT_HINT, false);
        }
    }

    private String sectionTitle(String type) {
        return switch (type) {
            case "choice" -> "CHOICE NODE";
            case "condition" -> "CONDITION NODE";
            case "event" -> "EVENT NODE";
            case "end" -> "END NODE";
            default -> "LINE NODE";
        };
    }

    private String shortHelp(String type) {
        return switch (type) {
            case "choice" -> "Question → player answer → that answer's destination";
            case "condition" -> "IF rules are true → green; otherwise → red";
            case "event" -> "Fire named event → continue";
            case "end" -> "Finish dialogue";
            default -> "Show text → continue to NEXT";
        };
    }

    private int typeColor(String type) {
        return switch (type) {
            case "choice" -> 0xFFA8F06A;
            case "condition" -> 0xFFD8E36A;
            case "event" -> 0xFFD8E36A;
            case "end" -> 0xFFFF9B9B;
            default -> 0xFFCFE6AE;
        };
    }

    private String nodeType(DialogueDefinition.Node node) {
        return node != null && node.type != null ? node.type.toLowerCase(Locale.ROOT) : "line";
    }

    private String choiceDisplayText(DialogueDefinition.Choice choice, int index) {
        if (choice == null) return "<empty>";

        if (choice.literal != null && !choice.literal.isBlank()) {
            return choice.literal.replace('\n', ' ');
        }

        if (choice.text != null && !choice.text.isBlank()) {
            String localized = project.getLocalizedChoiceText(project.preview_locale, choice, nodeId, index);

            return localized.isBlank() ? choice.text : localized;
        }

        return "<empty>";
    }

    private String conditionsSummary(List<DialogueDefinition.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "always";
        }

        List<String> parts = new ArrayList<>();

        for (DialogueDefinition.Condition condition : conditions) {
            if (condition == null) continue;

            String type = condition.type != null ? condition.type.toLowerCase(Locale.ROOT) : "always";

            String part = switch (type) {
                case "player_tag" -> "player has tag " + safe(condition.id);

                case "source_tag" -> "source has tag " + safe(condition.id);

                case "score" -> safe(condition.objective) + " " + safe(condition.operator) + " " + condition.value;

                case "has_item" -> "player has " + Math.max(1, condition.count) + "x " + safe(condition.id);

                case "dimension" -> "dimension is " + safe(condition.id);

                case "source_type" -> "source is " + safe(condition.id);

                case "mod_loaded" -> "mod " + safe(condition.id) + " is loaded";

                default -> "always";
            };

            if (condition.invert) {
                part = "NOT (" + part + ")";
            }

            parts.add(part);
        }

        return parts.isEmpty() ? "always" : String.join(" AND ", parts);
    }

    private String destination(String value) {
        return value != null && !value.isBlank() ? value : "<not connected>";
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "?";
    }

    private void normalizeNodeForType(DialogueDefinition.Node node) {
        String type = nodeType(node);

        if (("line".equals(type) || "choice".equals(type)) && node.line == null) {

            node.line = new DialogueDefinition.Line();

            node.line.literal = "choice".equals(type) ? "What will you do?" : "New node line";
        }

        if (node.choices == null) {
            node.choices = new ArrayList<>();
        }

        if ("choice".equals(type) && node.choices.isEmpty()) {

            DialogueDefinition.Choice choice = new DialogueDefinition.Choice();

            choice.literal = "Choice 1";
            node.choices.add(choice);
        }

        if (node.conditions == null) {
            node.conditions = new ArrayList<>();
        }

        if ("condition".equals(type) && node.conditions.isEmpty()) {

            node.conditions.add(new DialogueDefinition.Condition());
        }

        if (node.actions == null) {
            node.actions = new ArrayList<>();
        }
    }

    private static String actionsSummary(List<DialogueDefinition.Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return "No actions yet.";
        }

        StringBuilder result = new StringBuilder();

        int shown = Math.min(4, actions.size());

        for (int i = 0; i < shown; i++) {
            DialogueDefinition.Action action = actions.get(i);

            if (i > 0) {
                result.append("  →  ");
            }

            String type = action != null && action.type != null ? action.type : "?";

            if ("external".equalsIgnoreCase(type)) {
                type = "fire_external";
            }

            result.append(i + 1).append(". ").append(type);
        }

        if (actions.size() > shown) {
            result.append("  +").append(actions.size() - shown).append(" more");
        }

        return result.toString();
    }


    private static String firstExternalEvent(List<DialogueDefinition.Action> actions) {
        if (actions == null) return "";

        for (DialogueDefinition.Action action : actions) {
            if (action != null && "external".equalsIgnoreCase(action.type)) {
                return nullToEmpty(action.event);
            }
        }

        return "";
    }

    private static void setExternalEvent(List<DialogueDefinition.Action> actions, String event) {
        if (actions == null) return;

        DialogueDefinition.Action found = null;

        for (DialogueDefinition.Action action : actions) {
            if (action != null && "external".equalsIgnoreCase(action.type)) {
                found = action;
                break;
            }
        }

        if (event == null) {
            if (found != null) actions.remove(found);
            return;
        }

        if (found == null) {
            found = new DialogueDefinition.Action();
            found.type = "external";
            actions.add(found);
        }

        found.event = event;
    }

    private static int richRegionCount(DialogueDefinition.Line line) {
        return line != null && line.rich_regions != null ? line.rich_regions.size() : 0;
    }


    private static String effectsSummary(DialogueDefinition.Line line) {
        if (line.text_effects == null) {
            return "INHERIT";
        }

        if (line.text_effects.isEmpty()) {
            return "NONE";
        }

        return String.join(" + ", line.text_effects);
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

            if (font.width(word) <= pixelWidth) {
                current = word;
            } else {
                String rest = word;

                while (!rest.isEmpty()) {
                    String part = font.plainSubstrByWidth(rest, Math.max(8, pixelWidth));

                    if (part.isEmpty()) break;

                    result.add(part);
                    rest = rest.substring(Math.min(part.length(), rest.length()));
                }

                current = "";
            }
        }

        if (!current.isEmpty()) {
            result.add(current);
        }

        return result;
    }

    private String trim(String value, int pixelWidth) {
        if (value == null) return "";

        if (font.width(value) <= pixelWidth) {
            return value;
        }

        return font.plainSubstrByWidth(value, Math.max(0, pixelWidth - font.width("..."))) + "...";
    }

    private static String next(String current, List<String> values) {
        int index = values.indexOf(current);

        return values.get(index < 0 ? 0 : (index + 1) % values.size());
    }

    private static List<String> parseGradient(String value, boolean allowDisable) {
        if (value == null || value.isBlank()) return null;
        if (allowDisable && value.trim().equalsIgnoreCase("none")) return new ArrayList<>();

        List<String> result = new ArrayList<>();

        for (String part : value.split(",")) {
            if (!part.isBlank()) result.add(part.trim());
        }

        return result.isEmpty() ? null : result;
    }

    private static String gradientOverride(List<String> value) {
        return value == null ? "" : value.isEmpty() ? "none" : String.join(", ", value);
    }

    private static String nullable(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static Integer ni(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Float nf(String value) {
        try {
            return value == null || value.isBlank() ? null : Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String nullToDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private void rebuild() {
        minecraft.setScreen(this);
    }

    @Override
    public void onClose() {
        saveAndBack();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Card(int x, int y, int w, int h, int background, int accent) {
    }

    private record Label(String text, int x, int y, int color) {
    }
}
