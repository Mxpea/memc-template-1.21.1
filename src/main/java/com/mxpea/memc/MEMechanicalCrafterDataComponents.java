package com.mxpea.memc;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Extra grid dimensions required to preserve position in AE2 processing patterns. */
public final class MEMechanicalCrafterDataComponents {

    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MEMechanicalCrafter.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PATTERN_WIDTH =
            DATA_COMPONENTS.registerComponentType("pattern_width", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PATTERN_HEIGHT =
            DATA_COMPONENTS.registerComponentType("pattern_height", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private MEMechanicalCrafterDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
