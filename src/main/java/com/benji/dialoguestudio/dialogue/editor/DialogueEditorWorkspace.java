package com.benji.dialoguestudio.dialogue.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DialogueEditorWorkspace {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DialogueEditorWorkspace() {
    }

    public static Path root() {
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        Path current = gameDirectory.resolve("dialogue_studio");
        Path legacy = gameDirectory.resolve("oasiso_dialogue_editor");

        // Preserve existing Dialogue Studio projects created while the editor lived inside Oasiso.
        if (!Files.exists(current) && Files.exists(legacy)) {
            return legacy;
        }

        return current;
    }

    public static Path projectsRoot() {
        return root().resolve("projects");
    }

    public static Path exportsRoot() {
        return root().resolve("exports");
    }

    public static Path projectRoot(DialogueEditorProject project) {
        return projectsRoot().resolve(project.workspace_id);
    }

    public static Path projectJson(DialogueEditorProject project) {
        return projectRoot(project).resolve("project.json");
    }

    public static Path assetRoot(DialogueEditorProject project) {
        return projectRoot(project).resolve("assets");
    }

    public static void save(DialogueEditorProject project) throws IOException {
        project.normalize();
        Files.createDirectories(projectRoot(project));
        Files.writeString(projectJson(project), GSON.toJson(project), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.createDirectories(root());
        Files.writeString(root().resolve("last_project.txt"), project.workspace_id, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static DialogueEditorProject load(Path projectJson) throws IOException {
        DialogueEditorProject project = GSON.fromJson(Files.readString(projectJson, StandardCharsets.UTF_8), DialogueEditorProject.class);
        if (project == null) throw new IOException("Invalid Dialogue Studio project");
        project.normalize();
        return project;
    }

    public static List<DialogueEditorProject> listProjects() {
        List<DialogueEditorProject> result = new ArrayList<>();
        try {
            Files.createDirectories(projectsRoot());
            try (var stream = Files.list(projectsRoot())) {
                for (Path dir : stream.filter(Files::isDirectory).toList()) {
                    Path json = dir.resolve("project.json");
                    if (!Files.isRegularFile(json)) continue;
                    try {
                        result.add(load(json));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        result.sort(Comparator.comparing(project -> project.project_name.toLowerCase(Locale.ROOT)));
        return result;
    }

    public static DialogueEditorProject loadLastOrDefault() {
        try {
            Path marker = root().resolve("last_project.txt");
            if (Files.isRegularFile(marker)) {
                String id = Files.readString(marker, StandardCharsets.UTF_8).trim();
                Path json = projectsRoot().resolve(id).resolve("project.json");
                if (Files.isRegularFile(json)) return load(json);
            }
        } catch (Exception ignored) {
        }
        return DialogueEditorProject.createDefault();
    }

    public static String importTexture(DialogueEditorProject project, Path source) throws IOException {
        requireExtension(source, ".png");
        String name = sanitizeFileName(source.getFileName().toString());
        Path target = assetRoot(project).resolve("textures/gui/dialogue").resolve(name);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        DialogueEditorTextureCache.invalidate(project, "textures/gui/dialogue/" + name);
        return project.namespace + ":textures/gui/dialogue/" + name;
    }

    public static String importSound(DialogueEditorProject project, Path source) throws IOException {
        requireExtension(source, ".ogg");
        String fileName = sanitizeFileName(source.getFileName().toString());
        String base = fileName.substring(0, fileName.length() - 4);
        String eventKey = sanitizeSoundKey(base);

        Path target = assetRoot(project).resolve("sounds/dialogue").resolve(eventKey + ".ogg");
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        project.sounds.put(eventKey, "dialogue/" + eventKey);
        return project.namespace + ":" + eventKey;
    }

    public static void importDialogueJson(DialogueEditorProject project, Path source) throws IOException {
        requireExtension(source, ".json");
        DialogueDefinition definition = GSON.fromJson(Files.readString(source, StandardCharsets.UTF_8), DialogueDefinition.class);
        boolean graph = definition != null && definition.hasGraph();

        boolean legacy = definition != null && definition.lines != null && !definition.lines.isEmpty();

        if (definition == null || (!graph && !legacy)) {
            throw new IOException("Dialogue JSON has neither legacy lines nor a valid Nodes v3 graph");
        }
        project.definition = definition;
        project.selected_line = 0;
        project.selected_trigger = 0;
        project.normalize();
    }

    public static void importLang(DialogueEditorProject project, Path source) throws IOException {
        requireExtension(source, ".json");
        String file = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String locale = file.substring(0, file.length() - 5);

        JsonElement element = GSON.fromJson(Files.readString(source, StandardCharsets.UTF_8), JsonElement.class);
        if (element == null || !element.isJsonObject())
            throw new IOException("Language file must contain a JSON object");

        LinkedHashMap<String, String> map = project.languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>());
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) map.put(entry.getKey(), entry.getValue().getAsString());
        }
        project.preview_locale = locale;
    }

    public static Path importedAsset(DialogueEditorProject project, String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) return null;
        int colon = resourcePath.indexOf(':');
        if (colon < 0) return null;
        String namespace = resourcePath.substring(0, colon);
        if (!namespace.equals(project.namespace)) return null;
        String path = resourcePath.substring(colon + 1);
        return assetRoot(project).resolve(path);
    }

    private static void requireExtension(Path source, String extension) throws IOException {
        if (source == null || !Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IOException("Expected " + extension + " file");
        }
    }

    private static String sanitizeFileName(String value) {
        value = value.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_.-]", "_");
        return value.isBlank() ? "asset.png" : value;
    }

    private static String sanitizeSoundKey(String value) {
        value = value.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_./-]", "_");
        return value.isBlank() ? "dialogue_voice" : value;
    }
}
