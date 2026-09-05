package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DialogueZoneWorldEditScreen extends DialogueRetroScreen {

    public enum EditMode {MOVE, SIZE, TEXTURE_MOVE, TEXTURE_SCALE, TEXTURE_ROTATE}

    public enum GizmoAxis {NONE, X, Y, Z}

    public enum GizmoPlane {NONE, XY, XZ, YZ}

    private static final Gson GSON = new GsonBuilder().create();
    private static final double[] SNAP_VALUES = {0.0D, 0.25D, 0.5D, 1.0D};

    private final DialogueEditorProject project;
    private final String originalProjectSnapshot;
    private final DialogueEditorScreen.Tab returnTab;

    private EditMode mode = EditMode.MOVE;
    private boolean uiHidden;
    private GizmoAxis hoveredRotationAxis = GizmoAxis.NONE;
    private GizmoAxis dragRotationAxis = GizmoAxis.NONE;
    private double rotationDragStartAngle;
    private GizmoAxis hoveredAxis = GizmoAxis.NONE;
    private GizmoAxis dragAxis = GizmoAxis.NONE;
    private int hoveredAxisSign = 1;
    private int dragAxisSign = 1;

    private GizmoPlane hoveredPlane = GizmoPlane.NONE;
    private GizmoPlane dragPlane = GizmoPlane.NONE;
    private Vec3 dragPlaneStartPoint = Vec3.ZERO;

    private boolean markerPlacementMode;

    private int snapIndex = 2; // 0.5 block by default.
    private double dragStartAxisParameter;
    private Vec3 dragStartCenter = Vec3.ZERO;

    private double dragStartAnchorX;
    private double dragStartAnchorY;
    private double dragStartAnchorZ;
    private double dragStartOffsetX;
    private double dragStartOffsetY;
    private double dragStartOffsetZ;

    private double dragStartRadius;
    private double dragStartHeight;
    private double dragStartSizeX;
    private double dragStartSizeY;
    private double dragStartSizeZ;

    private double dragStartTextureOffsetX;
    private double dragStartTextureOffsetY;
    private double dragStartTextureOffsetZ;
    private double dragStartTextureScaleX;
    private double dragStartTextureScaleY;
    private double dragStartTextureRotationX;
    private double dragStartTextureRotationY;
    private double dragStartTextureRotationZ;

    private boolean dragChanged;

    private double lastMouseX;
    private double lastMouseY;
    private Vec3 cursorHit;

    private BlockPos resolvedBlockAnchor;
    private Entity resolvedEntityAnchor;
    private int resolveCooldown;

    private String status = "World Edit ready";

    private Button modeButton;
    private Button snapButton;
    private Button markerButton;
    private Button defaultVisualButton;
    private Button textureFitButton;
    private Button textureModeButton;

    public DialogueZoneWorldEditScreen(DialogueEditorProject project) {
        this(project, DialogueEditorScreen.Tab.ZONE, false);
    }

    public DialogueZoneWorldEditScreen(DialogueEditorProject project, DialogueEditorScreen.Tab returnTab, boolean startInTextureMode) {
        super(Component.literal("Dialogue Studio - Zone World Edit"));

        this.project = project != null ? project : DialogueEditorProject.createDefault();
        this.project.normalize();
        this.returnTab = returnTab != null ? returnTab : DialogueEditorScreen.Tab.ZONE;
        prepareZone();

        if (startInTextureMode) {
            this.mode = EditMode.TEXTURE_MOVE;
            this.uiHidden = true;
            this.status = "Texture gizmo ready - H shows the full HUD";
        }

        this.originalProjectSnapshot = GSON.toJson(this.project);
    }

    @Override
    protected void init() {
        int x = 8;
        int buttonH = 16;
        int gap = 3;
        int row1 = Math.max(8, height - 38);
        int row2 = row1 + buttonH + 2;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Apply"), b -> applyAndReturn()).bounds(x, row1, 40, buttonH).build());
        x += 40 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), b -> cancelAndReturn()).bounds(x, row1, 42, buttonH).build());
        x += 42 + gap;

        modeButton = addRenderableWidget(DialogueRetroButton.retroBuilder(modeText(), b -> cycleMode()).bounds(x, row1, 56, buttonH).build());
        x += 56 + gap;

        snapButton = addRenderableWidget(DialogueRetroButton.retroBuilder(snapText(), b -> cycleSnap()).bounds(x, row1, 54, buttonH).build());

        x = 8;
        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("P Cur"), b -> placeAtCursor()).bounds(x, row2, 44, buttonH).build());
        x += 44 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("F Me"), b -> placeAtPlayer()).bounds(x, row2, 42, buttonH).build());
        x += 42 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("R Find"), b -> {
            resolveAnchorNow();
            status = anchorResolved() ? "Anchor re-resolved" : "Anchor target is not currently visible";
        }).bounds(x, row2, 48, buttonH).build());
        x += 48 + gap;

        markerButton = addRenderableWidget(DialogueRetroButton.retroBuilder(markerText(), b -> toggleMarkerPlacement()).bounds(x, row2, 52, buttonH).build());
        x += 52 + gap;

        defaultVisualButton = addRenderableWidget(DialogueRetroButton.retroBuilder(defaultVisualText(), b -> toggleDefaultVisual()).bounds(x, row2, 62, buttonH).build());
        x += 62 + gap;

        textureFitButton = addRenderableWidget(DialogueRetroButton.retroBuilder(textureFitText(), b -> toggleTextureFit()).bounds(x, row2, 62, buttonH).build());
        x += 62 + gap;

        textureModeButton = addRenderableWidget(DialogueRetroButton.retroBuilder(textureModeText(), b -> cycleTextureMode()).bounds(x, row2, 78, buttonH).build());

        int row3 = Math.max(8, row1 - buttonH - 2);
        x = 8;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Tex Move"), b -> setMode(EditMode.TEXTURE_MOVE)).bounds(x, row3, 58, buttonH).build());
        x += 58 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Tex Scale"), b -> setMode(EditMode.TEXTURE_SCALE)).bounds(x, row3, 62, buttonH).build());
        x += 62 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Tex Rotate"), b -> setMode(EditMode.TEXTURE_ROTATE)).bounds(x, row3, 66, buttonH).build());
        x += 66 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Reset Tex"), b -> resetTextureTransform()).bounds(x, row3, 60, buttonH).build());
        x += 60 + gap;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("H HUD"), b -> toggleUi()).bounds(x, row3, 48, buttonH).build());

        resolveAnchorNow();
    }

    private void prepareZone() {
        DialogueDefinition.Trigger trigger = project.currentTrigger();
        trigger.type = "zone";

        if (trigger.anchor == null) {
            trigger.anchor = new DialogueDefinition.ZoneAnchor();
        }

        if (trigger.visual == null) {
            trigger.visual = new DialogueDefinition.ZoneVisual();
        }

        if (trigger.shape == null || trigger.shape.isBlank()) {
            trigger.shape = "cylinder";
        }

        if (trigger.anchor.type == null || trigger.anchor.type.isBlank()) {
            trigger.anchor.type = "absolute";
        }

        if ("absolute".equalsIgnoreCase(trigger.anchor.type) && (trigger.anchor.x == null || trigger.anchor.y == null || trigger.anchor.z == null)) {

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                trigger.anchor.x = minecraft.player.getX();
                trigger.anchor.y = minecraft.player.getY();
                trigger.anchor.z = minecraft.player.getZ();
            } else {
                trigger.anchor.x = 0.0D;
                trigger.anchor.y = 64.0D;
                trigger.anchor.z = 0.0D;
            }
        }

        trigger.radius = Math.max(0.1D, trigger.radius);
        trigger.height = Math.max(0.1D, trigger.height);
        trigger.size_x = Math.max(0.1D, trigger.size_x);
        trigger.size_y = Math.max(0.1D, trigger.size_y);
        trigger.size_z = Math.max(0.1D, trigger.size_z);
    }

    @Override
    public void tick() {
        super.tick();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (--resolveCooldown <= 0) {
            resolveCooldown = 20;

            String anchorType = anchorType();
            if ("entity".equals(anchorType)) {
                if (resolvedEntityAnchor == null || !resolvedEntityAnchor.isAlive()) {
                    resolveEntityAnchor();
                }
            } else if ("block".equals(anchorType) && resolvedBlockAnchor == null) {
                resolveBlockAnchor();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        cursorHit = raycastSurface(mouseX, mouseY);

        Ray ray = mouseRay(mouseX, mouseY);
        if (dragRotationAxis != GizmoAxis.NONE) {
            hoveredAxis = GizmoAxis.NONE;
            hoveredPlane = GizmoPlane.NONE;
            hoveredRotationAxis = dragRotationAxis;
        } else if (dragAxis != GizmoAxis.NONE) {
            hoveredAxis = dragAxis;
            hoveredAxisSign = dragAxisSign;
            hoveredPlane = GizmoPlane.NONE;
            hoveredRotationAxis = GizmoAxis.NONE;
        } else if (dragPlane != GizmoPlane.NONE) {
            hoveredAxis = GizmoAxis.NONE;
            hoveredPlane = dragPlane;
            hoveredRotationAxis = GizmoAxis.NONE;
        } else if (mode == EditMode.TEXTURE_ROTATE) {
            hoveredAxis = GizmoAxis.NONE;
            hoveredPlane = GizmoPlane.NONE;
            hoveredRotationAxis = pickRotationRing(ray);
        } else {
            AxisPick axisPick = pickAxis(ray);
            hoveredAxis = axisPick.axis;
            hoveredAxisSign = axisPick.sign;
            hoveredPlane = (mode == EditMode.MOVE || mode == EditMode.TEXTURE_MOVE) && hoveredAxis == GizmoAxis.NONE ? pickPlane(ray) : GizmoPlane.NONE;
            hoveredRotationAxis = GizmoAxis.NONE;
        }

        if (!uiHidden) {
            renderHud(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
        } else {
            graphics.drawString(font, "Texture gizmo | 1 move  2 scale  3 rotate XYZ | 4 mode  5 stretch/repeat | 0 reset | H HUD", 8, 8, 0xFFFFF7DD, true);
        }
    }

    private void renderHud(GuiGraphics graphics) {
        DialogueDefinition.Trigger trigger = trigger();
        Vec3 center = center();

        int panelX = 8;
        int panelW = Math.min(214, Math.max(186, width / 8));
        int panelH = 66;
        int panelY = Math.max(8, height - 128);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x9A11170E);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, 0x9A0C110A);

        String anchorState = anchorResolved() ? "OK" : "?";
        int anchorColor = anchorResolved() ? 0xFF7FEA9A : 0xFFFFAA55;

        graphics.drawString(font, "ZONE EDIT  " + modeLabel() + "  " + normalizeShape(trigger.shape) + "  A:" + anchorState, panelX + 5, panelY + 5, anchorColor, false);

        graphics.drawString(font, String.format(Locale.ROOT, "XYZ %.1f  %.1f  %.1f", center.x, center.y, center.z), panelX + 5, panelY + 15, 0xFFF5EDD4, false);

        String dimensions = switch (normalizeShape(trigger.shape)) {
            case "sphere" -> String.format(Locale.ROOT, "R %.2f", trigger.radius);
            case "box" ->
                    String.format(Locale.ROOT, "SIZE %.1f / %.1f / %.1f", trigger.size_x, trigger.size_y, trigger.size_z);
            default -> String.format(Locale.ROOT, "R %.2f  H %.2f", trigger.radius, trigger.height);
        };

        String selection;
        if (markerPlacementMode) {
            selection = "MARKER: click surface";
        } else if (dragRotationAxis != GizmoAxis.NONE) {
            selection = "drag " + dragRotationAxis + " rotation";
        } else if (mode == EditMode.TEXTURE_ROTATE && hoveredRotationAxis != GizmoAxis.NONE) {
            selection = "hover " + hoveredRotationAxis + " rotation";
        } else if (dragPlane != GizmoPlane.NONE) {
            selection = "drag " + dragPlane;
        } else if (dragAxis != GizmoAxis.NONE) {
            selection = "drag " + axisLabel(dragAxis, dragAxisSign);
        } else if (hoveredPlane != GizmoPlane.NONE) {
            selection = "hover " + hoveredPlane;
        } else if (hoveredAxis != GizmoAxis.NONE) {
            selection = "hover " + axisLabel(hoveredAxis, hoveredAxisSign);
        } else {
            selection = "snap " + snapName();
        }

        if (isTextureMode()) {
            DialogueDefinition.ZoneVisual visual = trigger.visual;
            dimensions = String.format(Locale.ROOT, "T %.2f %.2f %.2f | S %.2f / %.2f | R %.0f/%.0f/%.0f°", visual.texture_offset_x, visual.texture_offset_y, visual.texture_offset_z, visual.texture_scale_x, visual.texture_scale_y, visual.texture_rotation_x, visual.texture_rotation, visual.texture_rotation_z);
        }

        graphics.drawString(font, dimensions + "  |  " + selection, panelX + 5, panelY + 25, 0xFFFFD45A, false);

        String shownStatus = status != null ? status : "Ready";
        if (shownStatus.length() > 34) shownStatus = shownStatus.substring(0, 33) + "…";
        graphics.drawString(font, shownStatus, panelX + 5, panelY + 37, 0xFFB8FF72, false);

        graphics.drawString(font, "G zone move | T zone size | 1/2/3 texture", panelX + 5, panelY + 49, 0xFFC1B89B, false);
        graphics.drawString(font, "H HUD | V snap | M marker | Enter apply", panelX + 5, panelY + 57, markerPlacementMode ? 0xFFB8FF72 : 0xFFC1B89B, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!uiHidden && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            if (markerPlacementMode) {
                placeMarkerAtCursor();
                return true;
            }

            Ray ray = mouseRay(mouseX, mouseY);

            if (mode == EditMode.TEXTURE_ROTATE) {
                GizmoAxis rotationAxis = pickRotationRing(ray);
                if (rotationAxis != GizmoAxis.NONE) {
                    beginRotationDrag(ray, rotationAxis);
                    return true;
                }
            }

            AxisPick axisPick = pickAxis(ray);
            if (axisPick.axis != GizmoAxis.NONE) {
                beginDrag(axisPick.axis, axisPick.sign, ray);
                return true;
            }

            if (mode == EditMode.MOVE || mode == EditMode.TEXTURE_MOVE) {
                GizmoPlane plane = pickPlane(ray);
                if (plane != GizmoPlane.NONE) {
                    beginPlaneDrag(plane, ray);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragRotationAxis != GizmoAxis.NONE) {
            updateRotationDrag(mouseRay(mouseX, mouseY));
            return true;
        }

        if (button == 0 && dragAxis != GizmoAxis.NONE) {
            updateDrag(mouseRay(mouseX, mouseY));
            return true;
        }

        if (button == 0 && dragPlane != GizmoPlane.NONE) {
            updatePlaneDrag(mouseRay(mouseX, mouseY));
            return true;
        }

        if (button == 1 && minecraft != null && minecraft.player != null) {
            float sensitivity = 0.35F;
            minecraft.player.setYRot(minecraft.player.getYRot() + (float) dragX * sensitivity);
            minecraft.player.setXRot(Mth.clamp(minecraft.player.getXRot() + (float) dragY * sensitivity, -90.0F, 90.0F));
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (dragAxis != GizmoAxis.NONE || dragPlane != GizmoPlane.NONE || dragRotationAxis != GizmoAxis.NONE)) {
            dragAxis = GizmoAxis.NONE;
            dragPlane = GizmoPlane.NONE;
            dragAxisSign = 1;
            dragRotationAxis = GizmoAxis.NONE;

            if (dragChanged) {
                dragChanged = false;
            }
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_H) {
            toggleUi();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelAndReturn();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyAndReturn();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_G) {
            setMode(EditMode.MOVE);
            return true;
        }

        // S is deliberately left free for normal WASD movement.
        if (keyCode == GLFW.GLFW_KEY_T) {
            setMode(EditMode.SIZE);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) {
            setMode(EditMode.TEXTURE_MOVE);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) {
            setMode(EditMode.TEXTURE_SCALE);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_3 || keyCode == GLFW.GLFW_KEY_KP_3) {
            setMode(EditMode.TEXTURE_ROTATE);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_4 || keyCode == GLFW.GLFW_KEY_KP_4) {
            cycleTextureMode();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_5 || keyCode == GLFW.GLFW_KEY_KP_5) {
            toggleTextureFit();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
            resetTextureTransform();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_V) {
            cycleSnap();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_P) {
            placeAtCursor();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F) {
            placeAtPlayer();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            resolveAnchorNow();
            status = anchorResolved() ? "Anchor re-resolved" : "Anchor target is not currently visible";
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_M) {
            toggleMarkerPlacement();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void applyAndReturn() {
        if (minecraft == null) {
            return;
        }

        try {
            DialogueEditorHistory.checkpoint(project);
            DialogueEditorWorkspace.save(project);
            status = "Zone changes applied";
        } catch (Exception exception) {
            status = "Saved in memory; project save failed: " + exception.getMessage();
        }

        minecraft.setScreen(new DialogueEditorScreen(project, returnTab));
    }

    public void cancelAndReturn() {
        if (minecraft == null) {
            return;
        }

        DialogueEditorProject restored = GSON.fromJson(originalProjectSnapshot, DialogueEditorProject.class);
        if (restored != null) {
            restored.normalize();
            minecraft.setScreen(new DialogueEditorScreen(restored, returnTab));
        } else {
            minecraft.setScreen(new DialogueEditorScreen(project, returnTab));
        }
    }

    @Override
    public void onClose() {
        cancelAndReturn();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void cycleMode() {
        setMode(mode == EditMode.MOVE ? EditMode.SIZE : EditMode.MOVE);
    }

    private void setMode(EditMode newMode) {
        mode = newMode != null ? newMode : EditMode.MOVE;
        dragAxis = GizmoAxis.NONE;
        hoveredAxis = GizmoAxis.NONE;
        dragPlane = GizmoPlane.NONE;
        hoveredPlane = GizmoPlane.NONE;
        dragAxisSign = 1;
        hoveredAxisSign = 1;
        hoveredRotationAxis = GizmoAxis.NONE;
        dragRotationAxis = GizmoAxis.NONE;

        status = switch (mode) {
            case MOVE -> "ZONE MOVE: drag XYZ axes or plane handles";
            case SIZE -> "ZONE SIZE: drag axes to resize trigger shape";
            case TEXTURE_MOVE -> "TEXTURE MOVE: drag XYZ axes or planes";
            case TEXTURE_SCALE -> "TEXTURE SCALE: X/Z horizontal, Y vertical";
            case TEXTURE_ROTATE -> "TEXTURE ROTATE: drag X(red), Y(green), Z(blue) rotation rings";
        };

        if (modeButton != null) {
            modeButton.setMessage(modeText());
        }
    }

    private Component modeText() {
        return Component.literal(mode == EditMode.SIZE ? "T Z.SIZE" : "G Z.MOVE");
    }

    private String modeLabel() {
        return switch (mode) {
            case MOVE -> "ZONE MOVE";
            case SIZE -> "ZONE SIZE";
            case TEXTURE_MOVE -> "TEX MOVE";
            case TEXTURE_SCALE -> "TEX SCALE";
            case TEXTURE_ROTATE -> "TEX ROTATE";
        };
    }

    private boolean isTextureMode() {
        return mode == EditMode.TEXTURE_MOVE || mode == EditMode.TEXTURE_SCALE || mode == EditMode.TEXTURE_ROTATE;
    }

    private void toggleDefaultVisual() {
        DialogueDefinition.ZoneVisual visual = trigger().visual;
        visual.show_default_zone = !visual.show_default_zone;
        visual.preset = "custom";
        status = "Default marker layer = " + (visual.show_default_zone ? "ON" : "OFF");
        if (defaultVisualButton != null) defaultVisualButton.setMessage(defaultVisualText());
    }

    private Component defaultVisualText() {
        return Component.literal("Def " + (trigger().visual.show_default_zone ? "ON" : "OFF"));
    }

    private void toggleTextureFit() {
        DialogueDefinition.ZoneVisual visual = trigger().visual;
        visual.texture_fit = "repeat".equalsIgnoreCase(visual.texture_fit) ? "stretch" : "repeat";
        visual.preset = "custom";
        status = "Texture fit = " + visual.texture_fit;
        if (textureFitButton != null) textureFitButton.setMessage(textureFitText());
    }

    private Component textureFitText() {
        return Component.literal("UV " + ("repeat".equalsIgnoreCase(trigger().visual.texture_fit) ? "REP" : "STR"));
    }

    private void cycleTextureMode() {
        DialogueDefinition.ZoneVisual visual = trigger().visual;
        String current = visual.texture_mode != null ? visual.texture_mode.toLowerCase(Locale.ROOT) : "plane";
        visual.texture_mode = switch (current) {
            case "plane" -> "cylinder_wrap";
            case "cylinder_wrap" -> "box_wrap";
            default -> "plane";
        };
        visual.preset = "custom";
        status = "Texture mode = " + visual.texture_mode;
        if (textureModeButton != null) textureModeButton.setMessage(textureModeText());
    }

    private Component textureModeText() {
        String mode = trigger().visual.texture_mode != null ? trigger().visual.texture_mode : "plane";
        String shortMode = switch (mode.toLowerCase(Locale.ROOT)) {
            case "cylinder_wrap" -> "CYL";
            case "box_wrap" -> "BOX";
            default -> "PLANE";
        };
        return Component.literal("Tex " + shortMode);
    }

    private void resetTextureTransform() {
        DialogueDefinition.ZoneVisual visual = trigger().visual;
        visual.texture_offset_x = 0.0D;
        visual.texture_offset_y = 0.0D;
        visual.texture_offset_z = 0.0D;
        visual.texture_scale_x = 1.0D;
        visual.texture_scale_y = 1.0D;
        visual.texture_rotation_x = 0.0D;
        visual.texture_rotation = 0.0D;
        visual.texture_rotation_z = 0.0D;
        visual.preset = "custom";
        status = "Texture transform reset";
    }

    private void toggleUi() {
        uiHidden = !uiHidden;
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = !uiHidden;
            }
        }
        status = uiHidden ? "HUD hidden - press H to show it" : "HUD visible";
    }

    private void cycleSnap() {
        snapIndex = (snapIndex + 1) % SNAP_VALUES.length;
        status = "Snap = " + snapName();
        if (snapButton != null) {
            snapButton.setMessage(snapText());
        }
    }

    private Component snapText() {
        return Component.literal("V " + snapName());
    }

    private String snapName() {
        double snap = snap();
        return snap <= 0.0D ? "OFF" : String.format(Locale.ROOT, "%.2f", snap);
    }

    private double snap() {
        return SNAP_VALUES[Mth.clamp(snapIndex, 0, SNAP_VALUES.length - 1)];
    }

    private void beginDrag(GizmoAxis axis, int sign, Ray ray) {
        dragAxis = axis;
        dragAxisSign = sign >= 0 ? 1 : -1;
        dragPlane = GizmoPlane.NONE;
        dragChanged = false;

        captureDragStart();
        dragStartCenter = gizmoCenter();
        dragStartAxisParameter = axisParameter(ray, dragStartCenter, axisVector(axis).scale(dragAxisSign));
    }

    private void beginPlaneDrag(GizmoPlane plane, Ray ray) {
        if (plane == GizmoPlane.NONE || ray == null) {
            return;
        }

        Vec3 hit = planeIntersection(ray, plane, gizmoCenter());
        if (hit == null) {
            return;
        }

        dragAxis = GizmoAxis.NONE;
        dragPlane = plane;
        dragChanged = false;
        captureDragStart();
        dragPlaneStartPoint = hit;
    }

    private void captureDragStart() {
        dragStartCenter = gizmoCenter();

        DialogueDefinition.Trigger trigger = trigger();
        DialogueDefinition.ZoneAnchor anchor = trigger.anchor;

        dragStartAnchorX = anchor.x != null ? anchor.x : 0.0D;
        dragStartAnchorY = anchor.y != null ? anchor.y : 0.0D;
        dragStartAnchorZ = anchor.z != null ? anchor.z : 0.0D;

        dragStartOffsetX = anchor.offset_x;
        dragStartOffsetY = anchor.offset_y;
        dragStartOffsetZ = anchor.offset_z;

        dragStartRadius = trigger.radius;
        dragStartHeight = trigger.height;
        dragStartSizeX = trigger.size_x;
        dragStartSizeY = trigger.size_y;
        dragStartSizeZ = trigger.size_z;

        DialogueDefinition.ZoneVisual visual = trigger.visual;
        dragStartTextureOffsetX = visual.texture_offset_x;
        dragStartTextureOffsetY = visual.texture_offset_y;
        dragStartTextureOffsetZ = visual.texture_offset_z;
        dragStartTextureScaleX = visual.texture_scale_x;
        dragStartTextureScaleY = visual.texture_scale_y;
        dragStartTextureRotationX = visual.texture_rotation_x;
        dragStartTextureRotationY = visual.texture_rotation;
        dragStartTextureRotationZ = visual.texture_rotation_z;
    }

    private void updateDrag(Ray ray) {
        if (dragAxis == GizmoAxis.NONE || ray == null) {
            return;
        }

        double currentParameter = axisParameter(ray, dragStartCenter, axisVector(dragAxis).scale(dragAxisSign));
        double delta = currentParameter - dragStartAxisParameter;

        if (!Double.isFinite(delta)) {
            return;
        }

        switch (mode) {
            case MOVE -> applyMoveDelta(delta * dragAxisSign);
            case SIZE -> applySizeDelta(delta);
            case TEXTURE_MOVE -> applyTextureMoveDelta(delta * dragAxisSign);
            case TEXTURE_SCALE -> applyTextureScaleDelta(delta);
            case TEXTURE_ROTATE -> {
                return;
            }
        }

        dragChanged = true;
    }

    private void updatePlaneDrag(Ray ray) {
        if (dragPlane == GizmoPlane.NONE || ray == null) {
            return;
        }

        Vec3 hit = planeIntersection(ray, dragPlane, dragStartCenter);
        if (hit == null) {
            return;
        }

        Vec3 delta = hit.subtract(dragPlaneStartPoint);

        if (mode == EditMode.TEXTURE_MOVE) {
            DialogueDefinition.ZoneVisual visual = trigger().visual;
            switch (dragPlane) {
                case XY -> {
                    visual.texture_offset_x = snapped(dragStartTextureOffsetX + delta.x);
                    visual.texture_offset_y = snapped(dragStartTextureOffsetY + delta.y);
                }
                case XZ -> {
                    visual.texture_offset_x = snapped(dragStartTextureOffsetX + delta.x);
                    visual.texture_offset_z = snapped(dragStartTextureOffsetZ + delta.z);
                }
                case YZ -> {
                    visual.texture_offset_y = snapped(dragStartTextureOffsetY + delta.y);
                    visual.texture_offset_z = snapped(dragStartTextureOffsetZ + delta.z);
                }
            }
            visual.preset = "custom";
            dragChanged = true;
            return;
        }

        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        boolean absolute = "absolute".equalsIgnoreCase(anchor.type);

        double x = dragStartAnchorX;
        double y = dragStartAnchorY;
        double z = dragStartAnchorZ;
        double ox = dragStartOffsetX;
        double oy = dragStartOffsetY;
        double oz = dragStartOffsetZ;

        if (absolute) {
            switch (dragPlane) {
                case XY -> {
                    anchor.x = snapped(x + delta.x);
                    anchor.y = snapped(y + delta.y);
                }
                case XZ -> {
                    anchor.x = snapped(x + delta.x);
                    anchor.z = snapped(z + delta.z);
                }
                case YZ -> {
                    anchor.y = snapped(y + delta.y);
                    anchor.z = snapped(z + delta.z);
                }
            }
        } else {
            switch (dragPlane) {
                case XY -> {
                    anchor.offset_x = snapped(ox + delta.x);
                    anchor.offset_y = snapped(oy + delta.y);
                }
                case XZ -> {
                    anchor.offset_x = snapped(ox + delta.x);
                    anchor.offset_z = snapped(oz + delta.z);
                }
                case YZ -> {
                    anchor.offset_y = snapped(oy + delta.y);
                    anchor.offset_z = snapped(oz + delta.z);
                }
            }
        }

        dragChanged = true;
    }

    private void applyMoveDelta(double delta) {
        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        boolean absolute = "absolute".equalsIgnoreCase(anchor.type);

        if (absolute) {
            switch (dragAxis) {
                case X -> anchor.x = snapped(dragStartAnchorX + delta);
                case Y -> anchor.y = snapped(dragStartAnchorY + delta);
                case Z -> anchor.z = snapped(dragStartAnchorZ + delta);
            }
        } else {
            switch (dragAxis) {
                case X -> anchor.offset_x = snapped(dragStartOffsetX + delta);
                case Y -> anchor.offset_y = snapped(dragStartOffsetY + delta);
                case Z -> anchor.offset_z = snapped(dragStartOffsetZ + delta);
            }
        }
    }

    private void applySizeDelta(double delta) {
        DialogueDefinition.Trigger trigger = trigger();
        String shape = normalizeShape(trigger.shape);

        switch (shape) {
            case "sphere" -> {
                if (dragAxis != GizmoAxis.NONE) {
                    trigger.radius = positiveSnapped(dragStartRadius + delta);
                }
            }

            case "box" -> {
                switch (dragAxis) {
                    case X -> trigger.size_x = positiveSnapped(dragStartSizeX + delta * 2.0D);
                    case Y -> trigger.size_y = positiveSnapped(dragStartSizeY + delta);
                    case Z -> trigger.size_z = positiveSnapped(dragStartSizeZ + delta * 2.0D);
                }
            }

            default -> {
                if (dragAxis == GizmoAxis.Y) {
                    trigger.height = positiveSnapped(dragStartHeight + delta);
                } else if (dragAxis == GizmoAxis.X || dragAxis == GizmoAxis.Z) {
                    trigger.radius = positiveSnapped(dragStartRadius + delta);
                }
            }
        }
    }

    private void applyTextureMoveDelta(double delta) {
        DialogueDefinition.ZoneVisual visual = trigger().visual;

        switch (dragAxis) {
            case X -> visual.texture_offset_x = snapped(dragStartTextureOffsetX + delta);
            case Y -> visual.texture_offset_y = snapped(dragStartTextureOffsetY + delta);
            case Z -> visual.texture_offset_z = snapped(dragStartTextureOffsetZ + delta);
        }

        visual.preset = "custom";
    }

    private void applyTextureScaleDelta(double delta) {
        DialogueDefinition.ZoneVisual visual = trigger().visual;
        String mode = visual.texture_mode != null ? visual.texture_mode.toLowerCase(Locale.ROOT) : "plane";

        switch (dragAxis) {
            case Y -> visual.texture_scale_y = Math.max(0.05D, dragStartTextureScaleY + delta * 0.5D);
            case X -> visual.texture_scale_x = Math.max(0.05D, dragStartTextureScaleX + delta * 0.5D);
            case Z -> {
                if ("plane".equals(mode)) {
                    visual.texture_scale_y = Math.max(0.05D, dragStartTextureScaleY + delta * 0.5D);
                } else {
                    visual.texture_scale_x = Math.max(0.05D, dragStartTextureScaleX + delta * 0.5D);
                }
            }
        }

        visual.preset = "custom";
    }

    private void beginRotationDrag(Ray ray, GizmoAxis axis) {
        if (axis == null || axis == GizmoAxis.NONE) return;

        Double angle = rotationAngle(ray, axis);
        if (angle == null) return;

        captureDragStart();
        dragRotationAxis = axis;
        hoveredRotationAxis = axis;
        rotationDragStartAngle = angle;
        dragChanged = false;
    }

    private void updateRotationDrag(Ray ray) {
        if (dragRotationAxis == GizmoAxis.NONE) return;

        Double angle = rotationAngle(ray, dragRotationAxis);
        if (angle == null) return;

        double deltaDegrees = Mth.wrapDegrees((float) Math.toDegrees(angle - rotationDragStartAngle));

        DialogueDefinition.ZoneVisual visual = trigger().visual;

        switch (dragRotationAxis) {
            case X -> visual.texture_rotation_x = dragStartTextureRotationX + deltaDegrees;
            case Y -> visual.texture_rotation = dragStartTextureRotationY + deltaDegrees;
            case Z -> visual.texture_rotation_z = dragStartTextureRotationZ + deltaDegrees;
            default -> {
                return;
            }
        }

        visual.preset = "custom";
        dragChanged = true;
    }

    private GizmoAxis pickRotationRing(Ray ray) {
        if (ray == null) return GizmoAxis.NONE;

        GizmoAxis bestAxis = GizmoAxis.NONE;
        double bestError = Double.MAX_VALUE;
        double threshold = handleSize() * 1.8D;
        double radius = rotationRingRadius();

        for (GizmoAxis axis : List.of(GizmoAxis.X, GizmoAxis.Y, GizmoAxis.Z)) {
            Vec3 hit = rotationPlaneIntersection(ray, axis);
            if (hit == null) continue;

            Vec3 center = gizmoCenter();
            double distance = switch (axis) {
                case X -> Math.sqrt((hit.y - center.y) * (hit.y - center.y) + (hit.z - center.z) * (hit.z - center.z));
                case Y -> Math.sqrt((hit.x - center.x) * (hit.x - center.x) + (hit.z - center.z) * (hit.z - center.z));
                case Z -> Math.sqrt((hit.x - center.x) * (hit.x - center.x) + (hit.y - center.y) * (hit.y - center.y));
                default -> 0.0D;
            };

            double error = Math.abs(distance - radius);
            if (error <= threshold && error < bestError) {
                bestError = error;
                bestAxis = axis;
            }
        }

        return bestAxis;
    }

    private Vec3 rotationPlaneIntersection(Ray ray, GizmoAxis axis) {
        Vec3 center = gizmoCenter();

        return switch (axis) {
            case X -> planeIntersection(ray, GizmoPlane.YZ, center);
            case Y -> planeIntersection(ray, GizmoPlane.XZ, center);
            case Z -> planeIntersection(ray, GizmoPlane.XY, center);
            default -> null;
        };
    }

    private Double rotationAngle(Ray ray, GizmoAxis axis) {
        if (ray == null || axis == null || axis == GizmoAxis.NONE) return null;

        Vec3 center = gizmoCenter();
        Vec3 hit = rotationPlaneIntersection(ray, axis);
        if (hit == null) return null;

        return switch (axis) {
            case X -> {
                double dy = hit.y - center.y;
                double dz = hit.z - center.z;
                yield dy * dy + dz * dz < 1.0E-6D ? null : Math.atan2(dz, dy);
            }
            case Y -> {
                double dx = hit.x - center.x;
                double dz = hit.z - center.z;
                yield dx * dx + dz * dz < 1.0E-6D ? null : Math.atan2(dz, dx);
            }
            case Z -> {
                double dx = hit.x - center.x;
                double dy = hit.y - center.y;
                yield dx * dx + dy * dy < 1.0E-6D ? null : Math.atan2(dy, dx);
            }
            default -> null;
        };
    }

    double rotationRingRadius() {
        DialogueDefinition.ZoneVisual visual = trigger().visual;
        String textureMode = visual.texture_mode != null ? visual.texture_mode.toLowerCase(Locale.ROOT) : "plane";

        double base = switch (textureMode) {
            case "box_wrap" -> Math.max(trigger().size_x, trigger().size_z) * 0.65D;
            case "cylinder_wrap" -> trigger().radius * 1.25D;
            default -> {
                double size = visual.size > 0.0D ? visual.size : Math.max(trigger().radius * 2.0D, Math.max(trigger().size_x, trigger().size_z));
                yield size * 0.65D * Math.max(visual.texture_scale_x, visual.texture_scale_y);
            }
        };

        return Math.max(0.75D, base);
    }

    private double snapped(double value) {
        double snap = snap();
        if (snap <= 0.0D) {
            return value;
        }
        return Math.round(value / snap) * snap;
    }

    private double positiveSnapped(double value) {
        return Math.max(0.1D, snapped(value));
    }

    private void placeAtCursor() {
        Vec3 hit = cursorHit != null ? cursorHit : raycastSurface(lastMouseX, lastMouseY);
        if (hit == null) {
            status = "No block surface under cursor";
            return;
        }

        convertToAbsolute(hit);
        status = String.format(Locale.ROOT, "Placed absolute anchor at %.2f %.2f %.2f", hit.x, hit.y, hit.z);
    }

    private void placeAtPlayer() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        Vec3 pos = minecraft.player.position();
        convertToAbsolute(pos);
        status = "Placed absolute anchor at player position";
    }

    private void toggleMarkerPlacement() {
        markerPlacementMode = !markerPlacementMode;
        dragAxis = GizmoAxis.NONE;
        dragPlane = GizmoPlane.NONE;
        status = markerPlacementMode ? "MARKER MODE: left-click a block surface" : "Marker placement cancelled";
        if (markerButton != null) markerButton.setMessage(markerText());
    }

    private Component markerText() {
        return Component.literal(markerPlacementMode ? "M CLICK" : "M Mark");
    }

    private void placeMarkerAtCursor() {
        Vec3 hit = cursorHit != null ? cursorHit : raycastSurface(lastMouseX, lastMouseY);
        if (hit == null) {
            status = "Marker: point at a block surface first";
            return;
        }

        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            status = "Marker: no active player connection";
            return;
        }

        if (!minecraft.player.hasPermissions(2)) {
            status = "Marker needs cheats/OP. Use P for an absolute anchor instead.";
            markerPlacementMode = false;
            if (markerButton != null) markerButton.setMessage(markerText());
            return;
        }

        String tag = markerTag();
        String command = String.format(Locale.ROOT, "summon minecraft:marker %.3f %.3f %.3f {Tags:[\"%s\"]}", hit.x, hit.y, hit.z, tag);

        minecraft.player.connection.sendCommand(command);

        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        anchor.type = "entity";
        anchor.target = "minecraft:marker";
        anchor.entity_tag = tag;
        anchor.pick = "nearest";
        anchor.offset_x = 0.0D;
        anchor.offset_y = 0.0D;
        anchor.offset_z = 0.0D;
        anchor.x = null;
        anchor.y = null;
        anchor.z = null;

        resolvedBlockAnchor = null;
        resolvedEntityAnchor = null;
        resolveCooldown = 2;
        markerPlacementMode = false;
        if (markerButton != null) markerButton.setMessage(markerText());
        status = "Marker placed/requested: " + tag + " (R if it is not highlighted yet)";
    }

    private String markerTag() {
        String workspace = project.workspace_id != null ? project.workspace_id : "project";
        workspace = workspace.replaceAll("[^a-zA-Z0-9]", "");
        if (workspace.length() > 8) workspace = workspace.substring(0, 8);
        if (workspace.isBlank()) workspace = "project";
        return "dlgstd_zone_" + workspace.toLowerCase(Locale.ROOT) + "_" + project.selected_trigger;
    }

    private void convertToAbsolute(Vec3 center) {
        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        anchor.type = "absolute";
        anchor.target = null;
        anchor.entity_tag = null;
        anchor.pick = "nearest";

        anchor.offset_x = 0.0D;
        anchor.offset_y = 0.0D;
        anchor.offset_z = 0.0D;

        anchor.x = snapped(center.x);
        anchor.y = snapped(center.y);
        anchor.z = snapped(center.z);

        resolvedBlockAnchor = null;
        resolvedEntityAnchor = null;
    }

    private void resolveAnchorNow() {
        resolvedBlockAnchor = null;
        resolvedEntityAnchor = null;

        switch (anchorType()) {
            case "block" -> resolveBlockAnchor();
            case "entity" -> resolveEntityAnchor();
        }
    }

    private void resolveEntityAnchor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        String target = anchor.target;

        double search = Math.max(16.0D, trigger().visual != null ? trigger().visual.preview_distance : 16.0D);
        search = Math.min(96.0D, search);

        AABB box = minecraft.player.getBoundingBox().inflate(search);

        List<Entity> candidates = minecraft.level.getEntities(minecraft.player, box, entity -> entity != null && entity.isAlive() && matchesEntity(entity, target) && matchesEntityTag(entity, anchor.entity_tag));

        resolvedEntityAnchor = candidates.stream().min(Comparator.comparingDouble(minecraft.player::distanceToSqr)).orElse(null);
    }

    private void resolveBlockAnchor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        String target = anchor.target;

        int horizontal = Mth.clamp((int) Math.ceil(Math.max(12.0D, trigger().visual != null ? trigger().visual.preview_distance : 16.0D)), 4, 32);

        int vertical = Mth.clamp((int) Math.ceil(Math.max(4.0D, anchor.search_height)), 4, 16);
        BlockPos playerPos = minecraft.player.blockPosition();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-horizontal, -vertical, -horizontal), playerPos.offset(horizontal, vertical, horizontal))) {

            BlockState state = minecraft.level.getBlockState(pos);
            if (!matchesBlock(state, target)) {
                continue;
            }

            double distance = minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }

        resolvedBlockAnchor = best;
    }

    DialogueEditorProject project() {
        return project;
    }

    DialogueDefinition.Trigger trigger() {
        return project.currentTrigger();
    }

    EditMode mode() {
        return mode;
    }

    GizmoAxis hoveredAxis() {
        return hoveredAxis;
    }

    int hoveredAxisSign() {
        return hoveredAxisSign;
    }

    GizmoAxis dragAxis() {
        return dragAxis;
    }

    int dragAxisSign() {
        return dragAxisSign;
    }

    GizmoPlane hoveredPlane() {
        return hoveredPlane;
    }

    GizmoPlane dragPlane() {
        return dragPlane;
    }

    boolean markerPlacementMode() {
        return markerPlacementMode;
    }

    boolean uiHidden() {
        return uiHidden;
    }

    BlockPos resolvedBlockAnchor() {
        return resolvedBlockAnchor;
    }

    Entity resolvedEntityAnchor() {
        return resolvedEntityAnchor;
    }

    Vec3 cursorHit() {
        return cursorHit;
    }

    Vec3 center() {
        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        Vec3 base = anchorBase();

        if (base == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                base = minecraft.player.position();
            } else {
                base = Vec3.ZERO;
            }
        }

        return base.add(anchor.offset_x, anchor.offset_y, anchor.offset_z);
    }

    Vec3 gizmoCenter() {
        if (!isTextureMode()) {
            return center();
        }

        DialogueDefinition.ZoneVisual visual = trigger().visual;
        Vec3 zone = center();
        return zone.add(visual.texture_offset_x, visual.y_offset + visual.texture_offset_y, visual.texture_offset_z);
    }

    double textureHorizontalExtent() {
        DialogueDefinition.Trigger trigger = trigger();
        DialogueDefinition.ZoneVisual visual = trigger.visual;
        String mode = visual.texture_mode != null ? visual.texture_mode.toLowerCase(Locale.ROOT) : "plane";

        return switch (mode) {
            case "box_wrap" -> Math.max(0.1D, Math.max(trigger.size_x, trigger.size_z) * 0.5D);
            case "cylinder_wrap" -> Math.max(0.1D, trigger.radius);
            default -> {
                double size = visual.size > 0.0D ? visual.size : Math.max(trigger.radius * 2.0D, Math.max(trigger.size_x, trigger.size_z));
                yield Math.max(0.1D, size * 0.5D);
            }
        };
    }

    double textureVerticalExtent() {
        DialogueDefinition.Trigger trigger = trigger();
        DialogueDefinition.ZoneVisual visual = trigger.visual;
        String mode = visual.texture_mode != null ? visual.texture_mode.toLowerCase(Locale.ROOT) : "plane";

        if ("plane".equals(mode)) {
            double size = visual.size > 0.0D ? visual.size : Math.max(trigger.radius * 2.0D, Math.max(trigger.size_x, trigger.size_z));
            return Math.max(0.1D, size * 0.5D);
        }

        double height = visual.visual_height > 0.0D ? visual.visual_height : ("box".equals(normalizeShape(trigger.shape)) ? trigger.size_y : trigger.height);

        return Math.max(0.1D, height);
    }

    GizmoAxis hoveredRotationAxis() {
        return hoveredRotationAxis;
    }

    GizmoAxis dragRotationAxis() {
        return dragRotationAxis;
    }

    Vec3 anchorBase() {
        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        String type = anchorType();

        if ("absolute".equals(type)) {
            if (anchor.x == null || anchor.y == null || anchor.z == null) {
                return null;
            }
            return new Vec3(anchor.x, anchor.y, anchor.z);
        }

        if ("entity".equals(type)) {
            if (resolvedEntityAnchor == null || !resolvedEntityAnchor.isAlive()) {
                return null;
            }
            return new Vec3(resolvedEntityAnchor.getX(), resolvedEntityAnchor.getY(), resolvedEntityAnchor.getZ());
        }

        if ("block".equals(type)) {
            if (resolvedBlockAnchor == null) {
                return null;
            }
            return new Vec3(resolvedBlockAnchor.getX() + 0.5D, resolvedBlockAnchor.getY() + 1.0D, resolvedBlockAnchor.getZ() + 0.5D);
        }

        return null;
    }

    boolean anchorResolved() {
        if ("absolute".equals(anchorType())) {
            DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
            return anchor.x != null && anchor.y != null && anchor.z != null;
        }
        return anchorBase() != null;
    }

    Vec3 gizmoEnd(GizmoAxis axis) {
        return gizmoEnd(axis, 1);
    }

    Vec3 gizmoEnd(GizmoAxis axis, int sign) {
        Vec3 center = gizmoCenter();
        Vec3 direction = axisVector(axis).scale(sign >= 0 ? 1.0D : -1.0D);
        double length = gizmoLength(axis);
        return center.add(direction.scale(length));
    }

    boolean supportsNegativeSizeHandle(GizmoAxis axis) {
        if (mode != EditMode.SIZE) return false;
        String shape = normalizeShape(trigger().shape);
        if ("sphere".equals(shape)) return axis != GizmoAxis.NONE;
        return axis == GizmoAxis.X || axis == GizmoAxis.Z;
    }

    double gizmoLength(GizmoAxis axis) {
        DialogueDefinition.Trigger trigger = trigger();

        if (mode == EditMode.MOVE || mode == EditMode.TEXTURE_MOVE) {
            Minecraft minecraft = Minecraft.getInstance();
            Camera camera = minecraft.gameRenderer.getMainCamera();
            double distance = camera.getPosition().distanceTo(gizmoCenter());
            return Mth.clamp(distance * 0.18D, 1.35D, 4.5D);
        }

        if (mode == EditMode.TEXTURE_SCALE) {
            DialogueDefinition.ZoneVisual visual = trigger.visual;
            double horizontal = textureHorizontalExtent() * Math.max(0.05D, visual.texture_scale_x);
            double vertical = textureVerticalExtent() * Math.max(0.05D, visual.texture_scale_y);

            return switch (axis) {
                case Y -> Math.max(0.45D, vertical);
                case X, Z -> Math.max(0.45D, horizontal);
                default -> 0.45D;
            };
        }

        if (mode == EditMode.TEXTURE_ROTATE) {
            return rotationRingRadius();
        }

        String shape = normalizeShape(trigger.shape);

        return switch (shape) {
            case "sphere" -> Math.max(0.35D, trigger.radius);
            case "box" -> switch (axis) {
                case X -> Math.max(0.35D, trigger.size_x * 0.5D);
                case Y -> Math.max(0.35D, trigger.size_y);
                case Z -> Math.max(0.35D, trigger.size_z * 0.5D);
                default -> 0.35D;
            };
            default -> axis == GizmoAxis.Y ? Math.max(0.35D, trigger.height) : Math.max(0.35D, trigger.radius);
        };
    }

    double handleSize() {
        Minecraft minecraft = Minecraft.getInstance();
        double distance = minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(gizmoCenter());
        return Mth.clamp(distance * 0.025D, 0.10D, 0.28D);
    }

    double planeInner() {
        return gizmoLength(GizmoAxis.X) * 0.20D;
    }

    double planeOuter() {
        return gizmoLength(GizmoAxis.X) * 0.47D;
    }

    Vec3[] planeCorners(GizmoPlane plane) {
        Vec3 c = gizmoCenter();
        double i = planeInner();
        double o = planeOuter();

        return switch (plane) {
            case XY -> new Vec3[]{c.add(i, i, 0), c.add(o, i, 0), c.add(o, o, 0), c.add(i, o, 0)};
            case XZ -> new Vec3[]{c.add(i, 0, i), c.add(o, 0, i), c.add(o, 0, o), c.add(i, 0, o)};
            case YZ -> new Vec3[]{c.add(0, i, i), c.add(0, o, i), c.add(0, o, o), c.add(0, i, o)};
            default -> new Vec3[0];
        };
    }

    private AxisPick pickAxis(Ray ray) {
        if (ray == null) {
            return new AxisPick(GizmoAxis.NONE, 1);
        }

        if (mode == EditMode.TEXTURE_ROTATE) {
            return new AxisPick(GizmoAxis.NONE, 1);
        }

        Vec3 center = gizmoCenter();
        double threshold = handleSize() * 1.9D;

        GizmoAxis best = GizmoAxis.NONE;
        int bestSign = 1;
        double bestDistance = Double.MAX_VALUE;

        for (GizmoAxis axis : List.of(GizmoAxis.X, GizmoAxis.Y, GizmoAxis.Z)) {
            int[] signs = mode == EditMode.SIZE && supportsNegativeSizeHandle(axis) ? new int[]{1, -1} : new int[]{1};

            for (int sign : signs) {
                Vec3 end = gizmoEnd(axis, sign);
                double segmentDistance = distanceRayToSegment(ray, center, end);
                double handleDistance = distanceRayToPoint(ray, end);
                double distance = Math.min(segmentDistance, handleDistance * 0.72D);

                if (distance <= threshold && distance < bestDistance) {
                    bestDistance = distance;
                    best = axis;
                    bestSign = sign;
                }
            }
        }

        return new AxisPick(best, bestSign);
    }

    private GizmoPlane pickPlane(Ray ray) {
        if (ray == null || (mode != EditMode.MOVE && mode != EditMode.TEXTURE_MOVE)) {
            return GizmoPlane.NONE;
        }

        GizmoPlane best = GizmoPlane.NONE;
        double bestRayDistance = Double.MAX_VALUE;

        for (GizmoPlane plane : List.of(GizmoPlane.XY, GizmoPlane.XZ, GizmoPlane.YZ)) {
            Vec3 hit = planeIntersection(ray, plane, gizmoCenter());
            if (hit == null || !insidePlaneHandle(hit, plane)) continue;
            double rayDistance = hit.distanceToSqr(ray.origin);
            if (rayDistance < bestRayDistance) {
                bestRayDistance = rayDistance;
                best = plane;
            }
        }

        return best;
    }

    private boolean insidePlaneHandle(Vec3 hit, GizmoPlane plane) {
        Vec3 d = hit.subtract(gizmoCenter());
        double i = planeInner();
        double o = planeOuter();
        return switch (plane) {
            case XY -> d.x >= i && d.x <= o && d.y >= i && d.y <= o;
            case XZ -> d.x >= i && d.x <= o && d.z >= i && d.z <= o;
            case YZ -> d.y >= i && d.y <= o && d.z >= i && d.z <= o;
            default -> false;
        };
    }

    private static Vec3 planeIntersection(Ray ray, GizmoPlane plane, Vec3 point) {
        Vec3 normal = switch (plane) {
            case XY -> new Vec3(0, 0, 1);
            case XZ -> new Vec3(0, 1, 0);
            case YZ -> new Vec3(1, 0, 0);
            default -> Vec3.ZERO;
        };

        double denominator = ray.direction.dot(normal);
        if (Math.abs(denominator) < 1.0E-5D) return null;
        double t = point.subtract(ray.origin).dot(normal) / denominator;
        if (t < 0.0D || !Double.isFinite(t)) return null;
        return ray.origin.add(ray.direction.scale(t));
    }

    private Ray mouseRay(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 origin = camera.getPosition();

        Vector3f forwardF = camera.getLookVector();
        Vector3f upF = camera.getUpVector();
        Vector3f leftF = camera.getLeftVector();

        Vec3 forward = new Vec3(forwardF.x(), forwardF.y(), forwardF.z()).normalize();
        Vec3 up = new Vec3(upF.x(), upF.y(), upF.z()).normalize();
        Vec3 right = new Vec3(-leftF.x(), -leftF.y(), -leftF.z()).normalize();

        double nx = mouseX / Math.max(1.0D, width) * 2.0D - 1.0D;
        double ny = 1.0D - mouseY / Math.max(1.0D, height) * 2.0D;

        double fovDegrees = minecraft.options.fov().get();
        double tan = Math.tan(Math.toRadians(fovDegrees) * 0.5D);
        double aspect = width / (double) Math.max(1, height);

        Vec3 direction = forward.add(right.scale(nx * aspect * tan)).add(up.scale(ny * tan)).normalize();

        return new Ray(origin, direction);
    }

    private Vec3 raycastSurface(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        Ray ray = mouseRay(mouseX, mouseY);
        if (ray == null) {
            return null;
        }

        Vec3 end = ray.origin.add(ray.direction.scale(128.0D));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(ray.origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));

        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }

        return hit.getLocation();
    }

    private static double distanceRayToSegment(Ray ray, Vec3 a, Vec3 b) {
        double best = Double.MAX_VALUE;
        final int samples = 48;

        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            Vec3 point = a.lerp(b, t);
            double rayT = Math.max(0.0D, point.subtract(ray.origin).dot(ray.direction));
            Vec3 onRay = ray.origin.add(ray.direction.scale(rayT));
            best = Math.min(best, point.distanceTo(onRay));
        }

        return best;
    }

    private static double distanceRayToPoint(Ray ray, Vec3 point) {
        double rayT = Math.max(0.0D, point.subtract(ray.origin).dot(ray.direction));
        Vec3 onRay = ray.origin.add(ray.direction.scale(rayT));
        return point.distanceTo(onRay);
    }

    private static String axisLabel(GizmoAxis axis, int sign) {
        if (axis == GizmoAxis.NONE) return "NONE";
        return axis.name() + (sign < 0 ? "-" : "+");
    }

    private static double axisParameter(Ray ray, Vec3 axisOrigin, Vec3 axisDirection) {
        Vec3 d = ray.direction.normalize();
        Vec3 a = axisDirection.normalize();
        Vec3 w0 = ray.origin.subtract(axisOrigin);

        double b = d.dot(a);
        double d0 = d.dot(w0);
        double e = a.dot(w0);
        double denominator = 1.0D - b * b;

        if (Math.abs(denominator) < 1.0E-5D) {
            double cameraDistance = ray.origin.distanceTo(axisOrigin);
            Vec3 approximate = ray.origin.add(ray.direction.scale(cameraDistance));
            return approximate.subtract(axisOrigin).dot(a);
        }

        return (e - b * d0) / denominator;
    }

    private static Vec3 axisVector(GizmoAxis axis) {
        return switch (axis) {
            case X -> new Vec3(1.0D, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, 1.0D, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, 1.0D);
            default -> Vec3.ZERO;
        };
    }

    private String anchorType() {
        DialogueDefinition.ZoneAnchor anchor = trigger().anchor;
        return anchor != null && anchor.type != null ? anchor.type.toLowerCase(Locale.ROOT) : "absolute";
    }

    private static String normalizeShape(String shape) {
        if (shape == null) {
            return "cylinder";
        }

        return switch (shape.toLowerCase(Locale.ROOT)) {
            case "sphere", "box", "cylinder" -> shape.toLowerCase(Locale.ROOT);
            default -> "cylinder";
        };
    }

    private static boolean matchesEntity(Entity entity, String target) {
        if (target == null || target.isBlank() || "*".equals(target)) {
            return true;
        }

        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            if (id == null) {
                return false;
            }

            TagKey<net.minecraft.world.entity.EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, id);
            return entity.getType().is(tag);
        }

        ResourceLocation id = ResourceLocation.tryParse(target);
        return id != null && id.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    private static boolean matchesEntityTag(Entity entity, String tag) {
        return tag == null || tag.isBlank() || entity.getTags().contains(tag);
    }

    private static boolean matchesBlock(BlockState state, String target) {
        if (target == null || target.isBlank() || "*".equals(target)) {
            return true;
        }

        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            if (id == null) {
                return false;
            }

            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
            return state.is(tag);
        }

        ResourceLocation id = ResourceLocation.tryParse(target);
        return id != null && id.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    private record AxisPick(GizmoAxis axis, int sign) {
    }

    private record Ray(Vec3 origin, Vec3 direction) {
    }
}
