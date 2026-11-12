package com.mxpea.memc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mxpea.memc.command.MemcCommand;
import com.mxpea.memc.network.MEMCPayloads;

@Mod(MEMechanicalCrafter.MODID)
public class MEMechanicalCrafter {
    public static final String MODID = "memc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MEMechanicalCrafter(IEventBus modEventBus) {
        modEventBus.addListener(MEMechanicalCrafterCapabilities::registerCapabilities);
        modEventBus.addListener(MEMechanicalCrafterDataComponents::register);
        modEventBus.addListener(MEMCPayloads::register);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MemcCommand.register(event.getDispatcher());
    }
}
