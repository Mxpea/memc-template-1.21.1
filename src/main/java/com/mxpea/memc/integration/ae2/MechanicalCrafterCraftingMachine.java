package com.mxpea.memc.integration.ae2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import com.mxpea.memc.MEMechanicalCrafterDataComponents;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Provides positional, all-or-nothing input delivery; Create still performs the actual craft. */
public class MechanicalCrafterCraftingMachine implements ICraftingMachine {
    private final MechanicalCrafterBlockEntity blockEntity;

    public MechanicalCrafterCraftingMachine(MechanicalCrafterBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        ItemStack icon = AllBlocks.MECHANICAL_CRAFTER.asStack();
        return new PatternContainerGroup(AEItemKey.of(icon), icon.getHoverName(), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs, Direction ejectionDirection) {
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide || blockEntity.isRemoved()
                || !(details instanceof AEProcessingPattern pattern) || inputs == null
                || inputs.length != details.getInputs().length) {
            return false;
        }
        GridLayout<MechanicalCrafterBlockEntity> grid = MechanicalCrafterGrid.find(blockEntity);
        if (grid == null) {
            return false;
        }
        ItemStack definition = details.getDefinition().toStack();
        Integer width = definition.get(MEMechanicalCrafterDataComponents.PATTERN_WIDTH.get());
        Integer height = definition.get(MEMechanicalCrafterDataComponents.PATTERN_HEIGHT.get());
        if (width == null || height == null) {
            return false;
        }
        // The wrench group chooses where we may insert. Create's arrows choose the full
        // assembly that will start. Check that starting it cannot consume somebody else's
        // items, including members outside the shared input group.
        List<MechanicalCrafterBlockEntity> assembly = RecipeGridHandler.getAllCraftersOfChain(blockEntity);
        if (assembly == null || !assembly.containsAll(grid.slots().stream()
                .filter(java.util.Objects::nonNull).toList())) {
            return false;
        }
        Map<MechanicalCrafterBlockEntity, Boolean> covers = new HashMap<>();
        for (MechanicalCrafterBlockEntity crafter : assembly) {
            if (crafter.isRemoved() || crafter.getSpeed() == 0 || CraftingStartGuard.isDeferred(crafter)
                    || !crafter.getInventory().getItem(0).isEmpty()) {
                return false;
            }
            CompoundTag state = new CompoundTag();
            crafter.write(state, crafter.getLevel().registryAccess(), false);
            if (!"IDLE".equals(state.getString("Phase"))) {
                return false;
            }
            covers.put(crafter, state.getBoolean("Cover"));
        }
        List<GenericStack> placed = grid.placeWhere(width, height, pattern.getSparseInputs(), (crafter, input) -> {
            if (input == null) {
                return true; // Both uncovered surplus slots and physical holes may remain empty.
            }
            return crafter != null && !covers.get(crafter) && input.amount() == 1
                    && input.what() instanceof AEItemKey item
                    && crafter.getInventory().insertItem(0, item.toStack(1), true).isEmpty();
        });
        if (placed == null) {
            return false;
        }
        // Check against the current recipe manager too: edited patterns or changed data packs
        // must not make the crafting CPU wait for an output Create will never produce.
        List<GenericStack> actualOutputs = MechanicalCraftingPatterns.outputs(
                blockEntity.getLevel(), grid.width(), grid.height(), placed);
        if (!MechanicalCraftingPatterns.sameOutputs(actualOutputs, details.getOutputs())) {
            return false;
        }

        Map<AEKey, Long> supplied = new HashMap<>();
        Map<AEKey, Long> required = new HashMap<>();
        for (KeyCounter counter : inputs) {
            if (counter == null) {
                return false;
            }
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0 || !(entry.getKey() instanceof AEItemKey)) {
                    return false;
                }
                if (amount > 0) {
                    long previous = supplied.getOrDefault(entry.getKey(), 0L);
                    if (Long.MAX_VALUE - previous < amount) {
                        return false;
                    }
                    supplied.put(entry.getKey(), previous + amount);
                }
            }
        }

        List<AtomicInsertion.Step> steps = new ArrayList<>();
        for (int index = 0; index < grid.slots().size(); index++) {
            GenericStack input = placed.get(index);
            if (input == null) {
                continue;
            }
            MechanicalCrafterBlockEntity crafter = grid.slots().get(index);
            AEItemKey item = (AEItemKey) input.what();
            required.merge(item, 1L, Long::sum);
            steps.add(new SlotInsertion(crafter.getInventory(), item.toStack(1)));
        }
        if (!required.equals(supplied) || steps.isEmpty()) {
            return false;
        }
        // Keep Create's eager callbacks from starting a partial/covered sub-layout while
        // insertion or rollback is still in progress. This affects only this server-thread batch.
        if (!CraftingStartGuard.insert(assembly, () -> AtomicInsertion.commit(steps))) {
            return false;
        }
        // Use Create's redstone-style start: it explicitly permits empty crafters without
        // covers, while still performing normal recipe assembly, animation and output.
        blockEntity.checkCompletedRecipe(true);
        return true;
    }

    @Override
    public boolean acceptsPlans() {
        // Returning false makes AE2 fall back to generic inventory insertion, which loses
        // positions and can bypass a busy/invalid grid. Always handle or reject pushes here.
        return true;
    }

    private record SlotInsertion(MechanicalCrafterBlockEntity.Inventory inventory, ItemStack stack)
            implements AtomicInsertion.Step {
        @Override
        public boolean simulate() {
            return inventory.insertItem(0, stack.copy(), true).isEmpty();
        }

        @Override
        public boolean insert() {
            return inventory.insertItem(0, stack.copy(), false).isEmpty();
        }

        @Override
        public void rollback() {
            inventory.setStackInSlot(0, ItemStack.EMPTY);
        }
    }
}
