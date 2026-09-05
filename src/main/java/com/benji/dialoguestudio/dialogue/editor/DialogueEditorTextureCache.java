package com.benji.dialoguestudio.dialogue.editor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DialogueEditorTextureCache {

    private static final Map<Path, ResourceLocation> CACHE = new HashMap<>();

    private DialogueEditorTextureCache() {
    }

    public static ResourceLocation resolve(DialogueEditorProject project, String declared, ResourceLocation fallback) {
        if (declared == null || declared.isBlank()) return fallback;

        Path imported = DialogueEditorWorkspace.importedAsset(project, declared);
        if (imported != null && Files.isRegularFile(imported)) {
            return CACHE.computeIfAbsent(imported.toAbsolutePath().normalize(), DialogueEditorTextureCache::loadDynamic);
        }

        ResourceLocation parsed = ResourceLocation.tryParse(declared);
        return parsed != null ? parsed : fallback;
    }

    public static void invalidate(DialogueEditorProject project, String relativeAssetPath) {
        Path path = DialogueEditorWorkspace.assetRoot(project).resolve(relativeAssetPath).toAbsolutePath().normalize();
        ResourceLocation old = CACHE.remove(path);
        if (old != null) Minecraft.getInstance().getTextureManager().release(old);
    }

    public static void clear() {
        for (ResourceLocation id : CACHE.values()) Minecraft.getInstance().getTextureManager().release(id);
        CACHE.clear();
    }

    private static ResourceLocation loadDynamic(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(stream);
            DynamicTexture texture = new DynamicTexture(image);
            return Minecraft.getInstance().getTextureManager().register("dialogue_studio_editor", texture);
        } catch (Exception exception) {
            return null;
        }
    }
}
