package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.client.dialogue.DialogueImageTextures;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DialogueEditorTextureCache {

    private DialogueEditorTextureCache() {
    }

    public static ResourceLocation resolve(DialogueEditorProject project, String declared, ResourceLocation fallback) {
        if (declared == null || declared.isBlank()) return fallback;

        Path imported = DialogueEditorWorkspace.importedAsset(project, declared);
        if (imported != null && Files.isRegularFile(imported)) {
            return DialogueImageTextures.EDITOR.resolve(imported, fallback, project.animate_preview);
        }

        return DialogueImageTextures.EDITOR.resolve(ResourceLocation.tryParse(declared), fallback, project.animate_preview);
    }

    public static void invalidate(DialogueEditorProject project, String relativeAssetPath) {
        DialogueImageTextures.EDITOR.invalidate(DialogueEditorWorkspace.assetRoot(project).resolve(relativeAssetPath));
    }

    public static void clear() {
        DialogueImageTextures.EDITOR.clear();
    }

    public static void restart() {
        DialogueImageTextures.EDITOR.restart();
    }
}
