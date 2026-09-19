package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.text.DialogueTextEffects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DialogueEditorTextEffectsScreen extends DialogueRetroScreen {

    private static final List<String> EFFECTS = DialogueTextEffects.ALL;

    private final Screen parent;
    private final boolean allowInherit;
    private final boolean appearanceOnly;
    private final Consumer<List<String>> callback;

    private List<String> selected;
    private boolean inherit;

    public DialogueEditorTextEffectsScreen(Screen parent, List<String> current, boolean allowInherit, Consumer<List<String>> callback) {
        this(parent, current, allowInherit, false, callback);
    }

    public DialogueEditorTextEffectsScreen(Screen parent, List<String> current, boolean allowInherit, boolean appearanceOnly, Consumer<List<String>> callback) {
        super(Component.literal("Dialogue Studio - Text Effects"));

        this.parent = parent;
        this.allowInherit = allowInherit;
        this.appearanceOnly = appearanceOnly;
        this.callback = callback;

        this.inherit = allowInherit && current == null;

        this.selected = current != null ? normalize(current) : new ArrayList<>();
    }

    @Override
    protected void init() {
        int panelW = Math.min(400, width - 20);

        int left = (width - panelW) / 2;

        int panelH = Math.min(232, height - 16);
        int top = (height - panelH) / 2;

        int y = top + 42;

        if (allowInherit) {
            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Mode: " + (inherit ? "INHERIT" : "CUSTOM")), button -> {
                inherit = !inherit;
                rebuild();
            }).bounds(left + 16, y, panelW - 32, 20).build());

            y += 28;
        }

        int gridY = y;
        int index = 0;
        for (String effect : appearanceOnly ? DialogueTextEffects.APPEARANCE : EFFECTS) {

            boolean enabled = selected.contains(effect);

            int column = index % 2;
            int row = index / 2;
            int buttonWidth = (panelW - 38) / 2;
            var effectButton = addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal((enabled ? "[ON] " : "[OFF] ") + effect.replace('_', ' ').toUpperCase(Locale.ROOT)), button -> {
                inherit = false;

                if (enabled) {
                    selected.remove(effect);
                } else {
                    selected.add(effect);
                }

                selected = normalize(selected);

                rebuild();
            }).bounds(left + 16 + column * (buttonWidth + 6), gridY + row * 24, buttonWidth, 20).build());
            String hint = switch (effect) {
                case "glow" -> "Soft glow using the selected font and current letter colour.";
                case "color_flow" -> "Cycles through the gradient palette. Set at least two comma-separated gradient colours on the same global/line/region style.";
                default -> "Combine with other effects; appearance effects do not replace this animation.";
            };
            effectButton.setTooltip(Tooltip.create(Component.literal(hint)));
            index++;
        }

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Apply"), button -> {
            callback.accept(inherit ? null : List.copyOf(selected));

            minecraft.setScreen(parent);
        }).bounds(left + 16, top + panelH - 30, (panelW - 38) / 2, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), button -> minecraft.setScreen(parent)).bounds(left + 22 + (panelW - 38) / 2, top + panelH - 30, (panelW - 38) / 2, 20).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private List<String> normalize(List<String> values) {
        LinkedHashSet<String> set = new LinkedHashSet<>();

        for (String effect : appearanceOnly ? DialogueTextEffects.APPEARANCE : EFFECTS) {

            if (values != null && values.stream().anyMatch(value -> value != null && effect.equalsIgnoreCase(value))) {
                set.add(effect);
            }
        }

        return new ArrayList<>(set);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelW = Math.min(400, width - 20);

        int left = (width - panelW) / 2;

        int panelH = Math.min(232, height - 16);
        int top = (height - panelH) / 2;

        graphics.fill(left, top, left + panelW, top + panelH, 0xF0141A11);
        graphics.fill(left + 1, top + 1, left + panelW - 1, top + panelH - 1, 0xFF0C110A);
        graphics.drawString(font, appearanceOnly ? "NPC NAME APPEARANCE" : "COMBINED TEXT EFFECTS", left + 16, top + 12, 0xFFB8FF72, false);
        graphics.drawString(font, "Multiple effects are applied together.", left + 16, top + 25, DialogueRetroTheme.TEXT_HINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
