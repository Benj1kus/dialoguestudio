package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;

public final class DialogueEditorColorPickerScreen extends DialogueRetroScreen {

    private final Screen parent;
    private final Consumer<String> callback;

    private float hue;
    private float saturation;
    private float brightness;

    private boolean draggingSB;
    private boolean draggingHue;
    private EditBox hexBox;
    private boolean updatingHex;

    public DialogueEditorColorPickerScreen(Screen parent, String initial, Consumer<String> callback) {
        super(Component.literal("Dialogue Studio - Color Picker"));
        this.parent = parent;
        this.callback = callback;

        int rgb = DialogueEditorPreview.parseColor(initial);
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    @Override
    protected void init() {
        int panelW = 250;
        int left = (width - panelW) / 2;
        int top = Math.max(24, (height - 236) / 2);

        hexBox = new DialogueRetroEditBox(font, left + 16, top + 184, 142, 20, Component.literal("HEX"));
        hexBox.setMaxLength(7);
        hexBox.setValue(hex());
        hexBox.setResponder(value -> {
            if (updatingHex) return;
            String clean = value != null ? value.trim() : "";
            if (!clean.startsWith("#")) clean = "#" + clean;
            if (clean.matches("#[0-9a-fA-F]{6}")) {
                int rgb = Integer.parseInt(clean.substring(1), 16);
                float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
                hue = hsb[0];
                saturation = hsb[1];
                brightness = hsb[2];
            }
        });
        addRenderableWidget(hexBox);

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Use color"), b -> {
            callback.accept(hex());
            minecraft.setScreen(parent);
        }).bounds(left + 164, top + 184, 70, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(left + 164, top + 208, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelW = 250;
        int left = (width - panelW) / 2;
        int top = Math.max(24, (height - 236) / 2);

        graphics.fill(left, top, left + panelW, top + 236, 0xF0141A11);
        graphics.fill(left + 1, top + 1, left + panelW - 1, top + 235, 0xFF0C110A);
        graphics.drawString(font, "COLOR PICKER", left + 16, top + 10, 0xFFB8FF72, false);

        int sx = left + 16;
        int sy = top + 30;
        int sw = 150;
        int sh = 140;

        // Saturation / brightness square.
        for (int yy = 0; yy < sh; yy += 2) {
            float b = 1.0F - yy / (float) (sh - 1);
            for (int xx = 0; xx < sw; xx += 2) {
                float s = xx / (float) (sw - 1);
                int rgb = Color.HSBtoRGB(hue, s, b) & 0xFFFFFF;
                graphics.fill(sx + xx, sy + yy, sx + Math.min(sw, xx + 2), sy + Math.min(sh, yy + 2), 0xFF000000 | rgb);
            }
        }

        int hx = sx + sw + 10;
        int hw = 18;
        for (int yy = 0; yy < sh; yy += 2) {
            float h = yy / (float) (sh - 1);
            int rgb = Color.HSBtoRGB(h, 1.0F, 1.0F) & 0xFFFFFF;
            graphics.fill(hx, sy + yy, hx + hw, sy + Math.min(sh, yy + 2), 0xFF000000 | rgb);
        }

        int markerX = sx + Math.round(saturation * (sw - 1));
        int markerY = sy + Math.round((1.0F - brightness) * (sh - 1));
        graphics.hLine(markerX - 4, markerX + 4, markerY, 0xFFFFFFFF);
        graphics.vLine(markerX, markerY - 4, markerY + 4, 0xFFFFFFFF);
        graphics.hLine(hx - 2, hx + hw + 2, sy + Math.round(hue * (sh - 1)), 0xFFFFFFFF);

        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        graphics.fill(left + 200, top + 30, left + 234, top + 64, 0xFF000000 | rgb);
        graphics.drawString(font, "Preview", left + 194, top + 70, 0xFFE8E0C3, false);
        graphics.drawString(font, String.format(Locale.ROOT, "H %.0f°", hue * 360.0F), left + 194, top + 90, DialogueRetroTheme.TEXT_HINT, false);
        graphics.drawString(font, String.format(Locale.ROOT, "S %.0f%%", saturation * 100.0F), left + 194, top + 102, DialogueRetroTheme.TEXT_HINT, false);
        graphics.drawString(font, String.format(Locale.ROOT, "V %.0f%%", brightness * 100.0F), left + 194, top + 114, DialogueRetroTheme.TEXT_HINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelW = 250;
            int left = (width - panelW) / 2;
            int top = Math.max(24, (height - 236) / 2);
            int sx = left + 16;
            int sy = top + 30;
            int sw = 150;
            int sh = 140;
            int hx = sx + sw + 10;

            if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
                draggingSB = true;
                updateSB(mouseX, mouseY, sx, sy, sw, sh);
                return true;
            }
            if (mouseX >= hx && mouseX <= hx + 18 && mouseY >= sy && mouseY <= sy + sh) {
                draggingHue = true;
                updateHue(mouseY, sy, sh);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int panelW = 250;
        int left = (width - panelW) / 2;
        int top = Math.max(24, (height - 236) / 2);
        int sx = left + 16;
        int sy = top + 30;
        int sw = 150;
        int sh = 140;

        if (draggingSB) {
            updateSB(mouseX, mouseY, sx, sy, sw, sh);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseY, sy, sh);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSB = false;
        draggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSB(double mouseX, double mouseY, int sx, int sy, int sw, int sh) {
        saturation = Mth.clamp((float) ((mouseX - sx) / sw), 0.0F, 1.0F);
        brightness = 1.0F - Mth.clamp((float) ((mouseY - sy) / sh), 0.0F, 1.0F);
        syncHex();
    }

    private void updateHue(double mouseY, int sy, int sh) {
        hue = Mth.clamp((float) ((mouseY - sy) / sh), 0.0F, 1.0F);
        syncHex();
    }

    private String hex() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private void syncHex() {
        if (hexBox == null) return;
        updatingHex = true;
        hexBox.setValue(hex());
        updatingHex = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
