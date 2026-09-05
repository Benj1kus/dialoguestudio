package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public abstract class DialogueRetroSlider extends AbstractSliderButton {

    private static final int MAX_TRAIL_POINTS = 11;
    private static final long TRAIL_LIFETIME_MS = 265L;
    private static final long SLIDER_SOUND_INTERVAL_MS = 58L;

    private final Deque<TrailPoint> trail = new ArrayDeque<>();

    private double lastTrackedValue;
    private boolean trackingInitialized;

    private long lastMoveSoundAt;

    private long hoverStartedAt = -1L;
    private long lastHoverRenderAt = -1L;
    private long lastMotionRenderAt = -1L;

    private boolean wasMouseHovered;

    private float hoverAmount;
    private float moveEnergy;


    protected DialogueRetroSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);

        this.lastTrackedValue = value;
    }


    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();

        boolean mouseHover = this.active && this.isMouseOver(mouseX, mouseY);

        if (mouseHover && !this.wasMouseHovered) {

            this.hoverStartedAt = now;

            DialogueJuiceSound.hoverClick();
        }

        this.wasMouseHovered = mouseHover;

        updateHoverAmount(now, mouseHover);

        updateMovementJuice(now);

        int x0 = getX();

        int y0 = getY();

        int x1 = x0 + width;

        int y1 = y0 + height;

        float hoverBounce = hoverBounce(now);

        float centerX = x0 + width * 0.5F;

        float centerY = y0 + height * 0.5F;

        float bodyScaleX = 1.0F + this.hoverAmount * 0.004F + hoverBounce;

        float bodyScaleY = 1.0F + this.hoverAmount * 0.003F - hoverBounce * 0.25F;

        graphics.pose().pushPose();

        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(bodyScaleX, bodyScaleY, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);

        graphics.fill(x0 + 2, y0 + 2, x1 + 2, y1 + 2, 0x77000000);
        graphics.fill(x0, y0, x1, y1, DialogueRetroTheme.BLACK);
        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF4D4E4F, 0xFF424344);

        Minecraft minecraft = Minecraft.getInstance();
        String shown = minecraft.font.plainSubstrByWidth(getMessage().getString(),

                Math.max(8, width - 10));

        int textWidth = minecraft.font.width(shown);
        int textX = x0 + (width - textWidth) / 2;

        graphics.drawString(minecraft.font, shown, textX, y0 + 3, this.active ? 0xFFF4F4F4 : 0xFFA3A3A3, false);

        int trackY = y1 - 7;
        int trackX0 = x0 + 5;
        int trackX1 = x1 - 5;

        int travel = Math.max(1, width - 12);
        int knobX = x0 + 5 + Mth.clamp((int) Math.round(this.value * travel), 0, travel);

        graphics.fill(trackX0 - 1, trackY - 1, trackX1 + 1, trackY + 4, DialogueRetroTheme.BLACK);
        graphics.fillGradient(trackX0, trackY, trackX1, trackY + 3, 0xFFD0D1D4, 0xFF8C8D90);

        if (knobX > trackX0) {
            graphics.fillGradient(trackX0, trackY, knobX, trackY + 3, 0xFF4D9637, DialogueRetroTheme.GREEN);
        }

        drawTrail(graphics, x0, trackY, now);

        float knobPulse = 1.0F + this.hoverAmount * 0.055F + this.moveEnergy * 0.085F;
        float knobCenterX = knobX + 0.5F;
        float knobCenterY = trackY + 1.0F;

        graphics.pose().pushPose();
        graphics.pose().translate(knobCenterX, knobCenterY, 0.0F);
        graphics.pose().scale(knobPulse, 1.0F + (knobPulse - 1.0F) * 0.72F, 1.0F);
        graphics.pose().translate(-knobCenterX, -knobCenterY, 0.0F);

        graphics.fill(knobX - 4, trackY - 4, knobX + 5, trackY + 7, DialogueRetroTheme.BLACK);

        int knobTop = this.active && isHoveredOrFocused() ? 0xFFE0E1E3 : DialogueRetroTheme.BUTTON_NEUTRAL_TOP;
        int knobBottom = this.active ? DialogueRetroTheme.BUTTON_NEUTRAL : 0xFF77787A;

        graphics.fillGradient(knobX - 3, trackY - 3, knobX + 4, trackY + 6, knobTop, knobBottom);
        graphics.fill(knobX - 3, trackY - 3, knobX + 4, trackY - 2, 0xFFF0F0F1);
        graphics.fill(knobX - 3, trackY + 5, knobX + 4, trackY + 6, 0xFF707174);

        if (this.moveEnergy > 0.12F) {
            int flashAlpha = Mth.clamp(Math.round(this.moveEnergy * 72.0F), 0, 72);

            graphics.fill(knobX - 2, trackY - 2, knobX + 3, trackY + 5, flashAlpha << 24 | 0x00FFFFFF);
        }

        graphics.pose().popPose();

        graphics.pose().popPose();
    }


    private void updateMovementJuice(long now) {
        if (!this.trackingInitialized) {
            this.trackingInitialized = true;

            this.lastTrackedValue = this.value;
        }

        double delta = this.value - this.lastTrackedValue;

        if (Math.abs(delta) > 0.0015D) {

            addTrailSamples(this.lastTrackedValue, this.value, now);

            this.moveEnergy = Math.min(1.0F,

                    this.moveEnergy + (float) Math.min(0.58D, Math.abs(delta) * 5.0D));

            if (now - this.lastMoveSoundAt >= SLIDER_SOUND_INTERVAL_MS) {

                DialogueJuiceSound.sliderTick(this.value);

                this.lastMoveSoundAt = now;
            }

            this.lastTrackedValue = this.value;
        }

        if (this.lastMotionRenderAt < 0L) {
            this.lastMotionRenderAt = now;
        }

        long elapsed = Math.min(80L, Math.max(0L, now - this.lastMotionRenderAt));

        this.lastMotionRenderAt = now;

        this.moveEnergy *= (float) Math.exp(-elapsed * 0.013D);

        while (!this.trail.isEmpty() && now - this.trail.peekFirst().time > TRAIL_LIFETIME_MS) {

            this.trail.removeFirst();
        }
    }


    private void addTrailSamples(double from, double to, long now) {
        int count = Mth.clamp((int) Math.ceil(Math.abs(to - from) * 18.0D), 1, 4);

        for (int i = 0; i < count; i++) {

            double t = (i + 1.0D) / (count + 1.0D);
            double sample = Mth.lerp(t, from, to);

            this.trail.addLast(new TrailPoint(sample,

                    now - (long) ((count - i) * 14L)));
        }

        while (this.trail.size() > MAX_TRAIL_POINTS) {

            this.trail.removeFirst();
        }
    }


    private void drawTrail(GuiGraphics graphics, int x0, int trackY, long now) {
        if (this.trail.isEmpty()) {
            return;
        }

        int travel = Math.max(1, width - 12);
        Iterator<TrailPoint> iterator = this.trail.iterator();

        while (iterator.hasNext()) {
            TrailPoint point = iterator.next();

            float age = (now - point.time) / (float) TRAIL_LIFETIME_MS;

            if (age >= 1.0F) {
                iterator.remove();
                continue;
            }

            float fade = 1.0F - age;
            fade *= fade;

            int alpha = Mth.clamp(Math.round(104.0F * fade), 0, 104);

            int px = x0 + 5 + Mth.clamp((int) Math.round(point.value * travel), 0, travel);
            int radius = age < 0.35F ? 2 : 1;
            int color = alpha << 24 | (DialogueRetroTheme.LIME & 0x00FFFFFF);

            graphics.fill(px - radius, trackY - radius, px + radius + 1, trackY + radius + 2, color);
        }
    }


    private void updateHoverAmount(long now, boolean hovered) {
        if (this.lastHoverRenderAt < 0L) {
            this.lastHoverRenderAt = now;
        }

        long elapsed = Math.min(60L, Math.max(0L, now - this.lastHoverRenderAt));

        float target = hovered ? 1.0F : 0.0F;
        float response = 1.0F - (float) Math.exp(-elapsed * 0.020D);

        this.hoverAmount += (target - this.hoverAmount) * response;
        this.lastHoverRenderAt = now;
    }


    private float hoverBounce(long now) {
        if (this.hoverStartedAt < 0L) {
            return 0.0F;
        }

        float t = (now - this.hoverStartedAt) / 300.0F;

        if (t < 0.0F || t >= 1.0F) {

            return 0.0F;
        }

        return Mth.sin(t * Mth.PI * 3.0F) * (float) Math.exp(-4.0F * t) * 0.018F;
    }


    private record TrailPoint(double value, long time) {
    }
}
