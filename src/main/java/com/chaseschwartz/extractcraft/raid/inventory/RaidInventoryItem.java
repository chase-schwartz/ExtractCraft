package com.chaseschwartz.extractcraft.raid.inventory;

import net.minecraft.resources.ResourceLocation;

public record RaidInventoryItem(ResourceLocation itemId, int count, int slotCost, double weight, int value) {
    public RaidInventoryItem {
        count = Math.max(1, count);
        slotCost = Math.max(1, slotCost);
        weight = Math.max(0.0D, weight);
        value = Math.max(0, value);
    }

    public int totalSlotCost() {
        return slotCost;
    }

    public double totalWeight() {
        return weight;
    }

    public int totalValue() {
        return value;
    }
}
