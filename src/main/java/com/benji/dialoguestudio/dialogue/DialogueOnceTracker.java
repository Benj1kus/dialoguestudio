package com.benji.dialoguestudio.dialogue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.*;

final class DialogueOnceTracker {

    private static final String PLAYER_TAG = "OasisoDialogueSeen";

    private static final String ENTITY_TAG = "OasisoDialogueSeen";

    private static final Map<UUID, Set<ResourceLocation>> SESSION_SEEN = new HashMap<>();

    private DialogueOnceTracker() {
    }

    static boolean hasSeen(ServerPlayer player, Entity source, ResourceLocation id, String mode) {
        mode = normalize(mode);

        return switch (mode) {

            case "player" -> getPlayerSeen(player).getBoolean(id.toString());

            case "entity" ->
                    source != null && source.getPersistentData().getCompound(ENTITY_TAG).getBoolean(id.toString());

            case "session" -> SESSION_SEEN.getOrDefault(player.getUUID(), Collections.emptySet()).contains(id);

            default -> false;
        };
    }

    static void markSeen(ServerPlayer player, Entity source, ResourceLocation id, String mode) {
        mode = normalize(mode);

        switch (mode) {

            case "player" -> {
                CompoundTag seen = getPlayerSeen(player);

                seen.putBoolean(id.toString(), true);

                savePlayerSeen(player, seen);
            }

            case "entity" -> {
                if (source == null) {
                    return;
                }

                CompoundTag root = source.getPersistentData();
                CompoundTag seen = root.getCompound(ENTITY_TAG);
                seen.putBoolean(id.toString(), true);

                root.put(ENTITY_TAG, seen);
            }

            case "session" -> SESSION_SEEN.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(id);
        }
    }

    private static CompoundTag getPlayerSeen(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();

        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);

        return persisted.getCompound(PLAYER_TAG);
    }

    private static void savePlayerSeen(ServerPlayer player, CompoundTag seen) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(PLAYER_TAG, seen);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "never";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}