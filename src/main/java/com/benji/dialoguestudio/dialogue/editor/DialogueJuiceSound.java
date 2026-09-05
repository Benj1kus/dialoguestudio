package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

final class DialogueJuiceSound {

    private static boolean sliderToggle;
    private static boolean typingToggle;

    private DialogueJuiceSound() {
    }

    static void hoverClick() {
        play(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.46F, 0.16F);
    }

    static void nodeHoverClick() {
        play(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.28F, 0.12F);
    }

    static void nodePressClick() {
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.18F, 0.18F);
    }

    static void sliderTick(double value) {
        sliderToggle = !sliderToggle;
        float pitch = 1.02F + (float) Math.max(0.0D, Math.min(1.0D, value)) * 0.34F + (sliderToggle ? 0.035F : -0.025F);
        play(sliderToggle ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF, pitch, 0.105F);
    }

    static void typingClick() {
        typingToggle = !typingToggle;
        long phase = System.nanoTime() >>> 18;
        float jitter = ((phase & 7L) - 3L) * 0.018F;
        float pitch = (typingToggle ? 1.34F : 1.46F) + jitter;

        play(typingToggle ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF, pitch, 0.105F);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft == null || minecraft.getSoundManager() == null) {

            return;
        }

        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
