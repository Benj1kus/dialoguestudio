package com.benji.dialoguestudio.dialogue.editor;

import com.benji.dialoguestudio.DialogueStudio;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class DialogueEditorClientHooks {

    public static final KeyMapping OPEN_EDITOR = new KeyMapping("key.dlgstd.dialogue_editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.dlgstd");

    private DialogueEditorClientHooks() {
    }

    @Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_EDITOR);
        }
    }

    @Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void movementInput(MovementInputUpdateEvent event) {
            Minecraft minecraft = Minecraft.getInstance();

            if (!(minecraft.screen instanceof DialogueZoneWorldEditScreen) || minecraft.player == null || event.getEntity() != minecraft.player) {
                return;
            }

            long window = minecraft.getWindow().getWindow();

            boolean forward = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
            boolean backward = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
            boolean left = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
            boolean right = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
            boolean jump = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
            boolean sneak = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT);

            var input = event.getInput();

            input.up = forward;
            input.down = backward;
            input.left = left;
            input.right = right;

            input.forwardImpulse = (forward ? 1.0F : 0.0F) - (backward ? 1.0F : 0.0F);
            input.leftImpulse = (left ? 1.0F : 0.0F) - (right ? 1.0F : 0.0F);

            input.jumping = jump;
            input.shiftKeyDown = sneak;
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();

            while (OPEN_EDITOR.consumeClick()) {
                if (minecraft.screen instanceof DialogueZoneWorldEditScreen zoneEditor) {
                    zoneEditor.applyAndReturn();
                    continue;
                }

                if (minecraft.screen instanceof DialogueEditorScreen) {
                    minecraft.setScreen(null);
                } else {
                    minecraft.setScreen(new DialogueEditorScreen(DialogueEditorWorkspace.loadLastOrDefault(), DialogueEditorScreen.Tab.PROJECT));
                }
            }
        }
    }
}
