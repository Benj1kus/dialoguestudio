package com.benji.dialoguestudio.dialogue.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DialogueDefinition {

    public int format = 3;

    public String voice;
    public String voice_source = "master";

    public float voice_pitch = 1.0F;
    public float voice_volume = 0.55F;
    public int voice_every = 1;

    public int char_ticks = 2;
    public int hold_ticks = 20;
    public int fade_ticks = 14;

    public String text_color = "white";
    public List<String> text_gradient;

    public String text_effect = "wave";
    public List<String> text_effects;

    public String text_style;

    public boolean markdown = false;
    public String text_font;
    public String text_outline_color;
    public List<String> text_outline_gradient;
    public float text_outline_thickness = 0.0F;

    public String frame;
    public String background;

    public float background_alpha = 0.62F;
    public float background_bob = 1.6F;
    public float background_speed = 1.1F;

    public String sprite_position = "center";
    public String sprite_transition = "bounce";

    public int sprite_move_ticks = 10;
    public int sprite_transition_ticks = 8;

    public boolean freeze_source = true;
    public boolean source_invulnerable = true;
    public boolean cancel_if_source_missing = true;
    public boolean exclusive_source = true;
    public String once = "never";

    public Layout layout = new Layout();
    public List<Trigger> triggers = new ArrayList<>();

    public List<Line> lines = new ArrayList<>();
    public boolean graph_enabled = false;
    public String start_node;
    public Map<String, Node> nodes = new LinkedHashMap<>();


    public boolean hasGraph() {
        return graph_enabled && start_node != null && !start_node.isBlank() && nodes != null && !nodes.isEmpty() && nodes.containsKey(start_node);
    }


    public static class Layout {

        public int canvas_width = 192;
        public int canvas_height = 108;

        public int frame_x = 0;
        public int frame_y = 63;
        public int frame_width = 192;
        public int frame_height = 45;

        public int text_x = 23;
        public int text_y = 75;
        public int text_width = 146;

        public float text_scale = 0.72F;
        public int line_height = 10;

        public int sprite_width = 126;
        public int sprite_height = 78;
        public int sprite_y = 0;

        public float sprite_left_x = 0.0F;
        public float sprite_center_x = 33.0F;
        public float sprite_right_x = 66.0F;

        public int choice_x = 23;
        public int choice_y = 86;
        public int choice_width = 146;
        public float choice_scale = 0.62F;
        public int choice_line_height = 9;

        public String choice_color = "white";
        public String choice_selected_color = "gold";
        public String choice_disabled_color = "#777777";
    }


    public static class Line {

        public String text;
        public String literal;
        public String sprite;

        public Integer char_ticks;
        public Integer hold_ticks;

        public String voice;
        public String voice_source;
        public Float voice_pitch;
        public Float voice_volume;
        public Integer voice_every;

        public String text_color;
        public List<String> text_gradient;

        public String text_effect;
        public List<String> text_effects;
        public String text_style;

        public Boolean markdown;
        public String text_font;
        public String text_outline_color;
        public List<String> text_outline_gradient;
        public Float text_outline_thickness;

        public List<TextRegion> rich_regions;

        public String frame;
        public String background;

        public String sprite_position;
        public Float sprite_x;
        public String sprite_transition;

        public Integer sprite_move_ticks;
        public Integer sprite_transition_ticks;

        public Integer sprite_width;
        public Integer sprite_height;
    }

    public static class TextRegion {
        public String name;

        public int start = 0;
        public int end = 0;

        public String match;
        public String locale;
        public String color;
        public List<String> gradient;
        public List<String> effects;

        public Boolean bold;
        public Boolean italic;
        public Boolean underline;
        public Boolean strikethrough;

        public String font;

        public String outline_color;
        public List<String> outline_gradient;
        public Float outline_thickness;

        public TextAnimation animation = new TextAnimation();
    }

    public static class TextAnimation {
        public Float wave_amplitude;
        public Float wave_speed;
        public Float wave_frequency;

        public Float shake_strength;

        public Float explode_amount;
        public Integer explode_ticks;

        public Float slide_distance;
        public Integer slide_ticks;
    }


    public static class Node {
        public String type = "line";

        public Line line;

        public String next;

        public String else_node;

        public List<Choice> choices = new ArrayList<>();

        public List<Condition> conditions = new ArrayList<>();

        public List<Action> actions = new ArrayList<>();
    }


    public static class Choice {
        public String text;
        public String literal;

        public String goto_node;
        public String when_unavailable = "hide";

        public List<Condition> conditions = new ArrayList<>();
        public List<Action> actions = new ArrayList<>();
    }

    public static class Condition {
        public String type = "always";

        public String id;

        public String objective;
        public String operator = ">=";
        public int value = 1;

        public int count = 1;
        public String state = "active";

        public Map<String, String> data = new LinkedHashMap<>();

        public boolean invert = false;
    }

    public static class Action {
        public String type = "fire_external";

        public String target = "player";

        public String id;

        public int count = 1;

        public String objective;
        public int value = 1;

        public String command;

        public String event;

        public String sound_source = "master";
        public float volume = 1.0F;
        public float sound_pitch = 1.0F;

        public double x = 0.0D;
        public double y = 0.0D;
        public double z = 0.0D;

        public boolean relative = false;
        public String dimension;
        public Float yaw;
        public Float teleport_pitch;

        public double spread_x = 0.0D;
        public double spread_y = 0.0D;
        public double spread_z = 0.0D;
        public double speed = 0.0D;

        public Map<String, String> data = new LinkedHashMap<>();
    }


    public static class Trigger {
        public String type = "manual";
        public String target;

        public double radius = 5.0D;
        public double look_angle = 12.0D;

        public int check_interval = 5;
        public int cooldown_ticks = 40;

        public boolean consume = false;

        public String once;

        public String event;

        public String dimension;

        public Double x;
        public Double y;
        public Double z;

        public Double min_x;
        public Double min_y;
        public Double min_z;

        public Double max_x;
        public Double max_y;
        public Double max_z;

        public ZoneAnchor anchor;
        public String shape = "cylinder";

        public double height = 2.0D;
        public double size_x = 6.0D;
        public double size_y = 2.0D;
        public double size_z = 6.0D;
        public ZoneVisual visual = new ZoneVisual();
    }


    public static class ZoneAnchor {
        public String type = "absolute";
        public String target;
        public String entity_tag;
        public String pick = "nearest";

        public Double x;
        public Double y;
        public Double z;

        public double offset_x = 0.0D;
        public double offset_y = 0.0D;
        public double offset_z = 0.0D;

        public double search_height = 8.0D;
    }


    public static class ZoneVisual {
        public boolean enabled = true;
        public String style = "auto";
        public boolean show_default_zone = true;

        public String color = "cyan";
        public float alpha = 0.55F;
        public double y_offset = 0.03D;
        public double size = 0.0D;
        public double visual_height = 0.0D;
        public double preview_distance = 16.0D;

        public String texture;
        public String texture_mode = "plane";
        public String texture_fit = "stretch";
        public double texture_repeat_x = 1.0D;
        public double texture_repeat_y = 1.0D;
        public double texture_scroll_u = 0.0D;
        public double texture_scroll_v = 0.0D;

        public double texture_offset_x = 0.0D;
        public double texture_offset_y = 0.0D;
        public double texture_offset_z = 0.0D;
        public double texture_scale_x = 1.0D;
        public double texture_scale_y = 1.0D;

        public double texture_rotation_x = 0.0D;
        public double texture_rotation = 0.0D;
        public double texture_rotation_z = 0.0D;

        public boolean fill_enabled = false;
        public String fill_mode = "gradient";
        public String fill_color_bottom = "cyan";
        public String fill_color_top = "cyan";
        public float fill_alpha_bottom = 0.35F;
        public float fill_alpha_top = 0.0F;

        public boolean pulse = true;
        public double pulse_amplitude = 0.035D;
        public double pulse_speed = 1.0D;

        public boolean bob = false;
        public double bob_amplitude = 0.20D;
        public double bob_speed = 0.75D;

        public boolean rotate = false;
        public double rotate_speed = 30.0D;

        public boolean alpha_breathe = false;
        public double alpha_breathe_amount = 0.18D;
        public double alpha_breathe_speed = 0.70D;

        public String preset = "custom";
    }
}
