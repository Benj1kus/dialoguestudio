package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.client.dialogue.DialogueInteractiveMarkerRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record DialogueInteractiveMarkerS2CPacket(List<Marker> markers, List<MarkerText> texts) {

    private static final int MAX_MARKERS = 128;
    private static final int MAX_TEXTS = 128;

    public DialogueInteractiveMarkerS2CPacket(List<Marker> markers) {
        this(markers, List.of());
    }

    public static void encode(DialogueInteractiveMarkerS2CPacket packet, FriendlyByteBuf buffer) {
        int markerCount = Math.min(packet.markers.size(), MAX_MARKERS);
        buffer.writeVarInt(markerCount);

        for (int i = 0; i < markerCount; i++) {
            Marker marker = packet.markers.get(i);

            buffer.writeUtf(marker.key, 512);
            buffer.writeInt(marker.entityId);
            buffer.writeDouble(marker.x);
            buffer.writeDouble(marker.y);
            buffer.writeDouble(marker.z);

            buffer.writeUtf(marker.texture != null ? marker.texture : "", 512);
            buffer.writeDouble(marker.size);
            writeWorldSettings(buffer, marker.yOffset, marker.previewDistance, marker.animated, marker.bob, marker.bobAmplitude, marker.bobSpeed, marker.pulse, marker.pulseAmount, marker.pulseSpeed, marker.sway, marker.swayDegrees, marker.swaySpeed);
        }

        int textCount = Math.min(packet.texts.size(), MAX_TEXTS);
        buffer.writeVarInt(textCount);

        for (int i = 0; i < textCount; i++) {
            MarkerText text = packet.texts.get(i);

            buffer.writeUtf(text.key, 512);
            buffer.writeInt(text.entityId);
            buffer.writeDouble(text.x);
            buffer.writeDouble(text.y);
            buffer.writeDouble(text.z);

            buffer.writeUtf(text.text != null ? text.text : "", 512);
            buffer.writeUtf(text.color != null ? text.color : "white", 64);
            buffer.writeDouble(text.scale);
            buffer.writeBoolean(text.shadow);
            buffer.writeBoolean(text.background);
            buffer.writeFloat(text.backgroundAlpha);

            writeWorldSettings(buffer, text.yOffset, text.previewDistance, text.animated, text.bob, text.bobAmplitude, text.bobSpeed, text.pulse, text.pulseAmount, text.pulseSpeed, text.sway, text.swayDegrees, text.swaySpeed);
        }
    }

    private static void writeWorldSettings(FriendlyByteBuf buffer, double yOffset, double previewDistance, boolean animated, boolean bob, double bobAmplitude, double bobSpeed, boolean pulse, double pulseAmount, double pulseSpeed, boolean sway, double swayDegrees, double swaySpeed) {
        buffer.writeDouble(yOffset);
        buffer.writeDouble(previewDistance);

        buffer.writeBoolean(animated);

        buffer.writeBoolean(bob);
        buffer.writeDouble(bobAmplitude);
        buffer.writeDouble(bobSpeed);

        buffer.writeBoolean(pulse);
        buffer.writeDouble(pulseAmount);
        buffer.writeDouble(pulseSpeed);

        buffer.writeBoolean(sway);
        buffer.writeDouble(swayDegrees);
        buffer.writeDouble(swaySpeed);
    }

    public static DialogueInteractiveMarkerS2CPacket decode(FriendlyByteBuf buffer) {
        int markerCount = Math.min(buffer.readVarInt(), MAX_MARKERS);
        List<Marker> markers = new ArrayList<>(markerCount);

        for (int i = 0; i < markerCount; i++) {
            String key = buffer.readUtf(512);
            int entityId = buffer.readInt();
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();

            String texture = buffer.readUtf(512);
            double size = buffer.readDouble();
            WorldSettings settings = readWorldSettings(buffer);

            markers.add(new Marker(key, entityId, x, y, z, texture, size, settings.yOffset, settings.previewDistance, settings.animated, settings.bob, settings.bobAmplitude, settings.bobSpeed, settings.pulse, settings.pulseAmount, settings.pulseSpeed, settings.sway, settings.swayDegrees, settings.swaySpeed));
        }

        int textCount = Math.min(buffer.readVarInt(), MAX_TEXTS);
        List<MarkerText> texts = new ArrayList<>(textCount);

        for (int i = 0; i < textCount; i++) {
            String key = buffer.readUtf(512);
            int entityId = buffer.readInt();
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();

            String text = buffer.readUtf(512);
            String color = buffer.readUtf(64);
            double scale = buffer.readDouble();
            boolean shadow = buffer.readBoolean();
            boolean background = buffer.readBoolean();
            float backgroundAlpha = buffer.readFloat();

            WorldSettings settings = readWorldSettings(buffer);

            texts.add(new MarkerText(key, entityId, x, y, z, text, color, scale, shadow, background, backgroundAlpha, settings.yOffset, settings.previewDistance, settings.animated, settings.bob, settings.bobAmplitude, settings.bobSpeed, settings.pulse, settings.pulseAmount, settings.pulseSpeed, settings.sway, settings.swayDegrees, settings.swaySpeed));
        }

        return new DialogueInteractiveMarkerS2CPacket(markers, texts);
    }

    private static WorldSettings readWorldSettings(FriendlyByteBuf buffer) {
        return new WorldSettings(buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(DialogueInteractiveMarkerS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DialogueInteractiveMarkerRenderer.setEntries(packet.markers, packet.texts)));
        context.setPacketHandled(true);
    }

    public record Marker(String key, int entityId, double x, double y, double z, String texture, double size,
                         double yOffset, double previewDistance, boolean animated, boolean bob, double bobAmplitude,
                         double bobSpeed, boolean pulse, double pulseAmount, double pulseSpeed, boolean sway,
                         double swayDegrees, double swaySpeed) {
    }

    public record MarkerText(String key, int entityId, double x, double y, double z, String text, String color,
                             double scale, boolean shadow, boolean background, float backgroundAlpha, double yOffset,
                             double previewDistance, boolean animated, boolean bob, double bobAmplitude,
                             double bobSpeed, boolean pulse, double pulseAmount, double pulseSpeed, boolean sway,
                             double swayDegrees, double swaySpeed) {
    }

    private record WorldSettings(double yOffset, double previewDistance, boolean animated, boolean bob,
                                 double bobAmplitude, double bobSpeed, boolean pulse, double pulseAmount,
                                 double pulseSpeed, boolean sway, double swayDegrees, double swaySpeed) {
    }
}
