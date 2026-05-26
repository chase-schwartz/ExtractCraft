package com.chaseschwartz.extractcraft.raid.map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record RaidLootItem(Item item, int count) {
    public ItemStack createStack() {
        return new ItemStack(item, count);
    }
}
