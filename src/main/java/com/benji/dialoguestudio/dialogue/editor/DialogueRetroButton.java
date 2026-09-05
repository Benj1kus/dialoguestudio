package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DialogueRetroButton extends Button {

    private static final long HOVER_JELLY_MS = 310L;
    private static final long PRESS_JELLY_MS = 430L;
    private static final long PRESS_DARKEN_MS = 155L;

    private final boolean selected;
    private final boolean tabStyle;
    private final OnPress actualOnPress;

    private boolean pressActionPending;

    private boolean wasMouseHovered;
    private long hoverStartedAt = -1L;
    private long pressStartedAt = -1L;
    private long lastRenderAt = -1L;
    private float hoverAmount;


    private DialogueRetroButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean selected, boolean tabStyle) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);

        this.selected = selected;
        this.tabStyle = tabStyle;
        this.actualOnPress = onPress;
    }


    public static Builder retroBuilder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }


    @Override
    public void onPress() {
        this.pressStartedAt = System.currentTimeMillis();

        if (this.pressActionPending) {
            return;
        }

        this.pressActionPending = true;

        Minecraft minecraft = Minecraft.getInstance();

        Screen screenAtPress = minecraft.screen;
        CompletableFuture.delayedExecutor(105L, TimeUnit.MILLISECONDS).execute(() -> minecraft.execute(() -> {
            this.pressActionPending = false;

            if (minecraft.screen != screenAtPress) {

                return;
            }

            this.actualOnPress.onPress(this);
        }));
    }


    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();

        boolean mouseHover = this.active && this.isMouseOver(mouseX, mouseY);

        if (mouseHover && !this.wasMouseHovered) {

            this.hoverStartedAt = now;

            DialogueJuiceSound.hoverClick();
        }

        this.wasMouseHovered = mouseHover;

        updateHoverAmount(now, mouseHover);

        float hoverJelly = hoverJelly(now);
        float pressJelly = pressJelly(now);

        float scaleX = 1.0F + this.hoverAmount * 0.010F + hoverJelly + pressJelly;
        float scaleY = 1.0F + this.hoverAmount * 0.007F - hoverJelly * 0.32F + pressJelly * 0.72F;

        int x0 = getX();
        int y0 = getY();

        int x1 = x0 + width;
        int y1 = y0 + height;

        float centerX = x0 + width * 0.5F;
        float centerY = y0 + height * 0.5F;

        graphics.pose().pushPose();

        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);

        boolean hover = isHoveredOrFocused();

        boolean enabled = this.active;
        boolean pressDarkened = isPressDarkened(now);

        int shadowGrow = mouseHover ? 1 : 0;

        graphics.fill(x0 + 2 - shadowGrow, y0 + 2, x1 + 2 + shadowGrow, y1 + 2 + shadowGrow, 0x8F000000);
        graphics.fill(x0, y0, x1, y1, DialogueRetroTheme.BLACK);

        if (!enabled) {
            drawDisabledFace(graphics, x0, y0, x1, y1);

        } else if (selected) {
            drawSelectedFace(graphics, x0, y0, x1, y1, hover, pressDarkened);

        } else {
            drawNeutralFace(graphics, x0, y0, x1, y1, hover, pressDarkened);
        }

        drawBevel(graphics, x0, y0, x1, y1, enabled, selected, pressDarkened);

        int alphaByte = Mth.ceil(this.alpha * 255.0F) << 24;

        int rgb;

        if (!enabled) {
            rgb = DialogueRetroTheme.BUTTON_TEXT_DISABLED & 0x00FFFFFF;

        } else if (selected) {
            rgb = 0x00FFFFFF;

        } else {
            rgb = DialogueRetroTheme.BUTTON_TEXT & 0x00FFFFFF;
        }

        int textColor = alphaByte | rgb;

        Minecraft minecraft = Minecraft.getInstance();

        String shown = minecraft.font.plainSubstrByWidth(getMessage().getString(),

                Math.max(8, width - 8));

        int textWidth = minecraft.font.width(shown);
        int textX = x0 + (width - textWidth) / 2;
        int textY = y0 + (height - 8) / 2;
        int textJellyY = Math.round(-hoverJelly * 18.0F - pressJelly * 12.0F);

        graphics.drawString(minecraft.font, shown, textX, textY + textJellyY, textColor, false);

        graphics.pose().popPose();
    }


    private boolean isPressDarkened(long now) {
        if (this.pressStartedAt < 0L) {
            return false;
        }

        long elapsed = now - this.pressStartedAt;

        return elapsed >= 0L && elapsed < PRESS_DARKEN_MS;
    }


    private void drawNeutralFace(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean hover, boolean pressed) {
        if (pressed) {
            graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF9A9B9E, 0xFF77787B);

            return;
        }

        if (hover) {
            graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, DialogueRetroTheme.BUTTON_HOVER_TOP, DialogueRetroTheme.BUTTON_HOVER);
            return;
        }

        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, DialogueRetroTheme.BUTTON_NEUTRAL_TOP, DialogueRetroTheme.BUTTON_NEUTRAL);
    }


    private void drawSelectedFace(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean hover, boolean pressed) {
        int top;
        int bottom;

        if (pressed) {
            top = 0xFF357824;
            bottom = 0xFF245A18;

        } else if (hover) {
            top = 0xFF70A960;
            bottom = DialogueRetroTheme.BUTTON_SELECTED;

        } else {
            top = DialogueRetroTheme.BUTTON_SELECTED_TOP;
            bottom = DialogueRetroTheme.BUTTON_SELECTED;
        }

        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, top, bottom);
    }


    private void drawDisabledFace(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF929396, 0xFF737477);
    }


    private void drawBevel(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean enabled, boolean selected, boolean pressed) {
        int hi;
        int lo;

        if (!enabled) {
            hi = 0xFFB1B2B4;
            lo = 0xFF57585A;

        } else if (selected) {
            hi = pressed ? 0xFF4D8C3B : 0xFF75AC65;
            lo = pressed ? 0xFF1F4E15 : DialogueRetroTheme.BUTTON_SELECTED_BOTTOM;

        } else {
            hi = pressed ? 0xFFB7B8BA : 0xFFE2E3E5;
            lo = pressed ? 0xFF5E5F61 : DialogueRetroTheme.BUTTON_NEUTRAL_BOTTOM;
        }

        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, hi);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, hi);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, lo);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, lo);
    }


    private void updateHoverAmount(long now, boolean hovered) {
        if (this.lastRenderAt < 0L) {
            this.lastRenderAt = now;
        }

        long elapsed = Math.min(60L, Math.max(0L, now - this.lastRenderAt));

        this.lastRenderAt = now;

        float target = hovered ? 1.0F : 0.0F;
        float response = 1.0F - (float) Math.exp(-elapsed * 0.020D);

        this.hoverAmount += (target - this.hoverAmount) * response;
    }


    private float hoverJelly(long now) {
        if (this.hoverStartedAt < 0L) {
            return 0.0F;
        }

        float t = (now - this.hoverStartedAt) / (float) HOVER_JELLY_MS;

        if (t < 0.0F || t >= 1.0F) {

            return 0.0F;
        }

        float decay = (float) Math.exp(-3.8F * t);

        return Mth.sin(t * Mth.PI * 3.2F) * decay * 0.030F;
    }


    private float pressJelly(long now) {
        if (this.pressStartedAt < 0L) {
            return 0.0F;
        }

        float t = (now - this.pressStartedAt) / (float) PRESS_JELLY_MS;

        if (t < 0.0F || t >= 1.0F) {

            return 0.0F;
        }

        float decay = (float) Math.exp(-4.8F * t);

        return (0.032F + Mth.sin(t * Mth.PI * 4.0F) * 0.052F) * decay;
    }
    
    private void drawTabFace(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean hover) {
        if (selected) {
            drawSelectedFace(graphics, x0, y0, x1, y1, hover, false);

        } else {
            drawNeutralFace(graphics, x0, y0, x1, y1, hover, false);
        }
    }


    private void drawActionFace(GuiGraphics graphics, int x0, int y0, int x1, int y1, boolean hover) {
        drawNeutralFace(graphics, x0, y0, x1, y1, hover, false);
    }


    public static final class Builder {

        private final Component message;
        private final OnPress onPress;

        private int x;
        private int y;

        private int width = 150;
        private int height = 20;

        private boolean selected;
        private boolean tabStyle;


        private Builder(Component message, OnPress onPress) {
            this.message = message;

            this.onPress = onPress;
        }


        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;

            this.y = y;

            this.width = width;

            this.height = height;

            return this;
        }


        public Builder selected(boolean selected) {
            this.selected = selected;

            return this;
        }


        public Builder tabStyle(boolean tabStyle) {
            this.tabStyle = tabStyle;

            return this;
        }


        public DialogueRetroButton build() {
            return new DialogueRetroButton(x, y, width, height, message, onPress, selected, tabStyle);
        }
    }
}
