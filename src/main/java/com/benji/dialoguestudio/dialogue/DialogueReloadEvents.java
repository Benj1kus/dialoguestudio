package com.benji.dialoguestudio.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueReloadEvents {

    private DialogueReloadEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                DialogueRegistry.reload(manager);
            }
        });
    }
}