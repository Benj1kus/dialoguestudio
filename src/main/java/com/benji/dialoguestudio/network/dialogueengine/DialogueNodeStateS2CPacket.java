package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.client.dialogue.DialogueClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record DialogueNodeStateS2CPacket(UUID sessionId, String nodeId, List<Boolean> enabledChoices) {

    public static void encode(DialogueNodeStateS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeUtf(packet.nodeId != null ? packet.nodeId : "", 256);

        int count = Math.min(packet.enabledChoices != null ? packet.enabledChoices.size() : 0, 128);
        buffer.writeVarInt(count);

        for (int i = 0; i < count; i++) {
            buffer.writeBoolean(Boolean.TRUE.equals(packet.enabledChoices.get(i)));
        }
    }

    public static DialogueNodeStateS2CPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        String nodeId = buffer.readUtf(256);

        int count = Math.min(buffer.readVarInt(), 128);
        List<Boolean> enabled = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            enabled.add(buffer.readBoolean());
        }

        return new DialogueNodeStateS2CPacket(sessionId, nodeId, enabled);
    }

    public static void handle(DialogueNodeStateS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DialogueClient.setNodeState(packet.sessionId, packet.nodeId, packet.enabledChoices)));

        context.setPacketHandled(true);
    }
}
