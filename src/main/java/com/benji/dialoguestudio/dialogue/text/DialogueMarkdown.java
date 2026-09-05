package com.benji.dialoguestudio.dialogue.text;

import java.util.ArrayList;
import java.util.List;

public final class DialogueMarkdown {

    private DialogueMarkdown() {
    }

    public record CharStyle(boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        public static final CharStyle NORMAL = new CharStyle(false, false, false, false);
    }

    public record Result(String text, List<CharStyle> styles) {
        public CharStyle styleAt(int index) {
            if (index < 0 || index >= styles.size()) {
                return CharStyle.NORMAL;
            }
            return styles.get(index);
        }
    }

    public static Result parse(String source, boolean enabled) {
        String raw = source != null ? source : "";

        if (!enabled || raw.isEmpty()) {
            List<CharStyle> styles = new ArrayList<>(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                styles.add(CharStyle.NORMAL);
            }
            return new Result(raw, List.copyOf(styles));
        }

        StringBuilder visible = new StringBuilder();
        List<CharStyle> styles = new ArrayList<>();

        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;

        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);

            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (isMarkerChar(next) || next == '\\') {
                    visible.append(next);
                    styles.add(new CharStyle(bold, italic, underline, strike));
                    i += 2;
                    continue;
                }
            }

            if (startsWith(raw, i, "***") && ((bold && italic) || hasClosing(raw, "***", i + 3))) {
                bold = !bold;
                italic = !italic;
                i += 3;
                continue;
            }

            if (startsWith(raw, i, "**") && (bold || hasClosing(raw, "**", i + 2))) {
                bold = !bold;
                i += 2;
                continue;
            }

            if (startsWith(raw, i, "~~") && (strike || hasClosing(raw, "~~", i + 2))) {
                strike = !strike;
                i += 2;
                continue;
            }

            if (startsWith(raw, i, "__") && (underline || hasClosing(raw, "__", i + 2))) {
                underline = !underline;
                i += 2;
                continue;
            }

            if ((c == '*' || c == '_') && (italic || hasClosing(raw, String.valueOf(c), i + 1))) {
                italic = !italic;
                i++;
                continue;
            }

            visible.append(c);
            styles.add(new CharStyle(bold, italic, underline, strike));
            i++;
        }

        return new Result(visible.toString(), List.copyOf(styles));
    }

    private static boolean startsWith(String value, int index, String token) {
        return index >= 0 && index + token.length() <= value.length() && value.startsWith(token, index);
    }

    private static boolean hasClosing(String value, String token, int from) {
        int index = from;
        while (index >= 0 && index < value.length()) {
            index = value.indexOf(token, index);
            if (index < 0) {
                return false;
            }

            if (index == 0 || value.charAt(index - 1) != '\\') {
                return true;
            }
            index += token.length();
        }
        return false;
    }

    private static boolean isMarkerChar(char c) {
        return c == '*' || c == '_' || c == '~';
    }
}
