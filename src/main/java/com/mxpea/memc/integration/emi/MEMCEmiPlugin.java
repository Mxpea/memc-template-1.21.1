package com.mxpea.memc.integration.emi;

import java.util.List;
import java.util.function.Predicate;

import appeng.menu.me.items.PatternEncodingTermMenu;

import com.mxpea.memc.integration.ae2.GhostRecipeInputs;
import com.mxpea.memc.network.MechanicalRecipeTransfer;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

/** EMI discovers this client plugin only when EMI is installed. */
@EmiEntrypoint
public final class MEMCEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeHandler(PatternEncodingTermMenu.TYPE, new Handler());
    }

    /** Prefer the backing recipe: JEI-bridged EMI recipes use a synthetic display ID. */
    public static RecipeHolder<?> backingRecipe(EmiRecipe recipe) {
        RecipeHolder<?> backing = recipe.getBackingRecipe();
        if (backing != null) {
            return backing;
        }
        var level = Minecraft.getInstance().level;
        return level == null || recipe.getId() == null ? null
                : level.getRecipeManager().byKey(recipe.getId()).orElse(null);
    }

    public static boolean isMechanical(EmiRecipe recipe) {
        RecipeHolder<?> backing = backingRecipe(recipe);
        return backing != null && backing.value() instanceof MechanicalCraftingRecipe;
    }

    private static List<ItemStack> selectInputs(EmiRecipe displayed, MechanicalCraftingRecipe recipe) {
        if (recipe.getWidth() <= 0 || recipe.getHeight() <= 0
                || (long) recipe.getWidth() * recipe.getHeight() > 81
                || (long) recipe.getWidth() * recipe.getHeight() != recipe.getIngredients().size()) {
            return null;
        }
        // EMI wrappers may omit empty ingredients or condense repeated ones. Their
        // order is therefore not a grid. Always take positions from the backing recipe.
        List<ItemStack> offered = displayed.getInputs().stream()
                .flatMap(input -> input.getEmiStacks().stream()).map(EmiStack::getItemStack)
                .filter(stack -> !stack.isEmpty()).toList();
        List<Predicate<ItemStack>> ingredients = recipe.getIngredients().stream()
                .<Predicate<ItemStack>>map(ingredient -> ingredient::test).toList();
        List<ItemStack> inputs = GhostRecipeInputs.select(ingredients, offered, ItemStack.EMPTY, index -> {
            ItemStack[] alternatives = recipe.getIngredients().get(index).getItems();
            return alternatives.length == 0 ? null : alternatives[0];
        });
        return inputs == null ? null : inputs.stream()
                .map(stack -> stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1)).toList();
    }

    private static final class Handler implements EmiRecipeHandler<PatternEncodingTermMenu> {
        @Override
        public EmiPlayerInventory getInventory(AbstractContainerScreen<PatternEncodingTermMenu> screen) {
            // Ghost-pattern encoding does not require real ingredients in the player/ME inventory.
            return new EmiPlayerInventory(List.<EmiStack>of());
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return isMechanical(recipe);
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<PatternEncodingTermMenu> context) {
            var backing = backingRecipe(recipe);
            return backing != null && backing.value() instanceof MechanicalCraftingRecipe mechanical
                    && selectInputs(recipe, mechanical) != null;
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<PatternEncodingTermMenu> context) {
            var backing = backingRecipe(recipe);
            if (backing == null || !(backing.value() instanceof MechanicalCraftingRecipe mechanical)) {
                return false;
            }
            List<ItemStack> inputs = selectInputs(recipe, mechanical);
            if (inputs == null) {
                return false;
            }
            PacketDistributor.sendToServer(new MechanicalRecipeTransfer(
                    context.getScreenHandler().containerId, backing.id(), inputs));
            return true;
        }
    }
}
