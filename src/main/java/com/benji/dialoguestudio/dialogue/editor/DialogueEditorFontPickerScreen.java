package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class DialogueEditorFontPickerScreen extends DialogueRetroScreen {

    private final Screen parent;
    private final DialogueEditorProject project;
    private final String current;
    private final boolean allowInherit;
    private final Consumer<String> callback;

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    private int left;
    private int panelW;
    private int contentTop;
    private int contentBottom;
    private int contentHeight;
    private int scrollOffset;
    private String errorMessage;

    public DialogueEditorFontPickerScreen(Screen parent, DialogueEditorProject project, String current, Consumer<String> callback) {
        this(parent, project, current, false, callback);
    }
    public DialogueEditorFontPickerScreen(Screen parent, DialogueEditorProject project, String current, boolean allowInherit, Consumer<String> callback) {
        super(Component.literal("Dialogue Studio - Font"));

        this.parent = parent;
        this.project = project;
        this.current = current;
        this.allowInherit = allowInherit;
        this.callback = callback;
    }

    @Override
    protected void init() {
        contentWidgets.clear();

        project.normalize();

        DialogueEditorFontPreviewPack.ensureLoaded(project);

        panelW = Math.min(620, width - 20);

        left = (width - panelW) / 2;

        contentTop = 84;

        contentBottom = height - 42;

        int innerW = panelW - 32;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Import TTF..."), b -> minecraft.setScreen(new DialogueEditorFilePickerScreen(this, minecraft.gameDirectory.toPath(), ".ttf", this::importTtf))).bounds(left + 16, 48, innerW / 2 - 4, 20).build());
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Import MSDF JSON + PNG..."), b -> minecraft.setScreen(new DialogueEditorFilePickerScreen(this, minecraft.gameDirectory.toPath(), ".json", this::importMsdf))).bounds(left + 20 + innerW / 2, 48, innerW / 2 - 4, 20).build());

        List<FontRow> rows = rows();

        int rowH = 34;

        contentHeight = Math.max(contentBottom - contentTop, rows.size() * rowH + 6);

        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));

        int y = contentTop + 4 - scrollOffset;

        for (FontRow row : rows) {

            String selected = sameFont(current, row.id) ? "✓ " : "";
            Button button = DialogueRetroButton.retroBuilder(Component.literal(selected + row.title), b -> callback.accept(row.id)).bounds(left + 16, y, innerW, 20).build();

            contentWidgets.add(button);
            addRenderableWidget(button);

            y += rowH;
        }

        updateVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(left + 16, height - 30, innerW, 20).build());
    }

    private List<FontRow> rows() {
        List<FontRow> result = new ArrayList<>();

        if (allowInherit) {
            result.add(new FontRow(null, "INHERIT parent font", "no override"));
            result.add(new FontRow("minecraft:default", "Minecraft default (VANILLA override)", "force vanilla font here"));

        } else {
            result.add(new FontRow(null, "Minecraft default", "vanilla"));
        }

        for (Map.Entry<String, DialogueEditorProject.FontAsset> entry : project.fonts.entrySet()) {

            DialogueEditorProject.FontAsset asset = entry.getValue();

            String id = project.namespace + ":" + entry.getKey();
            String kind = asset != null && "bitmap_msdf".equals(asset.type) ? "MSDF atlas" : "TTF • HQ 8x";

            result.add(new FontRow(id, id, kind));
        }

        return result;
    }

    private void importTtf(Path path) {
        try {
            String id = DialogueEditorFontImporter.importTtf(project, path);

            DialogueEditorFontPreviewPack.refresh(project);

            callback.accept(id);

        } catch (Exception e) {
            errorMessage = "Font import failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());

            minecraft.setScreen(this);
        }
    }

    private void importMsdf(Path path) {
        try {
            String id = DialogueEditorFontImporter.importMsdf(project, path);

            DialogueEditorFontPreviewPack.refresh(project);

            callback.accept(id);

        } catch (Exception e) {
            errorMessage = "MSDF import failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());

            minecraft.setScreen(this);
        }
    }

    private void updateVisibility() {
        for (AbstractWidget widget : contentWidgets) {

            widget.visible = widget.getY() + widget.getHeight() >= contentTop && widget.getY() <= contentBottom;
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
                scrollOffset = Math.max(0, scrollOffset - 34);

            } else if (delta < 0) {
                scrollOffset = Math.min(maxScroll(), scrollOffset + 34);
            }

            if (old != scrollOffset) {
                minecraft.setScreen(this);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 10, left + panelW, height - 8, 0xF0121710);
        graphics.drawString(font, "TEXT FONT", left + 16, 18, 0xFFB8FF72, false);
        graphics.drawString(font, allowInherit ? "INHERIT and VANILLA are different: vanilla explicitly cancels a parent custom font." : "Default: Minecraft. Import TTF or MSDF atlas.", left + 16, 30, DialogueRetroTheme.TEXT_HINT, false);
        graphics.drawString(font, DialogueEditorFontPreviewPack.statusLine(project), left + 16, 39, 0xFFFFD45A, false);

        if (errorMessage != null && !errorMessage.isBlank()) {

            graphics.drawString(font, font.plainSubstrByWidth(errorMessage, panelW - 32), left + 16, 72, 0xFFFF777D, false);
        }

        graphics.enableScissor(left, contentTop, left + panelW, contentBottom);

        int y = contentTop + 4 - scrollOffset;

        for (FontRow row : rows()) {

            graphics.drawString(font, row.kind, left + 24, y + 22, 0xFF9C957B, false);

            y += 34;
        }

        graphics.disableScissor();

        if (maxScroll() > 0) {
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
        int thumbH = Math.max(18, Math.round(trackH * viewportH / (float) contentHeight));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = trackTop + Math.round(travel * scrollOffset / (float) max);

        graphics.fill(trackX, trackTop, trackX + 2, trackBottom, 0x555B664C);

        graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB8FF72);
    }

    private boolean sameFont(String a, String b) {
        if (allowInherit) {
            if (isBlank(a)) {
                return isBlank(b);
            }

            if (isBlank(b)) {
                return false;
            }

            return normalizeFont(a).equals(normalizeFont(b));
        }
        if (isVanilla(a)) {
            return isBlank(b);
        }

        return !isBlank(b) && normalizeFont(a).equals(normalizeFont(b));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isVanilla(String value) {
        return isBlank(value) || "minecraft:default".equalsIgnoreCase(value) || "vanilla".equalsIgnoreCase(value) || "default".equalsIgnoreCase(value);
    }

    private static String normalizeFont(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record FontRow(String id, String title, String kind) {
    }
}
