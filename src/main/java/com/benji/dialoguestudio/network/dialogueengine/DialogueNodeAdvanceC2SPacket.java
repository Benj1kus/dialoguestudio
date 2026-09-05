package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.dialogue.DialogueSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DialogueNodeAdvanceC2SPacket(UUID sessionId, String nodeId) {

    public static void encode(DialogueNodeAdvanceC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeUtf(packet.nodeId != null ? packet.nodeId : "", 256);
    }

    public static DialogueNodeAdvanceC2SPacket decode(FriendlyByteBuf buffer) {
        return new DialogueNodeAdvanceC2SPacket(buffer.readUUID(), buffer.readUtf(256));
    }

    public static void handle(DialogueNodeAdvanceC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();

        if (player != null) {
            context.enqueueWork(() -> DialogueSessionManager.advanceNode(player, packet.sessionId, packet.nodeId));
        }

        context.setPacketHandled(true);
    }
}
