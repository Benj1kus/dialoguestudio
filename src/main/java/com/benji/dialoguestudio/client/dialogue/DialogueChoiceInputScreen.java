package com.benji.dialoguestudio.client.dialogue;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class DialogueChoiceInputScreen extends Screen {

    public DialogueChoiceInputScreen() {
        super(Component.literal("Dialogue Choice"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DialogueClient.updateChoiceHover(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (DialogueClientKeyMappings.matchesSkipMouse(button)) {
            DialogueClient.setSkipInputHeld(true);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && DialogueClient.clickChoice(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (DialogueClientKeyMappings.matchesSkipMouse(button)) {
            DialogueClient.setSkipInputHeld(false);
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (DialogueClientKeyMappings.matchesSkip(keyCode, scanCode)) {
            DialogueClient.setSkipInputHeld(true);
            return true;
        }

        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            DialogueClient.clearChoiceHover();
            DialogueClient.selectChoiceNumber(keyCode - GLFW.GLFW_KEY_0);

            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            DialogueClient.clearChoiceHover();
            DialogueClient.moveChoiceSelection(-1);

            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            DialogueClient.clearChoiceHover();
            DialogueClient.moveChoiceSelection(1);

            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            DialogueClient.clearChoiceHover();
            DialogueClient.submitSelectedChoice();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (DialogueClientKeyMappings.matchesSkip(keyCode, scanCode)) {
            DialogueClient.setSkipInputHeld(false);
            return true;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
