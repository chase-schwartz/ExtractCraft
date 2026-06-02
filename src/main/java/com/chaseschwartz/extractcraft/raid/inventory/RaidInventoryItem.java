package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record RaidInventoryItem(ResourceLocation itemId, String lookupKey, String displayName, String category, int count, int slotCost, double weight, int value, ItemStack storedStack) {
    public RaidInventoryItem(ResourceLocation itemId, int count, int slotCost, double weight, int value) {
        this(itemId, itemId.toString(), itemId.toString(), "unknown", count, slotCost, weight, value);
    }

    public RaidInventoryItem(ResourceLocation itemId, String lookupKey, String displayName, String category, int count, int slotCost, double weight, int value) {
        this(itemId, lookupKey, displayName, category, count, slotCost, weight, value, ItemStack.EMPTY);
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
        storedStack = storedStack == null ? ItemStack.EMPTY : storedStack.copy();
        if (!storedStack.isEmpty()) {
            storedStack.setCount(count);
        }
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

    public boolean canMerge(RaidInventoryItem other) {
        if (other == null || !lookupKey.equals(other.lookupKey)) {
            return false;
        }

        ItemStack stack = toItemStack();
        ItemStack otherStack = other.toItemStack();
        return !stack.isEmpty()
                && !otherStack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, otherStack)
                && stack.getMaxStackSize() > 1;
    }

    public int maxStackSize() {
        ItemStack stack = toItemStack();
        return stack.isEmpty() ? 1 : Math.max(1, stack.getMaxStackSize());
    }

    public RaidInventoryItem withCount(int newCount) {
        int adjustedCount = Math.max(1, newCount);
        int oldStackUnits = Math.max(1, (int) Math.ceil(count / (double) maxStackSize()));
        int unitSlotCost = Math.max(1, (int) Math.ceil(slotCost / (double) oldStackUnits));
        int newStackUnits = Math.max(1, (int) Math.ceil(adjustedCount / (double) maxStackSize()));
        double weightPerItem = weight / Math.max(1, count);
        double valuePerItem = value / (double) Math.max(1, count);
        return new RaidInventoryItem(
                itemId,
                lookupKey,
                displayName,
                category,
                adjustedCount,
                unitSlotCost * newStackUnits,
                weightPerItem * adjustedCount,
                (int) Math.round(valuePerItem * adjustedCount),
                toItemStack());
    }

    public ItemStack toItemStack() {
        if (!storedStack.isEmpty()) {
            ItemStack copy = storedStack.copy();
            copy.setCount(count);
            return copy;
        }

        return ItemStackVariantFactory.create(lookupKey, count)
                .orElseGet(() -> {
                    if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                        return new ItemStack(BuiltInRegistries.ITEM.get(itemId), count);
                    }
                    return ItemStack.EMPTY;
                });
    }
}
