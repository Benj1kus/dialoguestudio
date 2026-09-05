package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.dialogue.DialogueSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DialogueEngineFinishC2SPacket(UUID sessionId) {

    public static void encode(DialogueEngineFinishC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
    }

    public static DialogueEngineFinishC2SPacket decode(FriendlyByteBuf buffer) {
        return new DialogueEngineFinishC2SPacket(buffer.readUUID());
    }

    public static void handle(DialogueEngineFinishC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        ServerPlayer player = context.getSender();

        if (player != null) {
            context.enqueueWork(() -> DialogueSessionManager.finish(player, packet.sessionId));
        }

        context.setPacketHandled(true);
    }
}