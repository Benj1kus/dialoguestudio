package com.benji.dialoguestudio;

import com.benji.dialoguestudio.network.dialogueengine.DialogueNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DialogueStudio.MODID)
public final class DialogueStudio {

    public static final String MODID = "dlgstd";

    public DialogueStudio(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DialogueNetwork::register);
    }
}
