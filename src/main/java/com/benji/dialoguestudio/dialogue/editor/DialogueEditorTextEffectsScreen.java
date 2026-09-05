package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DialogueEditorTextEffectsScreen extends DialogueRetroScreen {

    private static final List<String> EFFECTS = List.of("wave", "shake", "explode", "linear");

    private final Screen parent;
    private final boolean allowInherit;
    private final Consumer<List<String>> callback;

    private List<String> selected;
    private boolean inherit;

    public DialogueEditorTextEffectsScreen(Screen parent, List<String> current, boolean allowInherit, Consumer<List<String>> callback) {
        super(Component.literal("Dialogue Studio - Text Effects"));

        this.parent = parent;
        this.allowInherit = allowInherit;
        this.callback = callback;

        this.inherit = allowInherit && current == null;

        this.selected = current != null ? normalize(current) : new ArrayList<>();
    }

    @Override
    protected void init() {
        int panelW = Math.min(360, width - 30);

        int left = (width - panelW) / 2;

        int top = Math.max(26, (height - 250) / 2);

        int y = top + 42;

        if (allowInherit) {
            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Mode: " + (inherit ? "INHERIT" : "CUSTOM")), button -> {
                inherit = !inherit;
                rebuild();
            }).bounds(left + 16, y, panelW - 32, 20).build());

            y += 28;
        }

        for (String effect : EFFECTS) {

            boolean enabled = selected.contains(effect);

            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal((enabled ? "[ON]  " : "[OFF] ") + effect.toUpperCase(Locale.ROOT)), button -> {
                inherit = false;

                if (enabled) {
                    selected.remove(effect);
                } else {
                    selected.add(effect);
                }

                selected = normalize(selected);

                rebuild();
            }).bounds(left + 16, y, panelW - 32, 20).build());

            y += 26;
        }

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Apply"), button -> {
            callback.accept(inherit ? null : List.copyOf(selected));

            minecraft.setScreen(parent);
        }).bounds(left + 16, top + 214, (panelW - 38) / 2, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), button -> minecraft.setScreen(parent)).bounds(left + 22 + (panelW - 38) / 2, top + 214, (panelW - 38) / 2, 20).build());
    }

    private void rebuild() {
        minecraft.setScreen(new DialogueEditorTextEffectsScreen(parent, inherit ? null : selected, allowInherit, callback));
    }

    private static List<String> normalize(List<String> values) {
        LinkedHashSet<String> set = new LinkedHashSet<>();

        for (String effect : EFFECTS) {

            if (values != null && values.stream().anyMatch(value -> value != null && effect.equalsIgnoreCase(value))) {
                set.add(effect);
            }
        }

        return new ArrayList<>(set);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelW = Math.min(360, width - 30);

        int left = (width - panelW) / 2;

        int top = Math.max(26, (height - 250) / 2);

        graphics.fill(left, top, left + panelW, top + 250, 0xF0141A11);
        graphics.fill(left + 1, top + 1, left + panelW - 1, top + 249, 0xFF0C110A);
        graphics.drawString(font, "COMBINED TEXT EFFECTS", left + 16, top + 12, 0xFFB8FF72, false);
        graphics.drawString(font, "Multiple effects are applied together.", left + 16, top + 25, DialogueRetroTheme.TEXT_HINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
