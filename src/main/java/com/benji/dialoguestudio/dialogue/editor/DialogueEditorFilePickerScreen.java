package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DialogueEditorFilePickerScreen extends DialogueRetroScreen {

    private final Screen parent;
    private final String extension;
    private final Consumer<Path> callback;
    private final boolean selectDirectory;

    private Path current;
    private int page;
    private String search = "";

    private static final int MIN_ROWS = 5;

    public DialogueEditorFilePickerScreen(Screen parent, Path initial, String extension, Consumer<Path> callback) {
        this(parent, initial, extension, callback, false);
    }

    public DialogueEditorFilePickerScreen(Screen parent, Path initial, String extension, Consumer<Path> callback, boolean selectDirectory) {
        this(parent, initial, extension, callback, "", 0, selectDirectory);
    }

    private DialogueEditorFilePickerScreen(Screen parent, Path initial, String extension, Consumer<Path> callback, String search, int page, boolean selectDirectory) {
        super(Component.literal(selectDirectory ? "Dialogue Studio - Folder Picker" : "Dialogue Studio - File Picker"));

        this.parent = parent;
        this.current = initial != null ? initial : Minecraft.getInstance().gameDirectory.toPath();

        this.extension = extension != null ? extension.toLowerCase(Locale.ROOT) : "";

        this.callback = callback;
        this.search = search != null ? search : "";
        this.page = page;
        this.selectDirectory = selectDirectory;
    }

    @Override
    protected void init() {
        int y = 8;
        int x = 10;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Game"), b -> jump(minecraft.gameDirectory.toPath())).bounds(x, y, 54, 20).build());
        x += 58;
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Home"), b -> jump(home())).bounds(x, y, 54, 20).build());
        x += 58;
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Desktop"), b -> jump(preferred(home().resolve("Desktop"), home()))).bounds(x, y, 66, 20).build());
        x += 70;
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Downloads"), b -> jump(preferred(home().resolve("Downloads"), home()))).bounds(x, y, 78, 20).build());
        x += 82;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Up"), b -> {
            if (current.getParent() != null) {
                jump(current.getParent());
            }
        }).bounds(x, y, 42, 20).build());

        int searchY = 34;

        EditBox searchBox = new DialogueRetroEditBox(font, 10, searchY, Math.max(90, width - 104), 20, Component.literal("Search"));

        searchBox.setValue(search);
        searchBox.setResponder(value -> search = value);

        addRenderableWidget(searchBox);

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Find"), b -> {
            page = 0;
            rebuild();
        }).bounds(width - 88, searchY, 78, 20).build());

        List<Path> entries = entries();

        int rows = rowsPerPage();
        int start = page * rows;
        int end = Math.min(entries.size(), start + rows);
        int top = 78;

        for (int i = start; i < end; i++) {
            Path path = entries.get(i);
            boolean directory = Files.isDirectory(path);

            String name = (directory ? "[DIR]  " : "") + path.getFileName();
            int rowY = top + (i - start) * 24;

            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(name), b -> {
                if (directory) {
                    jump(path);
                    return;
                }

                if (!selectDirectory) {
                    callback.accept(path);

                    if (minecraft.screen == this) {
                        minecraft.setScreen(parent);
                    }
                }
            }).bounds(16, rowY, width - 32, 20).build());
        }

        int pages = Math.max(1, (entries.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuild();
        }).bounds(width / 2 - 72, height - 28, 30, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal((page + 1) + "/" + pages), b -> {
        }).bounds(width / 2 - 38, height - 28, 76, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), b -> {
            page = Math.min(pages - 1, page + 1);
            rebuild();
        }).bounds(width / 2 + 42, height - 28, 30, 20).build());

        if (selectDirectory) {
            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Select folder"), b -> {
                if (current == null || !Files.isDirectory(current)) {
                    return;
                }

                callback.accept(current);

                if (minecraft.screen == this) {
                    minecraft.setScreen(parent);
                }
            }).bounds(10, height - 28, 108, 20).build());
        }

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(width - 86, height - 28, 76, 20).build());
    }

    private Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    private Path preferred(Path preferred, Path fallback) {
        return Files.isDirectory(preferred) ? preferred : fallback;
    }

    private void jump(Path target) {
        if (target != null && Files.isDirectory(target)) {
            current = target;
        }

        page = 0;
        rebuild();
    }

    private List<Path> entries() {
        List<Path> result = new ArrayList<>();

        try (var stream = Files.list(current)) {
            stream.filter(path -> {
                if (Files.isDirectory(path)) {
                    return true;
                }

                if (selectDirectory) {
                    return false;
                }

                if (extension.isBlank()) {
                    return true;
                }

                return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
            }).filter(path -> search.isBlank() || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing((Path path) -> !Files.isDirectory(path)).thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).forEach(result::add);

        } catch (Exception ignored) {
        }

        return result;
    }

    private int rowsPerPage() {
        return Math.max(MIN_ROWS, Math.max(1, (height - 118) / 24));
    }

    private void rebuild() {
        minecraft.setScreen(new DialogueEditorFilePickerScreen(parent, current, extension, callback, search, page, selectDirectory));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<Path> entries = entries();

        int rows = rowsPerPage();
        int pages = Math.max(1, (entries.size() + rows - 1) / rows);
        int old = page;

        if (delta > 0) {
            page = Math.max(0, page - 1);
        } else if (delta < 0) {
            page = Math.min(pages - 1, page + 1);
        }

        if (page != old) {
            rebuild();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.drawString(font, (selectDirectory ? "Folder: " : "Folder: ") + current, 10, 59, DialogueRetroTheme.TEXT_PATH, false);

        String hint = selectDirectory ? "Open the destination folder, then click Select folder. Mouse wheel changes pages." : "Mouse wheel changes pages. Showing folders and " + (extension.isBlank() ? "files" : extension + " files") + ". You can also drag files directly into Dialogue Studio.";

        graphics.drawString(font, hint, 10, height - 40, DialogueRetroTheme.TEXT_HINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
