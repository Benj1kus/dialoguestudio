package com.benji.dialoguestudio.dialogue.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class DialogueEditorUiSettings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DialogueEditorUiSettings INSTANCE;

    public String preset = "auto";

    public int inspector_width = 338;
    public int row_spacing = 34;
    public int control_height = 20;
    public int timeline_height = 74;
    public int min_tab_width = 64;

    private DialogueEditorUiSettings() {
    }

    public static DialogueEditorUiSettings get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        INSTANCE.normalize();
        return INSTANCE;
    }

    public static void save() {
        try {
            DialogueEditorUiSettings settings = get();
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(settings), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    public void setPreset(String value) {
        preset = value != null ? value.toLowerCase(Locale.ROOT) : "auto";

        switch (preset) {
            case "compact" -> {
                inspector_width = 282;
                row_spacing = 30;
                control_height = 18;
                timeline_height = 66;
                min_tab_width = 56;
            }
            case "comfortable" -> {
                inspector_width = 338;
                row_spacing = 34;
                control_height = 20;
                timeline_height = 76;
                min_tab_width = 64;
            }
            case "large" -> {
                inspector_width = 394;
                row_spacing = 39;
                control_height = 22;
                timeline_height = 90;
                min_tab_width = 72;
            }
            case "preview_focus" -> {
                inspector_width = 258;
                row_spacing = 31;
                control_height = 18;
                timeline_height = 68;
                min_tab_width = 58;
            }
            case "custom" -> {
                // Keep current custom values.
            }
            default -> preset = "auto";
        }

        normalize();
        save();
    }

    public void reset() {
        preset = "auto";
        inspector_width = 338;
        row_spacing = 34;
        control_height = 20;
        timeline_height = 74;
        min_tab_width = 64;
        save();
    }

    public int resolvedInspectorWidth(int screenWidth) {
        int value;

        if ("auto".equalsIgnoreCase(preset)) {
            value = Math.round(screenWidth * 0.38F);
            value = clamp(value, 270, 360);
        } else {
            value = inspector_width;
        }

        int maxAllowed = Math.max(180, screenWidth - 180);
        int minAllowed = Math.min(230, maxAllowed);
        return clamp(value, minAllowed, maxAllowed);
    }

    public int resolvedRowSpacing(int screenHeight) {
        if ("auto".equalsIgnoreCase(preset)) {
            return screenHeight < 420 ? 29 : screenHeight < 560 ? 32 : 34;
        }
        return clamp(row_spacing, 26, 52);
    }

    public int resolvedControlHeight(int screenHeight) {
        if ("auto".equalsIgnoreCase(preset)) {
            return screenHeight < 420 ? 18 : 20;
        }
        return clamp(control_height, 16, 28);
    }

    public int resolvedTimelineHeight(int screenHeight) {
        if ("auto".equalsIgnoreCase(preset)) {
            return clamp(Math.round(screenHeight * 0.15F), 66, 92);
        }
        return clamp(timeline_height, 58, 120);
    }

    public int resolvedMinTabWidth() {
        return clamp(min_tab_width, 50, 90);
    }

    public void makeCustom() {
        preset = "custom";
        normalize();
        save();
    }

    private void normalize() {
        if (preset == null || preset.isBlank()) preset = "auto";
        inspector_width = clamp(inspector_width, 230, 520);
        row_spacing = clamp(row_spacing, 26, 52);
        control_height = clamp(control_height, 16, 28);
        timeline_height = clamp(timeline_height, 58, 120);
        min_tab_width = clamp(min_tab_width, 50, 90);
    }

    private static DialogueEditorUiSettings load() {
        try {
            Path file = file();
            if (Files.isRegularFile(file)) {
                DialogueEditorUiSettings loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), DialogueEditorUiSettings.class);
                if (loaded != null) return loaded;
            }
        } catch (Exception ignored) {
        }
        return new DialogueEditorUiSettings();
    }

    private static Path file() {
        return DialogueEditorWorkspace.root().resolve("ui_settings.json");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
