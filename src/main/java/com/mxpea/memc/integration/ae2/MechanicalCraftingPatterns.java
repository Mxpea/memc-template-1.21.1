package com.mxpea.memc.integration.ae2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.mxpea.memc.MEMechanicalCrafterDataComponents;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Uses Create's actual recipe lookup, including its priority rules and crafting remainders. */
public final class MechanicalCraftingPatterns {
    private MechanicalCraftingPatterns() {
    }

    public static List<GenericStack> outputs(Level level, int width, int height, List<GenericStack> inputs) {
        if (width <= 0 || height <= 0 || width > 81 || height > 81
                || (long) width * height != inputs.size() || inputs.size() > 81) {
            return List.of();
        }
        ListTag grid = new ListTag();
        List<ItemStack> stacks = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            GenericStack input = inputs.get(index);
            if (input == null) {
                continue;
            }
            if (!(input.what() instanceof AEItemKey key) || input.amount() != 1) {
                return List.of();
            }
            ItemStack stack = key.toStack(1);
            stacks.add(stack);
            CompoundTag cell = new CompoundTag();
            cell.putInt("x", index % width);
            cell.putInt("y", -(index / width));
            cell.put("item", stack.saveOptional(level.registryAccess()));
            grid.add(cell);
        }
        if (stacks.isEmpty()) {
            return List.of();
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Grid", grid);
        RecipeGridHandler.GroupedItems grouped = RecipeGridHandler.GroupedItems.read(tag, level.registryAccess());
        ItemStack output = RecipeGridHandler.tryToApplyRecipe(level, grouped);
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        List<GenericStack> outputs = new ArrayList<>();
        outputs.add(new GenericStack(AEItemKey.of(output), output.getCount()));
        // Create returns containers separately (e.g. buckets), in addition to the main result.
        for (ItemStack stack : stacks) {
            if (stack.hasCraftingRemainingItem()) {
                ItemStack remainder = stack.getCraftingRemainingItem();
                if (!remainder.isEmpty()) {
                    outputs.add(new GenericStack(AEItemKey.of(remainder), remainder.getCount()));
                }
            }
        }
        return outputs;
    }

    public static boolean sameOutputs(List<GenericStack> first, List<GenericStack> second) {
        // The primary result must agree too, even if it is also produced as a remainder.
        return !first.isEmpty() && !second.isEmpty()
                && first.get(0).what().equals(second.get(0).what())
                && totals(first).equals(totals(second));
    }

    private static Map<AEKey, Long> totals(List<GenericStack> stacks) {
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack != null) {
                totals.merge(stack.what(), stack.amount(), Math::addExact);
            }
        }
        return totals;
    }

    public static ItemStack encode(int width, int height, List<GenericStack> inputs, List<GenericStack> outputs) {
        List<GenericStack> condensed = totals(outputs).entrySet().stream()
                .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        if (inputs.isEmpty() || inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS
                || condensed.isEmpty() || condensed.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, condensed);
        encoded.set(MEMechanicalCrafterDataComponents.PATTERN_WIDTH.get(), width);
        encoded.set(MEMechanicalCrafterDataComponents.PATTERN_HEIGHT.get(), height);
        return encoded;
    }
}
