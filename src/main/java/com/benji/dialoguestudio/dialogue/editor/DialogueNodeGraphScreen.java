package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DialogueNodeGraphScreen extends DialogueRetroScreen {

    private static final int TOOLBAR_H = 30;
    private static final int INFO_H = 24;
    private static final int CANVAS_TOP = TOOLBAR_H + INFO_H;
    private static final int INSPECTOR_W = 282;

    private static final double NODE_W = 196.0D;
    private static final double MIN_ZOOM = 0.20D;
    private static final double MAX_ZOOM = 1.80D;

    private final DialogueEditorProject project;
    private final Screen parent;

    private double panX = 18.0D;
    private double panY = 12.0D;
    private double zoom = 1.0D;

    private String draggingNode;
    private double dragOffsetX;
    private double dragOffsetY;

    private boolean panning;
    private double lastMouseX;
    private double lastMouseY;

    private long lastClickTime;
    private String lastClickNode;

    private PortRef pendingLink;
    private int renderMouseX;
    private int renderMouseY;

    private final Map<String, NodeJuiceState> nodeJuiceStates = new HashMap<>();

    public DialogueNodeGraphScreen(Screen parent, DialogueEditorProject project) {
        super(Component.literal("Dialogue Studio - Node Graph"));
        this.parent = parent;
        this.project = project;
        this.project.normalize();
    }

    @Override
    protected void init() {
        int x = 6;
        int y = 5;

        x = addTopButton(x, y, 54, "Back", this::saveAndBack);
        x = addTopButton(x, y, 58, "+ Line", () -> addNode("line"));
        x = addTopButton(x, y, 72, "+ Choice", () -> addNode("choice"));
        x = addTopButton(x, y, 84, "+ Condition", () -> addNode("condition"));
        x = addTopButton(x, y, 70, "+ Actions", () -> addNode("event"));
        x = addTopButton(x, y, 56, "+ End", () -> addNode("end"));
        x = addTopButton(x, y, 52, "Fit", this::fitGraph);

        int right = width - INSPECTOR_W + 8;
        int buttonW = INSPECTOR_W - 16;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Edit selected"), b -> openSelectedEditor()).bounds(right, height - 72, buttonW, 20).build());

        int half = (buttonW - 4) / 2;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Set START"), b -> {
            if (project.selected_node != null) {
                project.definition.start_node = project.selected_node;
                project.definition.graph_enabled = true;
            }
        }).bounds(right, height - 48, half, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Delete"), b -> {
            if (project.selected_node != null) {
                project.deleteNode(project.selected_node);
                rebuild();
            }
        }).bounds(right + half + 4, height - 48, half, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Auto layout"), b -> {
            autoLayoutReadable();
            fitGraph();
        }).bounds(right, height - 24, buttonW, 20).build());
    }

    private int addTopButton(int x, int y, int w, String text, Runnable action) {
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build());
        return x + w + 4;
    }

    private void addNode(String type) {
        String id = project.addNode(type);
        DialogueEditorProject.NodePosition p = project.node_positions.get(id);

        if (p != null) {
            double cx = (width - INSPECTOR_W) * 0.5D;
            double cy = CANVAS_TOP + (height - CANVAS_TOP) * 0.5D;

            p.x = worldX(cx) - NODE_W * 0.5D;
            p.y = worldY(cy) - nodeHeightWorld(project.definition.nodes.get(id)) * 0.5D;
        }

        rebuild();
    }

    private void openSelectedEditor() {
        if (project.selected_node == null) return;

        minecraft.setScreen(new DialogueNodeEditorScreen(this, project, project.selected_node));
    }

    private void saveAndBack() {
        try {
            DialogueEditorWorkspace.save(project);
        } catch (Exception ignored) {
        }

        DialogueEditorHistory.checkpoint(project);
        minecraft.setScreen(parent);
    }

    private void rebuild() {
        minecraft.setScreen(this);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderMouseX = mouseX;
        renderMouseY = mouseY;

        int canvasRight = width - INSPECTOR_W;

        graphics.fill(0, TOOLBAR_H, canvasRight, height, 0xFF0B1009);
        graphics.fill(0, TOOLBAR_H, canvasRight, CANVAS_TOP, 0xF0121A10);
        graphics.fill(0, CANVAS_TOP - 1, canvasRight, CANVAS_TOP, 0xFF31402A);

        renderGrid(graphics, canvasRight);
        graphics.enableScissor(0, CANVAS_TOP, canvasRight, height);

        renderConnections(graphics);
        renderPendingLink(graphics);

        for (Map.Entry<String, DialogueDefinition.Node> entry : project.definition.nodes.entrySet()) {
            renderNode(graphics, entry.getKey(), entry.getValue(), mouseX, mouseY);
        }

        graphics.disableScissor();

        renderInfoBar(graphics, canvasRight);
        renderInspector(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInfoBar(GuiGraphics graphics, int canvasRight) {
        String text;

        if (pendingLink != null) {
            text = "CONNECTING  " + portLabel(pendingLink) + "  -> click the destination node   |   Esc = cancel link";
        } else {
            text = "LMB drag node";
        }

        graphics.drawString(font, trim(text, Math.max(40, canvasRight - 16)), 8, TOOLBAR_H + 8, pendingLink != null ? 0xFFFFD45A : 0xFFBDB497, false);

        String zoomText = Math.round(zoom * 100.0D) + "%";
        int zw = font.width(zoomText);

        graphics.drawString(font, zoomText, canvasRight - zw - 8, TOOLBAR_H + 8, 0xFFB8FF72, false);
    }

    private void renderGrid(GuiGraphics graphics, int canvasRight) {
        double spacing = 28.0D * zoom;

        if (spacing < 8.0D) spacing *= 2.0D;
        if (spacing < 8.0D) spacing *= 2.0D;

        int startX = (int) Math.floor((panX * zoom) % spacing);
        int startY = CANVAS_TOP + (int) Math.floor((panY * zoom) % spacing);

        for (double x = startX; x < canvasRight; x += spacing) {
            graphics.vLine((int) x, CANVAS_TOP, height, 0xFF1A2116);
        }

        for (double y = startY; y < height; y += spacing) {
            graphics.hLine(0, canvasRight, (int) y, 0xFF1A2116);
        }
    }

    private void renderConnections(GuiGraphics graphics) {
        for (Map.Entry<String, DialogueDefinition.Node> entry : project.definition.nodes.entrySet()) {
            String fromId = entry.getKey();
            DialogueDefinition.Node node = entry.getValue();
            if (node == null) continue;

            String type = nodeType(node);

            if ("choice".equals(type) && node.choices != null) {
                for (int i = 0; i < node.choices.size(); i++) {
                    DialogueDefinition.Choice choice = node.choices.get(i);
                    drawConnection(graphics, fromId, choice != null ? choice.goto_node : null, i, 0xFFA8F06A);
                }
            } else if ("condition".equals(type)) {
                drawConnection(graphics, fromId, node.next, 0, 0xFF86D955);
                drawConnection(graphics, fromId, node.else_node, 1, 0xFFFF4D55);
            } else if (!"end".equals(type)) {
                drawConnection(graphics, fromId, node.next, 0, 0xFFAAA187);
            }
        }
    }

    private void drawConnection(GuiGraphics graphics, String fromId, String toId, int portIndex, int color) {
        if (toId == null || !project.definition.nodes.containsKey(toId)) return;

        DialogueEditorProject.NodePosition from = project.node_positions.get(fromId);
        DialogueEditorProject.NodePosition to = project.node_positions.get(toId);
        DialogueDefinition.Node fromNode = project.definition.nodes.get(fromId);
        DialogueDefinition.Node toNode = project.definition.nodes.get(toId);

        if (from == null || to == null || fromNode == null || toNode == null) return;

        int x1 = screenX(from.x + NODE_W);
        int y1 = screenY(from.y + portOffsetWorld(fromNode, portIndex));

        int x2 = screenX(to.x);
        int y2 = screenY(to.y + Math.min(34.0D, nodeHeightWorld(toNode) * 0.45D));

        int middle = x1 + (x2 - x1) / 2;

        graphics.hLine(Math.min(x1, middle), Math.max(x1, middle), y1, color);
        graphics.vLine(middle, Math.min(y1, y2), Math.max(y1, y2), color);
        graphics.hLine(Math.min(middle, x2), Math.max(middle, x2), y2, color);

        graphics.fill(x2 - 2, y2 - 2, x2 + 3, y2 + 3, color);
    }

    private void renderNode(GuiGraphics graphics, String id, DialogueDefinition.Node node, int mouseX, int mouseY) {
        DialogueEditorProject.NodePosition p = project.node_positions.get(id);

        if (p == null || node == null) {
            return;
        }

        int x = screenX(p.x);
        int y = screenY(p.y);
        int w = scaled(NODE_W);
        int h = scaled(nodeHeightWorld(node));

        if (x + w < 0 || y + h < CANVAS_TOP || x > width - INSPECTOR_W || y > height) {

            return;
        }

        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h && mouseX < width - INSPECTOR_W;

        long now = System.currentTimeMillis();

        NodeJuiceState juice = nodeJuiceStates.computeIfAbsent(id, ignored -> new NodeJuiceState());

        if (hovered && !juice.wasHovered) {

            juice.hoverStartedAt = now;
            DialogueJuiceSound.nodeHoverClick();
        }

        juice.wasHovered = hovered;
        updateNodeHoverAmount(juice, hovered, now);

        float hoverJelly = nodeHoverJelly(juice, now);
        float pressJelly = nodePressJelly(juice, now);

        boolean dragging = id.equals(draggingNode);

        float scaleX = 1.0F + juice.hoverAmount * 0.008F + hoverJelly + pressJelly + (dragging ? 0.012F : 0.0F);
        float scaleY = 1.0F + juice.hoverAmount * 0.006F - hoverJelly * 0.30F + pressJelly * 0.72F + (dragging ? -0.005F : 0.0F);

        float centerX = x + w * 0.5F;
        float centerY = y + h * 0.5F;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);

        String type = nodeType(node);

        int headerColor = nodeHeaderColor(type);

        boolean selected = id.equals(project.selected_node);

        if (hovered || dragging) {
            graphics.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x75000000);
        }

        graphics.fill(x, y, x + w, y + h, selected ? 0xFF34442C : 0xFF1A2116);

        int headerH = Math.max(7, scaled(22));

        graphics.fill(x, y, x + w, y + headerH, headerColor);

        if (selected) {
            outline(graphics, x, y, x + w, y + h, 0xFFFFD45A);
        } else if (hovered && zoom >= 0.40D) {
            outline(graphics, x, y, x + w, y + h, 0x669EFF77);
        }

        if (zoom >= 0.48D && w >= 86) {

            graphics.drawString(font, trim(id, Math.max(20, w - 10)), x + 5, y + 6, 0xFFFFFFFF, false);

            renderNodeBody(graphics, id, node, x, y, w, h);
        } else if (zoom >= 0.30D && w >= 56) {

            graphics.drawString(font, trim(id, Math.max(15, w - 8)), x + 4, y + Math.max(3, headerH / 2 - 4), 0xFFFFFFFF, false);
        }

        renderPorts(graphics, node, x, y, w);

        if (id.equals(project.definition.start_node) && zoom >= 0.42D) {

            graphics.drawString(font, "START", x + 5, y + h - 11, 0xFF86D955, false);
        }

        drawNodePressFlash(graphics, x, y, w, h, juice, now);
        graphics.pose().popPose();
    }

    private void renderNodeBody(GuiGraphics graphics, String id, DialogueDefinition.Node node, int x, int y, int w, int h) {
        String type = nodeType(node);
        int baseY = y + Math.max(21, scaled(26));

        switch (type) {
            case "choice" -> renderChoiceBody(graphics, node, x, baseY, w);
            case "condition" -> renderConditionBody(graphics, node, x, baseY, w);
            case "event" -> renderEventBody(graphics, node, x, baseY, w);
            case "end" -> {
                graphics.drawString(font, "END DIALOGUE", x + 5, baseY, 0xFFFF9B9B, false);
                graphics.drawString(font, "releases source/session", x + 5, baseY + 13, 0xFFC7BEA0, false);
            }
            default -> renderLineBody(graphics, node, x, baseY, w);
        }
    }

    private void renderLineBody(GuiGraphics graphics, DialogueDefinition.Node node, int x, int y, int w) {
        graphics.drawString(font, "SHOW TEXT", x + 5, y, 0xFFCFE6AE, false);
        graphics.drawString(font, trim(lineSummary(node.line), Math.max(20, w - 10)), x + 5, y + 13, 0xFFF0E8D0, false);

        graphics.drawString(font, trim("NEXT -> " + destination(node.next), Math.max(20, w - 10)), x + 5, y + 29, 0xFFBDB497, false);

        if (node.actions != null && !node.actions.isEmpty()) {
            graphics.drawString(font, trim("ACTIONS: " + actionsSummary(node.actions), Math.max(20, w - 10)), x + 5, y + 43, 0xFFA9C7A7, false);
        }
    }

    private void renderChoiceBody(GuiGraphics graphics, DialogueDefinition.Node node, int x, int y, int w) {
        graphics.drawString(font, "PLAYER CHOOSES", x + 5, y, 0xFFA8F06A, false);

        String prompt = lineSummary(node.line);
        graphics.drawString(font, trim("Q: " + prompt, Math.max(20, w - 10)), x + 5, y + 13, 0xFFF0E8D0, false);

        int rowY = y + 31;

        if (node.choices == null || node.choices.isEmpty()) {
            graphics.drawString(font, "<no choices>", x + 5, rowY, 0xFFFF9B9B, false);
            return;
        }

        for (int i = 0; i < node.choices.size(); i++) {
            DialogueDefinition.Choice choice = node.choices.get(i);
            String text = choiceSummary(choice);
            String target = destination(choice != null ? choice.goto_node : null);
            int actionCount = choice != null && choice.actions != null ? choice.actions.size() : 0;

            String row = (i + 1) + ". " + text + " -> " + target + (actionCount > 0 ? "  [+" + actionCount + " action" + (actionCount == 1 ? "" : "s") + "]" : "");

            graphics.drawString(font, trim(row, Math.max(20, w - 18)), x + 8, rowY + i * 16, 0xFFECE5C9, false);

            if (choice != null && choice.conditions != null && !choice.conditions.isEmpty()) {
                graphics.drawString(font, "[" + choice.conditions.size() + " condition" + (choice.conditions.size() == 1 ? "" : "s") + "]", x + 16, rowY + i * 16 + 8, 0xFFAAA185, false);
            }
        }
    }

    private void renderConditionBody(GuiGraphics graphics, DialogueDefinition.Node node, int x, int y, int w) {
        graphics.drawString(font, "AUTO BRANCH", x + 5, y, 0xFFD8E36A, false);
        graphics.drawString(font, trim("IF " + conditionsSummary(node.conditions), Math.max(20, w - 10)), x + 5, y + 13, 0xFFF0E8D0, false);

        graphics.drawString(font, trim("TRUE  -> " + destination(node.next), Math.max(20, w - 14)), x + 8, y + 34, 0xFF86D955, false);

        graphics.drawString(font, trim("FALSE -> " + destination(node.else_node), Math.max(20, w - 14)), x + 8, y + 52, 0xFFFF777D, false);
    }

    private void renderEventBody(GuiGraphics graphics, DialogueDefinition.Node node, int x, int y, int w) {
        graphics.drawString(font, "DO ACTIONS", x + 5, y, 0xFFD8E36A, false);

        graphics.drawString(font, trim(actionsSummary(node.actions), Math.max(20, w - 10)), x + 5, y + 13, 0xFFF0E8D0, false);

        graphics.drawString(font, trim("THEN -> " + destination(node.next), Math.max(20, w - 10)), x + 5, y + 31, 0xFFBDB497, false);
    }


    private void renderPorts(GuiGraphics graphics, DialogueDefinition.Node node, int x, int y, int w) {
        String type = nodeType(node);
        if ("end".equals(type)) return;

        if ("choice".equals(type) && node.choices != null) {
            for (int i = 0; i < node.choices.size(); i++) {
                drawPort(graphics, x + w, y + scaled(portOffsetWorld(node, i)), 0xFFA8F06A);
            }
            return;
        }

        if ("condition".equals(type)) {
            drawPort(graphics, x + w, y + scaled(portOffsetWorld(node, 0)), 0xFF86D955);
            drawPort(graphics, x + w, y + scaled(portOffsetWorld(node, 1)), 0xFFFF4D55);
            return;
        }

        drawPort(graphics, x + w, y + scaled(portOffsetWorld(node, 0)), 0xFFAAA187);
    }

    private void drawPort(GuiGraphics graphics, int x, int y, int color) {
        int r = zoom < 0.45D ? 2 : 4;
        graphics.fill(x - r, y - r, x + r + 1, y + r + 1, color);

        if (r >= 4) {
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF0B1009);
        }
    }

    private void renderInspector(GuiGraphics graphics) {
        int x = width - INSPECTOR_W;

        graphics.fill(x, TOOLBAR_H, width, height, 0xF0121710);
        graphics.fill(x, TOOLBAR_H, x + 1, height, 0xFF445438);

        graphics.drawString(font, "NODE INSPECTOR", x + 10, TOOLBAR_H + 10, 0xFFB8FF72, false);

        int y = TOOLBAR_H + 30;
        int maxW = INSPECTOR_W - 20;

        if (project.selected_node == null) {
            y = drawWrapped(graphics, "Select a node to see what it does.", x + 10, y, maxW, 0xFFC7BEA0);

            y += 12;
            y = drawLegend(graphics, x + 10, y, maxW);
            return;
        }

        DialogueDefinition.Node node = project.definition.nodes.get(project.selected_node);
        if (node == null) return;

        String type = nodeType(node);

        graphics.drawString(font, trim("ID: " + project.selected_node, maxW), x + 10, y, 0xFFFFFFFF, false);
        y += 15;

        graphics.drawString(font, "TYPE: " + type.toUpperCase(Locale.ROOT), x + 10, y, nodeHeaderColor(type), false);
        y += 18;

        if (project.selected_node.equals(project.definition.start_node)) {
            graphics.drawString(font, "STARTS HERE", x + 10, y, 0xFF86D955, false);
            y += 17;
        }

        y = drawWrapped(graphics, typeExplanation(type), x + 10, y, maxW, 0xFFE8E0C3);

        y += 10;

        switch (type) {
            case "choice" -> {
                graphics.drawString(font, "PLAYER SEES:", x + 10, y, 0xFFA8F06A, false);
                y += 14;

                if (node.line != null) {
                    y = drawWrapped(graphics, "Question: " + lineSummary(node.line), x + 10, y, maxW, 0xFFF0E8D0);
                }

                if (node.choices != null) {
                    for (int i = 0; i < node.choices.size() && y < height - 150; i++) {
                        DialogueDefinition.Choice choice = node.choices.get(i);
                        String row = (i + 1) + ") " + choiceSummary(choice) + "  ->  " + destination(choice != null ? choice.goto_node : null);

                        y = drawWrapped(graphics, row, x + 14, y + 2, maxW - 8, 0xFFECE5C9);

                        if (choice != null && choice.actions != null && !choice.actions.isEmpty()) {
                            y = drawWrapped(graphics, "   actions: " + actionsSummary(choice.actions), x + 14, y, maxW - 8, 0xFFA9C7A7);
                        }
                    }
                }
            }

            case "condition" -> {
                y = drawWrapped(graphics, "IF " + conditionsSummary(node.conditions), x + 10, y, maxW, 0xFFD8E36A);

                y += 5;

                y = drawWrapped(graphics, "TRUE -> " + destination(node.next), x + 14, y, maxW - 8, 0xFF86D955);

                y = drawWrapped(graphics, "FALSE -> " + destination(node.else_node), x + 14, y, maxW - 8, 0xFFFF777D);
            }

            case "event" -> {
                y = drawWrapped(graphics, "Actions: " + actionsSummary(node.actions), x + 10, y, maxW, 0xFFD8E36A);

                y = drawWrapped(graphics, "Then continues to: " + destination(node.next), x + 10, y, maxW, 0xFFE8E0C3);
            }

            case "end" ->
                    y = drawWrapped(graphics, "No output. The dialogue session ends here.", x + 10, y, maxW, 0xFFFF9B9B);

            default -> {
                y = drawWrapped(graphics, "Text: " + lineSummary(node.line), x + 10, y, maxW, 0xFFF0E8D0);

                y = drawWrapped(graphics, "Next: " + destination(node.next), x + 10, y, maxW, 0xFFE8E0C3);

                if (node.actions != null && !node.actions.isEmpty()) {
                    y = drawWrapped(graphics, "Actions on enter: " + actionsSummary(node.actions), x + 10, y, maxW, 0xFFA9C7A7);
                }
            }
        }

        if (y < height - 125) {
            y += 10;
            drawWrapped(graphics, "Tip: click a colored output port on the node, then click a destination node. You do not need to type goto IDs manually.", x + 10, y, maxW, 0xFFA09A80);
        }
    }

    private int drawLegend(GuiGraphics graphics, int x, int y, int w) {
        graphics.drawString(font, "VISUAL LANGUAGE", x, y, 0xFFB8FF72, false);
        y += 15;

        String[] lines = {"LINE = show text, then continue", "CHOICE = player picks an option", "CONDITION = automatic TRUE/FALSE branch", "ACTIONS = change world/quest state, then continue", "END = finish dialogue"};

        int[] colors = {0xFFCFE6AE, 0xFFA8F06A, 0xFFD8E36A, 0xFFD8E36A, 0xFFFF9B9B};

        for (int i = 0; i < lines.length; i++) {
            y = drawWrapped(graphics, lines[i], x, y, w, colors[i]);
        }

        return y;
    }

    private String typeExplanation(String type) {
        return switch (type) {
            case "choice" ->
                    "CHOICE pauses the dialogue and displays answer buttons. Each answer has its own destination.";
            case "condition" ->
                    "CONDITION is invisible. The server checks its rules and automatically chooses TRUE or FALSE.";
            case "event" -> "ACTION/EVENT is invisible. It runs server-side Actions in order, then follows THEN.";
            case "end" -> "END closes the dialogue and releases the source/session.";
            default -> "LINE shows one normal dialogue line, then follows NEXT.";
        };
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        for (String line : wrap(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 12;
        }

        return y;
    }

    private List<String> wrap(String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            result.add("");
            return result;
        }

        String[] words = text.split("\\s+");
        String current = "";

        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;

            if (font.width(candidate) <= width) {
                current = candidate;
                continue;
            }

            if (!current.isEmpty()) result.add(current);

            if (font.width(word) <= width) {
                current = word;
            } else {
                String rest = word;

                while (!rest.isEmpty()) {
                    String part = font.plainSubstrByWidth(rest, Math.max(8, width));
                    if (part.isEmpty()) break;

                    result.add(part);
                    rest = rest.substring(Math.min(part.length(), rest.length()));
                }

                current = "";
            }
        }

        if (!current.isEmpty()) result.add(current);

        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < CANVAS_TOP) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (mouseX >= width - INSPECTOR_W) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            PortRef port = portAt(mouseX, mouseY);

            if (port != null) {
                pendingLink = port;
                markNodePressed(port.nodeId);
                return true;
            }
        }

        String hit = nodeAt(mouseX, mouseY);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pendingLink != null && hit != null) {

            applyLink(pendingLink, hit);
            pendingLink = null;
            markNodePressed(hit);
            DialogueEditorHistory.checkpoint(project);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit != null) {
            project.selected_node = hit;
            markNodePressed(hit);

            DialogueEditorProject.NodePosition position = project.node_positions.get(hit);
            if (position == null) return true;

            dragOffsetX = worldX(mouseX) - position.x;
            dragOffsetY = worldY(mouseY) - position.y;
            draggingNode = hit;

            long now = System.currentTimeMillis();

            if (hit.equals(lastClickNode) && now - lastClickTime < 350L) {
                draggingNode = null;
                openSelectedEditor();
            }

            lastClickTime = now;
            lastClickNode = hit;

            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {

            panning = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            project.selected_node = null;
            pendingLink = null;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingNode != null) {
            DialogueEditorProject.NodePosition position = project.node_positions.get(draggingNode);

            if (position != null) {
                position.x = worldX(mouseX) - dragOffsetX;
                position.y = worldY(mouseY) - dragOffsetY;
            }

            return true;
        }

        if (panning) {
            panX += (mouseX - lastMouseX) / zoom;
            panY += (mouseY - lastMouseY) / zoom;

            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingNode != null) {
            draggingNode = null;
            DialogueEditorHistory.checkpoint(project);
            return true;
        }

        if (panning) {
            panning = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= width - INSPECTOR_W) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        double oldZoom = zoom;

        zoom = clamp(zoom + delta * 0.10D, MIN_ZOOM, MAX_ZOOM);

        if (Math.abs(oldZoom - zoom) < 0.0001D) return true;
        double beforeX = worldXAt(mouseX, oldZoom, panX);
        double beforeY = worldYAt(mouseY, oldZoom, panY);

        panX = mouseX / zoom - beforeX;
        panY = (mouseY - CANVAS_TOP) / zoom - beforeY;

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && pendingLink != null) {
            pendingLink = null;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE && project.selected_node != null) {
            project.deleteNode(project.selected_node);
            rebuild();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER && project.selected_node != null) {
            openSelectedEditor();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F) {
            fitGraph();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            saveAndBack();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private PortRef portAt(double mouseX, double mouseY) {
        for (Map.Entry<String, DialogueDefinition.Node> entry : project.definition.nodes.entrySet()) {
            String id = entry.getKey();
            DialogueDefinition.Node node = entry.getValue();
            DialogueEditorProject.NodePosition p = project.node_positions.get(id);

            if (node == null || p == null || "end".equals(nodeType(node))) continue;

            int count = outputCount(node);

            for (int i = 0; i < count; i++) {
                int px = screenX(p.x + NODE_W);
                int py = screenY(p.y + portOffsetWorld(node, i));

                double hitRadius = zoom < 0.45D ? 6.0D : 8.0D;

                if (Math.abs(mouseX - px) <= hitRadius && Math.abs(mouseY - py) <= hitRadius) {
                    return new PortRef(id, nodeType(node), i);
                }
            }
        }

        return null;
    }

    private void applyLink(PortRef source, String targetNode) {
        DialogueDefinition.Node node = project.definition.nodes.get(source.nodeId);
        if (node == null || targetNode == null) return;

        switch (source.type) {
            case "choice" -> {
                if (node.choices != null && source.index >= 0 && source.index < node.choices.size()) {
                    node.choices.get(source.index).goto_node = targetNode;
                }
            }
            case "condition" -> {
                if (source.index == 0) node.next = targetNode;
                else node.else_node = targetNode;
            }
            default -> node.next = targetNode;
        }
    }

    private void renderPendingLink(GuiGraphics graphics) {
        if (pendingLink == null) return;

        DialogueEditorProject.NodePosition p = project.node_positions.get(pendingLink.nodeId);
        DialogueDefinition.Node node = project.definition.nodes.get(pendingLink.nodeId);

        if (p == null || node == null) return;

        int x = screenX(p.x + NODE_W);
        int y = screenY(p.y + portOffsetWorld(node, pendingLink.index));

        int color = switch (pendingLink.type) {
            case "choice" -> 0xFFA8F06A;
            case "condition" -> pendingLink.index == 0 ? 0xFF86D955 : 0xFFFF4D55;
            default -> 0xFFFFD45A;
        };

        int middle = (x + renderMouseX) / 2;

        graphics.hLine(Math.min(x, middle), Math.max(x, middle), y, color);
        graphics.vLine(middle, Math.min(y, renderMouseY), Math.max(y, renderMouseY), color);
        graphics.hLine(Math.min(middle, renderMouseX), Math.max(middle, renderMouseX), renderMouseY, color);
    }

    private void markNodePressed(String id) {
        if (id == null) {
            return;
        }

        NodeJuiceState state = nodeJuiceStates.computeIfAbsent(id, ignored -> new NodeJuiceState());
        state.pressStartedAt = System.currentTimeMillis();

        DialogueJuiceSound.nodePressClick();
    }

    private static void updateNodeHoverAmount(NodeJuiceState state, boolean hovered, long now) {
        if (state.lastRenderAt < 0L) {
            state.lastRenderAt = now;
        }

        long elapsed = Math.min(60L, Math.max(0L, now - state.lastRenderAt));

        state.lastRenderAt = now;

        float target = hovered ? 1.0F : 0.0F;
        float response = 1.0F - (float) Math.exp(-elapsed * 0.019D);

        state.hoverAmount += (target - state.hoverAmount) * response;
    }

    private static float nodeHoverJelly(NodeJuiceState state, long now) {
        if (state.hoverStartedAt < 0L) {
            return 0.0F;
        }

        float t = (now - state.hoverStartedAt) / 340.0F;
        if (t < 0.0F || t >= 1.0F) {

            return 0.0F;
        }

        return Mth.sin(t * Mth.PI * 3.1F) * (float) Math.exp(-4.0F * t) * 0.022F;
    }

    private static float nodePressJelly(NodeJuiceState state, long now) {
        if (state.pressStartedAt < 0L) {
            return 0.0F;
        }

        float t = (now - state.pressStartedAt) / 440.0F;
        if (t < 0.0F || t >= 1.0F) {

            return 0.0F;
        }

        return (0.021F + Mth.sin(t * Mth.PI * 4.0F) * 0.039F) * (float) Math.exp(-4.7F * t);
    }

    private static void drawNodePressFlash(GuiGraphics graphics, int x, int y, int w, int h, NodeJuiceState state, long now) {
        if (state.pressStartedAt < 0L) {
            return;
        }

        float t = (now - state.pressStartedAt) / 235.0F;
        if (t < 0.0F || t >= 1.0F) {

            return;
        }

        float fade = 1.0F - t;
        fade *= fade;
        int alpha = Mth.clamp(Math.round(fade * 92.0F), 0, 92);

        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, alpha << 24 | 0x00FFFFFF);
    }

    private String nodeAt(double mouseX, double mouseY) {
        for (Map.Entry<String, DialogueEditorProject.NodePosition> entry : project.node_positions.entrySet()) {
            DialogueDefinition.Node node = project.definition.nodes.get(entry.getKey());
            DialogueEditorProject.NodePosition p = entry.getValue();

            if (node == null || p == null) continue;

            int x = screenX(p.x);
            int y = screenY(p.y);
            int w = scaled(NODE_W);
            int h = scaled(nodeHeightWorld(node));

            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                return entry.getKey();
            }
        }

        return null;
    }

    private int outputCount(DialogueDefinition.Node node) {
        String type = nodeType(node);

        if ("end".equals(type)) return 0;
        if ("condition".equals(type)) return 2;

        if ("choice".equals(type)) {
            return node.choices != null ? node.choices.size() : 0;
        }

        return 1;
    }

    private double portOffsetWorld(DialogueDefinition.Node node, int index) {
        String type = nodeType(node);

        if ("choice".equals(type)) {
            return 59.0D + index * 16.0D;
        }

        if ("condition".equals(type)) {
            return index == 0 ? 62.0D : 80.0D;
        }

        return 58.0D;
    }

    private double nodeHeightWorld(DialogueDefinition.Node node) {
        String type = nodeType(node);

        return switch (type) {
            case "choice" -> {
                int count = node.choices != null ? node.choices.size() : 0;
                yield Math.max(92.0D, 72.0D + count * 16.0D);
            }
            case "condition" -> 108.0D;
            case "event" -> 90.0D;
            case "end" -> 64.0D;
            default -> node.actions != null && !node.actions.isEmpty() ? 108.0D : 92.0D;
        };
    }

    private String nodeType(DialogueDefinition.Node node) {
        return node != null && node.type != null ? node.type.toLowerCase(Locale.ROOT) : "line";
    }

    private int nodeHeaderColor(String type) {
        return switch (type) {
            case "choice" -> 0xFF3D7438;
            case "condition" -> 0xFF778E43;
            case "event" -> 0xFF566B3E;
            case "end" -> 0xFF7A3438;
            default -> 0xFF3F6438;
        };
    }

    private String lineSummary(DialogueDefinition.Line line) {
        if (line == null) return "<empty>";

        if (line.literal != null && !line.literal.isBlank()) {
            return line.literal.replace('\n', ' ');
        }

        if (line.text != null && !line.text.isBlank()) {
            return line.text;
        }

        return "<empty>";
    }

    private String choiceSummary(DialogueDefinition.Choice choice) {
        if (choice == null) return "<empty choice>";

        if (choice.literal != null && !choice.literal.isBlank()) {
            return choice.literal.replace('\n', ' ');
        }

        if (choice.text != null && !choice.text.isBlank()) {
            return choice.text;
        }

        return "<empty choice>";
    }

    private String conditionsSummary(List<DialogueDefinition.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) return "always";

        List<String> parts = new ArrayList<>();

        for (DialogueDefinition.Condition condition : conditions) {
            if (condition == null) continue;

            String type = condition.type != null ? condition.type.toLowerCase(Locale.ROOT) : "always";

            String part = switch (type) {
                case "player_tag" -> "player has tag " + safe(condition.id);
                case "source_tag" -> "source has tag " + safe(condition.id);
                case "score" -> safe(condition.objective) + " " + safe(condition.operator) + " " + condition.value;
                case "has_item" -> "has " + Math.max(1, condition.count) + "x " + safe(condition.id);
                case "dimension" -> "dimension = " + safe(condition.id);
                case "source_type" -> "source = " + safe(condition.id);
                case "mod_loaded" -> "mod loaded " + safe(condition.id);
                case "quest_state" -> "quest " + safe(condition.id) + " = " + safe(condition.state);
                default -> "always";
            };

            if (condition.invert) part = "NOT (" + part + ")";
            parts.add(part);
        }

        if (parts.isEmpty()) return "always";

        return String.join(" AND ", parts);
    }

    private String actionsSummary(List<DialogueDefinition.Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return "<no actions>";
        }

        List<String> names = new ArrayList<>();

        int shown = Math.min(3, actions.size());

        for (int i = 0; i < shown; i++) {
            DialogueDefinition.Action action = actions.get(i);

            if (action == null || action.type == null) {
                names.add("?");
                continue;
            }

            String type = "external".equalsIgnoreCase(action.type) ? "fire_external" : action.type;

            String detail = switch (type.toLowerCase(Locale.ROOT)) {
                case "give_item" -> "give " + Math.max(1, action.count) + "x " + safe(action.id);
                case "take_item" -> "take " + Math.max(1, action.count) + "x " + safe(action.id);
                case "quest_start" -> "start quest " + safe(action.id);
                case "quest_complete" -> "complete quest " + safe(action.id);
                case "quest_fail" -> "fail quest " + safe(action.id);
                case "quest_reset" -> "reset quest " + safe(action.id);
                case "set_score" -> "set " + safe(action.objective) + "=" + action.value;
                case "add_score" -> "add " + action.value + " to " + safe(action.objective);
                case "fire_external" -> "event " + safe(action.event != null ? action.event : action.id);
                case "kill" -> "kill " + safe(action.target);
                case "teleport" -> "teleport " + safe(action.target);
                case "play_sound" -> "sound " + safe(action.id);
                case "particle" -> "particle " + safe(action.id);
                case "run_command" -> "command";
                case "add_player_tag" -> "add tag " + safe(action.id);
                case "remove_player_tag" -> "remove tag " + safe(action.id);
                default -> type;
            };

            names.add(detail);
        }

        if (actions.size() > shown) {
            names.add("+" + (actions.size() - shown) + " more");
        }

        return String.join(" -> ", names);
    }


    private String destination(String value) {
        return value != null && !value.isBlank() ? value : "<not connected>";
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "?";
    }

    private String portLabel(PortRef port) {
        DialogueDefinition.Node node = project.definition.nodes.get(port.nodeId);

        if (node == null) return port.nodeId;

        return switch (port.type) {
            case "choice" -> {
                String choice = "?";

                if (node.choices != null && port.index >= 0 && port.index < node.choices.size()) {
                    choice = choiceSummary(node.choices.get(port.index));
                }

                yield port.nodeId + " / choice " + (port.index + 1) + " \"" + choice + "\"";
            }
            case "condition" -> port.nodeId + (port.index == 0 ? " / TRUE" : " / FALSE");
            default -> port.nodeId + " / NEXT";
        };
    }

    private void autoLayoutReadable() {
        project.normalize();

        double x = 45.0D;
        double y = 55.0D;
        double columnWidth = 250.0D;
        double maxHeightInColumn = 0.0D;
        int row = 0;

        for (Map.Entry<String, DialogueDefinition.Node> entry : project.definition.nodes.entrySet()) {
            DialogueDefinition.Node node = entry.getValue();
            double h = nodeHeightWorld(node);

            if (row >= 4) {
                x += columnWidth;
                y = 55.0D;
                row = 0;
                maxHeightInColumn = 0.0D;
            }

            project.node_positions.put(entry.getKey(), new DialogueEditorProject.NodePosition(x, y));

            y += h + 42.0D;
            maxHeightInColumn = Math.max(maxHeightInColumn, h);
            row++;
        }
    }

    private void fitGraph() {
        if (project.definition.nodes == null || project.definition.nodes.isEmpty()) {
            zoom = 1.0D;
            panX = 18.0D;
            panY = 12.0D;
            return;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Map.Entry<String, DialogueDefinition.Node> entry : project.definition.nodes.entrySet()) {
            DialogueEditorProject.NodePosition p = project.node_positions.get(entry.getKey());
            DialogueDefinition.Node node = entry.getValue();

            if (p == null || node == null) continue;

            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x + NODE_W);
            maxY = Math.max(maxY, p.y + nodeHeightWorld(node));
        }

        if (minX == Double.MAX_VALUE) return;

        double availableW = Math.max(80.0D, width - INSPECTOR_W - 36.0D);
        double availableH = Math.max(80.0D, height - CANVAS_TOP - 32.0D);

        double graphW = Math.max(1.0D, maxX - minX);
        double graphH = Math.max(1.0D, maxY - minY);

        zoom = clamp(Math.min(availableW / graphW, availableH / graphH), MIN_ZOOM, 1.20D);

        double centerX = (minX + maxX) * 0.5D;
        double centerY = (minY + maxY) * 0.5D;

        panX = availableW * 0.5D / zoom - centerX + 10.0D;
        panY = availableH * 0.5D / zoom - centerY;
    }

    private int scaled(double value) {
        return Math.max(2, (int) Math.round(value * zoom));
    }

    private int screenX(double worldX) {
        return (int) Math.round((worldX + panX) * zoom);
    }

    private int screenY(double worldY) {
        return CANVAS_TOP + (int) Math.round((worldY + panY) * zoom);
    }

    private double worldX(double screenX) {
        return screenX / zoom - panX;
    }

    private double worldY(double screenY) {
        return (screenY - CANVAS_TOP) / zoom - panY;
    }

    private double worldXAt(double screenX, double targetZoom, double targetPanX) {
        return screenX / targetZoom - targetPanX;
    }

    private double worldYAt(double screenY, double targetZoom, double targetPanY) {
        return (screenY - CANVAS_TOP) / targetZoom - targetPanY;
    }

    private String trim(String value, int pixelWidth) {
        if (value == null) return "";
        if (font.width(value) <= pixelWidth) return value;

        return font.plainSubstrByWidth(value, Math.max(0, pixelWidth - font.width("..."))) + "...";
    }

    private void outline(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.hLine(x1, x2, y1, color);
        graphics.hLine(x1, x2, y2, color);
        graphics.vLine(x1, y1, y2, color);
        graphics.vLine(x2, y1, y2, color);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class NodeJuiceState {
        private boolean wasHovered;
        private long hoverStartedAt = -1L;
        private long pressStartedAt = -1L;
        private long lastRenderAt = -1L;
        private float hoverAmount;
    }

    private record PortRef(String nodeId, String type, int index) {
    }
}
