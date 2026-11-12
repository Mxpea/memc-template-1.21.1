package com.mxpea.memc.integration.ae2;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server-side entry point implemented on AE2's existing pattern encoding menu. */
public interface MechanicalPatternTerminal {
    void memc$transfer(ResourceLocation recipeId, List<ItemStack> inputs);
}
