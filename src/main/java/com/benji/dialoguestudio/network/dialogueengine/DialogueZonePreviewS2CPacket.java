package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.client.dialogue.DialogueZonePreviewRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record DialogueZonePreviewS2CPacket(List<Zone> zones) {

    private static final int MAX_ZONES = 96;

    public static void encode(DialogueZonePreviewS2CPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(packet.zones.size(), MAX_ZONES);
        buffer.writeVarInt(count);

        for (int i = 0; i < count; i++) {
            Zone zone = packet.zones.get(i);

            buffer.writeUtf(zone.key, 512);
            buffer.writeUtf(zone.shape, 32);

            buffer.writeDouble(zone.x);
            buffer.writeDouble(zone.y);
            buffer.writeDouble(zone.z);

            buffer.writeDouble(zone.radius);
            buffer.writeDouble(zone.height);
            buffer.writeDouble(zone.sizeX);
            buffer.writeDouble(zone.sizeY);
            buffer.writeDouble(zone.sizeZ);

            buffer.writeUtf(zone.style, 32);
            buffer.writeBoolean(zone.showDefaultZone);

            buffer.writeUtf(zone.texture != null ? zone.texture : "", 512);
            buffer.writeUtf(zone.textureMode, 32);
            buffer.writeUtf(zone.textureFit, 32);
            buffer.writeDouble(zone.textureRepeatX);
            buffer.writeDouble(zone.textureRepeatY);
            buffer.writeDouble(zone.textureScrollU);
            buffer.writeDouble(zone.textureScrollV);

            buffer.writeDouble(zone.textureOffsetX);
            buffer.writeDouble(zone.textureOffsetY);
            buffer.writeDouble(zone.textureOffsetZ);
            buffer.writeDouble(zone.textureScaleX);
            buffer.writeDouble(zone.textureScaleY);
            buffer.writeDouble(zone.textureRotationX);
            buffer.writeDouble(zone.textureRotationY);
            buffer.writeDouble(zone.textureRotationZ);

            buffer.writeUtf(zone.color, 64);
            buffer.writeFloat(zone.alpha);
            buffer.writeDouble(zone.yOffset);
            buffer.writeDouble(zone.visualSize);
            buffer.writeDouble(zone.visualHeight);

            buffer.writeBoolean(zone.fillEnabled);
            buffer.writeUtf(zone.fillMode, 32);
            buffer.writeUtf(zone.fillColorBottom, 64);
            buffer.writeUtf(zone.fillColorTop, 64);
            buffer.writeFloat(zone.fillAlphaBottom);
            buffer.writeFloat(zone.fillAlphaTop);

            buffer.writeBoolean(zone.pulse);
            buffer.writeDouble(zone.pulseAmplitude);
            buffer.writeDouble(zone.pulseSpeed);

            buffer.writeBoolean(zone.bob);
            buffer.writeDouble(zone.bobAmplitude);
            buffer.writeDouble(zone.bobSpeed);

            buffer.writeBoolean(zone.rotate);
            buffer.writeDouble(zone.rotateSpeed);

            buffer.writeBoolean(zone.alphaBreathe);
            buffer.writeDouble(zone.alphaBreatheAmount);
            buffer.writeDouble(zone.alphaBreatheSpeed);

            buffer.writeDouble(zone.previewDistance);
        }
    }

    public static DialogueZonePreviewS2CPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_ZONES);
        List<Zone> zones = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            zones.add(new Zone(
                    buffer.readUtf(512),
                    buffer.readUtf(32),

                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),

                    buffer.readUtf(32),
                    buffer.readBoolean(),

                    buffer.readUtf(512),
                    buffer.readUtf(32),
                    buffer.readUtf(32),
                    buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(),

                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),

                    buffer.readUtf(64),
                    buffer.readFloat(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),

                    buffer.readBoolean(),
                    buffer.readUtf(32),
                    buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readFloat(), buffer.readFloat(),

                    buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readBoolean(), buffer.readDouble(),
                    buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),

                    buffer.readDouble()
            ));
        }

        return new DialogueZonePreviewS2CPacket(zones);
    }

    public static void handle(DialogueZonePreviewS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> DialogueZonePreviewRenderer.setZones(packet.zones)));
        context.setPacketHandled(true);
    }

    public record Zone(
            String key,
            String shape,

            double x,
            double y,
            double z,
            double radius,
            double height,
            double sizeX,
            double sizeY,
            double sizeZ,

            String style,
            boolean showDefaultZone,

            String texture,
            String textureMode,
            String textureFit,
            double textureRepeatX,
            double textureRepeatY,
            double textureScrollU,
            double textureScrollV,

            double textureOffsetX,
            double textureOffsetY,
            double textureOffsetZ,
            double textureScaleX,
            double textureScaleY,
            double textureRotationX,
            double textureRotationY,
            double textureRotationZ,

            String color,
            float alpha,
            double yOffset,
            double visualSize,
            double visualHeight,

            boolean fillEnabled,
            String fillMode,
            String fillColorBottom,
            String fillColorTop,
            float fillAlphaBottom,
            float fillAlphaTop,

            boolean pulse,
            double pulseAmplitude,
            double pulseSpeed,

            boolean bob,
            double bobAmplitude,
            double bobSpeed,

            boolean rotate,
            double rotateSpeed,

            boolean alphaBreathe,
            double alphaBreatheAmount,
            double alphaBreatheSpeed,

            double previewDistance
    ) {
    }
}
