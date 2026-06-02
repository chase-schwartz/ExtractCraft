package com.chaseschwartz.extractcraft.raid.containers;

import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.RaidState;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class ActiveLootContainerInteractionHandler {
    private static final ResourceLocation TACZ_GUN = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        RaidState raidState = RaidManager.getRaidState(player).orElse(null);
        if (raidState == null) {
            return;
        }

        RaidContainerLayout layout = RaidContainerService.load(raidState.raidMap().id()).orElse(null);
        if (layout == null || !RaidContainerService.isActiveLootContainer(layout, event.getPos())) {
            prioritizeInteractableBlockOverGun(event, player);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        BlockEntity blockEntity = player.level().getBlockEntity(event.getPos());
        if (!(blockEntity instanceof Container container)) {
            player.sendSystemMessage(Component.literal("This active loot container is unavailable."));
            return;
        }

        MenuConstructor constructor = (containerId, inventory, menuPlayer) -> new ActiveLootContainerMenu(containerId, inventory, container, event.getPos(), player);
        player.openMenu(new SimpleMenuProvider(constructor, Component.literal("Search Container")), buffer -> {
            buffer.writeBoolean(true);
            buffer.writeVarInt(container.getContainerSize());
            buffer.writeBlockPos(event.getPos());
        });
    }

    private static void prioritizeInteractableBlockOverGun(PlayerInteractEvent.RightClickBlock event, ServerPlayer player) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !isTaczGun(player.getMainHandItem())) {
            return;
        }

        BlockState state = player.level().getBlockState(event.getPos());
        BlockEntity blockEntity = player.level().getBlockEntity(event.getPos());
        if (!isLikelyInteractable(state, blockEntity, player, event)) {
            return;
        }

        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.FALSE);
    }

    private static boolean isLikelyInteractable(BlockState state, BlockEntity blockEntity, ServerPlayer player, PlayerInteractEvent.RightClickBlock event) {
        if (blockEntity instanceof Container) {
            return true;
        }

        MenuProvider menuProvider = state.getMenuProvider(player.level(), event.getPos());
        if (menuProvider != null) {
            return true;
        }

        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock;
    }

    private static boolean isTaczGun(ItemStack stack) {
        return !stack.isEmpty() && TACZ_GUN.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
