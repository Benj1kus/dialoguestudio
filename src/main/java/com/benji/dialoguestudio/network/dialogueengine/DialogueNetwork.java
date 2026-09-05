package com.benji.dialoguestudio.network.dialogueengine;

import com.benji.dialoguestudio.DialogueStudio;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.UUID;

public final class DialogueNetwork {

    private static final String PROTOCOL = "3";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "dialogue_engine"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private DialogueNetwork() {
    }


    public static void register() {
        CHANNEL.registerMessage(0, DialogueEngineStartS2CPacket.class, DialogueEngineStartS2CPacket::encode, DialogueEngineStartS2CPacket::decode, DialogueEngineStartS2CPacket::handle);
        CHANNEL.registerMessage(1, DialogueEngineFinishC2SPacket.class, DialogueEngineFinishC2SPacket::encode, DialogueEngineFinishC2SPacket::decode, DialogueEngineFinishC2SPacket::handle);
        CHANNEL.registerMessage(2, DialogueEngineStopS2CPacket.class, DialogueEngineStopS2CPacket::encode, DialogueEngineStopS2CPacket::decode, DialogueEngineStopS2CPacket::handle);
        CHANNEL.registerMessage(3, DialogueZonePreviewS2CPacket.class, DialogueZonePreviewS2CPacket::encode, DialogueZonePreviewS2CPacket::decode, DialogueZonePreviewS2CPacket::handle);
        CHANNEL.registerMessage(4, DialogueNodeStateS2CPacket.class, DialogueNodeStateS2CPacket::encode, DialogueNodeStateS2CPacket::decode, DialogueNodeStateS2CPacket::handle);
        CHANNEL.registerMessage(5, DialogueNodeAdvanceC2SPacket.class, DialogueNodeAdvanceC2SPacket::encode, DialogueNodeAdvanceC2SPacket::decode, DialogueNodeAdvanceC2SPacket::handle);
        CHANNEL.registerMessage(6, DialogueChoiceC2SPacket.class, DialogueChoiceC2SPacket::encode, DialogueChoiceC2SPacket::decode, DialogueChoiceC2SPacket::handle);
    }


    public static void start(ServerPlayer player, UUID sessionId, ResourceLocation dialogueId, String json) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DialogueEngineStartS2CPacket(sessionId, dialogueId, json));
    }


    public static void finish(UUID sessionId) {
        CHANNEL.sendToServer(new DialogueEngineFinishC2SPacket(sessionId));
    }


    public static void stop(ServerPlayer player, UUID sessionId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DialogueEngineStopS2CPacket(sessionId));
    }


    public static void syncZones(ServerPlayer player, List<DialogueZonePreviewS2CPacket.Zone> zones) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DialogueZonePreviewS2CPacket(zones));
    }


    public static void nodeState(ServerPlayer player, UUID sessionId, String nodeId, List<Boolean> enabledChoices) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DialogueNodeStateS2CPacket(sessionId, nodeId, enabledChoices));
    }


    public static void advanceNode(UUID sessionId, String nodeId) {
        CHANNEL.sendToServer(new DialogueNodeAdvanceC2SPacket(sessionId, nodeId));
    }


    public static void choose(UUID sessionId, String nodeId, int choiceIndex) {
        CHANNEL.sendToServer(new DialogueChoiceC2SPacket(sessionId, nodeId, choiceIndex));
    }
}
