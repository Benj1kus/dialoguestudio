package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class DialogueRetroEditBox extends EditBox {

    public DialogueRetroEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);

        setTextColor(DialogueRetroTheme.TEXT_LIGHT);
        setTextColorUneditable(DialogueRetroTheme.TEXT_MUTED);
    }


    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        String before = getValue();

        boolean handled = super.charTyped(codePoint, modifiers);

        if (handled && !before.equals(getValue())) {

            DialogueJuiceSound.typingClick();
        }

        return handled;
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        String before = getValue();

        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);

        if (handled && !before.equals(getValue())) {

            DialogueJuiceSound.typingClick();
        }

        return handled;
    }
}
