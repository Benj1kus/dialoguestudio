package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.trigger.DialogueTriggerEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class DialogueApi {

    private DialogueApi() {
    }

    public static boolean start(ServerPlayer player, Entity source, ResourceLocation dialogueId) {
        return DialogueSessionManager.start(player, source, dialogueId, null);
    }

    public static boolean start(ServerPlayer player, ResourceLocation dialogueId) {
        return start(player, null, dialogueId);
    }

    public static boolean start(ServerPlayer player, Entity source, ResourceLocation dialogueId, Runnable onFinish) {
        return DialogueSessionManager.start(player, source, dialogueId, onFinish);
    }

    public static boolean isActive(ServerPlayer player) {
        return DialogueSessionManager.isActive(player);
    }

    public static void cancel(ServerPlayer player) {
        DialogueSessionManager.cancel(player);
    }

    public static boolean fireExternal(ServerPlayer player, Entity source, String event) {
        return DialogueTriggerEngine.fireExternal(player, source, event);
    }
}