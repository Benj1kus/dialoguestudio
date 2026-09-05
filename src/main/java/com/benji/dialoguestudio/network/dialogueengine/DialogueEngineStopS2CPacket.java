package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.client.dialogue.DialogueClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DialogueEngineStopS2CPacket(UUID sessionId) {

    public static void encode(DialogueEngineStopS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
    }

    public static DialogueEngineStopS2CPacket decode(FriendlyByteBuf buffer) {
        return new DialogueEngineStopS2CPacket(buffer.readUUID());
    }

    public static void handle(DialogueEngineStopS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DialogueClient.cancel(packet.sessionId)));

        context.setPacketHandled(true);
    }
}