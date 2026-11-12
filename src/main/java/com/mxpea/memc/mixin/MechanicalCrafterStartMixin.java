package com.mxpea.memc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mxpea.memc.integration.ae2.CraftingStartGuard;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;

@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MechanicalCrafterStartMixin {
    @Inject(method = "checkCompletedRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void memc$deferRecipeUntilAllInputsArePresent(boolean poweredStart, CallbackInfo callback) {
        if (CraftingStartGuard.isDeferred(this)) {
            callback.cancel();
        }
    }
}
