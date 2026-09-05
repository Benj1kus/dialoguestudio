package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class DialogueRetroScreen extends Screen {

    protected DialogueRetroScreen(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        DialogueRetroTheme.drawDesktop(graphics, this.width, this.height);

        if (this.width > 18 && this.height > 18) {
            DialogueRetroTheme.drawPanel(graphics, 3, 3, this.width - 3, this.height - 3);
            DialogueRetroTheme.drawTitleBar(graphics, 6, 6, this.width - 6, 18);
        }
    }
}
