package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.client.dialogue.DialogueClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DialogueEngineStartS2CPacket(UUID sessionId, ResourceLocation dialogueId, String json) {

    public static void encode(DialogueEngineStartS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeResourceLocation(packet.dialogueId);
        buffer.writeUtf(packet.json, 262144);
    }

    public static DialogueEngineStartS2CPacket decode(FriendlyByteBuf buffer) {
        return new DialogueEngineStartS2CPacket(buffer.readUUID(), buffer.readResourceLocation(), buffer.readUtf(262144));
    }

    public static void handle(DialogueEngineStartS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DialogueClient.start(packet.sessionId, packet.dialogueId, packet.json)));

        context.setPacketHandled(true);
    }
}