package com.mxpea.memc.integration.ae2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/** A wrench-connected input group, independent of Create's recipe-routing arrows. */
public final class MechanicalCrafterGrid {
    private MechanicalCrafterGrid() {
    }

    public static GridLayout<MechanicalCrafterBlockEntity> find(MechanicalCrafterBlockEntity origin) {
        Level level = origin.getLevel();
        if (level == null || origin.isRemoved()) {
            return null;
        }

        // Create keeps the controller's member offsets in ConnectedInput and a single
        // controller offset on each member. Use its public persistence API instead of
        // reflection or following physical adjacency / crafting arrows.
        BlockPos controllerPos = controllerPosition(origin);
        if (!level.hasChunkAt(controllerPos)
                || !(level.getBlockEntity(controllerPos) instanceof MechanicalCrafterBlockEntity controller)) {
            return null;
        }
        CompoundTag group = new CompoundTag();
        controller.getInput().write(group);
        if (!group.getBoolean("Controller")) {
            return null;
        }
        ListTag offsets = group.getList("Data", Tag.TAG_COMPOUND);
        if (offsets.isEmpty() || offsets.size() > 81) {
            return null;
        }

        Direction facing = origin.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        Axis horizontal = facing.getClockWise().getAxis();
        // Match ConnectedInput.getInventories: top-to-bottom, then Create's
        // facing-dependent horizontal order. Preserve missing cells instead of packing.
        int direction = facing.getAxisDirection().getStep() * (horizontal == Axis.Z ? -1 : 1);
        int plane = facing.getAxis().choose(controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
        Map<GridLayout.Cell, MechanicalCrafterBlockEntity> cells = new HashMap<>();
        Set<BlockPos> positions = new HashSet<>();
        for (int index = 0; index < offsets.size(); index++) {
            BlockPos pos = controllerPos.offset(readOffset(offsets.getCompound(index)));
            if (!positions.add(pos) || !level.hasChunkAt(pos)
                    || !(level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity member)
                    || member.isRemoved()
                    || member.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) != facing
                    || !controllerPosition(member).equals(controllerPos)
                    || facing.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()) != plane) {
                return null;
            }
            int column = horizontal.choose(pos.getX(), pos.getY(), pos.getZ());
            if (cells.put(new GridLayout.Cell(column, pos.getY()), member) != null) {
                return null;
            }
        }
        if (!positions.contains(origin.getBlockPos()) || !positions.contains(controllerPos)) {
            return null;
        }
        // Create filters unloaded/missing members out of getInventories; never silently
        // fill a smaller group when a chunk or member is missing.
        List<MechanicalCrafterBlockEntity.Inventory> inventories = origin.getInput()
                .getInventories(level, origin.getBlockPos());
        if (inventories.size() != positions.size()) {
            return null;
        }
        return GridLayout.fromInputGroup(cells, direction, 81);
    }

    private static BlockPos controllerPosition(MechanicalCrafterBlockEntity crafter) {
        CompoundTag data = new CompoundTag();
        crafter.getInput().write(data);
        if (data.getBoolean("Controller")) {
            return crafter.getBlockPos();
        }
        ListTag offsets = data.getList("Data", Tag.TAG_COMPOUND);
        if (offsets.size() != 1) {
            // A malformed member cannot resolve to a valid controller.
            return crafter.getBlockPos();
        }
        return crafter.getBlockPos().offset(readOffset(offsets.getCompound(0)));
    }

    private static BlockPos readOffset(CompoundTag offset) {
        return new BlockPos(offset.getInt("X"), offset.getInt("Y"), offset.getInt("Z"));
    }
}
