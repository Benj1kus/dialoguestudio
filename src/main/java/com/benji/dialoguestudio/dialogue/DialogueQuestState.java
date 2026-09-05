package com.benji.dialoguestudio.dialogue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class DialogueQuestState {

    private static final String ROOT = "oasiso_dialogue_quests";

    private DialogueQuestState() {
    }

    public static String get(ServerPlayer player, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return "not_started";
        }

        CompoundTag persistent = player.getPersistentData();

        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            return "not_started";
        }

        CompoundTag quests = persistent.getCompound(ROOT);

        if (!quests.contains(questId, Tag.TAG_STRING)) {
            return "not_started";
        }

        String state = normalize(quests.getString(questId));
        return state.isBlank() ? "not_started" : state;
    }

    public static void set(ServerPlayer player, String questId, String state) {
        if (player == null || questId == null || questId.isBlank()) {
            return;
        }

        String normalized = normalize(state);

        if ("not_started".equals(normalized) || normalized.isBlank()) {
            reset(player, questId);
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        CompoundTag quests = persistent.contains(ROOT, Tag.TAG_COMPOUND) ? persistent.getCompound(ROOT) : new CompoundTag();

        quests.putString(questId, normalized);
        persistent.put(ROOT, quests);
    }

    public static void reset(ServerPlayer player, String questId) {
        if (player == null || questId == null || questId.isBlank()) {
            return;
        }

        CompoundTag persistent = player.getPersistentData();

        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag quests = persistent.getCompound(ROOT);
        quests.remove(questId);
        persistent.put(ROOT, quests);
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from == null || to == null) {
            return;
        }

        CompoundTag sourceData = from.getPersistentData();

        if (!sourceData.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }

        to.getPersistentData().put(ROOT, sourceData.getCompound(ROOT).copy());
    }

    public static boolean matches(ServerPlayer player, String questId, String expected) {
        String actual = get(player, questId);
        String wanted = normalize(expected);

        if ("started".equals(wanted)) {
            return !"not_started".equals(actual);
        }

        return actual.equals(wanted);
    }

    private static String normalize(String state) {
        if (state == null || state.isBlank()) {
            return "not_started";
        }

        String value = state.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "active", "completed", "failed", "not_started", "started" -> value;
            default -> value;
        };
    }
}
