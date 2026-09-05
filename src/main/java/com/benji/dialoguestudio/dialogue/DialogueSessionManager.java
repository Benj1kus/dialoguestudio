package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.network.dialogueengine.DialogueNetwork;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class DialogueSessionManager {

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<SourceKey, LockState> LOCKS = new HashMap<>();

    private static final int MAX_GRAPH_AUTO_STEPS = 64;

    private DialogueSessionManager() {
    }

    public static boolean start(ServerPlayer player, Entity source, ResourceLocation dialogueId, Runnable onFinish) {
        return start(player, source, dialogueId, null, onFinish);
    }

    public static boolean start(ServerPlayer player, Entity source, ResourceLocation dialogueId, String onceOverride, Runnable onFinish) {
        DialogueDefinition definition = DialogueRegistry.get(dialogueId);

        if (definition == null || ACTIVE.containsKey(player.getUUID())) {
            return false;
        }

        boolean canRun = definition.hasGraph() || (definition.lines != null && !definition.lines.isEmpty());

        if (!canRun) {
            return false;
        }

        String once = onceOverride != null ? onceOverride : definition.once;

        if (DialogueOnceTracker.hasSeen(player, source, dialogueId, once)) {
            return false;
        }

        if (definition.exclusive_source && source != null && isSourceBusy(source)) {
            return false;
        }

        UUID sessionId = UUID.randomUUID();

        Session session = new Session(sessionId, player.getUUID(), dialogueId, definition, source, onFinish);

        ACTIVE.put(player.getUUID(), session);

        if (source != null && (definition.freeze_source || definition.source_invulnerable)) {
            lockSource(source, player, dialogueId, definition.freeze_source, definition.source_invulnerable);
        }

        DialogueOnceTracker.markSeen(player, source, dialogueId, once);
        DialogueNetwork.start(player, sessionId, dialogueId, DialogueRegistry.toJson(definition));

        if (definition.hasGraph()) {
            resolveGraph(player, session, definition.start_node);
        }

        return true;
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void finish(ServerPlayer player, UUID sessionId) {
        Session session = ACTIVE.get(player.getUUID());

        if (session == null || !session.sessionId.equals(sessionId)) {
            return;
        }

        if (session.definition.hasGraph() && !session.graphEnding) {
            return;
        }

        remove(player, session, true, false);
    }

    public static void cancel(ServerPlayer player) {
        Session session = ACTIVE.get(player.getUUID());

        if (session == null) {
            return;
        }

        remove(player, session, false, true);
    }

    public static void advanceNode(ServerPlayer player, UUID sessionId, String nodeId) {
        Session session = validGraphSession(player, sessionId, nodeId);

        if (session == null) {
            return;
        }

        DialogueDefinition.Node node = session.definition.nodes.get(nodeId);

        if (node == null || !"line".equalsIgnoreCase(node.type)) {
            resendCurrentNode(player, session);
            return;
        }

        resolveGraph(player, session, node.next);
    }

    public static void choose(ServerPlayer player, UUID sessionId, String nodeId, int choiceIndex) {
        Session session = validGraphSession(player, sessionId, nodeId);

        if (session == null) {
            return;
        }

        DialogueDefinition.Node node = session.definition.nodes.get(nodeId);

        if (node == null || !"choice".equalsIgnoreCase(node.type) || node.choices == null || choiceIndex < 0 || choiceIndex >= node.choices.size()) {

            resendCurrentNode(player, session);
            return;
        }

        DialogueDefinition.Choice choice = node.choices.get(choiceIndex);

        Entity source = session.resolveSource(player.getServer());

        boolean available = DialogueGraphLogic.conditionsPass(player, source, session.dialogueId, choice.conditions);

        if (!available) {
            resendCurrentNode(player, session);
            return;
        }

        DialogueGraphLogic.runActions(player, source, session.dialogueId, nodeId, choice.actions);
        if (ACTIVE.get(player.getUUID()) != session) {
            return;
        }

        resolveGraph(player, session, choice.goto_node);
    }

    private static void resolveGraph(ServerPlayer player, Session session, String requestedNode) {
        String nodeId = requestedNode;

        Entity source = session.resolveSource(player.getServer());

        for (int step = 0; step < MAX_GRAPH_AUTO_STEPS; step++) {

            if (nodeId == null || nodeId.isBlank()) {

                finishGraph(player, session);
                return;
            }

            DialogueDefinition.Node node = session.definition.nodes.get(nodeId);

            if (node == null) {
                finishGraph(player, session);
                return;
            }

            session.currentNode = nodeId;
            DialogueGraphLogic.runActions(player, source, session.dialogueId, nodeId, node.actions);

            if (ACTIVE.get(player.getUUID()) != session) {
                return;
            }

            String type = node.type != null ? node.type.toLowerCase(Locale.ROOT) : "line";

            switch (type) {
                case "line" -> {
                    DialogueNetwork.nodeState(player, session.sessionId, nodeId, List.of());
                    return;
                }

                case "choice" -> {
                    List<Boolean> enabled = choiceAvailability(player, source, session, node);

                    DialogueNetwork.nodeState(player, session.sessionId, nodeId, enabled);
                    return;
                }

                case "condition" -> {
                    boolean pass = DialogueGraphLogic.conditionsPass(player, source, session.dialogueId, node.conditions);

                    nodeId = pass ? node.next : node.else_node;
                }

                case "event" -> nodeId = node.next;

                case "end" -> {
                    finishGraph(player, session);
                    return;
                }

                default -> {
                    finishGraph(player, session);
                    return;
                }
            }
        }
        cancel(player);
    }


    private static List<Boolean> choiceAvailability(ServerPlayer player, Entity source, Session session, DialogueDefinition.Node node) {
        if (node.choices == null || node.choices.isEmpty()) {
            return List.of();
        }

        List<Boolean> result = new ArrayList<>(node.choices.size());

        for (DialogueDefinition.Choice choice : node.choices) {

            result.add(choice != null && DialogueGraphLogic.conditionsPass(player, source, session.dialogueId, choice.conditions));
        }

        return result;
    }


    private static Session validGraphSession(ServerPlayer player, UUID sessionId, String nodeId) {
        Session session = ACTIVE.get(player.getUUID());

        if (session == null || !session.sessionId.equals(sessionId) || !session.definition.hasGraph() || session.graphEnding || session.currentNode == null || nodeId == null || !session.currentNode.equals(nodeId)) {
            return null;
        }

        return session;
    }


    private static void resendCurrentNode(ServerPlayer player, Session session) {
        if (session.currentNode == null) {
            return;
        }

        DialogueDefinition.Node node = session.definition.nodes.get(session.currentNode);

        if (node == null) {
            return;
        }

        List<Boolean> enabled = "choice".equalsIgnoreCase(node.type) ? choiceAvailability(player, session.resolveSource(player.getServer()), session, node) : List.of();

        DialogueNetwork.nodeState(player, session.sessionId, session.currentNode, enabled);
    }


    private static void finishGraph(ServerPlayer player, Session session) {
        if (session.graphEnding) {
            return;
        }

        session.graphEnding = true;
        DialogueNetwork.nodeState(player, session.sessionId, "__oasiso_end__", List.of());
    }


    public static void tick(MinecraftServer server) {
        List<UUID> cancel = new ArrayList<>();

        for (Session session : ACTIVE.values()) {

            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);

            if (player == null) {
                cancel.add(session.playerId);
                continue;
            }

            Entity source = session.resolveSource(server);

            if (session.sourceId != null && source == null && session.definition.cancel_if_source_missing) {

                cancel.add(session.playerId);
            }
        }

        for (UUID playerId : cancel) {

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player != null) {
                cancel(player);
            } else {
                Session session = ACTIVE.remove(playerId);

                if (session != null) {
                    unlockSource(session, server);
                }
            }
        }

        for (LockState state : LOCKS.values()) {

            Entity source = state.source;

            if (source == null || source.isRemoved()) {
                continue;
            }

            if (state.freeze) {
                source.setDeltaMovement(Vec3.ZERO);

                source.fallDistance = 0.0F;

                if (source instanceof Mob mob) {
                    mob.getNavigation().stop();

                    mob.setTarget(null);
                }
            }
        }
    }


    private static void remove(ServerPlayer player, Session session, boolean runFinish, boolean sendStop) {
        ACTIVE.remove(player.getUUID());

        unlockSource(session, player.getServer());

        if (sendStop) {
            DialogueNetwork.stop(player, session.sessionId);
        }

        if (runFinish && session.onFinish != null) {

            session.onFinish.run();
        }
    }


    private static boolean isSourceBusy(Entity source) {
        SourceKey key = new SourceKey(source.level().dimension(), source.getUUID());

        LockState state = LOCKS.get(key);

        return state != null && state.count > 0;
    }


    private static void lockSource(Entity source, ServerPlayer viewer, ResourceLocation dialogueId, boolean freeze, boolean invulnerable) {
        SourceKey key = new SourceKey(source.level().dimension(), source.getUUID());

        LockState state = LOCKS.get(key);

        if (state == null) {
            state = new LockState(source);

            LOCKS.put(key, state);
        }

        state.count++;

        state.freeze |= freeze;
        state.invulnerable |= invulnerable;

        if (freeze) {
            source.setDeltaMovement(Vec3.ZERO);

            if (source instanceof Mob mob) {
                mob.setNoAi(true);

                mob.getNavigation().stop();

                mob.setTarget(null);
            }
        }

        if (invulnerable) {
            source.setInvulnerable(true);
        }

        if (source instanceof DialogueLockable lockable) {
            lockable.setDialogueLocked(true, viewer, dialogueId);
        }
    }


    private static void unlockSource(Session session, MinecraftServer server) {
        if (session.sourceId == null || session.sourceDimension == null) {
            return;
        }

        SourceKey key = new SourceKey(session.sourceDimension, session.sourceId);

        LockState state = LOCKS.get(key);

        if (state == null) {
            return;
        }

        state.count--;

        if (state.count > 0) {
            return;
        }

        LOCKS.remove(key);

        Entity source = state.source;

        if (source == null || source.isRemoved()) {
            return;
        }

        if (source instanceof Mob mob && state.oldNoAi != null) {

            mob.setNoAi(state.oldNoAi);
        }

        source.setInvulnerable(state.oldInvulnerable);

        if (source instanceof DialogueLockable lockable) {

            ServerPlayer viewer = server != null ? server.getPlayerList().getPlayer(session.playerId) : null;

            lockable.setDialogueLocked(false, viewer, session.dialogueId);
        }
    }


    private static final class Session {

        private final UUID sessionId;
        private final UUID playerId;

        private final ResourceLocation dialogueId;
        private final DialogueDefinition definition;

        private final UUID sourceId;
        private final ResourceKey<Level> sourceDimension;

        private final Runnable onFinish;

        private String currentNode;
        private boolean graphEnding;

        private Session(UUID sessionId, UUID playerId, ResourceLocation dialogueId, DialogueDefinition definition, Entity source, Runnable onFinish) {
            this.sessionId = sessionId;

            this.playerId = playerId;

            this.dialogueId = dialogueId;

            this.definition = definition;

            this.onFinish = onFinish;

            if (source != null) {
                this.sourceId = source.getUUID();

                this.sourceDimension = source.level().dimension();
            } else {
                this.sourceId = null;

                this.sourceDimension = null;
            }
        }

        private Entity resolveSource(MinecraftServer server) {
            if (sourceId == null || sourceDimension == null || server == null) {
                return null;
            }

            ServerLevel level = server.getLevel(sourceDimension);

            if (level == null) {
                return null;
            }

            return level.getEntity(sourceId);
        }
    }


    private static final class LockState {

        private final Entity source;
        private final Boolean oldNoAi;
        private final boolean oldInvulnerable;

        private int count;
        private boolean freeze;
        private boolean invulnerable;

        private LockState(Entity source) {
            this.source = source;

            this.oldInvulnerable = source.isInvulnerable();

            this.oldNoAi = source instanceof Mob mob ? mob.isNoAi() : null;
        }
    }


    private record SourceKey(ResourceKey<Level> dimension, UUID entityId) {
    }
}
