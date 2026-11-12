package com.mxpea.memc.command;

import java.util.ArrayList;
import java.util.List;

import appeng.api.AECapabilities;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mxpea.memc.MEMechanicalCrafterDataComponents;
import com.mxpea.memc.integration.ae2.GridLayout;
import com.mxpea.memc.integration.ae2.MechanicalCrafterGrid;
import com.mxpea.memc.integration.ae2.MechanicalCraftingPatterns;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Encodes concrete inputs and the outputs Create will actually produce. */
public final class MemcCommand {
    private MemcCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("memc")
                .then(Commands.literal("inspect").executes(MemcCommand::inspect))
                .then(Commands.literal("encode")
                        .executes(MemcCommand::encodeCurrentGrid)
                        .then(Commands.argument("item_id", ResourceLocationArgument.id())
                                .executes(MemcCommand::encodeRecipe)))
                .then(Commands.literal("id")
                        .then(Commands.argument("item_id", ResourceLocationArgument.id())
                                .executes(MemcCommand::encodeRecipe))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        HitResult hit = player.pick(10.0, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)
                || !(player.level().getBlockEntity(blockHit.getBlockPos()) instanceof MechanicalCrafterBlockEntity crafter)) {
            source.sendFailure(Component.literal("请看向一个动力合成器"));
            return 0;
        }
        List<String> capabilitySides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (player.level().getCapability(AECapabilities.CRAFTING_MACHINE, crafter.getBlockPos(), side) != null) {
                capabilitySides.add(side.getName());
            }
        }
        source.sendSuccess(() -> Component.literal("AE2 机器能力所在面: "
                + (capabilitySides.isEmpty() ? "无（请确认已加载更新后的模组）" : String.join(", ", capabilitySides))), false);
        GridLayout<MechanicalCrafterBlockEntity> grid = MechanicalCrafterGrid.find(crafter);
        if (grid == null) {
            source.sendFailure(Component.literal("无法解析扳手连接输入组：请检查连接、区块加载及 81 格上限"));
            return 0;
        }
        int members = 0;
        int stopped = 0;
        int occupied = 0;
        int busy = 0;
        int covers = 0;
        for (MechanicalCrafterBlockEntity member : grid.slots()) {
            if (member == null) {
                continue;
            }
            members++;
            if (member.getSpeed() == 0) {
                stopped++;
            }
            if (!member.getInventory().getItem(0).isEmpty()) {
                occupied++;
            }
            CompoundTag state = new CompoundTag();
            member.write(state, player.level().registryAccess(), false);
            if (!"IDLE".equals(state.getString("Phase"))) {
                busy++;
            }
            if (state.getBoolean("Cover")) {
                covers++;
            }
        }
        String summary = String.format("扳手输入组: %dx%d，成员 %d，停转 %d，占用 %d，忙碌 %d，挡板 %d",
                grid.width(), grid.height(), members, stopped, occupied, busy, covers);
        source.sendSuccess(() -> Component.literal(summary), false);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            Integer width = held.get(MEMechanicalCrafterDataComponents.PATTERN_WIDTH.get());
            Integer height = held.get(MEMechanicalCrafterDataComponents.PATTERN_HEIGHT.get());
            if (width != null && height != null) {
                source.sendSuccess(() -> Component.literal("手持 MEMC 样板尺寸: " + width + "x" + height), false);
            }
        }
        return 1;
    }

    private static int encodeCurrentGrid(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        InteractionHand hand = blankPatternHand(player);
        if (hand == null) {
            source.sendFailure(Component.literal("请手持空白样板"));
            return 0;
        }
        HitResult hit = player.pick(10.0, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)
                || !(player.level().getBlockEntity(blockHit.getBlockPos()) instanceof MechanicalCrafterBlockEntity crafter)) {
            source.sendFailure(Component.literal("请看向一个动力合成器"));
            return 0;
        }
        GridLayout<MechanicalCrafterBlockEntity> grid = MechanicalCrafterGrid.find(crafter);
        if (grid == null) {
            source.sendFailure(Component.literal("扳手连接的输入组无效、部分区块未加载，或网格超过 81 格"));
            return 0;
        }
        List<GenericStack> inputs = new ArrayList<>(grid.slots().size());
        for (MechanicalCrafterBlockEntity cell : grid.slots()) {
            ItemStack stack = cell == null ? ItemStack.EMPTY : cell.getInventory().getItem(0);
            inputs.add(stack.isEmpty() ? null : new GenericStack(AEItemKey.of(stack), 1));
        }
        return finishEncoding(source, player, hand, grid.width(), grid.height(), inputs, null);
    }

    private static int encodeRecipe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        InteractionHand hand = blankPatternHand(player);
        if (hand == null) {
            source.sendFailure(Component.literal("请手持空白样板"));
            return 0;
        }
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item_id");
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            source.sendFailure(Component.literal("找不到物品: " + itemId));
            return 0;
        }
        Level level = player.level();
        List<RecipeHolder<MechanicalCraftingRecipe>> recipes = level.getRecipeManager().getAllRecipesFor(
                AllRecipeTypes.MECHANICAL_CRAFTING.<CraftingInput, MechanicalCraftingRecipe>getType());
        List<MechanicalCraftingRecipe> matches = recipes.stream().map(RecipeHolder::value)
                .filter(recipe -> recipe.getResultItem(level.registryAccess()).is(item)).toList();
        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("找不到机械合成配方: " + itemId));
            return 0;
        }
        if (matches.size() > 1) {
            source.sendFailure(Component.literal("该物品有多个机械合成配方；请摆好材料后使用 /memc encode"));
            return 0;
        }
        MechanicalCraftingRecipe recipe = matches.get(0);
        List<GenericStack> inputs = new ArrayList<>(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                inputs.add(null);
            } else {
                ItemStack[] choices = ingredient.getItems();
                if (choices.length == 0 || choices[0].isEmpty()) {
                    source.sendFailure(Component.literal("配方包含无法编码的材料: " + itemId));
                    return 0;
                }
                inputs.add(new GenericStack(AEItemKey.of(choices[0]), 1));
            }
        }
        return finishEncoding(source, player, hand, recipe.getWidth(), recipe.getHeight(), inputs, item);
    }

    private static int finishEncoding(CommandSourceStack source, Player player, InteractionHand hand,
            int width, int height, List<GenericStack> inputs, Item requestedItem) {
        List<GenericStack> outputs = MechanicalCraftingPatterns.outputs(player.level(), width, height, inputs);
        if (outputs.isEmpty()) {
            source.sendFailure(Component.literal("当前材料没有可用的 Create 自动合成配方，或网格超过 81 格"));
            return 0;
        }
        if (requestedItem != null && (!(outputs.get(0).what() instanceof AEItemKey output)
                || !output.toStack(1).is(requestedItem))) {
            source.sendFailure(Component.literal("Create 的实际配方优先级产生不同产物，无法编码此样板"));
            return 0;
        }
        ItemStack encoded = MechanicalCraftingPatterns.encode(width, height, inputs, outputs);
        if (encoded.isEmpty()) {
            source.sendFailure(Component.literal("输入或副产物数量超过 AE 样板支持的槽位上限"));
            return 0;
        }
        // Consume exactly one blank pattern, preserving the rest of a stack in either hand.
        ItemStack blanks = player.getItemInHand(hand);
        blanks.shrink(1);
        if (blanks.isEmpty()) {
            player.setItemInHand(hand, encoded);
        } else if (!player.getInventory().add(encoded)) {
            player.drop(encoded, false);
        }
        source.sendSuccess(() -> Component.literal(String.format("已编码机械合成样板 (%dx%d)", width, height)), false);
        return 1;
    }

    private static InteractionHand blankPatternHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (AEItems.BLANK_PATTERN.is(player.getItemInHand(hand))) {
                return hand;
            }
        }
        return null;
    }
}
