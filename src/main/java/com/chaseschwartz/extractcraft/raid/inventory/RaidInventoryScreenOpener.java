package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class RaidInventoryScreenOpener {
    private RaidInventoryScreenOpener() {
    }

    public static void open(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        RaidInventoryMenu.RaidInventorySnapshot snapshot = RaidInventoryMenu.RaidInventorySnapshot.from(inventory);
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> new RaidInventoryMenu(containerId, playerInventory, inventory),
                Component.literal("Raid Inventory")),
                snapshot::write);
    }

    public static void openGrid(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, menuPlayer) -> new ActiveLootContainerMenu(containerId, playerInventory, player),
                Component.literal("Raid Inventory")),
                buffer -> {
                    buffer.writeBoolean(false);
                    buffer.writeBlockPos(player.blockPosition());
                });
    }
}
