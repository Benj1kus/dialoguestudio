package com.benji.dialoguestudio.dialogue.text;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class DialogueTextTags {
    private DialogueTextTags() {
    }

    public static Result expand(DialogueMarkdown.Result source) {
        Minecraft minecraft = Minecraft.getInstance();
        String player = minecraft.player != null ? minecraft.player.getGameProfile().getName() : minecraft.getUser().getName();
        return expand(source, player);
    }

    public static String expandPlain(String source) {
        return expand(DialogueMarkdown.parse(source, false)).markdown().text();
    }

    public static Result expand(DialogueMarkdown.Result source, String player) {
        String text = source.text();
        StringBuilder output = new StringBuilder();
        List<DialogueMarkdown.CharStyle> styles = new ArrayList<>();
        List<Integer> origins = new ArrayList<>();
        int[] starts = new int[text.length() + 1];
        int[] ends = new int[text.length() + 1];

        int i = 0;
        while (i < text.length()) {
            int consumed = 1;
            String replacement = text.substring(i, i + 1);
            if (text.startsWith("[[player]]", i)) {
                consumed = 10;
                replacement = "[player]";
            } else if (text.startsWith("[[br]]", i)) {
                consumed = 6;
                replacement = "[br]";
            } else if (text.startsWith("[player]", i) && player != null && !player.isEmpty()) {
                consumed = 8;
                replacement = player;
            } else if (text.startsWith("[br]", i)) {
                consumed = 4;
                replacement = "\n";
            }

            int begin = output.length();
            output.append(replacement);
            int end = output.length();
            for (int j = 0; j < replacement.length(); j++) {
                origins.add(i);
                styles.add(source.styleAt(i));
            }
            for (int j = i; j < i + consumed; j++) starts[j] = begin;
            for (int j = i + 1; j <= i + consumed; j++) ends[j] = end;
            i += consumed;
        }
        starts[text.length()] = output.length();
        return new Result(text, new DialogueMarkdown.Result(output.toString(), List.copyOf(styles)), origins.stream().mapToInt(Integer::intValue).toArray(), starts, ends);
    }

    public static final class Result {
        private final String source;
        private final DialogueMarkdown.Result markdown;
        private final int[] origins;
        private final int[] starts;
        private final int[] ends;

        private Result(String source, DialogueMarkdown.Result markdown, int[] origins, int[] starts, int[] ends) {
            this.source = source;
            this.markdown = markdown;
            this.origins = origins;
            this.starts = starts;
            this.ends = ends;
        }

        public DialogueMarkdown.Result markdown() {
            return markdown;
        }

        public DialogueRichTextUtil.ResolvedStyle resolve(DialogueDefinition.Line line, int index, String locale) {
            if (index < 0 || index >= origins.length) return new DialogueRichTextUtil.ResolvedStyle();
            DialogueRichTextUtil.ResolvedStyle style = DialogueRichTextUtil.resolve(line, source, origins[index], locale);
            style.gradientStart = boundary(style.gradientStart, starts);
            style.gradientEnd = boundary(style.gradientEnd, ends);
            style.outlineGradientStart = boundary(style.outlineGradientStart, starts);
            style.outlineGradientEnd = boundary(style.outlineGradientEnd, ends);
            return style;
        }

        private int boundary(int index, int[] positions) {
            return index < 0 ? index : positions[Math.min(index, source.length())];
        }
    }
}
