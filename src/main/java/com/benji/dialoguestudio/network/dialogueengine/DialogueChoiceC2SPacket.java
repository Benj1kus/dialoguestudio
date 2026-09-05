package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.dialogue.DialogueSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DialogueChoiceC2SPacket(UUID sessionId, String nodeId, int choiceIndex) {

    public static void encode(DialogueChoiceC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeUtf(packet.nodeId != null ? packet.nodeId : "", 256);
        buffer.writeVarInt(packet.choiceIndex);
    }

    public static DialogueChoiceC2SPacket decode(FriendlyByteBuf buffer) {
        return new DialogueChoiceC2SPacket(buffer.readUUID(), buffer.readUtf(256), buffer.readVarInt());
    }

    public static void handle(DialogueChoiceC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();

        if (player != null) {
            context.enqueueWork(() -> DialogueSessionManager.choose(player, packet.sessionId, packet.nodeId, packet.choiceIndex));
        }

        context.setPacketHandled(true);
    }
}
