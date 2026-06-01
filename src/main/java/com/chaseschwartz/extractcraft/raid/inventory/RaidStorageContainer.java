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
        return usedCapacity() + item.totalSlotCost() <= capacity
                && usedWeight() + item.totalWeight() <= maxWeight;
    }

    public void add(RaidInventoryItem item) {
        items.add(item);
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
}
