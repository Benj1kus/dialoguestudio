package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;

import java.util.*;

public class DialogueEditorProject {

    public String workspace_id = UUID.randomUUID().toString();
    public String project_name = "New Dialogue";
    public String namespace = "mydialogues";
    public String dialogue_path = "dialogue_1";
    public String preview_locale = "en_us";

    public DialogueDefinition definition = new DialogueDefinition();

    public Map<String, LinkedHashMap<String, String>> languages = new LinkedHashMap<>();
    public Map<String, String> sounds = new LinkedHashMap<>();
    public Map<String, FontAsset> fonts = new LinkedHashMap<>();

    public int selected_line = 0;
    public int selected_trigger = 0;
    public boolean animate_preview = true;

    public String selected_node;
    public Map<String, NodePosition> node_positions = new LinkedHashMap<>();


    public static DialogueEditorProject createDefault() {
        DialogueEditorProject project = new DialogueEditorProject();

        DialogueDefinition.Line line = new DialogueDefinition.Line();
        line.literal = "Hello! This is a new dialogue.";
        line.sprite = "dlgstd:textures/gui/dialogue/default_sprite.png";
        project.definition.lines.add(line);

        DialogueDefinition.Trigger trigger = new DialogueDefinition.Trigger();
        trigger.type = "manual";
        project.definition.triggers.add(trigger);

        project.normalize();
        return project;
    }


    public void normalize() {
        if (workspace_id == null || workspace_id.isBlank()) {
            workspace_id = UUID.randomUUID().toString();
        }

        if (project_name == null || project_name.isBlank()) {
            project_name = "Dialogue Project";
        }

        namespace = sanitizeNamespace(namespace);
        dialogue_path = sanitizePath(dialogue_path);

        if (preview_locale == null || preview_locale.isBlank()) {
            preview_locale = "en_us";
        }

        if (definition == null) {
            definition = new DialogueDefinition();
        }

        if (definition.layout == null) {
            definition.layout = new DialogueDefinition.Layout();
        }

        if (definition.lines == null) {
            definition.lines = new ArrayList<>();
        }

        if (definition.triggers == null) {
            definition.triggers = new ArrayList<>();
        }

        if (definition.nodes == null) {
            definition.nodes = new LinkedHashMap<>();
        }

        if (definition.lines.isEmpty() && !definition.hasGraph()) {
            DialogueDefinition.Line line = new DialogueDefinition.Line();
            line.literal = "New dialogue line";
            line.sprite = "dlgstd:textures/gui/dialogue/default_sprite.png";
            definition.lines.add(line);
        }

        if (definition.triggers.isEmpty()) {
            DialogueDefinition.Trigger trigger = new DialogueDefinition.Trigger();
            trigger.type = "manual";
            definition.triggers.add(trigger);
        }

        if (languages == null) {
            languages = new LinkedHashMap<>();
        }

        if (sounds == null) {
            sounds = new LinkedHashMap<>();
        }

        if (fonts == null) {
            fonts = new LinkedHashMap<>();
        }

        fonts.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (FontAsset asset : fonts.values()) {
            if (asset.chars == null) asset.chars = new ArrayList<>();
            if (asset.type == null || asset.type.isBlank()) asset.type = "ttf";
        }

        if (node_positions == null) {
            node_positions = new LinkedHashMap<>();
        }

        selected_line = definition.lines.isEmpty() ? 0 : Math.max(0, Math.min(selected_line, definition.lines.size() - 1));

        selected_trigger = Math.max(0, Math.min(selected_trigger, definition.triggers.size() - 1));

        for (DialogueDefinition.Line line : definition.lines) {
            normalizeRichLine(line);
        }

        for (DialogueDefinition.Trigger trigger : definition.triggers) {
            normalizeZoneVisual(trigger);
        }

        normalizeGraph();

        for (DialogueDefinition.Node node : definition.nodes.values()) {
            if (node != null) {
                normalizeRichLine(node.line);
            }
        }
    }


    private static void normalizeZoneVisual(DialogueDefinition.Trigger trigger) {
        if (trigger == null) {
            return;
        }

        if (trigger.visual == null) {
            trigger.visual = new DialogueDefinition.ZoneVisual();
        }

        DialogueDefinition.ZoneVisual visual = trigger.visual;

        if (visual.style == null || visual.style.isBlank()) visual.style = "auto";
        if (visual.color == null || visual.color.isBlank()) visual.color = "cyan";
        if (visual.texture_mode == null || visual.texture_mode.isBlank()) visual.texture_mode = "plane";
        if (visual.texture_fit == null || visual.texture_fit.isBlank()) visual.texture_fit = "stretch";
        if (visual.fill_mode == null || visual.fill_mode.isBlank()) visual.fill_mode = "gradient";
        if (visual.fill_color_bottom == null || visual.fill_color_bottom.isBlank()) visual.fill_color_bottom = "cyan";
        if (visual.fill_color_top == null || visual.fill_color_top.isBlank()) visual.fill_color_top = "cyan";
        if (visual.preset == null || visual.preset.isBlank()) visual.preset = "custom";

        visual.alpha = Math.max(0.0F, Math.min(1.0F, visual.alpha));
        visual.fill_alpha_bottom = Math.max(0.0F, Math.min(1.0F, visual.fill_alpha_bottom));
        visual.fill_alpha_top = Math.max(0.0F, Math.min(1.0F, visual.fill_alpha_top));
        visual.texture_repeat_x = Math.max(0.01D, visual.texture_repeat_x);
        visual.texture_repeat_y = Math.max(0.01D, visual.texture_repeat_y);
        visual.texture_scale_x = Math.max(0.05D, visual.texture_scale_x);
        visual.texture_scale_y = Math.max(0.05D, visual.texture_scale_y);
        if (!Double.isFinite(visual.texture_offset_x)) visual.texture_offset_x = 0.0D;
        if (!Double.isFinite(visual.texture_offset_y)) visual.texture_offset_y = 0.0D;
        if (!Double.isFinite(visual.texture_offset_z)) visual.texture_offset_z = 0.0D;
        if (!Double.isFinite(visual.texture_rotation)) visual.texture_rotation = 0.0D;
        visual.pulse_amplitude = Math.max(0.0D, visual.pulse_amplitude);
        visual.pulse_speed = Math.max(0.0D, visual.pulse_speed);
        visual.bob_amplitude = Math.max(0.0D, visual.bob_amplitude);
        visual.bob_speed = Math.max(0.0D, visual.bob_speed);
        visual.alpha_breathe_amount = Math.max(0.0D, Math.min(1.0D, visual.alpha_breathe_amount));
        visual.alpha_breathe_speed = Math.max(0.0D, visual.alpha_breathe_speed);
        visual.preview_distance = Math.max(1.0D, visual.preview_distance);
    }

    private static void normalizeRichLine(DialogueDefinition.Line line) {
        if (line == null || line.rich_regions == null) {
            return;
        }

        line.rich_regions.removeIf(region -> region == null);

        for (DialogueDefinition.TextRegion region : line.rich_regions) {
            region.start = Math.max(0, region.start);
            region.end = Math.max(region.start, region.end);

            if (region.animation == null) {
                region.animation = new DialogueDefinition.TextAnimation();
            }
        }
    }


    private void normalizeGraph() {
        if (definition.nodes == null) {
            definition.nodes = new LinkedHashMap<>();
        }
        node_positions.keySet().removeIf(id -> !definition.nodes.containsKey(id));

        int autoIndex = 0;

        for (String nodeId : definition.nodes.keySet()) {
            if (!node_positions.containsKey(nodeId)) {
                int column = autoIndex % 4;
                int row = autoIndex / 4;

                node_positions.put(nodeId, new NodePosition(40 + column * 190, 60 + row * 120));
            }

            DialogueDefinition.Node node = definition.nodes.get(nodeId);

            if (node == null) {
                node = new DialogueDefinition.Node();
                definition.nodes.put(nodeId, node);
            }

            if (node.choices == null) {
                node.choices = new ArrayList<>();
            }

            if (node.conditions == null) {
                node.conditions = new ArrayList<>();
            }

            if (node.actions == null) {
                node.actions = new ArrayList<>();
            }

            autoIndex++;
        }

        if (definition.graph_enabled && !definition.nodes.isEmpty()) {

            if (definition.start_node == null || !definition.nodes.containsKey(definition.start_node)) {
                definition.start_node = definition.nodes.keySet().iterator().next();
            }
        }

        if (selected_node == null || !definition.nodes.containsKey(selected_node)) {

            if (definition.start_node != null && definition.nodes.containsKey(definition.start_node)) {
                selected_node = definition.start_node;
            } else if (!definition.nodes.isEmpty()) {
                selected_node = definition.nodes.keySet().iterator().next();
            } else {
                selected_node = null;
            }
        }
    }


    public DialogueDefinition.Line currentLine() {
        normalize();

        if (definition.lines.isEmpty()) {
            DialogueDefinition.Line line = new DialogueDefinition.Line();
            line.literal = "New dialogue line";
            definition.lines.add(line);
            selected_line = 0;
        }

        return definition.lines.get(selected_line);
    }

    public DialogueDefinition.Line previewLine() {
        normalize();

        if (definition.graph_enabled && selected_node != null) {

            DialogueDefinition.Node node = definition.nodes.get(selected_node);

            if (node != null && node.line != null) {
                return node.line;
            }
        }

        return currentLine();
    }


    public DialogueDefinition.Node currentNode() {
        normalize();

        return selected_node != null ? definition.nodes.get(selected_node) : null;
    }


    public DialogueDefinition.Trigger currentTrigger() {
        normalize();

        return definition.triggers.get(selected_trigger);
    }


    public String dialogueId() {
        return namespace + ":" + dialogue_path;
    }


    public String autoLangKey(int lineIndex) {
        return "dialogue." + namespace + "." + dialogue_path.replace('/', '.') + "." + (lineIndex + 1);
    }


    public String autoNodeLangKey(String nodeId) {
        return "dialogue." + namespace + "." + dialogue_path.replace('/', '.') + ".node." + sanitizeNodeId(nodeId);
    }


    public String autoChoiceLangKey(String nodeId, int choiceIndex) {
        return autoNodeLangKey(nodeId) + ".choice." + (choiceIndex + 1);
    }


    public String ensureLangKey(DialogueDefinition.Line line, int lineIndex) {
        if (line.text == null || line.text.isBlank()) {
            line.text = autoLangKey(lineIndex);
        }

        return line.text;
    }


    public String ensureNodeLangKey(DialogueDefinition.Line line, String nodeId) {
        if (line.text == null || line.text.isBlank()) {
            line.text = autoNodeLangKey(nodeId);
        }

        return line.text;
    }


    public String ensureChoiceLangKey(DialogueDefinition.Choice choice, String nodeId, int choiceIndex) {
        if (choice.text == null || choice.text.isBlank()) {
            choice.text = autoChoiceLangKey(nodeId, choiceIndex);
        }

        return choice.text;
    }


    public String getLocalizedText(String locale, DialogueDefinition.Line line, int lineIndex) {
        String key = ensureLangKey(line, lineIndex);

        return languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).getOrDefault(key, "");
    }


    public String getLocalizedNodeText(String locale, DialogueDefinition.Line line, String nodeId) {
        String key = ensureNodeLangKey(line, nodeId);

        return languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).getOrDefault(key, "");
    }


    public String getLocalizedChoiceText(String locale, DialogueDefinition.Choice choice, String nodeId, int choiceIndex) {
        String key = ensureChoiceLangKey(choice, nodeId, choiceIndex);

        return languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).getOrDefault(key, "");
    }


    public void setLocalizedText(String locale, DialogueDefinition.Line line, int lineIndex, String value) {
        String key = ensureLangKey(line, lineIndex);

        languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(key, value != null ? value : "");
    }


    public void setLocalizedNodeText(String locale, DialogueDefinition.Line line, String nodeId, String value) {
        String key = ensureNodeLangKey(line, nodeId);

        languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(key, value != null ? value : "");
    }


    public void setLocalizedChoiceText(String locale, DialogueDefinition.Choice choice, String nodeId, int choiceIndex, String value) {
        String key = ensureChoiceLangKey(choice, nodeId, choiceIndex);

        languages.computeIfAbsent(locale, ignored -> new LinkedHashMap<>()).put(key, value != null ? value : "");
    }

    public void convertLegacyLinesToGraph() {
        normalize();

        definition.nodes.clear();
        node_positions.clear();

        String previousId = null;

        for (int i = 0; i < definition.lines.size(); i++) {

            String id = uniqueNodeId("line_" + (i + 1));

            DialogueDefinition.Node node = new DialogueDefinition.Node();

            node.type = "line";
            node.line = copyLine(definition.lines.get(i));

            definition.nodes.put(id, node);

            node_positions.put(id, new NodePosition(50 + i * 190, 80));

            if (previousId != null) {
                definition.nodes.get(previousId).next = id;
            } else {
                definition.start_node = id;
            }

            previousId = id;
        }

        String endId = uniqueNodeId("end");

        DialogueDefinition.Node end = new DialogueDefinition.Node();

        end.type = "end";

        definition.nodes.put(endId, end);

        node_positions.put(endId, new NodePosition(50 + definition.lines.size() * 190, 80));

        if (previousId != null) {
            definition.nodes.get(previousId).next = endId;
        } else {
            definition.start_node = endId;
        }

        definition.graph_enabled = true;
        definition.format = Math.max(3, definition.format);

        selected_node = definition.start_node;

        normalizeGraph();
    }


    public String addNode(String requestedType) {
        normalize();

        String type = requestedType != null ? requestedType.toLowerCase(Locale.ROOT) : "line";

        String id = uniqueNodeId(type);

        DialogueDefinition.Node node = new DialogueDefinition.Node();

        node.type = type;

        if ("line".equals(type) || "choice".equals(type)) {

            node.line = new DialogueDefinition.Line();

            node.line.literal = "choice".equals(type) ? "What will you do?" : "New node line";

            node.line.sprite = previewLine() != null ? previewLine().sprite : "dlgstd:textures/gui/dialogue/default_sprite.png";
        }

        if ("choice".equals(type)) {
            DialogueDefinition.Choice choiceA = new DialogueDefinition.Choice();

            choiceA.literal = "Choice 1";

            DialogueDefinition.Choice choiceB = new DialogueDefinition.Choice();

            choiceB.literal = "Choice 2";

            node.choices.add(choiceA);
            node.choices.add(choiceB);
        }

        if ("condition".equals(type)) {
            node.conditions.add(new DialogueDefinition.Condition());
        }

        if ("event".equals(type)) {
        }

        definition.nodes.put(id, node);

        int index = definition.nodes.size() - 1;

        node_positions.put(id, new NodePosition(60 + (index % 4) * 190, 70 + (index / 4) * 120));

        if (definition.start_node == null) {
            definition.start_node = id;
        }

        definition.graph_enabled = true;
        definition.format = Math.max(3, definition.format);

        selected_node = id;

        return id;
    }


    public boolean renameNode(String oldId, String requestedId) {
        normalize();

        if (oldId == null || !definition.nodes.containsKey(oldId)) {
            return false;
        }

        String newId = sanitizeNodeId(requestedId);

        if (newId.isBlank() || oldId.equals(newId) || definition.nodes.containsKey(newId)) {
            return false;
        }

        LinkedHashMap<String, DialogueDefinition.Node> rewritten = new LinkedHashMap<>();

        for (Map.Entry<String, DialogueDefinition.Node> entry : definition.nodes.entrySet()) {

            rewritten.put(entry.getKey().equals(oldId) ? newId : entry.getKey(), entry.getValue());
        }

        definition.nodes = rewritten;

        for (DialogueDefinition.Node node : definition.nodes.values()) {

            node.next = replaceNodeRef(node.next, oldId, newId);

            node.else_node = replaceNodeRef(node.else_node, oldId, newId);

            if (node.choices != null) {
                for (DialogueDefinition.Choice choice : node.choices) {

                    choice.goto_node = replaceNodeRef(choice.goto_node, oldId, newId);
                }
            }
        }

        if (oldId.equals(definition.start_node)) {
            definition.start_node = newId;
        }

        NodePosition position = node_positions.remove(oldId);

        if (position != null) {
            node_positions.put(newId, position);
        }

        if (oldId.equals(selected_node)) {
            selected_node = newId;
        }

        rewriteNodeLangPrefix(oldId, newId);

        return true;
    }


    public void deleteNode(String nodeId) {
        if (nodeId == null) {
            return;
        }

        definition.nodes.remove(nodeId);
        node_positions.remove(nodeId);

        for (DialogueDefinition.Node node : definition.nodes.values()) {

            if (nodeId.equals(node.next)) {
                node.next = null;
            }

            if (nodeId.equals(node.else_node)) {
                node.else_node = null;
            }

            if (node.choices != null) {
                for (DialogueDefinition.Choice choice : node.choices) {

                    if (nodeId.equals(choice.goto_node)) {
                        choice.goto_node = null;
                    }
                }
            }
        }

        if (nodeId.equals(definition.start_node)) {
            definition.start_node = definition.nodes.isEmpty() ? null : definition.nodes.keySet().iterator().next();
        }

        selected_node = definition.start_node;

        if (definition.nodes.isEmpty()) {
            definition.graph_enabled = false;
        }

        normalizeGraph();
    }


    public String uniqueNodeId(String base) {
        base = sanitizeNodeId(base);

        if (base.isBlank()) {
            base = "node";
        }

        String candidate = base;

        int suffix = 2;

        while (definition.nodes.containsKey(candidate)) {
            candidate = base + "_" + suffix++;
        }

        return candidate;
    }


    public void autoLayoutNodes() {
        normalize();

        int i = 0;

        for (String id : definition.nodes.keySet()) {

            int column = i % 4;
            int row = i / 4;

            node_positions.put(id, new NodePosition(50 + column * 190, 70 + row * 120));

            i++;
        }
    }


    public void rewriteDialoguePath(String oldPath, String newPath) {
        oldPath = sanitizePath(oldPath);
        newPath = sanitizePath(newPath);

        if (oldPath.equals(newPath)) {
            return;
        }

        String oldPrefix = "dialogue." + namespace + "." + oldPath.replace('/', '.') + ".";

        String newPrefix = "dialogue." + namespace + "." + newPath.replace('/', '.') + ".";

        rewriteLanguagePrefix(oldPrefix, newPrefix);

        dialogue_path = newPath;
    }


    public void rewriteNamespace(String oldNamespace, String newNamespace) {
        oldNamespace = sanitizeNamespace(oldNamespace);
        newNamespace = sanitizeNamespace(newNamespace);

        if (oldNamespace.equals(newNamespace)) {
            return;
        }

        definition.frame = replacePrefix(definition.frame, oldNamespace, newNamespace);

        definition.background = replacePrefix(definition.background, oldNamespace, newNamespace);

        definition.voice = replacePrefix(definition.voice, oldNamespace, newNamespace);
        definition.text_font = replacePrefix(definition.text_font, oldNamespace, newNamespace);

        for (DialogueDefinition.Line line : definition.lines) {
            rewriteLineNamespace(line, oldNamespace, newNamespace);
        }

        for (DialogueDefinition.Node node : definition.nodes.values()) {

            if (node.line != null) {
                rewriteLineNamespace(node.line, oldNamespace, newNamespace);
            }
        }

        for (DialogueDefinition.Trigger trigger : definition.triggers) {

            if (trigger.visual != null) {
                trigger.visual.texture = replacePrefix(trigger.visual.texture, oldNamespace, newNamespace);
            }
        }

        String oldPrefix = "dialogue." + oldNamespace + ".";

        String newPrefix = "dialogue." + newNamespace + ".";

        rewriteLanguagePrefix(oldPrefix, newPrefix);

        namespace = newNamespace;
    }


    private void rewriteLanguagePrefix(String oldPrefix, String newPrefix) {
        Map<String, LinkedHashMap<String, String>> rewritten = new LinkedHashMap<>();

        for (Map.Entry<String, LinkedHashMap<String, String>> locale : languages.entrySet()) {

            LinkedHashMap<String, String> map = new LinkedHashMap<>();

            for (Map.Entry<String, String> entry : locale.getValue().entrySet()) {

                String key = entry.getKey().startsWith(oldPrefix) ? newPrefix + entry.getKey().substring(oldPrefix.length()) : entry.getKey();

                map.put(key, entry.getValue());
            }

            rewritten.put(locale.getKey(), map);
        }

        languages = rewritten;

        for (DialogueDefinition.Line line : definition.lines) {

            if (line.text != null && line.text.startsWith(oldPrefix)) {
                line.text = newPrefix + line.text.substring(oldPrefix.length());
            }
        }

        for (DialogueDefinition.Node node : definition.nodes.values()) {

            if (node.line != null && node.line.text != null && node.line.text.startsWith(oldPrefix)) {
                node.line.text = newPrefix + node.line.text.substring(oldPrefix.length());
            }

            if (node.choices != null) {
                for (DialogueDefinition.Choice choice : node.choices) {

                    if (choice.text != null && choice.text.startsWith(oldPrefix)) {
                        choice.text = newPrefix + choice.text.substring(oldPrefix.length());
                    }
                }
            }
        }
    }


    private void rewriteNodeLangPrefix(String oldId, String newId) {
        String oldPrefix = autoNodeLangKey(oldId);

        String newPrefix = autoNodeLangKey(newId);

        rewriteLanguagePrefix(oldPrefix, newPrefix);
    }


    private static void rewriteLineNamespace(DialogueDefinition.Line line, String oldNamespace, String newNamespace) {
        if (line == null) return;

        line.sprite = replacePrefix(line.sprite, oldNamespace, newNamespace);

        line.frame = replacePrefix(line.frame, oldNamespace, newNamespace);

        line.background = replacePrefix(line.background, oldNamespace, newNamespace);

        line.voice = replacePrefix(line.voice, oldNamespace, newNamespace);
        line.text_font = replacePrefix(line.text_font, oldNamespace, newNamespace);

        if (line.rich_regions != null) {
            for (DialogueDefinition.TextRegion region : line.rich_regions) {
                if (region != null) {
                    region.font = replacePrefix(region.font, oldNamespace, newNamespace);
                }
            }
        }
    }


    private static DialogueDefinition.Line copyLine(DialogueDefinition.Line source) {
        DialogueDefinition.Line line = new DialogueDefinition.Line();

        if (source == null) {
            return line;
        }

        line.text = source.text;
        line.literal = source.literal;
        line.sprite = source.sprite;

        line.char_ticks = source.char_ticks;
        line.hold_ticks = source.hold_ticks;

        line.voice = source.voice;
        line.voice_source = source.voice_source;
        line.voice_pitch = source.voice_pitch;
        line.voice_volume = source.voice_volume;
        line.voice_every = source.voice_every;

        line.text_color = source.text_color;
        line.text_gradient = source.text_gradient != null ? new ArrayList<>(source.text_gradient) : null;

        line.text_effect = source.text_effect;
        line.text_effects = source.text_effects != null ? new ArrayList<>(source.text_effects) : null;

        line.text_style = source.text_style;

        line.markdown = source.markdown;
        line.text_font = source.text_font;
        line.text_outline_color = source.text_outline_color;
        line.text_outline_gradient = source.text_outline_gradient != null ? new ArrayList<>(source.text_outline_gradient) : null;
        line.text_outline_thickness = source.text_outline_thickness;

        if (source.rich_regions != null) {
            line.rich_regions = new ArrayList<>();

            for (DialogueDefinition.TextRegion sourceRegion : source.rich_regions) {
                if (sourceRegion == null) continue;

                DialogueDefinition.TextRegion region = new DialogueDefinition.TextRegion();

                region.name = sourceRegion.name;
                region.start = sourceRegion.start;
                region.end = sourceRegion.end;
                region.match = sourceRegion.match;
                region.locale = sourceRegion.locale;

                region.color = sourceRegion.color;
                region.gradient = sourceRegion.gradient != null ? new ArrayList<>(sourceRegion.gradient) : null;

                region.effects = sourceRegion.effects != null ? new ArrayList<>(sourceRegion.effects) : null;

                region.bold = sourceRegion.bold;
                region.italic = sourceRegion.italic;
                region.underline = sourceRegion.underline;
                region.strikethrough = sourceRegion.strikethrough;
                region.font = sourceRegion.font;
                region.outline_color = sourceRegion.outline_color;
                region.outline_gradient = sourceRegion.outline_gradient != null ? new ArrayList<>(sourceRegion.outline_gradient) : null;
                region.outline_thickness = sourceRegion.outline_thickness;

                if (sourceRegion.animation != null) {
                    region.animation = new DialogueDefinition.TextAnimation();

                    region.animation.wave_amplitude = sourceRegion.animation.wave_amplitude;
                    region.animation.wave_speed = sourceRegion.animation.wave_speed;
                    region.animation.wave_frequency = sourceRegion.animation.wave_frequency;
                    region.animation.shake_strength = sourceRegion.animation.shake_strength;
                    region.animation.explode_amount = sourceRegion.animation.explode_amount;
                    region.animation.explode_ticks = sourceRegion.animation.explode_ticks;
                    region.animation.slide_distance = sourceRegion.animation.slide_distance;
                    region.animation.slide_ticks = sourceRegion.animation.slide_ticks;
                }

                line.rich_regions.add(region);
            }
        }

        line.frame = source.frame;
        line.background = source.background;

        line.sprite_position = source.sprite_position;
        line.sprite_x = source.sprite_x;
        line.sprite_transition = source.sprite_transition;

        line.sprite_move_ticks = source.sprite_move_ticks;
        line.sprite_transition_ticks = source.sprite_transition_ticks;

        line.sprite_width = source.sprite_width;
        line.sprite_height = source.sprite_height;

        return line;
    }


    private static String replaceNodeRef(String value, String oldId, String newId) {
        return oldId.equals(value) ? newId : value;
    }


    private static String replacePrefix(String value, String oldNamespace, String newNamespace) {
        if (value == null) {
            return null;
        }

        String prefix = oldNamespace + ":";

        return value.startsWith(prefix) ? newNamespace + ":" + value.substring(prefix.length()) : value;
    }


    public static String sanitizeNamespace(String value) {
        if (value == null) {
            return "mydialogues";
        }

        value = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");

        return value.isBlank() ? "mydialogues" : value;
    }


    public static String sanitizePath(String value) {
        if (value == null) {
            return "dialogue_1";
        }

        value = value.toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll("[^a-z0-9_./-]", "_");

        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        return value.isBlank() ? "dialogue_1" : value;
    }


    public static String sanitizeNodeId(String value) {
        if (value == null) {
            return "node";
        }

        value = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");

        return value.isBlank() ? "node" : value;
    }


    public static final class FontAsset {
        public String type = "ttf";
        public String file;
        public float size = 11.0F;
        public float oversample = 2.0F;
        public int height = 11;
        public int ascent = 9;
        public List<String> chars = new ArrayList<>();
    }


    public static final class NodePosition {
        public double x;
        public double y;

        public NodePosition() {
        }

        public NodePosition(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
