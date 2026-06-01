package com.chaseschwartz.extractcraft.raid.containers;

import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.RaidState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class ActiveLootContainerInteractionHandler {
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
            buffer.writeVarInt(container.getContainerSize());
            buffer.writeBlockPos(event.getPos());
        });
    }
}
