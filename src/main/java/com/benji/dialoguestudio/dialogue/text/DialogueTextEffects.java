package com.benji.dialoguestudio.dialogue.text;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public final class DialogueTextEffects {
    public static final List<String> APPEARANCE = List.of("glow", "color_flow");
    public static final List<String> ALL = List.of("wave", "shake", "explode", "linear", "glow", "color_flow");

    private DialogueTextEffects() {}

    public static boolean has(List<String> effects, String effect) {
        return effects != null && effects.stream().anyMatch(s -> s != null && effect.equalsIgnoreCase(s.trim()));
    }

    public static List<String> baseEffects(DialogueDefinition definition, DialogueDefinition.Line line) {
        if (line.text_effects != null) return line.text_effects;
        if (definition.text_effects != null) return definition.text_effects;
        String single = line.text_effect != null ? line.text_effect : definition.text_effect;
        return single == null || single.isBlank() ? List.of() : List.of(single.toLowerCase(Locale.ROOT));
    }

    public static List<String> palette(DialogueDefinition definition, DialogueDefinition.Line line, DialogueRichTextUtil.ResolvedStyle rich) {
        return rich.gradient != null ? rich.gradient : line.text_gradient != null ? line.text_gradient : definition.text_gradient;
    }

    public static int color(int base, List<String> effects, List<String> palette, double seconds,
                            int index, int start, int length, ToIntFunction<String> parseColor) {
        int localIndex = Math.max(0, index - start);
        int result = base & 0xFFFFFF;
        if (has(effects, "color_flow") && palette != null && palette.size() >= 2) {
            double phase = positiveMod(seconds * .84 + localIndex * .081, palette.size());
            int a = (int) phase;
            double t = .5 - .5 * Math.cos(Math.PI * (phase - a));
            result = blend(parseColor.applyAsInt(palette.get(a)), parseColor.applyAsInt(palette.get((a + 1) % palette.size())), t);
        }
        return result;
    }

    public static int gradient(List<String> palette, double fraction, ToIntFunction<String> parseColor) {
        if (palette == null || palette.isEmpty()) return 0xFFFFFF;
        if (palette.size() == 1) return parseColor.applyAsInt(palette.get(0));
        double position = Math.max(0, Math.min(1, fraction)) * (palette.size() - 1);
        int index = Math.min(palette.size() - 2, (int) position);
        return blend(parseColor.applyAsInt(palette.get(index)), parseColor.applyAsInt(palette.get(index + 1)), position - index);
    }

    public static int blend(int a, int b, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static double positiveMod(double value, double period) { return ((value % period) + period) % period; }

    public static final class Frame {
        private final DialogueRichTextUtil.ResolvedStyle[] styles;
        private final int[] starts, lengths;

        public Frame(int length, IntFunction<DialogueRichTextUtil.ResolvedStyle> resolver) {
            styles = new DialogueRichTextUtil.ResolvedStyle[length];
            starts = new int[length];
            lengths = new int[length];
            for (int i = 0; i < length; i++) styles[i] = resolver.apply(i);
            int start = 0;
            while (start < length) {
                int end = start + 1;
                while (end < length && Objects.equals(styles[start].effects, styles[end].effects)) end++;
                for (int i = start; i < end; i++) { starts[i] = start; lengths[i] = end - start; }
                start = end;
            }
        }
        public DialogueRichTextUtil.ResolvedStyle style(int index) { return styles[index]; }
        public int start(int index) { return starts[index]; }
        public int length(int index) { return lengths[index]; }
    }
}
