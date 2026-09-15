package com.benji.dialoguestudio.client.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DialogueClientKeyMappings {

    public static final String CATEGORY = "key.categories.dlgstd";

    public static final KeyMapping SKIP_DIALOGUE = new KeyMapping("key.dlgstd.skip_dialogue", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);

    private DialogueClientKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SKIP_DIALOGUE);
    }

    public static boolean matchesSkip(int keyCode, int scanCode) {
        return SKIP_DIALOGUE.matches(keyCode, scanCode);
    }

    public static boolean matchesSkipMouse(int button) {
        return SKIP_DIALOGUE.matchesMouse(button);
    }
}
