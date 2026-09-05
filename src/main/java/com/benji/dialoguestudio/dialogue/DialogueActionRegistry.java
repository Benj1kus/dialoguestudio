package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueActionRegistry {

    private static final Map<ResourceLocation, Handler> HANDLERS = new ConcurrentHashMap<>();

    private DialogueActionRegistry() {
    }

    public static void register(ResourceLocation id, Handler handler) {
        if (id == null || handler == null) {
            throw new IllegalArgumentException("Dialogue action id/handler cannot be null");
        }

        HANDLERS.put(id, handler);
    }

    public static boolean execute(String type, ServerPlayer player, Entity source, ResourceLocation dialogueId, String nodeId, DialogueDefinition.Action action) {
        ResourceLocation id = ResourceLocation.tryParse(type);

        if (id == null) {
            return false;
        }

        Handler handler = HANDLERS.get(id);

        if (handler == null) {
            return false;
        }

        handler.run(player, source, dialogueId, nodeId, action);

        return true;
    }

    @FunctionalInterface
    public interface Handler {
        void run(ServerPlayer player, Entity source, ResourceLocation dialogueId, String nodeId, DialogueDefinition.Action action);
    }
}
