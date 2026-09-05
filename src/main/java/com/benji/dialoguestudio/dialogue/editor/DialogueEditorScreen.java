package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.DialogueStudio;

import com.benji.dialoguestudio.dialogue.DialogueRegistry;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class DialogueEditorScreen extends DialogueRetroScreen {

    public enum Tab {
        PROJECT("Project"), DIALOGUE("Dialogue"), VISUALS("Visuals"), LINES("Lines"), LINE_OVERRIDES("Line+"), NODES("Nodes"), LAYOUT("Layout"), TRIGGERS("Triggers"), ZONE("Zone"), ZONE_FX("Zone FX"), GAMEPLAY("Gameplay"), EXPORT("Export");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum LayoutElement {FRAME, TEXT, SPRITE}

    private enum DragAxis {NONE, X, Y, SCALE}

    private static int LEFT = 338;
    private static LayoutElement LAYOUT_ELEMENT = LayoutElement.FRAME;
    private static String STATUS = "Ready";

    private String toastText;
    private int toastTicks = -1;
    private static final int TOAST_DURATION = 72;

    private static final EnumMap<Tab, Integer> SCROLL_BY_TAB = new EnumMap<>(Tab.class);
    private static int LINE_TIMELINE_SCROLL = 0;
    private static final ResourceLocation EDITOR_DEFAULT_SPRITE = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/default_sprite.png");

    private final DialogueEditorProject project;
    private final Tab tab;
    private final List<Label> labels = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private int previewTicks;
    private DialogueEditorPreview.Transform previewTransform;
    private DragAxis dragAxis = DragAxis.NONE;
    private int scrollOffset;
    private int bodyTop = 30;
    private int rowSpacing = 34;
    private int controlHeight = 20;
    private int timelineHeight = 74;
    private DialogueEditorUiSettings uiSettings;

    private int dragLineIndex = -1;
    private boolean dragLineMoved;
    private int timelineY = -1;
    private final List<Tip> tips = new ArrayList<>();

    private String previewTargetId;
    private LivingEntity previewTargetEntity;

    public DialogueEditorScreen() {
        this(DialogueEditorProject.createDefault(), Tab.PROJECT);
    }

    public DialogueEditorScreen(DialogueEditorProject project, Tab tab) {
        super(Component.literal("Dialogue Studio"));
        this.project = project != null ? project : DialogueEditorProject.createDefault();
        this.project.normalize();
        this.tab = tab != null ? tab : Tab.PROJECT;
        this.scrollOffset = SCROLL_BY_TAB.getOrDefault(this.tab, 0);
    }

    @Override
    protected void init() {
        labels.clear();
        tips.clear();
        scrollWidgets.clear();
        DialogueEditorFontPreviewPack.ensureLoaded(project);

        uiSettings = DialogueEditorUiSettings.get();
        LEFT = uiSettings.resolvedInspectorWidth(width);

        if (tab == Tab.ZONE_FX) {
            LEFT = Math.min(Math.max(338, width - 300), Math.max(370, LEFT));
        }

        rowSpacing = uiSettings.resolvedRowSpacing(height);
        controlHeight = uiSettings.resolvedControlHeight(height);
        timelineHeight = uiSettings.resolvedTimelineHeight(height);

        bodyTop = buildResponsiveTabs();
        buildStudioToolbar();

        switch (tab) {
            case PROJECT -> initProject();
            case DIALOGUE -> initDialogue();
            case VISUALS -> initVisuals();
            case LINES -> initLines();
            case LINE_OVERRIDES -> initLineOverrides();
            case NODES -> initNodes();
            case LAYOUT -> initLayout();
            case TRIGGERS -> initTriggers();
            case ZONE -> initZone();
            case ZONE_FX -> initZoneFx();
            case GAMEPLAY -> initGameplay();
            case EXPORT -> initExport();
        }

        updateScrollWidgetVisibility();
    }

    private int buildResponsiveTabs() {
        int margin = 6;
        int gap = 2;
        int tabH = 20;
        int rowGap = 3;
        int count = Tab.values().length;
        int minTabW = uiSettings != null ? uiSettings.resolvedMinTabWidth() : 64;

        int available = Math.max(1, width - margin * 2);
        int perRow = Math.max(1, Math.min(count, (available + gap) / (minTabW + gap)));
        int rows = (count + perRow - 1) / perRow;

        perRow = Math.max(1, (count + rows - 1) / rows);

        int tabW = Math.max(46, (available - gap * (perRow - 1)) / perRow);

        int index = 0;
        for (Tab t : Tab.values()) {
            int row = index / perRow;
            int column = index % perRow;
            int x = margin + column * (tabW + gap);
            int y = 6 + row * (tabH + rowGap);

            Button tabButton = DialogueRetroButton.retroBuilder(Component.literal(t.label), b -> reopen(t)).bounds(x, y, tabW, tabH).selected(t == tab).tabStyle(true).build();

            if (project.definition.graph_enabled && (t == Tab.LINES || t == Tab.LINE_OVERRIDES)) {
                tabButton.active = false;
            }

            addRenderableWidget(tabButton);

            index++;
        }

        return 6 + rows * (tabH + rowGap) + 4;
    }

    private void buildStudioToolbar() {
        int y = bodyTop + 2;
        int buttonH = Math.max(16, Math.min(20, controlHeight));

        int resetW = Math.min(78, Math.max(60, LEFT / 4));
        int allW = Math.min(72, Math.max(58, LEFT / 4));

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Reset tab"), b -> confirmResetCurrentTab()).bounds(LEFT - resetW - allW - 18, y, resetW, buttonH).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Reset all"), b -> confirmResetAll()).bounds(LEFT - allW - 12, y, allW, buttonH).build());
    }

    private void initProject() {
        field("Project name", project.project_name, 0, s -> project.project_name = s, 96);
        field("Namespace / mod id", project.namespace, 1, s -> {
            String old = project.namespace;
            String clean = DialogueEditorProject.sanitizeNamespace(s);
            project.rewriteNamespace(old, clean);
        }, 64);
        field("Dialogue path", project.dialogue_path, 2, s -> {
            String old = project.dialogue_path;
            project.rewriteDialoguePath(old, DialogueEditorProject.sanitizePath(s));
        }, 128);
        field("Preview language", project.preview_locale, 3, s -> project.preview_locale = s.toLowerCase(Locale.ROOT), 24);

        button("Save Studio project", 4, b -> saveProject());
        button("Project browser", 5, b -> minecraft.setScreen(new DialogueEditorProjectBrowserScreen(this)));
        button("Open project.json manually", 6, b -> openProjectPicker());
        button("Import language JSON", 7, b -> pickFile(".json", path -> {
            try {
                DialogueEditorWorkspace.importLang(project, path);
                DialogueEditorWorkspace.save(project);
                STATUS = "Imported language: " + path.getFileName();
            } catch (Exception e) {
                STATUS = "Language import failed: " + e.getMessage();
            }
            reopen(Tab.PROJECT);
        }));
        button(project.animate_preview ? "Preview animation: ON" : "Preview animation: OFF", 8, b -> {
            project.animate_preview = !project.animate_preview;
            reopen(Tab.PROJECT);
        });
        button("Import existing dialogue JSON", 9, b -> pickFile(".json", path -> {
            try {
                DialogueEditorWorkspace.importDialogueJson(project, path);
                DialogueEditorWorkspace.save(project);
                STATUS = "Imported dialogue: " + path.getFileName();
            } catch (Exception e) {
                STATUS = "Dialogue import failed: " + e.getMessage();
            }
            reopen(Tab.PROJECT);
        }));
        button("Quick export packs", 10, b -> exportProject(false));

        cycleButton("Studio UI preset", uiSettings.preset, 11, List.of("auto", "compact", "comfortable", "large", "preview_focus", "custom"), s -> uiSettings.setPreset(s));

        smallFields("Inspector width", String.valueOf(uiSettings.inspector_width), s -> {
            uiSettings.inspector_width = i(s, uiSettings.inspector_width);
            uiSettings.makeCustom();
        }, "Row spacing", String.valueOf(uiSettings.row_spacing), s -> {
            uiSettings.row_spacing = i(s, uiSettings.row_spacing);
            uiSettings.makeCustom();
        }, 12);

        smallFields("Button height", String.valueOf(uiSettings.control_height), s -> {
            uiSettings.control_height = i(s, uiSettings.control_height);
            uiSettings.makeCustom();
        }, "Timeline height", String.valueOf(uiSettings.timeline_height), s -> {
            uiSettings.timeline_height = i(s, uiSettings.timeline_height);
            uiSettings.makeCustom();
        }, 13);

        button("Apply Studio UI layout", 14, b -> {
            DialogueEditorUiSettings.save();
            reopen(tab);
        });
        button("Reset Studio UI layout", 15, b -> {
            uiSettings.reset();
            STATUS = "Studio UI layout reset.";
            reopen(tab);
        });

        help(17, "Save Project = internal Studio workspace only (project.json).");
        help(18, "Export = actual datapack + resource pack + ready .zip files.");
        help(19, "UI preset/layout is editor-only and is saved globally in ui_settings.json.");
        help(20, "Top tabs wrap automatically on small GUI scales.");
        help(21, "Right Shift opens/closes Dialogue Studio. Mouse wheel scrolls this panel.");
    }

    private void initDialogue() {
        assetField("Voice sound id", project.definition.voice, 0, ".ogg", true, s -> project.definition.voice = blankToNull(s));
        cycleButton("Voice source", project.definition.voice_source, 1, List.of("master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient", "voice"), s -> project.definition.voice_source = s);
        smallFields("Pitch", String.valueOf(project.definition.voice_pitch), s -> project.definition.voice_pitch = f(s, project.definition.voice_pitch), "Volume", String.valueOf(project.definition.voice_volume), s -> project.definition.voice_volume = f(s, project.definition.voice_volume), 2);
        smallFields("Voice every", String.valueOf(project.definition.voice_every), s -> project.definition.voice_every = i(s, project.definition.voice_every), "Char ticks", String.valueOf(project.definition.char_ticks), s -> project.definition.char_ticks = i(s, project.definition.char_ticks), 3);
        smallFields("Hold ticks", String.valueOf(project.definition.hold_ticks), s -> project.definition.hold_ticks = i(s, project.definition.hold_ticks), "Fade ticks", String.valueOf(project.definition.fade_ticks), s -> project.definition.fade_ticks = i(s, project.definition.fade_ticks), 4);
        colorField("Default text color", project.definition.text_color, 5, s -> project.definition.text_color = s);
        field("Gradient: color,color,color (blank = none)", join(project.definition.text_gradient), 6, s -> project.definition.text_gradient = parseGradient(s, false), 256);
        cycleButton("Legacy single text effect", project.definition.text_effect, 7, List.of("normal", "wave", "shake", "explode", "linear"), s -> project.definition.text_effect = s);

        button("Combined effects: " + effectsSummary(project.definition.text_effects, false), 8, b -> minecraft.setScreen(new DialogueEditorTextEffectsScreen(this, project.definition.text_effects, false, effects -> {
            project.definition.text_effects = effects;
            reopen(tab);
        })));

        toggleButton("Markdown default", project.definition.markdown, 9, value -> project.definition.markdown = value);

        button("GLOBAL default font: " + fontSummary(project.definition.text_font), 10, b -> minecraft.setScreen(new DialogueEditorFontPickerScreen(this, project, project.definition.text_font, value -> {
            project.definition.text_font = value;
            reopen(tab);
        })));

        button(globalOutlineSummary(), 11, b -> minecraft.setScreen(DialogueOutlineEditorScreen.global(this, project)));

        button("Test current voice", 13, b -> testVoice());
        help(14, "Markdown: **bold**  *italic*  ~~strike~~  __underline__. It is OFF by default for old packs.");
        help(15, "Font hierarchy: GLOBAL -> LINE -> REGION. A Line/Region override wins over the global font.");
        help(16, "Custom TTF/MSDF fonts activate after Export/Install + F3+T. Outline has its own compact editor.");
    }


    private void initVisuals() {
        assetField("Frame texture", project.definition.frame, 0, ".png", false, s -> project.definition.frame = blankToNull(s));
        assetField("Background texture", project.definition.background, 1, ".png", false, s -> project.definition.background = blankToNull(s));
        smallFields("BG alpha", String.valueOf(project.definition.background_alpha), s -> project.definition.background_alpha = f(s, project.definition.background_alpha), "BG bob", String.valueOf(project.definition.background_bob), s -> project.definition.background_bob = f(s, project.definition.background_bob), 2);
        field("Background speed", String.valueOf(project.definition.background_speed), 3, s -> project.definition.background_speed = f(s, project.definition.background_speed), 32);
        cycleButton("Default sprite position", project.definition.sprite_position, 4, List.of("left", "center", "right"), s -> project.definition.sprite_position = s);
        cycleButton("Default sprite transition", project.definition.sprite_transition, 5, List.of("none", "bounce", "sway", "fade_up"), s -> project.definition.sprite_transition = s);
        smallFields("Move ticks", String.valueOf(project.definition.sprite_move_ticks), s -> project.definition.sprite_move_ticks = i(s, project.definition.sprite_move_ticks), "Transition ticks", String.valueOf(project.definition.sprite_transition_ticks), s -> project.definition.sprite_transition_ticks = i(s, project.definition.sprite_transition_ticks), 6);
        help(8, "PNG picker copies files into the editor workspace.");
        help(9, "Export writes them to assets/<namespace>/textures/gui/dialogue/.");
    }

    private void initLines() {
        if (project.definition.graph_enabled) {
            initLegacyLineTabLocked();
            return;
        }

        DialogueDefinition.Line line = project.currentLine();
        navButtons("Line", project.selected_line, project.definition.lines.size(), 0, () -> {
            project.selected_line = Math.max(0, project.selected_line - 1);
            reopen(tab);
        }, () -> {
            project.selected_line = Math.min(project.definition.lines.size() - 1, project.selected_line + 1);
            reopen(tab);
        }, () -> addLine(), () -> removeLine());

        boolean literal = line.literal != null;
        button("Text mode: " + (literal ? "LITERAL" : "LANG"), 1, b -> {
            if (literal) {
                String old = line.literal != null ? line.literal : "";
                line.literal = null;
                project.ensureLangKey(line, project.selected_line);
                project.setLocalizedText(project.preview_locale, line, project.selected_line, old);
            } else {
                line.literal = project.getLocalizedText(project.preview_locale, line, project.selected_line);
                line.text = null;
            }
            reopen(tab);
        });

        String textValue = literal ? nullToEmpty(line.literal) : project.getLocalizedText(project.preview_locale, line, project.selected_line);
        field(literal ? "Line text" : "Translation text (" + project.preview_locale + ")", textValue, 2, value -> {
            if (line.literal != null) line.literal = value;
            else project.setLocalizedText(project.preview_locale, line, project.selected_line, value);
        }, 2048);

        if (!literal)
            field("Translation key", project.ensureLangKey(line, project.selected_line), 3, s -> line.text = s, 256);
        else help(3, "Switch to LANG to generate/edit language files visually.");

        assetField("Sprite PNG", line.sprite, 4, ".png", false, s -> line.sprite = blankToNull(s));
        colorField("Text color override (blank = inherit)", nullToEmpty(line.text_color), 5, s -> line.text_color = blankToNull(s));
        field("Gradient override: blank=inherit, none=disable", gradientOverride(line.text_gradient), 6, s -> line.text_gradient = parseGradient(s, true), 256);
        cycleNullableButton("Text effect override", line.text_effect, 7, List.of("normal", "wave", "shake", "explode", "linear"), s -> line.text_effect = s);
        assetField("Voice override", line.voice, 8, ".ogg", true, s -> line.voice = blankToNull(s));
        smallFields("Pitch", nullable(line.voice_pitch), s -> line.voice_pitch = nf(s), "Volume", nullable(line.voice_volume), s -> line.voice_volume = nf(s), 9);
        field("Voice every override (blank = inherit)", nullable(line.voice_every), 10, s -> line.voice_every = ni(s), 32);

        button("Combined effects: " + effectsSummary(line.text_effects, true), 11, b -> minecraft.setScreen(new DialogueEditorTextEffectsScreen(this, line.text_effects, true, effects -> {
            line.text_effects = effects;
            reopen(tab);
        })));

        button("Rich Text regions: " + richRegionCount(line) + "  •  open visual editor", 12, b -> {
            String resolved = line.literal != null ? nullToEmpty(line.literal) : project.getLocalizedText(project.preview_locale, line, project.selected_line);

            String locale = line.literal != null ? null : project.preview_locale;

            minecraft.setScreen(new DialogueRichTextEditorScreen(this, project, line, resolved, locale, "Line " + (project.selected_line + 1)));
        });
    }

    private void initLineOverrides() {
        if (project.definition.graph_enabled) {
            initLegacyLineTabLocked();
            return;
        }

        DialogueDefinition.Line line = project.currentLine();
        navButtons("Line", project.selected_line, project.definition.lines.size(), 0, () -> {
            project.selected_line = Math.max(0, project.selected_line - 1);
            reopen(tab);
        }, () -> {
            project.selected_line = Math.min(project.definition.lines.size() - 1, project.selected_line + 1);
            reopen(tab);
        }, this::addLine, this::removeLine);

        smallFields("Char ticks", nullable(line.char_ticks), s -> line.char_ticks = ni(s), "Hold ticks", nullable(line.hold_ticks), s -> line.hold_ticks = ni(s), 1);
        assetField("Frame override", line.frame, 2, ".png", false, s -> line.frame = blankToNull(s));
        assetField("Background override", line.background, 3, ".png", false, s -> line.background = blankToNull(s));
        cycleNullableButton("Sprite position override", line.sprite_position, 4, List.of("left", "center", "right"), s -> line.sprite_position = s);
        field("Exact sprite X (blank = position preset)", nullable(line.sprite_x), 5, s -> line.sprite_x = nf(s), 32);
        cycleNullableButton("Sprite transition override", line.sprite_transition, 6, List.of("none", "bounce", "sway", "fade_up"), s -> line.sprite_transition = s);
        smallFields("Move ticks", nullable(line.sprite_move_ticks), s -> line.sprite_move_ticks = ni(s), "Transition ticks", nullable(line.sprite_transition_ticks), s -> line.sprite_transition_ticks = ni(s), 7);
        smallFields("Sprite width", nullable(line.sprite_width), s -> line.sprite_width = ni(s), "Sprite height", nullable(line.sprite_height), s -> line.sprite_height = ni(s), 8);
        cycleNullableButton("Voice source override", line.voice_source, 9, List.of("master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient", "voice"), s -> line.voice_source = s);
        help(10, "Blank/INHERIT means this line uses the global value.");
    }

    private void initLegacyLineTabLocked() {
        help(0, "Nodes v3 runtime is ON. Legacy Lines / Line+ are locked so you do not accidentally edit data that the graph runtime ignores.");
        help(2, "Edit dialogue text, sprite, voice and per-line overrides inside each LINE or CHOICE node instead.");
        button("Open Nodes", 4, b -> reopen(Tab.NODES));
        button("Open visual Node Graph", 5, b -> minecraft.setScreen(new DialogueNodeGraphScreen(this, project)));
    }

    private void initNodes() {
        DialogueDefinition d = project.definition;

        toggleButton("Nodes v3 runtime", d.graph_enabled, 0, value -> {
            d.graph_enabled = value;
            if (value) d.format = Math.max(3, d.format);
        });

        int nodeRowOffset = d.graph_enabled ? 1 : 0;

        if (d.graph_enabled) {
            help(1, "Nodes v3 is ON — legacy Lines / Line+ are locked. Edit text, sprite, voice and overrides inside the nodes.");
        }

        if (d.nodes == null || d.nodes.isEmpty()) {
            help(2 + nodeRowOffset, "No node graph yet. Your old linear lines are still untouched and fully supported.");

            button("Convert current legacy lines -> Nodes v3", 3 + nodeRowOffset, b -> {
                project.convertLegacyLinesToGraph();
                DialogueEditorHistory.checkpoint(project);
                reopen(Tab.NODES);
            });

            help(5 + nodeRowOffset, "Conversion COPIES the old lines into graph nodes. It does not delete the legacy lines.");
            help(6 + nodeRowOffset, "This makes it safe to test Nodes v3 and switch graph_enabled back OFF if needed.");
            return;
        }

        field("Start node", nullToEmpty(d.start_node), 1 + nodeRowOffset, s -> d.start_node = blankToNull(s), 96);
        button("Open visual Node Graph", 2 + nodeRowOffset, b -> minecraft.setScreen(new DialogueNodeGraphScreen(this, project)));
        button("Selected node: " + (project.selected_node != null ? project.selected_node : "<none>"), 3 + nodeRowOffset, b -> minecraft.setScreen(new DialogueNodeGraphScreen(this, project)));
        button("+ LINE node", 4 + nodeRowOffset, b -> {
            project.addNode("line");
            reopen(Tab.NODES);
        });

        button("+ CHOICE node", 5 + nodeRowOffset, b -> {
            project.addNode("choice");
            reopen(Tab.NODES);
        });

        button("+ CONDITION node", 6 + nodeRowOffset, b -> {
            project.addNode("condition");
            reopen(Tab.NODES);
        });

        button("+ EVENT node", 7 + nodeRowOffset, b -> {
            project.addNode("event");
            reopen(Tab.NODES);
        });

        button("+ END node", 8 + nodeRowOffset, b -> {
            project.addNode("end");
            reopen(Tab.NODES);
        });

        help(10 + nodeRowOffset, "LINE -> dialogue line | CHOICE -> player answers | CONDITION -> server branch | EVENT -> external event | END -> finish.");
        help(11 + nodeRowOffset, "Graph traversal and choice conditions are server-authoritative; the client only displays the node the server approved.");
        help(12 + nodeRowOffset, "Old 'lines' remain in the JSON and run whenever Nodes v3 runtime is OFF.");
    }


    private void initLayout() {
        DialogueDefinition.Layout l = project.definition.layout;
        button("Gizmo target: " + LAYOUT_ELEMENT, 0, b -> {
            LAYOUT_ELEMENT = LayoutElement.values()[(LAYOUT_ELEMENT.ordinal() + 1) % LayoutElement.values().length];
            reopen(tab);
        });
        smallFields("Frame X", String.valueOf(l.frame_x), s -> l.frame_x = i(s, l.frame_x), "Frame Y", String.valueOf(l.frame_y), s -> l.frame_y = i(s, l.frame_y), 1);
        smallFields("Frame W", String.valueOf(l.frame_width), s -> l.frame_width = i(s, l.frame_width), "Frame H", String.valueOf(l.frame_height), s -> l.frame_height = i(s, l.frame_height), 2);
        smallFields("Text X", String.valueOf(l.text_x), s -> l.text_x = i(s, l.text_x), "Text Y", String.valueOf(l.text_y), s -> l.text_y = i(s, l.text_y), 3);
        smallFields("Text width", String.valueOf(l.text_width), s -> l.text_width = i(s, l.text_width), "Text scale", String.valueOf(l.text_scale), s -> l.text_scale = f(s, l.text_scale), 4);
        smallFields("Line height", String.valueOf(l.line_height), s -> l.line_height = i(s, l.line_height), "Sprite Y", String.valueOf(l.sprite_y), s -> l.sprite_y = i(s, l.sprite_y), 5);
        smallFields("Sprite W", String.valueOf(l.sprite_width), s -> l.sprite_width = i(s, l.sprite_width), "Sprite H", String.valueOf(l.sprite_height), s -> l.sprite_height = i(s, l.sprite_height), 6);
        smallFields("Left X", String.valueOf(l.sprite_left_x), s -> l.sprite_left_x = f(s, l.sprite_left_x), "Center X", String.valueOf(l.sprite_center_x), s -> l.sprite_center_x = f(s, l.sprite_center_x), 7);
        field("Right X", String.valueOf(l.sprite_right_x), 8, s -> l.sprite_right_x = f(s, l.sprite_right_x), 32);
        help(10, "Drag RED = X, GREEN = Y, BLUE = resize/scale in the preview.");
    }

    private void initTriggers() {
        DialogueDefinition.Trigger t = project.currentTrigger();
        navButtons("Trigger", project.selected_trigger, project.definition.triggers.size(), 0, () -> {
            project.selected_trigger = Math.max(0, project.selected_trigger - 1);
            reopen(tab);
        }, () -> {
            project.selected_trigger = Math.min(project.definition.triggers.size() - 1, project.selected_trigger + 1);
            reopen(tab);
        }, this::addTrigger, this::removeTrigger);

        cycleButton("Type", t.type, 1, List.of("manual", "external", "right_click_entity", "right_click_block", "proximity_entity", "proximity_block", "hit_entity", "shift_near_entity", "look_at_entity", "kill_entity", "enter_area", "zone"), s -> t.type = s);
        targetField("Target registry id / #tag", t.target, 2, s -> t.target = blankToNull(s));
        smallFields("Radius", String.valueOf(t.radius), s -> t.radius = d(s, t.radius), "Look angle", String.valueOf(t.look_angle), s -> t.look_angle = d(s, t.look_angle), 3);
        smallFields("Check interval", String.valueOf(t.check_interval), s -> t.check_interval = i(s, t.check_interval), "Cooldown", String.valueOf(t.cooldown_ticks), s -> t.cooldown_ticks = i(s, t.cooldown_ticks), 4);
        toggleButton("Consume", t.consume, 5, value -> t.consume = value);
        cycleNullableButton("Once override", t.once, 6, List.of("never", "player", "entity", "session"), s -> t.once = s);
        field("Dimension (blank = any)", nullToEmpty(t.dimension), 7, s -> t.dimension = blankToNull(s), 128);
        field("External event id", nullToEmpty(t.event), 8, s -> t.event = blankToNull(s), 128);
        help(10, "For zone triggers use the Zone and Zone FX tabs for visual editing.");
    }

    private void initZone() {
        DialogueDefinition.Trigger t = project.currentTrigger();

        if ("enter_area".equalsIgnoreCase(t.type)) {
            help(0, "Legacy/exact enter_area editor");
            tripleFields("Min X", nullable(t.min_x), v -> t.min_x = nd(v), "Min Y", nullable(t.min_y), v -> t.min_y = nd(v), "Min Z", nullable(t.min_z), v -> t.min_z = nd(v), 1);
            tripleFields("Max X", nullable(t.max_x), v -> t.max_x = nd(v), "Max Y", nullable(t.max_y), v -> t.max_y = nd(v), "Max Z", nullable(t.max_z), v -> t.max_z = nd(v), 2);
            tripleFields("Center X", nullable(t.x), v -> t.x = nd(v), "Center Y", nullable(t.y), v -> t.y = nd(v), "Center Z", nullable(t.z), v -> t.z = nd(v), 3);
            field("Sphere radius (used with center X/Y/Z)", String.valueOf(t.radius), 4, v -> t.radius = d(v, t.radius), 32);
            field("Dimension", nullToEmpty(t.dimension), 5, v -> t.dimension = blankToNull(v), 128);
            button("Use my current player position as center", 6, b -> {
                if (minecraft.player != null) {
                    t.x = minecraft.player.getX();
                    t.y = minecraft.player.getY();
                    t.z = minecraft.player.getZ();
                    reopen(tab);
                }
            });
            button("Convert to visual ZONE trigger", 7, b -> {
                t.type = "zone";
                if (t.anchor == null) t.anchor = new DialogueDefinition.ZoneAnchor();
                t.anchor.type = "absolute";
                t.anchor.x = t.x;
                t.anchor.y = t.y;
                t.anchor.z = t.z;
                reopen(tab);
            });
            help(9, "For new content, ZONE is easier and supports visual previews.");
            return;
        }

        if (!"zone".equalsIgnoreCase(t.type)) {
            help(1, "Selected trigger is not a zone or enter_area.");
            button("Convert selected trigger to ZONE", 2, b -> {
                t.type = "zone";
                if (t.anchor == null) t.anchor = new DialogueDefinition.ZoneAnchor();
                reopen(tab);
            });
            return;
        }
        if (t.anchor == null) t.anchor = new DialogueDefinition.ZoneAnchor();
        DialogueDefinition.ZoneAnchor a = t.anchor;

        cycleButton("Anchor type", a.type, 0, List.of("block", "entity", "absolute"), s -> a.type = s);
        zoneTargetField("Anchor target", a.target, 1, s -> a.target = blankToNull(s));
        field("Entity tag (optional)", nullToEmpty(a.entity_tag), 2, s -> a.entity_tag = blankToNull(s), 128);
        cycleButton("Pick", a.pick, 3, List.of("nearest", "all"), s -> a.pick = s);
        cycleButton("Shape", t.shape, 4, List.of("cylinder", "sphere", "box"), s -> t.shape = s);
        smallFields("Radius", String.valueOf(t.radius), s -> t.radius = d(s, t.radius), "Height", String.valueOf(t.height), s -> t.height = d(s, t.height), 5);
        smallFields("Size X", String.valueOf(t.size_x), s -> t.size_x = d(s, t.size_x), "Size Y", String.valueOf(t.size_y), s -> t.size_y = d(s, t.size_y), 6);
        smallFields("Size Z", String.valueOf(t.size_z), s -> t.size_z = d(s, t.size_z), "Search height", String.valueOf(a.search_height), s -> a.search_height = d(s, a.search_height), 7);
        tripleFields("Offset X", String.valueOf(a.offset_x), s -> a.offset_x = d(s, a.offset_x), "Y", String.valueOf(a.offset_y), s -> a.offset_y = d(s, a.offset_y), "Z", String.valueOf(a.offset_z), s -> a.offset_z = d(s, a.offset_z), 8);
        tripleFields("Absolute X", nullable(a.x), s -> a.x = nd(s), "Y", nullable(a.y), s -> a.y = nd(s), "Z", nullable(a.z), s -> a.z = nd(s), 9);
        help(10, "Tip: entity anchor + minecraft:marker + entity_tag removes coordinate work entirely.");
        button("Edit zone in world", 11, b -> openZoneWorldEditor());
        help(12, "Axiom-like mode: axis + plane gizmos, boundary size handles, snapping, anchor highlight and click-to-place markers.");
    }

    private void initZoneFx() {
        DialogueDefinition.Trigger t = project.currentTrigger();
        if (!"zone".equalsIgnoreCase(t.type)) {
            help(1, "Select a zone trigger first.");
            return;
        }

        if (t.visual == null) t.visual = new DialogueDefinition.ZoneVisual();
        DialogueDefinition.ZoneVisual v = t.visual;

        toggleButton("Preview enabled", v.enabled, 0, value -> {
            v.enabled = value;
            markZoneCustom(v);
        });

        cycleButton("Preset", v.preset, 1, List.of("custom", "gta_marker", "hologram", "danger_zone", "holy_zone", "portal"), preset -> applyZonePreset(t, v, preset));

        cycleButton("Default style", v.style, 2, List.of("auto", "ring", "outline", "pillar"), value -> {
            v.style = value;
            markZoneCustom(v);
        });

        toggleButton("Show default zone", v.show_default_zone, 3, value -> {
            v.show_default_zone = value;
            markZoneCustom(v);
        });

        assetField("Marker texture PNG", v.texture, 4, ".png", false, value -> {
            v.texture = blankToNull(value);
            markZoneCustom(v);
        });

        cycleButton("Texture mode", v.texture_mode, 5, List.of("plane", "cylinder_wrap", "box_wrap"), value -> {
            v.texture_mode = value;
            markZoneCustom(v);
        });

        cycleButton("Texture fit", v.texture_fit, 6, List.of("stretch", "repeat"), value -> {
            v.texture_fit = value;
            markZoneCustom(v);
        });

        smallFields("Repeat U", String.valueOf(v.texture_repeat_x), value -> {
            v.texture_repeat_x = Math.max(0.01D, d(value, v.texture_repeat_x));
            markZoneCustom(v);
        }, "Repeat V", String.valueOf(v.texture_repeat_y), value -> {
            v.texture_repeat_y = Math.max(0.01D, d(value, v.texture_repeat_y));
            markZoneCustom(v);
        }, 7);

        smallFields("UV scroll U/s", String.valueOf(v.texture_scroll_u), value -> {
            v.texture_scroll_u = d(value, v.texture_scroll_u);
            markZoneCustom(v);
        }, "UV scroll V/s", String.valueOf(v.texture_scroll_v), value -> {
            v.texture_scroll_v = d(value, v.texture_scroll_v);
            markZoneCustom(v);
        }, 8);

        toggleButton("Filled sides", v.fill_enabled, 9, value -> {
            v.fill_enabled = value;
            markZoneCustom(v);
        });

        cycleButton("Fill mode", v.fill_mode, 10, List.of("solid", "gradient"), value -> {
            v.fill_mode = value;
            markZoneCustom(v);
        });

        colorField("Fill bottom color", v.fill_color_bottom, 11, value -> {
            v.fill_color_bottom = value;
            markZoneCustom(v);
        });

        colorField("Fill top color", v.fill_color_top, 12, value -> {
            v.fill_color_top = value;
            markZoneCustom(v);
        });

        slider("Bottom alpha", v.fill_alpha_bottom, 0.0D, 1.0D, 13, value -> {
            v.fill_alpha_bottom = value.floatValue();
            markZoneCustom(v);
        });

        slider("Top alpha", v.fill_alpha_top, 0.0D, 1.0D, 14, value -> {
            v.fill_alpha_top = value.floatValue();
            markZoneCustom(v);
        });

        colorField("Outline / ring color", v.color, 15, value -> {
            v.color = value;
            markZoneCustom(v);
        });

        slider("Master alpha", v.alpha, 0.0D, 1.0D, 16, value -> {
            v.alpha = value.floatValue();
            markZoneCustom(v);
        });

        toggleButton("Pulse", v.pulse, 17, value -> {
            v.pulse = value;
            markZoneCustom(v);
        });

        slider("Pulse amount", v.pulse_amplitude, 0.0D, 0.35D, 18, value -> {
            v.pulse_amplitude = value;
            markZoneCustom(v);
        });

        slider("Pulse speed (cycles/s)", v.pulse_speed, 0.05D, 3.0D, 19, value -> {
            v.pulse_speed = value;
            markZoneCustom(v);
        });

        toggleButton("Bob / bounce", v.bob, 20, value -> {
            v.bob = value;
            markZoneCustom(v);
        });

        slider("Bob height", v.bob_amplitude, 0.0D, 2.0D, 21, value -> {
            v.bob_amplitude = value;
            markZoneCustom(v);
        });

        slider("Bob speed (cycles/s)", v.bob_speed, 0.05D, 3.0D, 22, value -> {
            v.bob_speed = value;
            markZoneCustom(v);
        });

        toggleButton("Rotate clockwise", v.rotate, 23, value -> {
            v.rotate = value;
            markZoneCustom(v);
        });

        slider("Rotation speed (deg/s)", v.rotate_speed, -360.0D, 360.0D, 24, value -> {
            v.rotate_speed = value;
            markZoneCustom(v);
        });

        toggleButton("Alpha breathing", v.alpha_breathe, 25, value -> {
            v.alpha_breathe = value;
            markZoneCustom(v);
        });

        slider("Alpha breathe amount", v.alpha_breathe_amount, 0.0D, 1.0D, 26, value -> {
            v.alpha_breathe_amount = value;
            markZoneCustom(v);
        });

        slider("Alpha breathe speed", v.alpha_breathe_speed, 0.05D, 3.0D, 27, value -> {
            v.alpha_breathe_speed = value;
            markZoneCustom(v);
        });

        smallFields("Y offset", String.valueOf(v.y_offset), value -> {
            v.y_offset = d(value, v.y_offset);
            markZoneCustom(v);
        }, "Visual size", String.valueOf(v.size), value -> {
            v.size = Math.max(0.0D, d(value, v.size));
            markZoneCustom(v);
        }, 28);

        smallFields("Visual height", String.valueOf(v.visual_height), value -> {
            v.visual_height = Math.max(0.0D, d(value, v.visual_height));
            markZoneCustom(v);
        }, "Preview distance", String.valueOf(v.preview_distance), value -> {
            v.preview_distance = Math.max(1.0D, d(value, v.preview_distance));
            markZoneCustom(v);
        }, 29);

        tripleFields("Texture offset X", String.valueOf(v.texture_offset_x), value -> {
            v.texture_offset_x = d(value, v.texture_offset_x);
            markZoneCustom(v);
        }, "Y", String.valueOf(v.texture_offset_y), value -> {
            v.texture_offset_y = d(value, v.texture_offset_y);
            markZoneCustom(v);
        }, "Z", String.valueOf(v.texture_offset_z), value -> {
            v.texture_offset_z = d(value, v.texture_offset_z);
            markZoneCustom(v);
        }, 31);

        smallFields("Texture scale X", String.valueOf(v.texture_scale_x), value -> {
            v.texture_scale_x = Math.max(0.05D, d(value, v.texture_scale_x));
            markZoneCustom(v);
        }, "Texture scale Y", String.valueOf(v.texture_scale_y), value -> {
            v.texture_scale_y = Math.max(0.05D, d(value, v.texture_scale_y));
            markZoneCustom(v);
        }, 32);

        field("Texture rotation (degrees)", String.valueOf(v.texture_rotation), 33, value -> {
            v.texture_rotation = d(value, v.texture_rotation);
            markZoneCustom(v);
        }, 32);

        button("Edit texture with world gizmo", 34, b -> openZoneTextureEditor());

        help(36, "RIGHT PREVIEW is live: fill, texture, colors, pulse, bob, rotation and alpha breathing update before export.");
        help(37, "World texture gizmo: 1 = move, 2 = scale, 3 = rotate. H hides/shows its UI; Reset Tex restores transform.");
        help(38, "Texture is a separate layer: it stays visible even when Default style is outline / pillar / ring.");
        help(39, "cylinder_wrap and box_wrap cover side walls. repeat uses Repeat U / V instead of stretching once.");
        help(40, "Filled sides support solid color or bottom-to-top gradient. Set Top alpha to 0 for a GTA/RPG fade-out marker.");
        help(41, "Animations affect default geometry, fills and custom textures together. Negative rotation speed rotates counter-clockwise.");
    }

    private void markZoneCustom(DialogueDefinition.ZoneVisual visual) {
        visual.preset = "custom";
    }

    private void applyZonePreset(DialogueDefinition.Trigger trigger, DialogueDefinition.ZoneVisual visual, String preset) {
        String normalized = preset != null ? preset.toLowerCase(Locale.ROOT) : "custom";
        visual.preset = normalized;

        switch (normalized) {
            case "san_marker" -> {
                visual.show_default_zone = true;
                visual.style = "outline";
                visual.color = "#FFD45A";
                visual.alpha = 0.90F;

                visual.fill_enabled = true;
                visual.fill_mode = "gradient";
                visual.fill_color_bottom = "#FFD45A";
                visual.fill_color_top = "#FFD45A";
                visual.fill_alpha_bottom = 0.65F;
                visual.fill_alpha_top = 0.02F;

                visual.pulse = true;
                visual.pulse_amplitude = 0.055D;
                visual.pulse_speed = 0.80D;
                visual.bob = true;
                visual.bob_amplitude = 0.16D;
                visual.bob_speed = 0.65D;
                visual.rotate = true;
                visual.rotate_speed = 28.0D;
                visual.alpha_breathe = false;
            }
            case "hologram" -> {
                visual.show_default_zone = true;
                visual.style = "outline";
                visual.color = "#42F2E1";
                visual.alpha = 0.72F;

                visual.fill_enabled = true;
                visual.fill_mode = "gradient";
                visual.fill_color_bottom = "#28F0D0";
                visual.fill_color_top = "#7EFFFF";
                visual.fill_alpha_bottom = 0.35F;
                visual.fill_alpha_top = 0.03F;

                visual.texture_fit = "repeat";
                visual.texture_repeat_x = 4.0D;
                visual.texture_repeat_y = 2.0D;
                visual.texture_scroll_u = 0.08D;
                visual.texture_scroll_v = -0.12D;

                visual.pulse = true;
                visual.pulse_amplitude = 0.025D;
                visual.pulse_speed = 1.15D;
                visual.bob = true;
                visual.bob_amplitude = 0.08D;
                visual.bob_speed = 0.70D;
                visual.rotate = true;
                visual.rotate_speed = 14.0D;
                visual.alpha_breathe = true;
                visual.alpha_breathe_amount = 0.22D;
                visual.alpha_breathe_speed = 0.85D;
            }
            case "danger_zone" -> {
                visual.show_default_zone = true;
                visual.style = "outline";
                visual.color = "#FF3A22";
                visual.alpha = 0.92F;

                visual.fill_enabled = true;
                visual.fill_mode = "gradient";
                visual.fill_color_bottom = "#FF1600";
                visual.fill_color_top = "#FFB000";
                visual.fill_alpha_bottom = 0.50F;
                visual.fill_alpha_top = 0.05F;

                visual.pulse = true;
                visual.pulse_amplitude = 0.08D;
                visual.pulse_speed = 1.65D;
                visual.bob = false;
                visual.rotate = true;
                visual.rotate_speed = 42.0D;
                visual.alpha_breathe = true;
                visual.alpha_breathe_amount = 0.25D;
                visual.alpha_breathe_speed = 1.4D;
            }
            case "holy_zone" -> {
                visual.show_default_zone = true;
                visual.style = "pillar";
                visual.color = "#FFE08A";
                visual.alpha = 0.82F;

                visual.fill_enabled = true;
                visual.fill_mode = "gradient";
                visual.fill_color_bottom = "#FFD66B";
                visual.fill_color_top = "#FFF8D0";
                visual.fill_alpha_bottom = 0.34F;
                visual.fill_alpha_top = 0.0F;

                visual.pulse = true;
                visual.pulse_amplitude = 0.025D;
                visual.pulse_speed = 0.65D;
                visual.bob = true;
                visual.bob_amplitude = 0.10D;
                visual.bob_speed = 0.50D;
                visual.rotate = true;
                visual.rotate_speed = 18.0D;
                visual.alpha_breathe = true;
                visual.alpha_breathe_amount = 0.16D;
                visual.alpha_breathe_speed = 0.55D;
            }
            case "portal" -> {
                visual.show_default_zone = true;
                visual.style = "outline";
                visual.color = "#9A5CFF";
                visual.alpha = 0.80F;

                visual.fill_enabled = true;
                visual.fill_mode = "gradient";
                visual.fill_color_bottom = "#3D0A78";
                visual.fill_color_top = "#B76CFF";
                visual.fill_alpha_bottom = 0.40F;
                visual.fill_alpha_top = 0.02F;

                visual.texture_fit = "repeat";
                visual.texture_repeat_x = 3.0D;
                visual.texture_repeat_y = 2.0D;
                visual.texture_scroll_u = 0.12D;
                visual.texture_scroll_v = 0.05D;

                visual.pulse = true;
                visual.pulse_amplitude = 0.045D;
                visual.pulse_speed = 0.90D;
                visual.bob = true;
                visual.bob_amplitude = 0.12D;
                visual.bob_speed = 0.55D;
                visual.rotate = true;
                visual.rotate_speed = 36.0D;
                visual.alpha_breathe = true;
                visual.alpha_breathe_amount = 0.30D;
                visual.alpha_breathe_speed = 0.75D;
            }
            default -> visual.preset = "custom";
        }

        reopen(tab);
    }

    private void initGameplay() {
        DialogueDefinition d = project.definition;
        toggleButton("Freeze source", d.freeze_source, 0, value -> d.freeze_source = value);
        toggleButton("Source invulnerable", d.source_invulnerable, 1, value -> d.source_invulnerable = value);
        toggleButton("Cancel if source missing", d.cancel_if_source_missing, 2, value -> d.cancel_if_source_missing = value);
        toggleButton("Exclusive source", d.exclusive_source, 3, value -> d.exclusive_source = value);
        cycleButton("Once", d.once, 4, List.of("never", "player", "entity", "session"), s -> d.once = s);
        help(6, "These are the exact runtime fields used by DialogueSessionManager.");
        help(7, "The editor only generates normal Dialogue Engine JSON; manual JSON editing remains supported.");
    }

    private void initExport() {
        button("Save Studio project", 0, b -> saveProject());
        button("Export datapack + resource pack", 1, b -> exportProject(false));
        button("Export and open folder", 2, b -> exportProject(true));
        button("Export for Mods", 3, b -> exportForMods());

        button("Install into current instance/world", 4, b -> {
            try {
                DialogueEditorExporter.installToCurrentInstance(project);
                STATUS = minecraft.getSingleplayerServer() != null
                        ? "Installed. Enable DialogueStudio_* resource pack and run /reload."
                        : "Resource pack installed locally. Datapack install requires singleplayer.";
            } catch (Exception e) {
                STATUS = "Install failed: " + e.getMessage();
            }
        });

        button("Open Studio export folder", 5, b -> {
            try {
                Files.createDirectories(DialogueEditorWorkspace.exportsRoot());
                Util.getPlatform().openFile(DialogueEditorWorkspace.exportsRoot().toFile());
                STATUS = "Opened export folder.";
            } catch (Exception e) {
                STATUS = "Could not open folder: " + e.getMessage();
            }
        });

        button("Copy dialogue JSON to clipboard", 6, b -> {
            minecraft.keyboardHandler.setClipboard(DialogueRegistry.toJson(project.definition));
            STATUS = "Dialogue JSON copied to clipboard.";
        });

        help(8, "IMPORTANT: project.json is NOT a datapack. It only lets Studio reopen your work.");
        help(9, "Export creates editable folders plus datapack.zip and resourcepack.zip.");
        help(10, "Export for Mods creates ready data/ + assets/ folders. Copy them into your mod's src/main/resources.");
        help(11, "Each mod export is clean and project-specific. See README.txt for sound/lang merge instructions.");
        help(13, "For testing: use Install in singleplayer, enable generated resource pack, then /reload.");
    }

    private void exportForMods() {
        Path initial = minecraft.gameDirectory.toPath();

        minecraft.setScreen(new DialogueEditorFilePickerScreen(this, initial, "", destination -> {
            try {
                var result = DialogueEditorExporter.exportForMod(project, destination);

                STATUS = "Mod template ready: " + result.root();
                showToast("Mod export complete  •  copy data + assets into src/main/resources");

                Util.getPlatform().openFile(result.root().toFile());
            } catch (Exception e) {
                STATUS = "Mod export failed: " + e.getMessage();
                showToast("Mod export failed");
            }
        }, true));
    }

    private void openZoneWorldEditor() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            STATUS = "Join a world before using Zone World Edit Mode.";
            return;
        }

        DialogueDefinition.Trigger trigger = project.currentTrigger();
        trigger.type = "zone";
        if (trigger.anchor == null) trigger.anchor = new DialogueDefinition.ZoneAnchor();
        if (trigger.visual == null) trigger.visual = new DialogueDefinition.ZoneVisual();

        DialogueEditorHistory.checkpoint(project);
        minecraft.setScreen(new DialogueZoneWorldEditScreen(project, Tab.ZONE, false));
    }

    private void openZoneTextureEditor() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            STATUS = "Join a world before using the texture gizmo.";
            return;
        }

        DialogueDefinition.Trigger trigger = project.currentTrigger();
        trigger.type = "zone";
        if (trigger.anchor == null) trigger.anchor = new DialogueDefinition.ZoneAnchor();
        if (trigger.visual == null) trigger.visual = new DialogueDefinition.ZoneVisual();

        if (trigger.visual.texture == null || trigger.visual.texture.isBlank()) {
            STATUS = "Import/select a Marker texture PNG before opening the texture gizmo.";
            return;
        }

        DialogueEditorHistory.checkpoint(project);
        minecraft.setScreen(new DialogueZoneWorldEditScreen(project, Tab.ZONE_FX, true));
    }

    private void exportProject(boolean openFolder) {
        try {
            var result = DialogueEditorExporter.export(project);
            STATUS = "Ready";
            showToast("Export complete  •  datapack.zip + resourcepack.zip");
            if (openFolder) Util.getPlatform().openFile(result.root().toFile());
        } catch (Exception e) {
            STATUS = "Export failed: " + e.getMessage();
        }
    }

    @Override
    public void tick() {
        super.tick();
        previewTicks++;
        DialogueEditorHistory.watch(project);

        if (toastTicks >= 0) {
            toastTicks++;
            if (toastTicks > TOAST_DURATION) {
                toastTicks = -1;
                toastText = null;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        DialogueRetroTheme.drawPanel(graphics, 4, bodyTop, LEFT, height - 4);
        DialogueRetroTheme.drawTitleBar(graphics, 7, bodyTop + 3, LEFT - 3, 18);
        DialogueRetroTheme.drawDarkInset(graphics, 8, bodyTop + 24, LEFT - 4, height - 21);

        DialogueRetroTheme.drawPanel(graphics, LEFT + 4, bodyTop, width - 4, height - 4);
        DialogueRetroTheme.drawDarkInset(graphics, LEFT + 8, bodyTop + 4, width - 8, height - 8);

        graphics.drawString(font, "DIALOGUE STUDIO", 12, bodyTop + 8, DialogueRetroTheme.LIME, false);

        graphics.fill(8, height - 20, LEFT - 4, height - 7, DialogueRetroTheme.BEIGE);
        graphics.fill(8, height - 20, LEFT - 4, height - 19, DialogueRetroTheme.CREAM_LIGHT);
        graphics.fill(8, height - 8, LEFT - 4, height - 7, DialogueRetroTheme.BLACK);
        graphics.drawString(font, ellipsize(STATUS, Math.max(40, LEFT - 28)), 12, height - 17, DialogueRetroTheme.TEXT_HINT, false);

        int inspectorClipTop = inspectorContentTop();
        int inspectorClipBottom = inspectorContentBottom();


        graphics.enableScissor(4, inspectorClipTop, LEFT, inspectorClipBottom);

        for (Label label : labels) {

            if (label.y + font.lineHeight >= inspectorClipTop && label.y <= inspectorClipBottom - 1) {

                graphics.drawString(font, label.text, label.x, label.y, label.color, false);
            }
        }

        graphics.disableScissor();

        int px = LEFT + 12;
        int py = bodyTop + 6;
        int pw = Math.max(80, width - px - 12);
        int ph = Math.max(90, height - py - 12);

        boolean triggerView = tab == Tab.TRIGGERS || tab == Tab.ZONE || tab == Tab.ZONE_FX;
        boolean lineTimeline = tab == Tab.LINES || tab == Tab.LINE_OVERRIDES;
        timelineY = -1;

        if (tab == Tab.ZONE_FX) {
            previewTransform = null;
            DialogueZoneFxGuiPreview.render(project, graphics, px, py, pw, ph, previewTicks, partialTick);
        } else if (triggerView) {
            int cardH = Math.min(170, Math.max(105, ph / 3));
            int previewH = Math.max(80, ph - cardH - 8);
            renderTriggerCard(graphics, px, py, pw, cardH, mouseX, mouseY);
            previewTransform = DialogueEditorPreview.render(project, graphics, px, py + cardH + 8, pw, previewH, previewTicks, partialTick);
        } else if (lineTimeline) {
            int timelineH = Math.min(timelineHeight, Math.max(58, ph - 96));
            int gap = 10;
            int previewH = Math.max(86, ph - timelineH - gap);

            previewTransform = DialogueEditorPreview.render(project, graphics, px, py, pw, previewH, previewTicks, partialTick, true);

            timelineY = py + ph - timelineH;

            renderLineTimeline(graphics, px, timelineY, pw, timelineH, mouseX, mouseY);
        } else {
            previewTransform = DialogueEditorPreview.render(project, graphics, px, py, pw, ph, previewTicks, partialTick);
        }

        if (tab == Tab.LAYOUT) renderGizmo(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderTips(graphics, mouseX, mouseY);
        renderToast(graphics, partialTick);
    }

    private void renderTriggerCard(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        DialogueDefinition.Trigger t = project.currentTrigger();
        DialogueRetroTheme.drawPanel(graphics, x, y, x + w, y + h);
        DialogueRetroTheme.drawTitleBar(graphics, x + 3, y + 3, x + w - 3, 20);
        DialogueRetroTheme.drawDarkInset(graphics, x + 6, y + 25, x + w - 6, y + h - 6);
        graphics.drawString(font, "TRIGGER / WORLD PREVIEW", x + 9, y + 8, DialogueRetroTheme.LIME, false);

        String target = triggerPreviewTarget(t);
        boolean hasModelPreview = target != null && !target.startsWith("#") && !target.equals("*");

        int textWidth = hasModelPreview ? Math.max(90, w / 2 - 22) : Math.max(90, w - 18);
        int cursorY = y + 32;

        cursorY += drawWrappedText(graphics, "type: " + t.type, x + 9, cursorY, textWidth, 0xFFFFFFFF, 1);
        cursorY += 2;
        cursorY += drawWrappedText(graphics, "target: " + (target != null ? target : "<none>"), x + 9, cursorY, textWidth, 0xFFCFC6A6, 2);

        if ("zone".equalsIgnoreCase(t.type)) {
            cursorY += 2;
            String shape = t.shape != null ? t.shape : "cylinder";
            cursorY += drawWrappedText(graphics, "zone: " + shape + "  radius=" + fmt(t.radius) + "  height=" + fmt(t.height), x + 9, cursorY, textWidth, 0xFFFFD45A, 2);

            if (t.visual != null) {
                cursorY += 2;
                drawWrappedText(graphics, "visual: " + t.visual.style + "  " + t.visual.color, x + 9, cursorY, textWidth, 0xFF93E85D, 2);
            }
        }

        if (hasModelPreview) {
            ResourceLocation id = ResourceLocation.tryParse(target);
            if (id != null) {
                if (isEntityTarget(t)) {
                    renderEntityCard(graphics, id, x + w / 2, y + 24, w / 2 - 12, h - 34, mouseX, mouseY);
                } else if (isBlockTarget(t)) {
                    renderBlockCard(graphics, id, x + w / 2, y + 24, w / 2 - 12, h - 34);
                }
            }
        }
    }

    private void renderEntityCard(GuiGraphics graphics, ResourceLocation id, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (minecraft.level == null) {
            return;
        }

        if (!id.toString().equals(previewTargetId)) {
            previewTargetId = id.toString();
            previewTargetEntity = null;

            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);

            if (type != null) {
                Entity entity = type.create(minecraft.level);

                if (entity instanceof LivingEntity living) {
                    previewTargetEntity = living;
                }
            }
        }

        if (previewTargetEntity == null) {
            return;
        }

        int size = Math.max(20, Math.min(56, Math.min(w, h) / 2));

        int renderX = x + w / 2;
        int renderY = y + h - 8;

        float lookX = renderX - mouseX;
        float lookY = (renderY - size * 1.6F) - mouseY;

        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, renderX, renderY, size, lookX, lookY, previewTargetEntity);
    }

    private void renderBlockCard(GuiGraphics graphics, ResourceLocation id, int x, int y, int w, int h) {
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null || block.asItem() == Items.AIR) return;
        ItemStack stack = new ItemStack(block);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + w * 0.5F, y + h * 0.52F, 100);
        pose.scale(3F, 3F, 3F);
        graphics.renderItem(stack, -8, -8);
        pose.popPose();
    }

    private void renderGizmo(GuiGraphics graphics) {
        if (previewTransform == null) return;
        DialogueDefinition.Layout l = project.definition.layout;
        DialogueDefinition.Line line = project.currentLine();
        float cx;
        float cy;
        switch (LAYOUT_ELEMENT) {
            case FRAME -> {
                cx = l.frame_x;
                cy = l.frame_y;
            }
            case TEXT -> {
                cx = l.text_x;
                cy = l.text_y;
            }
            default -> {
                cx = line.sprite_x != null ? line.sprite_x : switch ((line.sprite_position != null ? line.sprite_position : project.definition.sprite_position).toLowerCase(Locale.ROOT)) {
                    case "left" -> l.sprite_left_x;
                    case "right" -> l.sprite_right_x;
                    default -> l.sprite_center_x;
                };
                cy = l.sprite_y;
            }
        }
        int x = Math.round(previewTransform.screenX(cx));
        int y = Math.round(previewTransform.screenY(cy));
        graphics.hLine(x, x + 38, y, 0xFFFF4D55);
        graphics.fill(x + 34, y - 3, x + 40, y + 4, 0xFFFF4D55);
        graphics.vLine(x, y - 38, y, 0xFF86D955);
        graphics.fill(x - 3, y - 40, x + 4, y - 34, 0xFF86D955);
        graphics.fill(x + 12, y - 16, x + 20, y - 8, 0xFF4AA3FF);
        graphics.drawString(font, "X", x + 42, y - 4, 0xFFFF4D55, false);
        graphics.drawString(font, "Y", x - 4, y - 51, 0xFF86D955, false);
        graphics.drawString(font, "S", x + 22, y - 18, 0xFF4AA3FF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((tab == Tab.LINES || tab == Tab.LINE_OVERRIDES) && button == 0 && timelineY >= 0) {
            int index = timelineLineAt(mouseX, mouseY);
            if (index >= 0) {
                project.selected_line = index;
                dragLineIndex = index;
                dragLineMoved = false;
                return true;
            }
        }

        if (tab == Tab.LAYOUT && button == 0 && previewTransform != null) {
            int[] origin = gizmoOrigin();
            if (origin != null) {
                int x = origin[0], y = origin[1];
                if (Math.abs(mouseY - y) <= 6 && mouseX >= x && mouseX <= x + 44) dragAxis = DragAxis.X;
                else if (Math.abs(mouseX - x) <= 6 && mouseY >= y - 44 && mouseY <= y) dragAxis = DragAxis.Y;
                else if (mouseX >= x + 8 && mouseX <= x + 24 && mouseY >= y - 20 && mouseY <= y - 4)
                    dragAxis = DragAxis.SCALE;
                if (dragAxis != DragAxis.NONE) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragLineIndex >= 0 && (tab == Tab.LINES || tab == Tab.LINE_OVERRIDES)) {
            int target = timelineLineAt(mouseX, mouseY);
            if (target >= 0 && target != dragLineIndex) {
                DialogueDefinition.Line moved = project.definition.lines.remove(dragLineIndex);
                project.definition.lines.add(target, moved);
                project.selected_line = target;
                dragLineIndex = target;
                dragLineMoved = true;
            }
            return true;
        }

        if (tab == Tab.LAYOUT && dragAxis != DragAxis.NONE && previewTransform != null) {
            float dx = (float) (dragX / previewTransform.scale());
            float dy = (float) (dragY / previewTransform.scale());
            DialogueDefinition.Layout l = project.definition.layout;
            switch (LAYOUT_ELEMENT) {
                case FRAME -> {
                    if (dragAxis == DragAxis.X) l.frame_x += Math.round(dx);
                    if (dragAxis == DragAxis.Y) l.frame_y += Math.round(dy);
                    if (dragAxis == DragAxis.SCALE) {
                        l.frame_width = Math.max(1, l.frame_width + Math.round(dx));
                        l.frame_height = Math.max(1, l.frame_height + Math.round(dy));
                    }
                }
                case TEXT -> {
                    if (dragAxis == DragAxis.X) l.text_x += Math.round(dx);
                    if (dragAxis == DragAxis.Y) l.text_y += Math.round(dy);
                    if (dragAxis == DragAxis.SCALE) l.text_scale = Math.max(0.1F, l.text_scale + dx * 0.01F);
                }
                case SPRITE -> {
                    DialogueDefinition.Line line = project.currentLine();
                    if (dragAxis == DragAxis.X) {
                        if (line.sprite_x == null) line.sprite_x = resolveSpriteXForEdit();
                        line.sprite_x += dx;
                    }
                    if (dragAxis == DragAxis.Y) l.sprite_y += Math.round(dy);
                    if (dragAxis == DragAxis.SCALE) {
                        l.sprite_width = Math.max(1, l.sprite_width + Math.round(dx));
                        l.sprite_height = Math.max(1, l.sprite_height + Math.round(dy));
                    }
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragLineIndex >= 0) {
            dragLineIndex = -1;
            DialogueEditorHistory.checkpoint(project);
            reopen(tab);
            return true;
        }

        dragAxis = DragAxis.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if ((tab == Tab.LINES || tab == Tab.LINE_OVERRIDES) && timelineY >= 0 && mouseX >= LEFT && mouseY >= timelineY) {
            int visible = timelineVisibleCards();
            int max = Math.max(0, project.definition.lines.size() - visible);
            int old = LINE_TIMELINE_SCROLL;
            if (delta > 0) LINE_TIMELINE_SCROLL = Math.max(0, LINE_TIMELINE_SCROLL - 1);
            else if (delta < 0) LINE_TIMELINE_SCROLL = Math.min(max, LINE_TIMELINE_SCROLL + 1);
            return old != LINE_TIMELINE_SCROLL || super.mouseScrolled(mouseX, mouseY, delta);
        }

        if (mouseX < LEFT && mouseY >= inspectorContentTop() && mouseY <= inspectorContentBottom()) {
            int max = maxLeftScroll();
            int old = scrollOffset;
            int step = Math.max(1, rowSpacing);

            if (delta > 0) scrollOffset = Math.max(0, scrollOffset - step);
            else if (delta < 0) scrollOffset = Math.min(max, scrollOffset + step);
            if (old != scrollOffset) {
                SCROLL_BY_TAB.put(tab, scrollOffset);
                reopen(tab);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            DialogueEditorProject restored = hasShiftDown() ? DialogueEditorHistory.redo(project) : DialogueEditorHistory.undo(project);
            if (restored != null) {
                STATUS = hasShiftDown() ? "Redo" : "Undo";
                minecraft.setScreen(new DialogueEditorScreen(restored, tab));
            }
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            DialogueEditorProject restored = DialogueEditorHistory.redo(project);
            if (restored != null) {
                STATUS = "Redo";
                minecraft.setScreen(new DialogueEditorScreen(restored, tab));
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;

        Path path = paths.get(0);
        String file = path.getFileName().toString().toLowerCase(Locale.ROOT);

        try {
            if (file.endsWith(".png")) {
                String id = DialogueEditorWorkspace.importTexture(project, path);

                if (tab == Tab.LINES || tab == Tab.LINE_OVERRIDES) {
                    project.currentLine().sprite = id;
                    STATUS = "Dropped PNG -> current line sprite";
                } else if (tab == Tab.ZONE_FX && "zone".equalsIgnoreCase(project.currentTrigger().type)) {
                    if (project.currentTrigger().visual == null)
                        project.currentTrigger().visual = new DialogueDefinition.ZoneVisual();
                    project.currentTrigger().visual.texture = id;
                    STATUS = "Dropped PNG -> zone marker";
                } else if (tab == Tab.VISUALS) {
                    if (project.definition.frame == null || project.definition.frame.isBlank()) {
                        project.definition.frame = id;
                        STATUS = "Dropped PNG -> frame";
                    } else {
                        project.definition.background = id;
                        STATUS = "Dropped PNG -> background";
                    }
                } else {
                    project.currentLine().sprite = id;
                    STATUS = "Dropped PNG -> current line sprite";
                }
            } else if (file.endsWith(".ogg")) {
                String id = DialogueEditorWorkspace.importSound(project, path);
                if (tab == Tab.LINES || tab == Tab.LINE_OVERRIDES) project.currentLine().voice = id;
                else project.definition.voice = id;
                STATUS = "Dropped OGG imported as voice: " + id;
            } else if (file.endsWith(".json")) {
                if (file.matches("[a-z]{2}_[a-z]{2}\\.json")) {
                    DialogueEditorWorkspace.importLang(project, path);
                    STATUS = "Dropped language JSON imported.";
                } else {
                    DialogueEditorWorkspace.importDialogueJson(project, path);
                    STATUS = "Dropped dialogue JSON imported.";
                }
            } else {
                STATUS = "Drop PNG, OGG or JSON files into Dialogue Studio.";
                return;
            }

            DialogueEditorWorkspace.save(project);
            DialogueEditorHistory.checkpoint(project);
            reopen(tab);
        } catch (Exception e) {
            STATUS = "Drop import failed: " + e.getMessage();
        }
    }

    @Override
    public void onClose() {
        try {
            DialogueEditorWorkspace.save(project);
        } catch (Exception ignored) {
        }
        DialogueEditorTextureCache.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int[] gizmoOrigin() {
        if (previewTransform == null) return null;
        DialogueDefinition.Layout l = project.definition.layout;
        DialogueDefinition.Line line = project.currentLine();
        float x, y;
        switch (LAYOUT_ELEMENT) {
            case FRAME -> {
                x = l.frame_x;
                y = l.frame_y;
            }
            case TEXT -> {
                x = l.text_x;
                y = l.text_y;
            }
            default -> {
                x = line.sprite_x != null ? line.sprite_x : resolveSpriteXForEdit();
                y = l.sprite_y;
            }
        }
        return new int[]{Math.round(previewTransform.screenX(x)), Math.round(previewTransform.screenY(y))};
    }

    private float resolveSpriteXForEdit() {
        DialogueDefinition.Line line = project.currentLine();
        DialogueDefinition.Layout l = project.definition.layout;
        String p = line.sprite_position != null ? line.sprite_position : project.definition.sprite_position;
        if (p == null) p = "center";
        return switch (p.toLowerCase(Locale.ROOT)) {
            case "left" -> l.sprite_left_x;
            case "right" -> l.sprite_right_x;
            default -> l.sprite_center_x;
        };
    }

    private void confirmResetCurrentTab() {
        if (tab == Tab.EXPORT) {
            STATUS = "Export has no editable values to reset.";
            return;
        }

        String target = switch (tab) {
            case LINES -> "the current dialogue line";
            case LINE_OVERRIDES -> "the current line overrides";
            case NODES -> "the entire node graph";
            case TRIGGERS -> "the current trigger";
            case ZONE -> "the current zone geometry/anchor";
            case ZONE_FX -> "the current zone visual settings";
            default -> "all values in the " + tab.label + " panel";
        };

        minecraft.setScreen(new DialogueEditorConfirmScreen(this, "Reset " + tab.label + "?", "Restore " + target + " to Dialogue Engine defaults? You can still use Ctrl+Z afterwards.", () -> {
            resetCurrentTabValues();
            DialogueEditorHistory.checkpoint(project);
            STATUS = "Reset " + tab.label + " to defaults.";
            minecraft.setScreen(new DialogueEditorScreen(project, tab));
        }));
    }

    private void confirmResetAll() {
        minecraft.setScreen(new DialogueEditorConfirmScreen(this, "Reset entire project?", "This restores dialogue data, lines, triggers, layout, languages and sound mappings to defaults. Imported files are kept in the Studio workspace. Ctrl+Z can restore the project afterwards.", () -> {
            resetWholeProjectValues();
            DialogueEditorHistory.checkpoint(project);
            STATUS = "Entire dialogue project reset to defaults.";
            minecraft.setScreen(new DialogueEditorScreen(project, Tab.PROJECT));
        }));
    }

    private void resetWholeProjectValues() {
        String workspaceId = project.workspace_id;
        DialogueEditorProject fresh = DialogueEditorProject.createDefault();

        project.project_name = fresh.project_name;
        project.namespace = fresh.namespace;
        project.dialogue_path = fresh.dialogue_path;
        project.preview_locale = fresh.preview_locale;
        project.definition = fresh.definition;
        project.languages = fresh.languages;
        project.sounds = fresh.sounds;
        project.fonts = fresh.fonts;
        project.node_positions = fresh.node_positions;
        project.selected_node = fresh.selected_node;
        project.selected_line = 0;
        project.selected_trigger = 0;
        project.animate_preview = true;
        project.workspace_id = workspaceId;
        project.normalize();
    }

    private void resetCurrentTabValues() {
        DialogueDefinition defaults = new DialogueDefinition();

        switch (tab) {
            case PROJECT -> {
                String oldNamespace = project.namespace;
                String oldPath = project.dialogue_path;
                project.rewriteNamespace(oldNamespace, "mydialogues");
                project.rewriteDialoguePath(oldPath, "dialogue_1");
                project.project_name = "New Dialogue";
                project.preview_locale = "en_us";
                project.animate_preview = true;
            }

            case DIALOGUE -> {
                DialogueDefinition d = project.definition;
                d.voice = defaults.voice;
                d.voice_source = defaults.voice_source;
                d.voice_pitch = defaults.voice_pitch;
                d.voice_volume = defaults.voice_volume;
                d.voice_every = defaults.voice_every;
                d.char_ticks = defaults.char_ticks;
                d.hold_ticks = defaults.hold_ticks;
                d.fade_ticks = defaults.fade_ticks;
                d.text_color = defaults.text_color;
                d.text_gradient = defaults.text_gradient;
                d.text_effect = defaults.text_effect;
                d.text_effects = defaults.text_effects;
                d.text_style = defaults.text_style;
                d.markdown = defaults.markdown;
                d.text_font = defaults.text_font;
                d.text_outline_color = defaults.text_outline_color;
                d.text_outline_gradient = defaults.text_outline_gradient;
                d.text_outline_thickness = defaults.text_outline_thickness;
            }

            case VISUALS -> {
                DialogueDefinition d = project.definition;
                d.frame = defaults.frame;
                d.background = defaults.background;
                d.background_alpha = defaults.background_alpha;
                d.background_bob = defaults.background_bob;
                d.background_speed = defaults.background_speed;
                d.sprite_position = defaults.sprite_position;
                d.sprite_transition = defaults.sprite_transition;
                d.sprite_move_ticks = defaults.sprite_move_ticks;
                d.sprite_transition_ticks = defaults.sprite_transition_ticks;
            }

            case LINES -> {
                DialogueDefinition.Line oldLine = project.currentLine();
                if (oldLine.text != null && project.languages != null) {
                    for (Map<String, String> language : project.languages.values()) {
                        language.remove(oldLine.text);
                    }
                }

                DialogueDefinition.Line line = new DialogueDefinition.Line();
                line.literal = "New dialogue line";
                line.sprite = "dlgstd:textures/gui/dialogue/default_sprite.png";
                project.definition.lines.set(project.selected_line, line);
            }

            case LINE_OVERRIDES -> {
                DialogueDefinition.Line line = project.currentLine();
                line.char_ticks = null;
                line.hold_ticks = null;
                line.frame = null;
                line.background = null;
                line.sprite_position = null;
                line.sprite_x = null;
                line.sprite_transition = null;
                line.sprite_move_ticks = null;
                line.sprite_transition_ticks = null;
                line.sprite_width = null;
                line.sprite_height = null;
                line.voice_source = null;
            }

            case NODES -> {
                project.definition.graph_enabled = false;
                project.definition.start_node = null;
                project.definition.nodes = new LinkedHashMap<>();
                project.node_positions = new LinkedHashMap<>();
                project.selected_node = null;
            }

            case LAYOUT -> project.definition.layout = new DialogueDefinition.Layout();

            case TRIGGERS -> {
                DialogueDefinition.Trigger trigger = new DialogueDefinition.Trigger();
                project.definition.triggers.set(project.selected_trigger, trigger);
            }

            case ZONE -> {
                DialogueDefinition.Trigger trigger = project.currentTrigger();
                trigger.type = "zone";
                trigger.anchor = new DialogueDefinition.ZoneAnchor();
                trigger.shape = "cylinder";
                trigger.radius = 5.0D;
                trigger.height = 2.0D;
                trigger.size_x = 6.0D;
                trigger.size_y = 2.0D;
                trigger.size_z = 6.0D;
                trigger.x = null;
                trigger.y = null;
                trigger.z = null;
                trigger.min_x = null;
                trigger.min_y = null;
                trigger.min_z = null;
                trigger.max_x = null;
                trigger.max_y = null;
                trigger.max_z = null;
            }

            case ZONE_FX -> {
                DialogueDefinition.Trigger trigger = project.currentTrigger();
                trigger.type = "zone";
                trigger.visual = new DialogueDefinition.ZoneVisual();
            }

            case GAMEPLAY -> {
                DialogueDefinition d = project.definition;
                d.freeze_source = defaults.freeze_source;
                d.source_invulnerable = defaults.source_invulnerable;
                d.cancel_if_source_missing = defaults.cancel_if_source_missing;
                d.exclusive_source = defaults.exclusive_source;
                d.once = defaults.once;
            }

            case EXPORT -> {
            }
        }

        project.normalize();
    }

    private void testVoice() {
        String value = project.definition.voice;

        if (value == null || value.isBlank()) {

            STATUS = "No voice selected.";

            showToast("No voice selected • choose/import an OGG first");

            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(value.trim());

        if (id == null || id.getPath().isBlank()) {

            STATUS = "Voice id is invalid: " + value;

            showToast("Invalid voice sound id");

            return;
        }

        SoundSource source;

        try {
            source = SoundSource.valueOf((project.definition.voice_source != null ? project.definition.voice_source : "master").toUpperCase(Locale.ROOT));

        } catch (Exception ignored) {
            source = SoundSource.MASTER;
        }

        try {
            SimpleSoundInstance sound = new SimpleSoundInstance(id, source, project.definition.voice_volume, project.definition.voice_pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);

            minecraft.getSoundManager().play(sound);

            STATUS = "Played voice request: " + id;

        } catch (Exception exception) {
            STATUS = "Could not test voice: " + (exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());

            showToast("Voice preview unavailable • check sound id/resource pack");
        }
    }

    private void saveProject() {
        try {
            DialogueEditorWorkspace.save(project);
            STATUS = "Saved: " + DialogueEditorWorkspace.projectJson(project);
        } catch (Exception e) {
            STATUS = "Save failed: " + e.getMessage();
        }
    }

    private void openProjectPicker() {
        Path root = DialogueEditorWorkspace.projectsRoot();
        minecraft.setScreen(new DialogueEditorFilePickerScreen(this, root, ".json", path -> {
            if (!path.getFileName().toString().equalsIgnoreCase("project.json")) {
                STATUS = "Choose a Dialogue Studio project.json file.";
                return;
            }
            try {
                DialogueEditorProject loaded = DialogueEditorWorkspace.load(path);
                STATUS = "Loaded: " + loaded.project_name;
                minecraft.setScreen(new DialogueEditorScreen(loaded, Tab.PROJECT));
            } catch (Exception e) {
                STATUS = "Load failed: " + e.getMessage();
            }
        }));
    }

    private void addLine() {
        DialogueDefinition.Line previous = project.currentLine();
        DialogueDefinition.Line line = new DialogueDefinition.Line();
        line.literal = "New dialogue line";
        line.sprite = previous.sprite != null ? previous.sprite : "dlgstd:textures/gui/dialogue/default_sprite.png";
        int insert = Math.min(project.definition.lines.size(), project.selected_line + 1);
        project.definition.lines.add(insert, line);
        project.selected_line = insert;
        reopen(tab);
    }

    private void removeLine() {
        if (project.definition.lines.size() <= 1) return;
        project.definition.lines.remove(project.selected_line);
        project.selected_line = Math.min(project.selected_line, project.definition.lines.size() - 1);
        reopen(tab);
    }

    private void addTrigger() {
        DialogueDefinition.Trigger trigger = new DialogueDefinition.Trigger();
        int insert = Math.min(project.definition.triggers.size(), project.selected_trigger + 1);
        project.definition.triggers.add(insert, trigger);
        project.selected_trigger = insert;
        reopen(tab);
    }

    private void removeTrigger() {
        if (project.definition.triggers.size() <= 1) return;
        project.definition.triggers.remove(project.selected_trigger);
        project.selected_trigger = Math.min(project.selected_trigger, project.definition.triggers.size() - 1);
        reopen(tab);
    }

    private void reopen(Tab newTab) {
        minecraft.setScreen(new DialogueEditorScreen(project, newTab));
    }

    private void pickFile(String extension, Consumer<Path> callback) {
        minecraft.setScreen(new DialogueEditorFilePickerScreen(this, minecraft.gameDirectory.toPath(), extension, callback));
    }

    private void field(String label, String value, int row, Consumer<String> responder, int maxLength) {
        int y = rowY(row);
        labels.add(new Label(label, 12, y - 10, 0xFFE8E0C3));
        EditBox box = new DialogueRetroEditBox(font, 12, y, LEFT - 24, controlHeight, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addScrollableWidget(box);
        addTip(12, y, LEFT - 24, controlHeight, tooltipText(label));
    }

    private void assetField(String label, String value, int row, String extension, boolean sound, Consumer<String> responder) {
        int y = rowY(row);
        labels.add(new Label(label, 12, y - 10, 0xFFE8E0C3));
        EditBox box = new DialogueRetroEditBox(font, 12, y, LEFT - 104, controlHeight, Component.literal(label));
        box.setMaxLength(512);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addScrollableWidget(box);
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Browse"), b -> pickFile(extension, path -> {
            try {
                String id = sound ? DialogueEditorWorkspace.importSound(project, path) : DialogueEditorWorkspace.importTexture(project, path);
                responder.accept(id);
                DialogueEditorWorkspace.save(project);
                STATUS = "Imported: " + path.getFileName();
            } catch (Exception e) {
                STATUS = "Import failed: " + e.getMessage();
            }
            reopen(tab);
        })).bounds(LEFT - 86, y, 74, controlHeight).build());
        addTip(12, y, LEFT - 24, controlHeight, tooltipText(label) + "  Browse copies the selected file into the Studio project.");
    }

    private void colorField(String label, String value, int row, Consumer<String> responder) {
        int y = rowY(row);
        labels.add(new Label(label, 12, y - 10, 0xFFE8E0C3));

        EditBox box = new DialogueRetroEditBox(font, 12, y, LEFT - 104, controlHeight, Component.literal(label));
        box.setMaxLength(64);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addScrollableWidget(box);

        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Color"), b -> minecraft.setScreen(new DialogueEditorColorPickerScreen(this, value, picked -> {
            responder.accept(picked);
            reopen(tab);
        }))).bounds(LEFT - 86, y, 74, controlHeight).build());

        addTip(12, y, LEFT - 24, controlHeight, tooltipText(label));
    }

    private void targetField(String label, String value, int row, Consumer<String> responder) {
        int y = rowY(row);
        labels.add(new Label(label, 12, y - 10, 0xFFE8E0C3));
        EditBox box = new DialogueRetroEditBox(font, 12, y, LEFT - 104, controlHeight, Component.literal(label));
        box.setMaxLength(256);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addScrollableWidget(box);
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Registry"), b -> openTargetRegistry(responder)).bounds(LEFT - 86, y, 74, controlHeight).build());
        addTip(12, y, LEFT - 24, controlHeight, tooltipText(label));
    }

    private void zoneTargetField(String label, String value, int row, Consumer<String> responder) {
        int y = rowY(row);
        labels.add(new Label(label, 12, y - 10, 0xFFE8E0C3));
        EditBox box = new DialogueRetroEditBox(font, 12, y, LEFT - 104, controlHeight, Component.literal(label));
        box.setMaxLength(256);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addScrollableWidget(box);
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("Registry"), b -> {
            DialogueDefinition.Trigger t = project.currentTrigger();
            if (t.anchor == null) t.anchor = new DialogueDefinition.ZoneAnchor();
            var kind = "block".equalsIgnoreCase(t.anchor.type) ? DialogueEditorRegistryPickerScreen.Kind.BLOCK : DialogueEditorRegistryPickerScreen.Kind.ENTITY;
            minecraft.setScreen(new DialogueEditorRegistryPickerScreen(this, kind, value, id -> {
                responder.accept(id);
                reopen(tab);
            }));
        }).bounds(LEFT - 86, y, 74, controlHeight).build());
        addTip(12, y, LEFT - 24, controlHeight, tooltipText(label));
    }

    private void openTargetRegistry(Consumer<String> responder) {
        DialogueDefinition.Trigger t = project.currentTrigger();
        var kind = isBlockTarget(t) ? DialogueEditorRegistryPickerScreen.Kind.BLOCK : DialogueEditorRegistryPickerScreen.Kind.ENTITY;
        minecraft.setScreen(new DialogueEditorRegistryPickerScreen(this, kind, t.target, id -> {
            responder.accept(id);
            reopen(tab);
        }));
    }

    private void smallFields(String labelA, String valueA, Consumer<String> responderA, String labelB, String valueB, Consumer<String> responderB, int row) {
        int y = rowY(row);
        int w = (LEFT - 34) / 2;
        labels.add(new Label(labelA, 12, y - 10, 0xFFE8E0C3));
        labels.add(new Label(labelB, 18 + w, y - 10, 0xFFE8E0C3));
        EditBox a = new DialogueRetroEditBox(font, 12, y, w, controlHeight, Component.literal(labelA));
        a.setValue(valueA != null ? valueA : "");
        a.setResponder(responderA);
        addScrollableWidget(a);
        EditBox b = new DialogueRetroEditBox(font, 18 + w, y, w, controlHeight, Component.literal(labelB));
        b.setValue(valueB != null ? valueB : "");
        b.setResponder(responderB);
        addScrollableWidget(b);
        addTip(12, y, w, controlHeight, tooltipText(labelA));
        addTip(18 + w, y, w, controlHeight, tooltipText(labelB));
    }

    private void tripleFields(String la, String va, Consumer<String> ca, String lb, String vb, Consumer<String> cb, String lc, String vc, Consumer<String> cc, int row) {
        int y = rowY(row);
        int w = (LEFT - 40) / 3;
        int[] xs = {12, 16 + w, 20 + w * 2};
        String[] ls = {la, lb, lc};
        String[] vs = {va, vb, vc};
        Consumer<String>[] cs = new Consumer[]{ca, cb, cc};
        for (int n = 0; n < 3; n++) {
            labels.add(new Label(ls[n], xs[n], y - 10, 0xFFE8E0C3));
            EditBox e = new DialogueRetroEditBox(font, xs[n], y, w, controlHeight, Component.literal(ls[n]));
            e.setValue(vs[n] != null ? vs[n] : "");
            e.setResponder(cs[n]);
            addScrollableWidget(e);
            addTip(xs[n], y, w, controlHeight, tooltipText(ls[n]));
        }
    }

    private void slider(String label, double current, double min, double max, int row, Consumer<Double> setter) {
        int y = rowY(row);
        double clamped = Mth.clamp(current, min, max);
        double normalized = max <= min ? 0.0D : (clamped - min) / (max - min);

        AbstractSliderButton slider = new DialogueRetroSlider(12, y, LEFT - 24, controlHeight, Component.empty(), normalized) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double actual = min + this.value * (max - min);
                this.setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, "%.3f", actual)));
            }

            @Override
            protected void applyValue() {
                double actual = min + this.value * (max - min);
                setter.accept(actual);
            }
        };

        addScrollableWidget(slider);
        addTip(12, y, LEFT - 24, controlHeight, tooltipText(label));
    }

    private void button(String text, int row, Button.OnPress press) {
        int y = rowY(row);
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal(text), press).bounds(12, y, LEFT - 24, controlHeight).build());
        addTip(12, y, LEFT - 24, controlHeight, tooltipText(text));
    }

    private void toggleButton(String label, boolean value, int row, Consumer<Boolean> setter) {
        button(label + ": " + (value ? "ON" : "OFF"), row, b -> {
            setter.accept(!value);
            reopen(tab);
        });
    }

    private void cycleButton(String label, String current, int row, List<String> values, Consumer<String> setter) {
        String now = current != null ? current : values.get(0);
        button(label + ": " + now, row, b -> {
            setter.accept(next(now, values));
            reopen(tab);
        });
    }

    private void cycleNullableButton(String label, String current, int row, List<String> values, Consumer<String> setter) {
        String shown = current == null ? "INHERIT" : current;
        button(label + ": " + shown, row, b -> {
            if (current == null) setter.accept(values.get(0));
            else {
                int index = values.indexOf(current);
                if (index < 0 || index == values.size() - 1) setter.accept(null);
                else setter.accept(values.get(index + 1));
            }
            reopen(tab);
        });
    }

    private void navButtons(String label, int index, int size, int row, Runnable prev, Runnable next, Runnable add, Runnable remove) {
        int y = rowY(row);
        labels.add(new Label(label + " " + (index + 1) + "/" + size, 12, y - 10, 0xFFE8E0C3));
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), b -> prev.run()).bounds(12, y, 42, controlHeight).build());
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), b -> next.run()).bounds(58, y, 42, controlHeight).build());
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("+"), b -> add.run()).bounds(104, y, 42, controlHeight).build());
        addScrollableWidget(DialogueRetroButton.retroBuilder(Component.literal("-"), b -> remove.run()).bounds(150, y, 42, controlHeight).build());
    }

    private void help(int row, String text) {
        int topOffset = Math.max(4, controlHeight / 4);

        int y = rowY(row) + topOffset;

        int maxWidth = Math.max(40, LEFT - 24);

        int lineStep = font.lineHeight + 2;
        int usableHeight = Math.max(font.lineHeight, rowSpacing - topOffset - 10);

        int maxLines = Math.max(1, usableHeight / lineStep);

        List<String> wrapped = wrapPlainText(text != null ? text : "", maxWidth);

        int drawCount = Math.min(maxLines, wrapped.size());

        for (int line = 0; line < drawCount; line++) {

            String shown = wrapped.get(line);

            if (line == drawCount - 1 && wrapped.size() > drawCount) {

                shown = ellipsize(shown + " …", maxWidth);
            }

            labels.add(new Label(shown, 12, y + line * lineStep, DialogueRetroTheme.TEXT_HINT));
        }
    }

    private int rowY(int row) {
        return bodyTop + 38 + row * rowSpacing - scrollOffset;
    }

    private int inspectorContentTop() {
        return bodyTop + 26;
    }

    private int inspectorContentBottom() {
        return height - 26;
    }

    private <T extends AbstractWidget> T addScrollableWidget(T widget) {
        scrollWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private void updateScrollWidgetVisibility() {
        int contentTop = inspectorContentTop();
        int contentBottom = inspectorContentBottom();

        for (AbstractWidget widget : scrollWidgets) {
            widget.visible = widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= contentBottom;
        }
    }

    private int maxLeftScroll() {
        int maxRow = switch (tab) {
            case PROJECT -> 21;
            case DIALOGUE -> 18;
            case VISUALS -> 9;
            case LINES -> 13;
            case LINE_OVERRIDES, LAYOUT, TRIGGERS -> 10;
            case ZONE_FX -> 36;
            case NODES -> 13;
            case ZONE -> 12;
            case GAMEPLAY -> 7;
            case EXPORT -> 13;
        };

        int contentBottom = bodyTop + 38 + maxRow * rowSpacing + controlHeight + 10;
        return Math.max(0, contentBottom - inspectorContentBottom());
    }

    private void renderLineTimeline(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        DialogueRetroTheme.drawPanel(graphics, x, y, x + w, y + h);
        DialogueRetroTheme.drawDarkInset(graphics, x + 4, y + 18, x + w - 4, y + h - 4);
        graphics.enableScissor(x, y, x + w, y + h);

        int cardW = 106;

        int gap = 4;

        int visible = Math.max(1, (w - 16) / (cardW + gap));

        LINE_TIMELINE_SCROLL = Math.max(0, Math.min(LINE_TIMELINE_SCROLL, Math.max(0, project.definition.lines.size() - visible)));

        String title = "LINE / SPRITE TIMELINE";

        graphics.drawString(font, ellipsize(title, Math.max(40, w - 16)), x + 8, y + 5, DialogueRetroTheme.TEXT_PATH, false);
        if (project.definition.lines.size() > visible) {

            String hint = "wheel: scroll";

            int hintW = font.width(hint);

            int titleW = font.width(title);

            if (titleW + hintW + 32 <= w) {

                graphics.drawString(font, hint, x + w - hintW - 8, y + 5, 0xFF9C957B, false);
            }
        }

        int end = Math.min(project.definition.lines.size(), LINE_TIMELINE_SCROLL + visible);

        for (int index = LINE_TIMELINE_SCROLL; index < end; index++) {

            DialogueDefinition.Line line = project.definition.lines.get(index);

            int slot = index - LINE_TIMELINE_SCROLL;

            int cx = x + 8 + slot * (cardW + gap);

            int cy = y + 20;

            int color = index == project.selected_line ? 0xFF344B2B : 0xFF1C2418;

            if (index == dragLineIndex) {
                color = 0xFF4A6339;
            }

            graphics.fill(cx, cy, cx + cardW, cy + 40, color);

            graphics.fill(cx + 1, cy + 1, cx + cardW - 1, cy + 39, 0xFF11170E);

            ResourceLocation sprite = DialogueEditorTextureCache.resolve(project, line.sprite, EDITOR_DEFAULT_SPRITE);

            if (sprite != null) {
                graphics.blit(sprite, cx + 4, cy + 4, 0, 0, 30, 30, 30, 30);
            }

            graphics.drawString(font, "#" + (index + 1), cx + 38, cy + 4, index == project.selected_line ? 0xFFB8FF72 : 0xFFFFFFFF, false);

            String text = line.literal != null ? line.literal : project.getLocalizedText(project.preview_locale, line, index);

            graphics.drawString(font, shortText(text, 11), cx + 38, cy + 15, 0xFFD8CFB0, false);

            String transition = line.sprite_transition != null ? line.sprite_transition : project.definition.sprite_transition;

            graphics.drawString(font, shortText(transition != null ? transition : "none", 11), cx + 38, cy + 27, 0xFF9C957B, false);
        }

        graphics.disableScissor();
    }


    private int timelineVisibleCards() {
        int w = Math.max(1, width - (LEFT + 12) - 12);
        return Math.max(1, (w - 16) / 110);
    }

    private int timelineLineAt(double mouseX, double mouseY) {
        if (timelineY < 0) return -1;
        int x = LEFT + 12;
        int w = width - x - 12;
        if (mouseX < x + 8 || mouseX > x + w - 8 || mouseY < timelineY + 20 || mouseY > timelineY + 60) {
            return -1;
        }
        int slot = (int) ((mouseX - (x + 8)) / 110.0D);
        int localX = (int) ((mouseX - (x + 8)) % 110.0D);
        if (localX > 106) return -1;
        int index = LINE_TIMELINE_SCROLL + slot;
        return index >= 0 && index < project.definition.lines.size() ? index : -1;
    }

    private static String shortText(String value, int max) {
        if (value == null || value.isBlank()) return "<empty>";
        value = value.replace('\n', ' ');
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private List<String> wrapPlainText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }

            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;

                if (line.length() > 0 && font.width(candidate) > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                }

                if (font.width(word) > maxWidth && line.length() == 0) {
                    StringBuilder chunk = new StringBuilder();
                    for (int i = 0; i < word.length(); i++) {
                        char c = word.charAt(i);
                        if (chunk.length() > 0 && font.width(chunk.toString() + c) > maxWidth) {
                            result.add(chunk.toString());
                            chunk.setLength(0);
                        }
                        chunk.append(c);
                    }
                    line.append(chunk);
                } else {
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                }
            }

            if (line.length() > 0) {
                result.add(line.toString());
            }
        }

        return result;
    }

    private int drawWrappedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color, int maxLines) {
        int drawn = 0;
        int lineStep = font.lineHeight + 1;

        for (var wrapped : font.split(Component.literal(text != null ? text : ""), Math.max(20, maxWidth))) {
            if (drawn >= Math.max(1, maxLines)) {
                break;
            }
            graphics.drawString(font, wrapped, x, y + drawn * lineStep, color, false);
            drawn++;
        }

        return Math.max(lineStep, drawn * lineStep);
    }

    private String ellipsize(String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }

        String suffix = "…";
        int suffixWidth = font.width(suffix);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (font.width(result.toString() + c) + suffixWidth > maxWidth) {
                break;
            }
            result.append(c);
        }
        return result + suffix;
    }

    private void showToast(String text) {
        toastText = text;
        toastTicks = 0;
    }

    private void renderToast(GuiGraphics graphics, float partialTick) {
        if (toastText == null || toastTicks < 0) {
            return;
        }

        float age = toastTicks + partialTick;
        float progress = Math.max(0.0F, Math.min(1.0F, age / TOAST_DURATION));

        float fadeIn = Math.min(1.0F, age / 6.0F);
        float fadeOut = age <= 46.0F ? 1.0F : Math.max(0.0F, 1.0F - (age - 46.0F) / (TOAST_DURATION - 46.0F));
        float alpha = fadeIn * fadeOut;
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0D);
        int rise = Math.round(24.0F * eased);

        int textW = font.width(toastText);
        int boxW = Math.min(width - 24, textW + 26);
        int boxH = 24;
        int x = (width - boxW) / 2;
        int y = height - 54 - rise;

        int bgAlpha = Math.max(0, Math.min(255, Math.round(190.0F * alpha)));
        int borderAlpha = Math.max(0, Math.min(255, Math.round(235.0F * alpha)));
        int textAlpha = Math.max(0, Math.min(255, Math.round(255.0F * alpha)));

        int shadow = (Math.max(0, Math.min(255, Math.round(150.0F * alpha))) << 24);
        graphics.fill(x + 3, y + 3, x + boxW + 3, y + boxH + 3, shadow);
        graphics.fill(x, y, x + boxW, y + boxH, (bgAlpha << 24) | 0xE9E2C8);
        graphics.fill(x, y, x + boxW, y + 1, (borderAlpha << 24) | 0x080A07);
        graphics.fill(x, y + 1, x + boxW, y + 3, (borderAlpha << 24) | 0x87D653);

        String shown = ellipsize(toastText, boxW - 18);
        int tx = x + (boxW - font.width(shown)) / 2;
        graphics.drawString(font, shown, tx, y + 8, (textAlpha << 24) | 0x151812, false);
    }

    private void addTip(int x, int y, int w, int h, String text) {
        if (text == null || text.isBlank()) return;
        tips.add(new Tip(x, y, w, h, text));
    }

    private void renderTips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Tip tip : tips) {
            if (tip.y < inspectorContentTop() || tip.y + tip.h > inspectorContentBottom()) continue;
            if (mouseX >= tip.x && mouseX <= tip.x + tip.w && mouseY >= tip.y && mouseY <= tip.y + tip.h) {
                graphics.renderTooltip(font, Component.literal(tip.text), mouseX, mouseY);
                return;
            }
        }
    }

    private static String tooltipText(String label) {
        String key = label != null ? label.toLowerCase(Locale.ROOT) : "";
        if (key.startsWith("export for mods"))
            return "Builds a mod-ready data/ + assets/ template for src/main/resources. No datapack/resourcepack installation is needed.";
        if (key.startsWith("save studio"))
            return "Saves project.json for reopening in Dialogue Studio. This is not the datapack used by Minecraft.";
        if (key.startsWith("quick export") || key.startsWith("export datapack") || key.startsWith("export and"))
            return "Builds a real datapack and resource pack, plus ready datapack.zip/resourcepack.zip files.";
        if (key.startsWith("install into"))
            return "Copies the generated resource pack into this instance and the datapack into the current singleplayer world.";
        if (key.startsWith("project browser"))
            return "Browse all saved Dialogue Studio projects with a live miniature preview.";
        if (key.contains("namespace"))
            return "Resource namespace / mod id, e.g. mydialogues. Used by textures, sounds, lang keys and the dialogue id.";
        if (key.contains("dialogue path"))
            return "Path after the namespace. Example quest/intro becomes mydialogues:quest/intro.";
        if (key.contains("preview language"))
            return "Language edited and shown in the live preview, e.g. en_us or ru_ru.";
        if (key.contains("voice sound"))
            return "Sound event id used for typewriter voice. Browse can import an .ogg and generate sounds.json automatically.";
        if (key.contains("voice source")) return "Minecraft sound category / volume slider controlling this voice.";
        if (key.contains("pitch")) return "Voice playback pitch. 1.0 is normal.";
        if (key.contains("volume")) return "Voice playback volume.";
        if (key.contains("voice every")) return "Play a voice sound every N revealed letters/digits.";
        if (key.contains("char ticks")) return "Ticks between revealed characters. 20 ticks = 1 second.";
        if (key.contains("hold ticks")) return "How long the completed line stays before advancing.";
        if (key.contains("fade ticks")) return "Dialogue fade-in/fade-out duration.";
        if (key.contains("text color") || key.equals("color"))
            return "Named color or HEX. Click Color for the visual HSV picker.";
        if (key.contains("gradient"))
            return "Comma-separated gradient stops, e.g. #42F2E1, purple, gold. 'none' disables an inherited line gradient.";
        if (key.contains("text effect"))
            return "Animation applied to revealed letters: normal, wave, shake, explode or linear.";
        if (key.contains("markdown"))
            return "Enable lightweight Markdown: **bold**, *italic*, ~~strike~~ and __underline__. Disabled by default for backwards compatibility.";
        if (key.contains("font"))
            return "Minecraft default is used when blank. The font picker can import TTF or an msdf-atlas-gen JSON + PNG pair.";
        if (key.contains("outline"))
            return "Text outline. Color accepts named/HEX/rainbow; gradient uses comma-separated colors; thickness 0 disables it.";
        if (key.contains("frame texture") || key.contains("frame override"))
            return "PNG used as the dialogue frame. Browse or drag a PNG into Studio.";
        if (key.contains("background texture") || key.contains("background override"))
            return "Fullscreen dialogue background PNG. Blank uses Dialogue Studio dithering_gradient.png.";
        if (key.contains("sprite png"))
            return "Character portrait PNG for this line. New projects use Dialogue Studio's transparent placeholder sprite.";
        if (key.contains("sprite position"))
            return "Preset horizontal sprite position. Exact sprite X can override this per line.";
        if (key.contains("sprite transition")) return "Visual transition when the portrait changes between lines.";
        if (key.contains("frame x") || key.contains("text x") || key.contains("left x") || key.contains("center x") || key.contains("right x"))
            return "Horizontal position in the Dialogue Engine virtual 192x108 canvas.";
        if (key.contains("frame y") || key.contains("text y") || key.contains("sprite y"))
            return "Vertical position in the Dialogue Engine virtual canvas.";
        if (key.contains("gizmo target"))
            return "Choose FRAME, TEXT or SPRITE, then drag the colored gizmo directly in the preview.";
        if (key.startsWith("type")) return "Trigger type deciding what starts the dialogue.";
        if (key.contains("target"))
            return "Entity/block registry id or #tag matched by this trigger. Registry opens a visual browser.";
        if (key.contains("radius")) return "Trigger/zone radius in Minecraft blocks.";
        if (key.contains("cooldown")) return "Ticks before this trigger instance can activate again.";
        if (key.contains("dimension"))
            return "Optional dimension id, e.g. minecraft:overworld. Blank means any dimension.";
        if (key.contains("anchor type"))
            return "What the visual zone attaches to: a block, entity or absolute position.";
        if (key.contains("entity tag"))
            return "Optional entity tag, especially useful with invisible minecraft:marker anchors.";
        if (key.contains("shape")) return "Zone gameplay shape: cylinder, sphere or box.";
        if (key.contains("offset")) return "Moves the real zone relative to its anchor.";
        if (key.contains("preview enabled"))
            return "Controls only zone visualization. The gameplay trigger still works when this is OFF.";
        if (key.startsWith("style")) return "Zone preview style: auto, ring, outline, sprite or pillar.";
        if (key.contains("custom floor"))
            return "Optional transparent PNG rendered flat on the ground for the zone preview.";
        if (key.contains("alpha")) return "Transparency from 0.0 to 1.0.";
        if (key.contains("visual size")) return "Rendered marker size in Minecraft blocks. 0 = automatic.";
        if (key.contains("preview distance")) return "Maximum distance at which the client sees this zone marker.";
        if (key.contains("edit zone in world"))
            return "Open the Axiom-like Zone World Edit Mode. Drag X/Y/Z gizmos directly in the world and edit the real trigger volume visually.";
        if (key.contains("freeze source")) return "Stops the dialogue source entity while dialogue is active.";
        if (key.contains("invulnerable")) return "Makes the dialogue source invulnerable during the dialogue.";
        if (key.startsWith("once")) return "Controls whether a dialogue can repeat: never, player, entity or session.";
        if (key.equals("browse")) return "Choose a file from your computer.";
        return "Dialogue Studio setting: " + label;
    }

    private String triggerPreviewTarget(DialogueDefinition.Trigger t) {
        if ("zone".equalsIgnoreCase(t.type) && t.anchor != null && t.anchor.target != null) return t.anchor.target;
        return t.target;
    }

    private boolean isEntityTarget(DialogueDefinition.Trigger t) {
        if ("zone".equalsIgnoreCase(t.type) && t.anchor != null) return "entity".equalsIgnoreCase(t.anchor.type);
        String type = t.type != null ? t.type.toLowerCase(Locale.ROOT) : "";
        return type.contains("entity") || type.equals("external");
    }

    private boolean isBlockTarget(DialogueDefinition.Trigger t) {
        if ("zone".equalsIgnoreCase(t.type) && t.anchor != null) return "block".equalsIgnoreCase(t.anchor.type);
        String type = t.type != null ? t.type.toLowerCase(Locale.ROOT) : "";
        return type.contains("block");
    }

    private static int richRegionCount(DialogueDefinition.Line line) {
        return line != null && line.rich_regions != null ? line.rich_regions.size() : 0;
    }


    private String globalOutlineSummary() {
        float thickness = Math.max(0.0F, project.definition.text_outline_thickness);

        if (thickness <= 0.01F) {
            return "Global text outline: OFF  •  Configure...";
        }

        String color = project.definition.text_outline_color != null && !project.definition.text_outline_color.isBlank() ? project.definition.text_outline_color : "black";

        return "Global text outline: " + String.format(Locale.ROOT, "%.2f", thickness) + " px  •  " + color + "  •  Configure...";
    }


    private static String fontSummary(String value) {
        if (value == null || value.isBlank() || "minecraft:default".equalsIgnoreCase(value)) {
            return "VANILLA";
        }
        return value.length() <= 24 ? value : "…" + value.substring(value.length() - 23);
    }


    private static String effectsSummary(List<String> effects, boolean inheritAllowed) {
        if (effects == null) return inheritAllowed ? "INHERIT" : "legacy";
        if (effects.isEmpty()) return "NONE";
        return String.join(" + ", effects);
    }

    private static String next(String current, List<String> values) {
        int index = values.indexOf(current);
        return values.get(index < 0 ? 0 : (index + 1) % values.size());
    }

    private static List<String> parseGradient(String value, boolean allowDisable) {
        if (value == null || value.isBlank()) return null;
        if (allowDisable && value.trim().equalsIgnoreCase("none")) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) if (!part.isBlank()) result.add(part.trim());
        return result.isEmpty() ? null : result;
    }

    private static String join(List<String> value) {
        return value == null ? "" : String.join(", ", value);
    }

    private static String gradientOverride(List<String> value) {
        return value == null ? "" : value.isEmpty() ? "none" : String.join(", ", value);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String nullable(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private static int i(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float f(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double d(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Integer ni(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Float nf(String s) {
        try {
            return s == null || s.isBlank() ? null : Float.parseFloat(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double nd(String s) {
        try {
            return s == null || s.isBlank() ? null : Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Label(String text, int x, int y, int color) {
    }

    private record Tip(int x, int y, int w, int h, String text) {
    }
}
