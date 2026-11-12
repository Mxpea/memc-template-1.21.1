package com.mxpea.memc.network;

import com.mxpea.memc.MEMechanicalCrafter;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MEMCPayloads {
    public static final String PROTOCOL_VERSION = "1";

    private MEMCPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToServer(MechanicalRecipeTransfer.TYPE,
                MechanicalRecipeTransfer.STREAM_CODEC, MechanicalRecipeTransfer::handle);
        String version = ModList.get().getModContainerById(MEMechanicalCrafter.MODID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        MEMechanicalCrafter.LOGGER.info("MEMC {} registered required payload {} (protocol {})",
                version, MechanicalRecipeTransfer.TYPE.id(), PROTOCOL_VERSION);
    }
}
