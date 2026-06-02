package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.List;

public class RaidStorageContainer {
    private final String id;
    private final String name;
    private final int capacity;
    private final double maxWeight;
    private final List<RaidInventoryItem> items = new ArrayList<>();

    public RaidStorageContainer(String id, String name, int capacity, double maxWeight) {
        this.id = id;
        this.name = name;
        this.capacity = Math.max(0, capacity);
        this.maxWeight = Math.max(0.0D, maxWeight);
    }

    public boolean canAdd(RaidInventoryItem item) {
        return countAddable(item, -1) >= item.count();
    }

    public void add(RaidInventoryItem item) {
        addPartial(item, -1);
    }

    public int addPartial(RaidInventoryItem item) {
        return addPartial(item, -1);
    }

    public int addPartial(RaidInventoryItem item, int excludedIndex) {
        if (item == null || item.count() <= 0) {
            return 0;
        }

        int remaining = item.count();
        for (int index = 0; index < items.size() && remaining > 0; index++) {
            if (index == excludedIndex) {
                continue;
            }

            RaidInventoryItem existing = items.get(index);
            if (!existing.canMerge(item)) {
                continue;
            }

            int freeStackSpace = existing.maxStackSize() - existing.count();
            int weightLimited = weightLimitedCount(item);
            int transfer = Math.min(remaining, Math.min(freeStackSpace, weightLimited));
            if (transfer <= 0) {
                continue;
            }

            items.set(index, existing.withCount(existing.count() + transfer));
            remaining -= transfer;
        }

        while (remaining > 0) {
            int transfer = Math.min(remaining, item.maxStackSize());
            transfer = Math.min(transfer, weightLimitedCount(item));
            if (transfer <= 0) {
                break;
            }

            RaidInventoryItem stack = item.withCount(transfer);
            if (usedCapacity() + stack.totalSlotCost() > capacity) {
                break;
            }

            items.add(stack);
            remaining -= transfer;
        }

        return item.count() - remaining;
    }

    public int countAddable(RaidInventoryItem item, int excludedIndex) {
        RaidStorageContainer copy = new RaidStorageContainer(id, name, capacity, maxWeight);
        copy.items.addAll(items);
        return copy.addPartial(item, excludedIndex);
    }

    public RaidInventoryItem itemAt(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    public RaidInventoryItem removeAt(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.remove(index);
    }

    public RaidInventoryItem removeCountAt(int index, int count) {
        if (index < 0 || index >= items.size() || count <= 0) {
            return null;
        }

        RaidInventoryItem item = items.get(index);
        if (count >= item.count()) {
            return items.remove(index);
        }

        RaidInventoryItem removed = item.withCount(count);
        items.set(index, item.withCount(item.count() - count));
        return removed;
    }

    public void clear() {
        items.clear();
    }

    public int usedCapacity() {
        return items.stream().mapToInt(RaidInventoryItem::totalSlotCost).sum();
    }

    public double usedWeight() {
        return items.stream().mapToDouble(RaidInventoryItem::totalWeight).sum();
    }

    public int totalValue() {
        return items.stream().mapToInt(RaidInventoryItem::totalValue).sum();
    }

    public int itemCount() {
        return items.size();
    }

    public List<RaidInventoryItem> items() {
        return List.copyOf(items);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }

    public double maxWeight() {
        return maxWeight;
    }

    private int weightLimitedCount(RaidInventoryItem item) {
        double perItemWeight = item.totalWeight() / Math.max(1, item.count());
        if (perItemWeight <= 0.0D) {
            return item.count();
        }

        double remainingWeight = maxWeight - usedWeight();
        return Math.max(0, (int) Math.floor((remainingWeight + 0.000001D) / perItemWeight));
    }
}
