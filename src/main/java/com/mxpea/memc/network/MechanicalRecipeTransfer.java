package com.mxpea.memc.network;

import java.util.ArrayList;
import java.util.List;

import com.mxpea.memc.MEMechanicalCrafter;
import com.mxpea.memc.integration.ae2.MechanicalPatternTerminal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sends only a recipe identity and selected item alternatives; the server derives all outputs. */
public record MechanicalRecipeTransfer(int containerId, ResourceLocation recipeId, List<ItemStack> inputs)
        implements CustomPacketPayload {
    public static final Type<MechanicalRecipeTransfer> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MEMechanicalCrafter.MODID, "mechanical_recipe_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MechanicalRecipeTransfer> STREAM_CODEC =
            StreamCodec.of(MechanicalRecipeTransfer::write, MechanicalRecipeTransfer::read);

    public MechanicalRecipeTransfer {
        if (inputs.size() > 81) {
            throw new IllegalArgumentException("Too many mechanical recipe inputs");
        }
        inputs = inputs.stream().map(ItemStack::copy).toList();
    }

    private static void write(RegistryFriendlyByteBuf buffer, MechanicalRecipeTransfer packet) {
        buffer.writeVarInt(packet.containerId());
        buffer.writeResourceLocation(packet.recipeId());
        buffer.writeVarInt(packet.inputs().size());
        for (ItemStack input : packet.inputs()) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, input);
        }
    }

    private static MechanicalRecipeTransfer read(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        ResourceLocation id = buffer.readResourceLocation();
        int size = buffer.readVarInt();
        if (size < 1 || size > 81) {
            throw new IllegalArgumentException("Invalid mechanical recipe grid size");
        }
        List<ItemStack> inputs = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            inputs.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        return new MechanicalRecipeTransfer(containerId, id, inputs);
    }

    public static void handle(MechanicalRecipeTransfer packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var menu = context.player().containerMenu;
            if (menu.containerId == packet.containerId() && menu.stillValid(context.player())
                    && menu instanceof MechanicalPatternTerminal terminal) {
                terminal.memc$transfer(packet.recipeId(), packet.inputs());
            }
        });
    }

    @Override
    public Type<MechanicalRecipeTransfer> type() {
        return TYPE;
    }
}
