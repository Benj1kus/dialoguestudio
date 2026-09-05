package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;


public final class DialogueNodeExternalEvent extends Event {

    private final ServerPlayer player;
    private final Entity source;
    private final ResourceLocation dialogueId;
    private final String nodeId;
    private final String eventId;
    private final DialogueDefinition.Action action;

    public DialogueNodeExternalEvent(ServerPlayer player, Entity source, ResourceLocation dialogueId, String nodeId, String eventId, DialogueDefinition.Action action) {
        this.player = player;
        this.source = source;
        this.dialogueId = dialogueId;
        this.nodeId = nodeId;
        this.eventId = eventId;
        this.action = action;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getSource() {
        return source;
    }

    public ResourceLocation getDialogueId() {
        return dialogueId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getEventId() {
        return eventId;
    }

    public DialogueDefinition.Action getAction() {
        return action;
    }
}
