package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.client.dialogue.DialogueInteractiveMarkerRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record DialogueInteractiveMarkerS2CPacket(List<Marker> markers) {

    private static final int MAX_MARKERS = 128;

    public static void encode(DialogueInteractiveMarkerS2CPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(packet.markers.size(), MAX_MARKERS);
        buffer.writeVarInt(count);

        for (int i = 0; i < count; i++) {
            Marker marker = packet.markers.get(i);

            buffer.writeUtf(marker.key, 512);
            buffer.writeInt(marker.entityId);
            buffer.writeDouble(marker.x);
            buffer.writeDouble(marker.y);
            buffer.writeDouble(marker.z);

            buffer.writeUtf(marker.texture != null ? marker.texture : "", 512);
            buffer.writeDouble(marker.size);
            buffer.writeDouble(marker.yOffset);
            buffer.writeDouble(marker.previewDistance);

            buffer.writeBoolean(marker.animated);

            buffer.writeBoolean(marker.bob);
            buffer.writeDouble(marker.bobAmplitude);
            buffer.writeDouble(marker.bobSpeed);

            buffer.writeBoolean(marker.pulse);
            buffer.writeDouble(marker.pulseAmount);
            buffer.writeDouble(marker.pulseSpeed);

            buffer.writeBoolean(marker.sway);
            buffer.writeDouble(marker.swayDegrees);
            buffer.writeDouble(marker.swaySpeed);
        }
    }

    public static DialogueInteractiveMarkerS2CPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_MARKERS);
        List<Marker> markers = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            markers.add(new Marker(
                    buffer.readUtf(512),
                    buffer.readInt(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readUtf(512),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean(),
                    buffer.readDouble(),
                    buffer.readDouble()
            ));
        }

        return new DialogueInteractiveMarkerS2CPacket(markers);
    }

    public static void handle(DialogueInteractiveMarkerS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> DialogueInteractiveMarkerRenderer.setMarkers(packet.markers)));
        context.setPacketHandled(true);
    }

    public record Marker(
            String key,
            int entityId,
            double x,
            double y,
            double z,
            String texture,
            double size,
            double yOffset,
            double previewDistance,
            boolean animated,
            boolean bob,
            double bobAmplitude,
            double bobSpeed,
            boolean pulse,
            double pulseAmount,
            double pulseSpeed,
            boolean sway,
            double swayDegrees,
            double swaySpeed
    ) {
    }
}
