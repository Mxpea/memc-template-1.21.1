package com.mxpea.memc.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mxpea.memc.integration.emi.MEMCEmiPlugin;

import dev.emi.emi.api.recipe.EmiRecipe;

/** EMI selects the first supporting handler, so AE2's generic handler must yield for mechanical recipes. */
@Pseudo
@Mixin(targets = "appeng.integration.modules.emi.EmiEncodePatternHandler", remap = false)
public abstract class EmiEncodePatternHandlerMixin {
    @Inject(method = "supportsRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void memc$usePositionalMechanicalHandler(EmiRecipe recipe, CallbackInfoReturnable<Boolean> callback) {
        if (MEMCEmiPlugin.isMechanical(recipe)) {
            callback.setReturnValue(false);
        }
    }
}
