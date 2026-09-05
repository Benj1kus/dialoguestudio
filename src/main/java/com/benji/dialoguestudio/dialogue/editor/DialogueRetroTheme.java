package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;

public final class DialogueRetroTheme {

    private DialogueRetroTheme() {
    }

    public static final int BLACK = 0xFF1E1E1F;
    public static final int BLACK_SOFT = 0xFF28292A;

    public static final int DARK_GREEN = 0xFF2C681C;
    public static final int GREEN = 0xFF3C8527;
    public static final int GREEN_MID = 0xFF4D9637;
    public static final int LIME = 0xFF639D52;
    public static final int LIME_SOFT = 0xFF3C8527;

    public static final int CREAM = 0xFFB1B2B5;
    public static final int CREAM_LIGHT = 0xFFD0D1D4;
    public static final int BEIGE = 0xFFB1B2B5;
    public static final int BEIGE_DARK = 0xFF58585A;

    public static final int DESKTOP = 0xFF2A2B2C;
    public static final int DESKTOP_DARK = 0xFF1E1F20;

    public static final int TEXT_LIGHT = 0xFFF4F4F4;
    public static final int TEXT_MUTED = 0xFFB7B7B7;
    public static final int TEXT_DARK = 0xFFF4F4F4;
    public static final int TEXT_DARK_MUTED = 0xFFA3A3A3;
    public static final int TEXT_HINT = 0xFFBDBDBD;
    public static final int TEXT_PATH = 0xFFD8D8D8;
    public static final int TEXT_ACCENT = 0xFFF4F4F4;

    public static final int BUTTON_TEXT = 0xFF202020;
    public static final int BUTTON_TEXT_DISABLED = 0xFF66676A;

    public static final int BUTTON_NEUTRAL_TOP = 0xFFD0D1D4;
    public static final int BUTTON_NEUTRAL = 0xFFB1B2B5;
    public static final int BUTTON_NEUTRAL_BOTTOM = 0xFF8C8D90;

    public static final int BUTTON_HOVER_TOP = 0xFFE0E1E3;
    public static final int BUTTON_HOVER = 0xFFC3C4C7;

    public static final int BUTTON_SELECTED_TOP = 0xFF639D52;
    public static final int BUTTON_SELECTED = 0xFF3C8527;
    public static final int BUTTON_SELECTED_BOTTOM = 0xFF2C681C;

    public static final int PANEL = 0xFF48494A;
    public static final int PANEL_DARK = 0xFF3F4041;
    public static final int PANEL_LIGHT_EDGE = 0xFF5A5B5C;

    public static final int ERROR = 0xFFFF746A;
    public static final int WARNING = 0xFFFFD86A;


    public static void drawDesktop(GuiGraphics graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, DESKTOP, DESKTOP_DARK);
    }


    public static void drawPanel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        graphics.fill(x0 + 3, y0 + 3, x1 + 3, y1 + 3, 0x94000000);
        graphics.fill(x0, y0, x1, y1, BLACK);
        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL, PANEL_DARK);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, PANEL_LIGHT_EDGE);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0xFF303132);
    }


    public static void drawDarkInset(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        graphics.fill(x0, y0, x1, y1, BLACK);
        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF343536, 0xFF28292A);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0xFF1B1C1D);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, 0xFF1B1C1D);
    }


    public static void drawTitleBar(GuiGraphics graphics, int x0, int y0, int x1, int height) {
        int y1 = y0 + Math.max(10, height);

        graphics.fill(x0, y0, x1, y1, BLACK);
        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF262627, 0xFF1E1E1F);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0xFF101011);
    }


    public static void drawSeparator(GuiGraphics graphics, int x0, int y, int x1) {
        graphics.fill(x0, y, x1, y + 1, 0xFF2F3031);

        graphics.fill(x0, y + 1, x1, y + 2, 0xFF5A5B5C);
    }


    public static void drawRetroScrollTrack(GuiGraphics graphics, int x, int y0, int y1, int thumbY, int thumbH) {

        graphics.fill(x - 1, y0, x + 3, y1, BLACK);
        graphics.fill(x, y0 + 1, x + 2, y1 - 1, 0xFF77787A);
        graphics.fill(x - 2, thumbY, x + 4, thumbY + thumbH, BLACK);
        graphics.fillGradient(x - 1, thumbY + 1, x + 3, thumbY + thumbH - 1, BUTTON_NEUTRAL_TOP, BUTTON_NEUTRAL_BOTTOM);
    }
}
