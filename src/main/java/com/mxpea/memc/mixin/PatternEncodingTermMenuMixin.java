package com.mxpea.memc.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;

import com.mxpea.memc.MEMechanicalCrafterDataComponents;
import com.mxpea.memc.integration.ae2.GhostRecipeSnapshot;
import com.mxpea.memc.integration.ae2.MechanicalCraftingPatterns;
import com.mxpea.memc.integration.ae2.MechanicalPatternTerminal;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuMixin implements MechanicalPatternTerminal {
    @Shadow @Final private PatternEncodingLogic encodingLogic;
    @Shadow @Final private ConfigInventory encodedInputsInv;
    @Shadow @Final private ConfigInventory encodedOutputsInv;
    @Unique private ResourceLocation memc$recipeId;
    @Unique private int memc$width;
    @Unique private int memc$height;
    @Unique private List<GenericStack> memc$inputs;
    @Unique private List<GenericStack> memc$outputs;

    @Override
    public void memc$transfer(ResourceLocation recipeId, List<ItemStack> selected) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (menu.isClientSide()) {
            return;
        }
        var level = menu.getPlayer().level();
        var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof MechanicalCraftingRecipe recipe)
                || recipe.getWidth() <= 0 || recipe.getHeight() <= 0
                || (long) recipe.getWidth() * recipe.getHeight() != selected.size()
                || selected.size() > encodedInputsInv.size()) {
            return;
        }
        List<GenericStack> inputs = new ArrayList<>(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            ItemStack stack = selected.get(index);
            if (!recipe.getIngredients().get(index).test(stack) || (!stack.isEmpty() && stack.getCount() != 1)) {
                return;
            }
            inputs.add(stack.isEmpty() ? null : new GenericStack(AEItemKey.of(stack), 1));
        }
        List<GenericStack> outputs = MechanicalCraftingPatterns.outputs(
                level, recipe.getWidth(), recipe.getHeight(), inputs);
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (outputs.isEmpty() || !outputs.get(0).what().equals(AEItemKey.of(result))
                || outputs.get(0).amount() != result.getCount()
                || outputs.size() > encodedOutputsInv.size()) {
            menu.getPlayer().displayClientMessage(Component.literal("此配方的实际 Create 产物不匹配，无法转移"), false);
            return;
        }
        // All checks finish before clearing the terminal's existing ghost recipe.
        encodingLogic.setMode(EncodingMode.PROCESSING);
        menu.setMode(EncodingMode.PROCESSING);
        encodedInputsInv.clear();
        encodedOutputsInv.clear();
        for (int index = 0; index < inputs.size(); index++) {
            encodedInputsInv.setStack(index, inputs.get(index));
        }
        for (int index = 0; index < outputs.size(); index++) {
            encodedOutputsInv.setStack(index, outputs.get(index));
        }
        memc$recipeId = recipeId;
        memc$width = recipe.getWidth();
        memc$height = recipe.getHeight();
        memc$inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        memc$outputs = List.copyOf(outputs);
        menu.broadcastChanges();
    }

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true, remap = false)
    private void memc$keepExistingPatternOnInvalidEdit(CallbackInfo callback) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (menu.isClientSide() || menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        if (memc$inputs == null) {
            memc$restoreEncodedPattern(menu);
        }
        if (memc$inputs != null && (!memc$matches(encodedInputsInv, memc$inputs)
                || !memc$matches(encodedOutputsInv, memc$outputs))) {
            menu.getPlayer().displayClientMessage(Component.literal("机械合成材料已改变，请从 JEI/EMI 重新转移配方"), false);
            // AE2 clears the previous encoded pattern when its private encoder returns null.
            // Reject here first so an invalid edit preserves the player's previous pattern.
            callback.cancel();
        }
    }

    @Inject(method = "encodeProcessingPattern", at = @At("HEAD"), cancellable = true, remap = false)
    private void memc$encodeMechanicalPattern(CallbackInfoReturnable<ItemStack> callback) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (memc$inputs == null) {
            memc$restoreEncodedPattern(menu);
        }
        if (memc$inputs == null) {
            return; // Ordinary AE2 processing recipes use the unmodified encoder.
        }
        if (!memc$matches(encodedInputsInv, memc$inputs) || !memc$matches(encodedOutputsInv, memc$outputs)) {
            menu.getPlayer().displayClientMessage(Component.literal("机械合成材料已改变，请从 JEI/EMI 重新转移配方"), false);
            callback.setReturnValue(null);
            return;
        }
        var level = menu.getPlayer().level();
        List<GenericStack> actual = MechanicalCraftingPatterns.outputs(level, memc$width, memc$height, memc$inputs);
        boolean recipeValid = true;
        if (memc$recipeId != null) {
            var holder = level.getRecipeManager().byKey(memc$recipeId).orElse(null);
            recipeValid = holder != null && holder.value() instanceof MechanicalCraftingRecipe recipe
                    && memc$validRecipe(recipe);
        }
        if (!recipeValid || !MechanicalCraftingPatterns.sameOutputs(actual, memc$outputs)) {
            menu.getPlayer().displayClientMessage(Component.literal("机械合成配方已改变，请从 JEI/EMI 重新转移"), false);
            callback.setReturnValue(null);
            return;
        }
        ItemStack encoded = MechanicalCraftingPatterns.encode(memc$width, memc$height, memc$inputs, actual);
        callback.setReturnValue(encoded.isEmpty() ? null : encoded);
    }

    @Unique
    private boolean memc$validRecipe(MechanicalCraftingRecipe recipe) {
        if (recipe.getWidth() != memc$width || recipe.getHeight() != memc$height
                || recipe.getIngredients().size() != memc$inputs.size()) {
            return false;
        }
        for (int index = 0; index < memc$inputs.size(); index++) {
            GenericStack input = memc$inputs.get(index);
            ItemStack stack = input == null ? ItemStack.EMPTY : ((AEItemKey) input.what()).toStack(1);
            if (!recipe.getIngredients().get(index).test(stack)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private void memc$restoreEncodedPattern(PatternEncodingTermMenu menu) {
        ItemStack encoded = encodingLogic.getEncodedPatternInv().getStackInSlot(0);
        Integer width = encoded.get(MEMechanicalCrafterDataComponents.PATTERN_WIDTH.get());
        Integer height = encoded.get(MEMechanicalCrafterDataComponents.PATTERN_HEIGHT.get());
        if (width == null || height == null || width <= 0 || height <= 0
                || (long) width * height > 81) {
            return;
        }
        if (PatternDetailsHelper.decodePattern(encoded, menu.getPlayer().level()) instanceof AEProcessingPattern pattern
                && (long) width * height == pattern.getSparseInputs().size()) {
            memc$width = width;
            memc$height = height;
            memc$inputs = Collections.unmodifiableList(new ArrayList<>(pattern.getSparseInputs()));
            memc$outputs = Collections.unmodifiableList(new ArrayList<>(pattern.getSparseOutputs()));
        }
    }

    @Unique
    private static boolean memc$matches(ConfigInventory inventory, List<GenericStack> expected) {
        return new GhostRecipeSnapshot<>(expected).matches(inventory.size(), inventory::getStack);
    }

    @Inject(method = "clear", at = @At("HEAD"), remap = false)
    private void memc$clearMechanicalRecipe(CallbackInfo callback) {
        memc$recipeId = null;
        memc$inputs = null;
        memc$outputs = null;
    }
}
