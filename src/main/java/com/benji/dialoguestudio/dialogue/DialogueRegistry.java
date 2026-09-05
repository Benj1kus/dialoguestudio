package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DialogueRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<ResourceLocation, DialogueDefinition> DEFINITIONS = new LinkedHashMap<>();

    private DialogueRegistry() {
    }

    public static void reload(ResourceManager manager) {
        DEFINITIONS.clear();

        Map<ResourceLocation, Resource> resources = manager.listResources("dialogues", id -> id.getPath().endsWith(".json"));

        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> load(entry.getKey(), entry.getValue()));

        LOGGER.info("Loaded {} Dialogue Studio dialogues", DEFINITIONS.size());
    }

    private static void load(ResourceLocation file, Resource resource) {
        String path = file.getPath();

        if (!path.startsWith("dialogues/") || !path.endsWith(".json")) {
            return;
        }

        path = path.substring("dialogues/".length(), path.length() - ".json".length());

        ResourceLocation dialogueId = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);

        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            DialogueDefinition definition = GSON.fromJson(reader, DialogueDefinition.class);

            boolean graph = definition != null && definition.hasGraph();
            boolean legacy = definition != null && definition.lines != null && !definition.lines.isEmpty();

            if (definition == null || (!graph && !legacy)) {

                LOGGER.warn("Dialogue {} has neither legacy lines nor a valid Nodes v3 graph", dialogueId);

                return;
            }

            DEFINITIONS.put(dialogueId, definition);

        } catch (Exception exception) {
            LOGGER.error("Failed to load dialogue {}", dialogueId, exception);
        }
    }

    public static DialogueDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static Map<ResourceLocation, DialogueDefinition> entries() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    public static String toJson(DialogueDefinition definition) {
        return GSON.toJson(definition);
    }
}