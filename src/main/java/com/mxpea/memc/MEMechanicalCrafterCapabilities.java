package com.mxpea.memc;

import com.simibubi.create.AllBlockEntityTypes;

import appeng.api.AECapabilities;

import com.mxpea.memc.integration.ae2.MechanicalCrafterCraftingMachine;

import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class MEMechanicalCrafterCapabilities {

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.CRAFTING_MACHINE,
                AllBlockEntityTypes.MECHANICAL_CRAFTER.get(),
                (be, context) -> new MechanicalCrafterCraftingMachine(be)
        );
        MEMechanicalCrafter.LOGGER.info("Registered AE2 crafting-machine capability for Create mechanical crafters");
    }
}
