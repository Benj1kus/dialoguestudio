package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueConditionRegistry {

    private static final Map<ResourceLocation, Handler> HANDLERS = new ConcurrentHashMap<>();

    private DialogueConditionRegistry() {
    }

    public static void register(ResourceLocation id, Handler handler) {
        if (id == null || handler == null) {
            throw new IllegalArgumentException("Dialogue condition id/handler cannot be null");
        }
        HANDLERS.put(id, handler);
    }

    public static boolean evaluate(String type, ServerPlayer player, Entity source, ResourceLocation dialogueId, DialogueDefinition.Condition condition) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        if (id == null) return false;

        Handler handler = HANDLERS.get(id);
        return handler != null && handler.test(player, source, dialogueId, condition);
    }

    @FunctionalInterface
    public interface Handler {
        boolean test(ServerPlayer player, Entity source, ResourceLocation dialogueId, DialogueDefinition.Condition condition);
    }
}
