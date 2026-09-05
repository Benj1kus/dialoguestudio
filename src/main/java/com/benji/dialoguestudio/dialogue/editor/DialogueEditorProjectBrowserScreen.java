package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class DialogueEditorProjectBrowserScreen extends DialogueRetroScreen {

    private final Screen parent;
    private List<DialogueEditorProject> projects = List.of();
    private int selected;
    private int page;
    private int ticks;

    private static final int ROWS = 7;

    public DialogueEditorProjectBrowserScreen(Screen parent) {
        super(Component.literal("Dialogue Studio - Projects"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        projects = DialogueEditorWorkspace.listProjects();
        if (projects.isEmpty()) selected = -1;
        else selected = Math.max(0, Math.min(selected, projects.size() - 1));

        int listW = Math.min(300, Math.max(210, width / 3));
        int start = page * ROWS;
        int end = Math.min(projects.size(), start + ROWS);

        for (int i = start; i < end; i++) {
            DialogueEditorProject project = projects.get(i);
            int index = i;
            int y = 48 + (i - start) * 36;
            String prefix = index == selected ? "> " : "";
            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(prefix + project.project_name + "  [" + project.dialogueId() + "]"), b -> {
                selected = index;
                rebuild();
            }).bounds(14, y, listW - 28, 30).build());
        }

        int pages = Math.max(1, (projects.size() + ROWS - 1) / ROWS);
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuild();
        }).bounds(14, height - 30, 30, 20).build());
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal((page + 1) + "/" + pages), b -> {
        }).bounds(48, height - 30, 70, 20).build());
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), b -> {
            page = Math.min(pages - 1, page + 1);
            rebuild();
        }).bounds(122, height - 30, 30, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("New project"), b -> minecraft.setScreen(new DialogueEditorScreen(DialogueEditorProject.createDefault(), DialogueEditorScreen.Tab.PROJECT))).bounds(listW + 16, height - 30, 92, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Open selected"), b -> {
            if (selected >= 0 && selected < projects.size()) {
                DialogueEditorProject project = projects.get(selected);
                try {
                    DialogueEditorWorkspace.save(project);
                } catch (Exception ignored) {
                }
                minecraft.setScreen(new DialogueEditorScreen(project, DialogueEditorScreen.Tab.PROJECT));
            }
        }).bounds(listW + 112, height - 30, 104, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(width - 82, height - 30, 68, 20).build());
    }

    private void rebuild() {
        minecraft.setScreen(this);
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int listW = Math.min(300, Math.max(210, width / 3));

        graphics.fill(8, 8, listW, height - 38, 0xE011170E);
        graphics.drawString(font, "DIALOGUE PROJECTS", 16, 18, 0xFFB8FF72, false);
        graphics.drawString(font, projects.size() + " project(s)", 16, 31, DialogueRetroTheme.TEXT_HINT, false);

        int px = listW + 12;
        int py = 12;
        int pw = width - px - 12;
        int ph = height - 54;

        if (selected >= 0 && selected < projects.size()) {
            DialogueEditorProject project = projects.get(selected);
            DialogueEditorPreview.render(project, graphics, px, py, pw, ph, ticks, partialTick);
            graphics.drawString(font, project.project_name, px + 12, py + ph - 17, 0xFFFFFFFF, false);
        } else {
            graphics.fill(px, py, px + pw, py + ph, 0xCC0D120B);
            graphics.drawCenteredString(font, "No saved projects yet", px + pw / 2, py + ph / 2, 0xFFE8E0C3);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int pages = Math.max(1, (projects.size() + ROWS - 1) / ROWS);
        int old = page;
        if (delta > 0) page = Math.max(0, page - 1);
        else if (delta < 0) page = Math.min(pages - 1, page + 1);
        if (old != page) {
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
