package com.benji.dialoguestudio.dialogue.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class DialogueEditorHistory {

    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_HISTORY = 80;
    private static final int COMMIT_DELAY_TICKS = 8;

    private static final Map<String, State> STATES = new HashMap<>();

    private DialogueEditorHistory() {
    }

    public static void watch(DialogueEditorProject project) {
        if (project == null) return;
        project.normalize();

        State state = state(project);
        String snapshot = GSON.toJson(project);

        if (state.current == null) {
            state.current = snapshot;
            return;
        }

        if (snapshot.equals(state.current)) {
            state.pending = null;
            state.pendingTicks = 0;
            return;
        }

        if (!snapshot.equals(state.pending)) {
            state.pending = snapshot;
            state.pendingTicks = 0;
            return;
        }

        state.pendingTicks++;
        if (state.pendingTicks >= COMMIT_DELAY_TICKS) {
            commitPending(state);
        }
    }

    public static DialogueEditorProject undo(DialogueEditorProject project) {
        if (project == null) return null;
        State state = state(project);
        syncCurrent(state, project);

        if (state.undo.isEmpty()) return null;

        state.redo.push(state.current);
        state.current = state.undo.pop();
        state.pending = null;
        state.pendingTicks = 0;
        return decode(state.current);
    }

    public static DialogueEditorProject redo(DialogueEditorProject project) {
        if (project == null) return null;
        State state = state(project);
        syncCurrent(state, project);

        if (state.redo.isEmpty()) return null;

        state.undo.push(state.current);
        trim(state.undo);
        state.current = state.redo.pop();
        state.pending = null;
        state.pendingTicks = 0;
        return decode(state.current);
    }

    public static void checkpoint(DialogueEditorProject project) {
        if (project == null) return;
        State state = state(project);
        syncCurrent(state, project);
    }

    private static State state(DialogueEditorProject project) {
        return STATES.computeIfAbsent(project.workspace_id, ignored -> new State());
    }

    private static void syncCurrent(State state, DialogueEditorProject project) {
        String snapshot = GSON.toJson(project);

        if (state.current == null) {
            state.current = snapshot;
            return;
        }

        if (!snapshot.equals(state.current)) {
            state.pending = snapshot;
            commitPending(state);
        }
    }

    private static void commitPending(State state) {
        if (state.pending == null || state.pending.equals(state.current)) {
            state.pending = null;
            state.pendingTicks = 0;
            return;
        }

        state.undo.push(state.current);
        trim(state.undo);
        state.current = state.pending;
        state.pending = null;
        state.pendingTicks = 0;
        state.redo.clear();
    }

    private static void trim(Deque<String> deque) {
        while (deque.size() > MAX_HISTORY) deque.removeLast();
    }

    private static DialogueEditorProject decode(String json) {
        DialogueEditorProject project = GSON.fromJson(json, DialogueEditorProject.class);
        if (project != null) project.normalize();
        return project;
    }

    private static final class State {
        private final Deque<String> undo = new ArrayDeque<>();
        private final Deque<String> redo = new ArrayDeque<>();
        private String current;
        private String pending;
        private int pendingTicks;
    }
}
