package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.List;

public class RaidStorageContainer {
    private final String id;
    private final String name;
    private final int capacity;
    private final double maxWeight;
    private final int gridWidth;
    private final int gridHeight;
    private final List<RaidInventoryItem> items = new ArrayList<>();

    public RaidStorageContainer(String id, String name, int capacity, double maxWeight) {
        this(id, name, capacity, maxWeight, Math.max(1, capacity), 1);
    }

    public RaidStorageContainer(String id, String name, int capacity, double maxWeight, int gridWidth, int gridHeight) {
        this.id = id;
        this.name = name;
        this.capacity = Math.max(0, capacity);
        this.maxWeight = Math.max(0.0D, maxWeight);
        this.gridWidth = Math.max(0, gridWidth);
        this.gridHeight = Math.max(0, gridHeight);
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
            GridPlacement placement = findFirstFit(stack).orElse(null);
            if (placement == null) {
                break;
            }

            items.add(stack.withPlacement(placement.x(), placement.y(), placement.rotated()));
            remaining -= transfer;
        }

        return item.count() - remaining;
    }

    public int addPartialAt(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex) {
        if (item == null || item.count() <= 0 || x < 0 || y < 0) {
            return 0;
        }

        RaidInventoryItem mergeTarget = itemAtCell(x, y, excludedIndex);
        if (mergeTarget != null) {
            int mergeIndex = itemIndexAtCell(x, y);
            if (mergeIndex != excludedIndex && mergeTarget.canMerge(item)) {
                int freeStackSpace = mergeTarget.maxStackSize() - mergeTarget.count();
                int transfer = Math.min(item.count(), Math.min(freeStackSpace, weightLimitedCount(item)));
                if (transfer > 0) {
                    items.set(mergeIndex, mergeTarget.withCount(mergeTarget.count() + transfer));
                }
                return transfer;
            }
            return 0;
        }

        int transfer = Math.min(item.count(), item.maxStackSize());
        transfer = Math.min(transfer, weightLimitedCount(item));
        if (transfer <= 0) {
            return 0;
        }

        RaidInventoryItem stack = item.withCount(transfer);
        if (usedCapacity() + stack.totalSlotCost() > capacity || !canFit(stack, x, y, rotated, excludedIndex)) {
            return 0;
        }

        items.add(stack.withPlacement(x, y, rotated));
        return transfer;
    }

    public int countAddable(RaidInventoryItem item, int excludedIndex) {
        RaidStorageContainer copy = new RaidStorageContainer(id, name, capacity, maxWeight, gridWidth, gridHeight);
        copy.items.addAll(items);
        return copy.addPartial(item, excludedIndex);
    }

    public boolean canFit(RaidInventoryItem item, int x, int y, boolean rotated) {
        return canFit(item, x, y, rotated, -1);
    }

    public boolean canFit(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex) {
        if (item == null || gridWidth <= 0 || gridHeight <= 0) {
            return false;
        }

        int width = footprintWidth(item, rotated);
        int height = footprintHeight(item, rotated);
        if (x < 0 || y < 0 || x + width > gridWidth || y + height > gridHeight) {
            return false;
        }

        for (int index = 0; index < items.size(); index++) {
            if (index == excludedIndex) {
                continue;
            }
            RaidInventoryItem existing = items.get(index);
            if (!existing.isPlaced()) {
                continue;
            }
            if (overlaps(x, y, width, height, existing.gridX(), existing.gridY(), footprintWidth(existing, existing.rotated()), footprintHeight(existing, existing.rotated()))) {
                return false;
            }
        }
        return true;
    }

    public RaidInventoryItem itemAtCell(int x, int y) {
        return itemAtCell(x, y, -1);
    }

    public int itemIndexAtCell(int x, int y) {
        for (int index = 0; index < items.size(); index++) {
            RaidInventoryItem item = items.get(index);
            if (coversCell(item, x, y)) {
                return index;
            }
        }
        return -1;
    }

    private RaidInventoryItem itemAtCell(int x, int y, int excludedIndex) {
        int index = itemIndexAtCell(x, y);
        return index < 0 || index == excludedIndex ? null : items.get(index);
    }

    public java.util.Optional<GridPlacement> findFirstFit(RaidInventoryItem item) {
        if (gridWidth <= 0 || gridHeight <= 0) {
            return java.util.Optional.empty();
        }

        for (boolean rotated : rotationOptions(item)) {
            int width = footprintWidth(item, rotated);
            int height = footprintHeight(item, rotated);
            for (int y = 0; y <= gridHeight - height; y++) {
                for (int x = 0; x <= gridWidth - width; x++) {
                    if (canFit(item, x, y, rotated)) {
                        return java.util.Optional.of(new GridPlacement(x, y, rotated));
                    }
                }
            }
        }
        return java.util.Optional.empty();
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

    public int gridWidth() {
        return gridWidth;
    }

    public int gridHeight() {
        return gridHeight;
    }

    private int weightLimitedCount(RaidInventoryItem item) {
        double perItemWeight = item.totalWeight() / Math.max(1, item.count());
        if (perItemWeight <= 0.0D) {
            return item.count();
        }

        double remainingWeight = maxWeight - usedWeight();
        return Math.max(0, (int) Math.floor((remainingWeight + 0.000001D) / perItemWeight));
    }

    private static List<Boolean> rotationOptions(RaidInventoryItem item) {
        if (!item.canRotate() || item.gridWidth() == item.gridHeight()) {
            return List.of(false);
        }
        return List.of(false, true);
    }

    private static int footprintWidth(RaidInventoryItem item, boolean rotated) {
        return rotated ? item.gridHeight() : item.gridWidth();
    }

    private static int footprintHeight(RaidInventoryItem item, boolean rotated) {
        return rotated ? item.gridWidth() : item.gridHeight();
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static boolean coversCell(RaidInventoryItem item, int x, int y) {
        if (!item.isPlaced()) {
            return false;
        }
        int width = footprintWidth(item, item.rotated());
        int height = footprintHeight(item, item.rotated());
        return x >= item.gridX() && x < item.gridX() + width && y >= item.gridY() && y < item.gridY() + height;
    }

    public record GridPlacement(int x, int y, boolean rotated) {
    }
}
