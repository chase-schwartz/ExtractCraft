package com.chaseschwartz.extractcraft.raid.inventory;

import net.minecraft.resources.ResourceLocation;

public record RaidInventoryItem(ResourceLocation itemId, String lookupKey, String displayName, String category, int count, int slotCost, double weight, int value) {
    public RaidInventoryItem(ResourceLocation itemId, int count, int slotCost, double weight, int value) {
        this(itemId, itemId.toString(), itemId.toString(), "unknown", count, slotCost, weight, value);
    }

    public RaidInventoryItem {
        if (lookupKey == null || lookupKey.isBlank()) {
            lookupKey = itemId.toString();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = lookupKey;
        }
        if (category == null || category.isBlank()) {
            category = "unknown";
        }
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
