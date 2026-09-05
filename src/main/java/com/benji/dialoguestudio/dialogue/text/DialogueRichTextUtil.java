package com.benji.dialoguestudio.dialogue.text;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogueRichTextUtil {

    private DialogueRichTextUtil() {
    }


    public static ResolvedStyle resolve(DialogueDefinition.Line line, String text, int characterIndex, String locale) {
        ResolvedStyle result = new ResolvedStyle();

        if (line == null || line.rich_regions == null || line.rich_regions.isEmpty() || text == null || text.isEmpty()) {

            return result;
        }

        for (DialogueDefinition.TextRegion region : line.rich_regions) {

            if (region == null || !localeMatches(region.locale, locale)) {
                continue;
            }

            Range range = resolveRange(region, text);

            if (range == null || characterIndex < range.start || characterIndex >= range.end) {
                continue;
            }

            if (region.color != null) {
                result.color = region.color;
            }

            if (region.gradient != null) {
                result.gradient = List.copyOf(region.gradient);

                result.gradientStart = range.start;

                result.gradientEnd = range.end;
            }

            if (region.effects != null) {
                result.effects = List.copyOf(region.effects);
            }

            if (region.bold != null) {
                result.bold = region.bold;
            }

            if (region.italic != null) {
                result.italic = region.italic;
            }

            if (region.underline != null) {
                result.underline = region.underline;
            }

            if (region.strikethrough != null) {
                result.strikethrough = region.strikethrough;
            }

            if (region.font != null) {
                result.font = region.font;
            }

            if (region.outline_color != null) {
                result.outlineColor = region.outline_color;
            }

            if (region.outline_gradient != null) {
                result.outlineGradient = List.copyOf(region.outline_gradient);
                result.outlineGradientStart = range.start;
                result.outlineGradientEnd = range.end;
            }

            if (region.outline_thickness != null) {
                result.outlineThickness = region.outline_thickness;
            }

            mergeAnimation(result.animation, region.animation);
        }

        return result;
    }


    public static Range resolveRange(DialogueDefinition.TextRegion region, String text) {
        if (region == null || text == null) {
            return null;
        }

        int length = text.length();
        int start = clamp(region.start, 0, length);
        int end = clamp(region.end, start, length);

        String match = region.match;

        if (match == null || match.isEmpty()) {

            return end > start ? new Range(start, end) : null;
        }

        if (end > start && text.substring(start, end).equals(match)) {

            return new Range(start, end);
        }

        int repaired = nearestOccurrence(text, match, start);

        if (repaired >= 0) {
            return new Range(repaired, repaired + match.length());
        }

        return end > start ? new Range(start, end) : null;
    }

    public static void repairRegions(DialogueDefinition.Line line, String text, String locale) {
        if (line == null || line.rich_regions == null || text == null) {
            return;
        }

        for (DialogueDefinition.TextRegion region : line.rich_regions) {

            if (region == null || !localeMatches(region.locale, locale)) {
                continue;
            }

            Range range = resolveRange(region, text);

            if (range == null) {
                continue;
            }

            region.start = range.start;

            region.end = range.end;

            if (region.match == null || region.match.isEmpty()) {

                region.match = text.substring(range.start, range.end);
            }
        }
    }


    public static List<Integer> regionIndicesAt(DialogueDefinition.Line line, String text, int characterIndex, String locale) {
        List<Integer> result = new ArrayList<>();

        if (line == null || line.rich_regions == null || text == null) {
            return result;
        }

        for (int i = 0; i < line.rich_regions.size(); i++) {

            DialogueDefinition.TextRegion region = line.rich_regions.get(i);

            if (region == null || !localeMatches(region.locale, locale)) {
                continue;
            }

            Range range = resolveRange(region, text);

            if (range != null && characterIndex >= range.start && characterIndex < range.end) {

                result.add(i);
            }
        }

        return result;
    }


    public static boolean localeMatches(String regionLocale, String activeLocale) {
        if (regionLocale == null || regionLocale.isBlank() || "*".equals(regionLocale)) {

            return true;
        }

        if (activeLocale == null || activeLocale.isBlank()) {
            return false;
        }

        return regionLocale.trim().toLowerCase(Locale.ROOT).equals(activeLocale.trim().toLowerCase(Locale.ROOT));
    }


    private static void mergeAnimation(DialogueDefinition.TextAnimation target, DialogueDefinition.TextAnimation source) {
        if (target == null || source == null) {
            return;
        }

        if (source.wave_amplitude != null) {
            target.wave_amplitude = source.wave_amplitude;
        }

        if (source.wave_speed != null) {
            target.wave_speed = source.wave_speed;
        }

        if (source.wave_frequency != null) {
            target.wave_frequency = source.wave_frequency;
        }

        if (source.shake_strength != null) {
            target.shake_strength = source.shake_strength;
        }

        if (source.explode_amount != null) {
            target.explode_amount = source.explode_amount;
        }

        if (source.explode_ticks != null) {
            target.explode_ticks = source.explode_ticks;
        }

        if (source.slide_distance != null) {
            target.slide_distance = source.slide_distance;
        }

        if (source.slide_ticks != null) {
            target.slide_ticks = source.slide_ticks;
        }
    }


    private static int nearestOccurrence(String text, String needle, int preferredStart) {
        int best = -1;

        int bestDistance = Integer.MAX_VALUE;

        int from = 0;

        while (from <= text.length()) {
            int found = text.indexOf(needle, from);

            if (found < 0) {
                break;
            }

            int distance = Math.abs(found - preferredStart);

            if (distance < bestDistance) {
                best = found;

                bestDistance = distance;
            }

            from = found + 1;
        }

        return best;
    }


    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }


    public static final class ResolvedStyle {
        public String color;
        public List<String> gradient;
        public List<String> effects;

        public Boolean bold;
        public Boolean italic;
        public Boolean underline;
        public Boolean strikethrough;

        public String font;

        public String outlineColor;
        public List<String> outlineGradient;
        public Float outlineThickness;

        public int gradientStart = -1;
        public int gradientEnd = -1;
        public int outlineGradientStart = -1;
        public int outlineGradientEnd = -1;

        public final DialogueDefinition.TextAnimation animation = new DialogueDefinition.TextAnimation();
    }


    public record Range(int start, int end) {
    }
}
