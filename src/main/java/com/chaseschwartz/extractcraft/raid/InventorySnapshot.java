package com.chaseschwartz.extractcraft.raid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public record InventorySnapshot(List<ItemStack> items) {
    public static InventorySnapshot capture(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> copiedItems = new ArrayList<>(inventory.getContainerSize());

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            copiedItems.add(inventory.getItem(slot).copy());
        }

        return new InventorySnapshot(List.copyOf(copiedItems));
    }

    public void restore(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        inventory.clearContent();

        int restoreSlots = Math.min(items.size(), inventory.getContainerSize());
        for (int slot = 0; slot < restoreSlots; slot++) {
            inventory.setItem(slot, items.get(slot).copy());
        }

        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }
}
