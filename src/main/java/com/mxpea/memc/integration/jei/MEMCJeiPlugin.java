package com.mxpea.memc.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import appeng.menu.me.items.PatternEncodingTermMenu;

import com.mxpea.memc.MEMechanicalCrafter;
import com.mxpea.memc.network.MechanicalRecipeTransfer;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

/** JEI is optional: only JEI's plugin discovery loads this client integration. */
@JeiPlugin
public final class MEMCJeiPlugin implements IModPlugin {
    private static final RecipeType<CraftingRecipe> MECHANICAL_CRAFTING =
            RecipeType.create("create", "mechanical_crafting", CraftingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MEMechanicalCrafter.MODID, "mechanical_patterns");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TransferHandler(registration.getTransferHelper()), MECHANICAL_CRAFTING);
    }

    private record TransferHandler(IRecipeTransferHandlerHelper helper)
            implements IRecipeTransferHandler<PatternEncodingTermMenu, CraftingRecipe> {
        @Override
        public Class<PatternEncodingTermMenu> getContainerClass() {
            return PatternEncodingTermMenu.class;
        }

        @Override
        public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
            return Optional.of(PatternEncodingTermMenu.TYPE);
        }

        @Override
        public RecipeType<CraftingRecipe> getRecipeType() {
            return MECHANICAL_CRAFTING;
        }

        @Override
        public IRecipeTransferError transferRecipe(PatternEncodingTermMenu menu, CraftingRecipe displayedRecipe,
                IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
            if (!(displayedRecipe instanceof MechanicalCraftingRecipe recipe)
                    || recipe.getWidth() <= 0 || recipe.getHeight() <= 0
                    || (long) recipe.getWidth() * recipe.getHeight() > 81) {
                return helper.createUserErrorWithTooltip(Component.literal("机械合成样板最多支持 81 格"));
            }
            var holder = player.level().getRecipeManager().getAllRecipesFor(
                    AllRecipeTypes.MECHANICAL_CRAFTING.<CraftingInput, MechanicalCraftingRecipe>getType())
                    .stream().filter(entry -> entry.value() == recipe).findFirst().orElse(null);
            if (holder == null) {
                return helper.createUserErrorWithTooltip(Component.literal("找不到该机械合成配方，请重新加载 JEI"));
            }
            // Create creates one JEI INPUT slot per grid cell, including empty ingredients.
            var inputSlots = slots.getSlotViews(RecipeIngredientRole.INPUT);
            if (inputSlots.size() != recipe.getIngredients().size()) {
                return helper.createInternalError();
            }
            List<ItemStack> inputs = new ArrayList<>(inputSlots.size());
            for (int index = 0; index < recipe.getIngredients().size(); index++) {
                var ingredient = recipe.getIngredients().get(index);
                if (ingredient.isEmpty()) {
                    inputs.add(ItemStack.EMPTY);
                    continue;
                }
                var slot = inputSlots.get(index);
                ItemStack selected = slot.getDisplayedItemStack().filter(ingredient::test)
                        .orElseGet(() -> slot.getItemStacks().filter(ingredient::test)
                                .findFirst().orElse(ItemStack.EMPTY));
                if (selected.isEmpty()) {
                    return helper.createUserErrorWithTooltip(Component.literal("配方含有无法转移的材料"));
                }
                inputs.add(selected.copyWithCount(1));
            }
            // Encoding uses ghost slots: no ingredients or blank patterns are consumed by '+'.
            if (doTransfer) {
                PacketDistributor.sendToServer(new MechanicalRecipeTransfer(menu.containerId, holder.id(), inputs));
            }
            return null;
        }
    }
}
