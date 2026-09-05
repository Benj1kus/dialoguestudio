package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.DialogueRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DialogueEditorExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DialogueEditorExporter() {
    }

    public record ExportResult(Path root, Path datapack, Path resourcepack) {
    }

    public record ModExportResult(Path root, Path dataRoot, Path assetsRoot) {
    }

    public static ExportResult export(DialogueEditorProject project) throws IOException {
        project.normalize();
        DialogueEditorWorkspace.save(project);

        String slug = sanitize(project.namespace + "_" + project.dialogue_path.replace('/', '_'));
        Path root = DialogueEditorWorkspace.exportsRoot().resolve(slug);
        Path datapack = root.resolve("datapack");
        Path resourcepack = root.resolve("resourcepack");

        deleteTree(root);
        Files.createDirectories(datapack);
        Files.createDirectories(resourcepack);

        writePackMcmeta(datapack, "Dialogue Studio - " + project.dialogueId());
        writePackMcmeta(resourcepack, "Dialogue Studio Resources - " + project.dialogueId());

        Path dialogue = datapack.resolve("data").resolve(project.namespace).resolve("dialogues").resolve(project.dialogue_path + ".json");

        Files.createDirectories(dialogue.getParent());
        Files.writeString(dialogue, DialogueRegistry.toJson(project.definition), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path namespaceAssets = resourcepack.resolve("assets").resolve(project.namespace);
        Files.createDirectories(namespaceAssets);

        copyUsedAssets(project, namespaceAssets);

        writeLanguages(project, namespaceAssets);
        writeSounds(project, namespaceAssets);
        writeFonts(project, namespaceAssets);

        zipTree(datapack, root.resolve("datapack.zip"));
        zipTree(resourcepack, root.resolve("resourcepack.zip"));

        writePackReadme(root);

        return new ExportResult(root, datapack, resourcepack);
    }

    public static ModExportResult exportForMod(DialogueEditorProject project, Path destination) throws IOException {

        if (destination == null || !Files.isDirectory(destination)) {
            throw new IOException("Choose an existing destination folder");
        }

        project.normalize();
        DialogueEditorWorkspace.save(project);

        String namespace = project.namespace;
        String dialogueSlug = sanitize(project.dialogue_path.replace('/', '_'));

        Path root = destination.resolve("DialogueStudio_ModExport_" + sanitize(namespace) + "_" + dialogueSlug);
        deleteTree(root);

        Files.createDirectories(root);

        Path dataRoot = root.resolve("data");
        Path assetsRoot = root.resolve("assets");
        Path dialogue = dataRoot.resolve(namespace).resolve("dialogues").resolve(project.dialogue_path + ".json");

        Files.createDirectories(dialogue.getParent());
        Files.writeString(dialogue, DialogueRegistry.toJson(project.definition), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path namespaceAssets = assetsRoot.resolve(namespace);
        Files.createDirectories(namespaceAssets);

        copyUsedAssets(project, namespaceAssets);
        writeLanguages(project, namespaceAssets);
        writeFonts(project, namespaceAssets);
        writeModSoundsFragment(project, root);
        writeModReadme(root, namespace);

        return new ModExportResult(root, dataRoot, assetsRoot);
    }

    private static void copyUsedAssets(DialogueEditorProject project, Path namespaceAssets) throws IOException {

        Path imported = DialogueEditorWorkspace.assetRoot(project);
        if (!Files.exists(imported)) {
            return;
        }

        String definitionJson = DialogueRegistry.toJson(project.definition);
        Path textures = imported.resolve("textures");

        if (Files.exists(textures)) {

            try (var stream = Files.walk(textures)) {

                for (Path file : stream.filter(Files::isRegularFile).toList()) {

                    String relative = imported.relativize(file).toString().replace('\\', '/');
                    String resourceId = project.namespace + ":" + relative;

                    if (!containsResource(definitionJson, resourceId)) {
                        continue;
                    }
                    copyOne(file, namespaceAssets.resolve(relative));
                }
            }
        }

        for (String key : usedRegisteredKeys(project, project.sounds)) {
            String soundPath = project.sounds.get(key);

            if (soundPath == null || soundPath.isBlank()) {

                continue;
            }
            copyOne(imported.resolve("sounds").resolve(soundPath + ".ogg"),
                    namespaceAssets.resolve("sounds").resolve(soundPath + ".ogg"));
        }

        for (String key : usedRegisteredKeys(project, project.fonts)) {
            DialogueEditorProject.FontAsset asset = project.fonts.get(key);

            if (asset == null || asset.file == null || asset.file.isBlank()) {
                continue;
            }

            String assetFile = asset.file.replace('\\', '/');
            if ("bitmap_msdf".equalsIgnoreCase(asset.type)) {
                String textureName = assetFile.startsWith("font/") ? assetFile.substring("font/".length()) : assetFile;
                copyOne(imported.resolve("textures/font").resolve(textureName),
                        namespaceAssets.resolve("textures/font").resolve(textureName));

            } else {
                copyOne(imported.resolve(assetFile),
                        namespaceAssets.resolve(assetFile));
            }
        }
    }

    private static boolean containsResource(String json, String resourceId) {
        return json != null && resourceId != null && json.contains("\"" + resourceId + "\"");
    }

    private static <T> Set<String> usedRegisteredKeys(DialogueEditorProject project, Map<String, T> registry) {
        Set<String> result = new LinkedHashSet<>();
        if (registry == null || registry.isEmpty()) {

            return result;
        }

        String json = DialogueRegistry.toJson(project.definition);
        String prefix = project.namespace + ":";

        for (String key : registry.keySet()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String id = prefix + key;
            if (containsResource(json, id)) {
                result.add(key);
            }
        }

        return result;
    }

    private static void copyOne(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static ExportResult installToCurrentInstance(DialogueEditorProject project) throws IOException {
        ExportResult result = export(project);
        Minecraft minecraft = Minecraft.getInstance();

        String slug = sanitize(project.namespace + "_" + project.dialogue_path.replace('/', '_'));
        Path resourceTarget = minecraft.gameDirectory.toPath().resolve("resourcepacks").resolve("DialogueStudio_" + slug);

        deleteTree(resourceTarget);
        copyTree(result.resourcepack, resourceTarget);

        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path dataTarget = worldRoot.resolve("datapacks").resolve("DialogueStudio_" + slug);

            deleteTree(dataTarget);
            copyTree(result.datapack, dataTarget);
        }

        return result;
    }

    private static void writeLanguages(DialogueEditorProject project, Path namespaceAssets) throws IOException {
        if (project.languages == null || project.languages.isEmpty()) {
            return;
        }

        Path langDir = namespaceAssets.resolve("lang");
        Files.createDirectories(langDir);

        for (Map.Entry<String, ? extends Map<String, String>> locale : project.languages.entrySet()) {
            JsonObject root = new JsonObject();

            for (Map.Entry<String, String> entry : locale.getValue().entrySet()) {
                root.addProperty(entry.getKey(), entry.getValue());
            }

            Files.writeString(langDir.resolve(locale.getKey() + ".json"), GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void writeLanguagesMerged(DialogueEditorProject project, Path namespaceAssets) throws IOException {
        if (project.languages == null || project.languages.isEmpty()) {
            return;
        }

        Path langDir = namespaceAssets.resolve("lang");
        Files.createDirectories(langDir);

        for (Map.Entry<String, ? extends Map<String, String>> locale : project.languages.entrySet()) {
            Path file = langDir.resolve(locale.getKey() + ".json");
            JsonObject root = readJsonObject(file);

            for (Map.Entry<String, String> entry : locale.getValue().entrySet()) {
                root.addProperty(entry.getKey(), entry.getValue());
            }

            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void writeSounds(DialogueEditorProject project, Path namespaceAssets) throws IOException {

        JsonObject root = new JsonObject();
        appendProjectSounds(project, root);

        if (root.size() == 0) {
            return;
        }
        Files.writeString(namespaceAssets.resolve("sounds.json"), GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeSoundsMerged(DialogueEditorProject project, Path namespaceAssets) throws IOException {
        if (project.sounds == null || project.sounds.isEmpty()) {
            return;
        }

        Path file = namespaceAssets.resolve("sounds.json");
        JsonObject root = readJsonObject(file);

        appendProjectSounds(project, root);

        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void appendProjectSounds(DialogueEditorProject project, JsonObject root) {
        for (String key : usedRegisteredKeys(project, project.sounds)) {

            String soundPath = project.sounds.get(key);
            if (soundPath == null || soundPath.isBlank()) {

                continue;
            }

            JsonObject sound = new JsonObject();
            JsonArray array = new JsonArray();
            JsonObject item = new JsonObject();

            item.addProperty("name", project.namespace + ":" + soundPath);
            item.addProperty("stream", false);

            array.add(item);
            sound.add("sounds", array);
            root.add(key, sound);
        }
    }

    private static void writeFonts(DialogueEditorProject project, Path namespaceAssets) throws IOException {
        if (project.fonts == null || project.fonts.isEmpty()) {
            return;
        }

        Path fontDir = namespaceAssets.resolve("font");
        Files.createDirectories(fontDir);

        for (String rawKey : usedRegisteredKeys(project, project.fonts)) {

            String key = sanitize(rawKey);
            DialogueEditorProject.FontAsset asset = project.fonts.get(rawKey);

            if (asset == null || asset.file == null || asset.file.isBlank()) {
                continue;
            }

            JsonObject root = new JsonObject();
            JsonArray providers = new JsonArray();
            JsonObject provider = new JsonObject();

            if ("bitmap_msdf".equalsIgnoreCase(asset.type)) {
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", project.namespace + ":" + asset.file);
                provider.addProperty("ascent", Math.max(1, Math.min(asset.height, asset.ascent)));
                provider.addProperty("height", Math.max(1, asset.height));

                JsonArray chars = new JsonArray();

                if (asset.chars != null) {
                    for (String row : asset.chars) {
                        chars.add(row != null ? row : "");
                    }
                }

                provider.add("chars", chars);
            } else {
                provider.addProperty("type", "ttf");

                String ttfFile = asset.file.replace('\\', '/');
                if (ttfFile.startsWith("font/")) {
                    ttfFile = ttfFile.substring("font/".length());
                }

                provider.addProperty("file", project.namespace + ":" + ttfFile);
                provider.addProperty("size", Math.max(1.0F, asset.size));
                provider.addProperty("oversample", Math.max(8.0F, asset.oversample));

                JsonArray shift = new JsonArray();
                shift.add(0.0F);
                shift.add(0.0F);
                provider.add("shift", shift);
            }

            providers.add(provider);
            root.add("providers", providers);

            Files.writeString(fontDir.resolve(key + ".json"), GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void writeModSoundsFragment(DialogueEditorProject project, Path root) throws IOException {
        JsonObject sounds = new JsonObject();
        appendProjectSounds(project, sounds);

        if (sounds.size() == 0) {
            return;
        }
        Files.writeString(root.resolve("SOUNDS_TO_MERGE.json"), GSON.toJson(sounds), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writePackReadme(Path root) throws IOException {

        String text = """
                Dialogue Studio Export
                
                1. Put datapack.zip into your world's datapacks folder.
                2. Put resourcepack.zip into .minecraft/resourcepacks and enable it.
                3. Run /reload or re-enter the world. Use F3+T after resource changes if needed.
                
                Both packs are required when the dialogue uses custom textures, fonts or sounds.
                """;

        Files.writeString(root.resolve("README.txt"), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeModReadme(Path root, String namespace) throws IOException {

        String text = """
                Dialogue Studio - Mod Export
                
                1. Copy data/ and assets/ into your mod's src/main/resources/.
                2. If SOUNDS_TO_MERGE.json exists, merge its top-level entries into assets/%s/sounds.json.
                   If sounds.json does not exist yet, move the file there and rename it to sounds.json.
                3. If a generated lang/<locale>.json already exists in your mod, merge its entries instead of replacing it.
                4. Rebuild/reload your mod.
                """.formatted(namespace);

        Files.writeString(root.resolve("README.txt"), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static JsonObject readJsonObject(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }

        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new IOException("Could not merge existing " + file.getFileName() + ": invalid JSON", exception);
        }
    }

    private static void writePackMcmeta(Path root, String description) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", description);

        JsonObject wrapper = new JsonObject();
        wrapper.add("pack", pack);

        Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(wrapper), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void zipTree(Path source, Path zipFile) throws IOException {
        Files.deleteIfExists(zipFile);
        Files.createDirectories(zipFile.getParent());

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            try (var stream = Files.walk(source)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    String entryName = source.relativize(path).toString().replace('\\', '/');

                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }

        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path out = target.resolve(relative.toString());

                if (Files.isDirectory(path)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }
}
