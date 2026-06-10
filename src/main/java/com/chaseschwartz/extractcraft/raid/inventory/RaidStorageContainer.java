package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;

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

    public int addPartialPreservingPlacement(RaidInventoryItem item) {
        return addPartialPreservingPlacement(item, false);
    }

    public int addPartialPreservingPlacement(RaidInventoryItem item, boolean ignoreWeight) {
        if (item == null || item.count() <= 0) {
            return 0;
        }
        if (!item.isPlaced()) {
            return ignoreWeight ? addPartialGridFirstFit(item, true) : addPartial(item);
        }

        int moved = addPartialAt(item, item.gridX(), item.gridY(), item.rotated(), -1, ignoreWeight);
        if (moved >= item.count()) {
            return moved;
        }

        RaidInventoryItem remaining = item.withCount(item.count() - moved);
        return moved + (ignoreWeight ? addPartialGridFirstFit(remaining, true) : addPartial(remaining));
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

    public int addPartialGridFirstFit(RaidInventoryItem item) {
        return addPartialGridFirstFit(item, false);
    }

    public int addPartialGridFirstFit(RaidInventoryItem item, boolean ignoreWeight) {
        if (item == null || item.count() <= 0) {
            return 0;
        }

        int remaining = item.count();
        for (int index = 0; index < items.size() && remaining > 0; index++) {
            RaidInventoryItem existing = items.get(index);
            if (!existing.canMerge(item)) {
                continue;
            }

            int freeStackSpace = existing.maxStackSize() - existing.count();
            int weightLimited = weightLimitedCount(item, ignoreWeight);
            int transfer = Math.min(remaining, Math.min(freeStackSpace, weightLimited));
            if (transfer <= 0) {
                continue;
            }

            items.set(index, existing.withCount(existing.count() + transfer));
            remaining -= transfer;
        }

        while (remaining > 0) {
            int transfer = Math.min(remaining, item.maxStackSize());
            transfer = Math.min(transfer, weightLimitedCount(item, ignoreWeight));
            if (transfer <= 0) {
                break;
            }

            RaidInventoryItem stack = item.withCount(transfer);
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
        return addPartialAt(item, x, y, rotated, excludedIndex, false);
    }

    public int addPartialAt(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex, boolean ignoreWeight) {
        if (item == null || item.count() <= 0 || x < 0 || y < 0) {
            return 0;
        }

        RaidInventoryItem mergeTarget = itemAtCell(x, y, excludedIndex);
        if (mergeTarget != null) {
            int mergeIndex = itemIndexAtCell(x, y);
            if (mergeIndex != excludedIndex && mergeTarget.canMerge(item)) {
                int freeStackSpace = mergeTarget.maxStackSize() - mergeTarget.count();
                int transfer = Math.min(item.count(), Math.min(freeStackSpace, weightLimitedCount(item, ignoreWeight)));
                if (transfer > 0) {
                    items.set(mergeIndex, mergeTarget.withCount(mergeTarget.count() + transfer));
                }
                return transfer;
            }
            return 0;
        }

        int transfer = Math.min(item.count(), item.maxStackSize());
        transfer = Math.min(transfer, weightLimitedCount(item, ignoreWeight));
        if (transfer <= 0) {
            ExtractCraft.LOGGER.info("Raid grid placement rejected: container={}, target=({},{}), item={}x {}, footprint={}x{}, cells={}, reason=weight used={} max={} ignoreWeight={}",
                    id,
                    x,
                    y,
                    item.count(),
                    item.lookupKey(),
                    footprintWidth(item, rotated),
                    footprintHeight(item, rotated),
                    checkedCells(x, y, footprintWidth(item, rotated), footprintHeight(item, rotated)),
                    usedWeight(),
                    maxWeight,
                    ignoreWeight);
            return 0;
        }

        RaidInventoryItem stack = item.withCount(transfer);
        if (usedCapacity() + stack.totalSlotCost() > capacity) {
            ExtractCraft.LOGGER.info("Raid grid placement rejected: container={}, target=({},{}), item={}x {}, footprint={}x{}, cells={}, reason=capacity used={} itemCost={} capacity={}",
                    id,
                    x,
                    y,
                    stack.count(),
                    stack.lookupKey(),
                    footprintWidth(stack, rotated),
                    footprintHeight(stack, rotated),
                    checkedCells(x, y, footprintWidth(stack, rotated), footprintHeight(stack, rotated)),
                    usedCapacity(),
                    stack.totalSlotCost(),
                    capacity);
            return 0;
        }
        if (!canFit(stack, x, y, rotated, excludedIndex)) {
            ExtractCraft.LOGGER.info("Raid grid placement rejected: container={}, target=({},{}), item={}x {}, footprint={}x{}, cells={}, reason={}",
                    id,
                    x,
                    y,
                    stack.count(),
                    stack.lookupKey(),
                    footprintWidth(stack, rotated),
                    footprintHeight(stack, rotated),
                    checkedCells(x, y, footprintWidth(stack, rotated), footprintHeight(stack, rotated)),
                    fitFailureDescription(stack, x, y, rotated, excludedIndex));
            return 0;
        }

        items.add(stack.withPlacement(x, y, rotated));
        return transfer;
    }

    public int moveItemToCell(int index, int x, int y, boolean rotated) {
        if (index < 0 || index >= items.size() || x < 0 || y < 0) {
            return 0;
        }

        RaidInventoryItem item = items.get(index);
        IgnoredFootprint ignoredFootprint = IgnoredFootprint.from(item);
        RaidInventoryItem mergeTarget = itemAtCell(x, y, index, ignoredFootprint);
        if (mergeTarget != null) {
            int mergeIndex = itemIndexAtCell(x, y);
            if (mergeIndex != index && mergeTarget.canMerge(item)) {
                int transfer = Math.min(item.count(), mergeTarget.maxStackSize() - mergeTarget.count());
                if (transfer > 0) {
                    items.set(mergeIndex, mergeTarget.withCount(mergeTarget.count() + transfer));
                    RaidInventoryItem remaining = item.withCount(item.count() - transfer);
                    if (remaining.count() <= 0) {
                        items.remove(index);
                    } else {
                        items.set(index, remaining);
                    }
                    return transfer;
                }
            }
            return 0;
        }

        if (!canFit(item, x, y, rotated, index, ignoredFootprint)) {
            ExtractCraft.LOGGER.info("Raid grid relocation rejected: container={}, sourceIndex={}, source=({},{}), target=({},{}), item={}x {}, footprint={}x{}, reason={}",
                    id,
                    index,
                    item.gridX(),
                    item.gridY(),
                    x,
                    y,
                    item.count(),
                    item.lookupKey(),
                    footprintWidth(item, rotated),
                    footprintHeight(item, rotated),
                    fitFailureDescription(item, x, y, rotated, index, ignoredFootprint));
            return 0;
        }

        items.set(index, item.withPlacement(x, y, rotated));
        return item.count();
    }

    public int countAddable(RaidInventoryItem item, int excludedIndex) {
        RaidStorageContainer copy = new RaidStorageContainer(id, name, capacity, maxWeight, gridWidth, gridHeight);
        copy.items.addAll(items);
        return copy.addPartial(item, excludedIndex);
    }

    public RaidStorageContainer copy() {
        RaidStorageContainer copy = new RaidStorageContainer(id, name, capacity, maxWeight, gridWidth, gridHeight);
        copy.items.addAll(items);
        return copy;
    }

    public boolean canFit(RaidInventoryItem item, int x, int y, boolean rotated) {
        return canFit(item, x, y, rotated, -1);
    }

    public boolean canFit(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex) {
        return canFit(item, x, y, rotated, excludedIndex, IgnoredFootprint.NONE);
    }

    private boolean canFit(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex, IgnoredFootprint ignoredFootprint) {
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
            if (ignoredFootprint.matches(existing)) {
                continue;
            }
            if (overlaps(x, y, width, height, existing.gridX(), existing.gridY(), footprintWidth(existing, existing.rotated()), footprintHeight(existing, existing.rotated()))) {
                return false;
            }
        }
        return true;
    }

    public String fitFailureDescription(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex) {
        return fitFailureDescription(item, x, y, rotated, excludedIndex, IgnoredFootprint.NONE);
    }

    private String fitFailureDescription(RaidInventoryItem item, int x, int y, boolean rotated, int excludedIndex, IgnoredFootprint ignoredFootprint) {
        if (item == null) {
            return "item=null";
        }
        if (gridWidth <= 0 || gridHeight <= 0) {
            return "grid disabled " + gridWidth + "x" + gridHeight;
        }

        int width = footprintWidth(item, rotated);
        int height = footprintHeight(item, rotated);
        if (x < 0 || y < 0 || x + width > gridWidth || y + height > gridHeight) {
            return "out_of_bounds grid=" + gridWidth + "x" + gridHeight;
        }

        for (int index = 0; index < items.size(); index++) {
            if (index == excludedIndex) {
                continue;
            }
            RaidInventoryItem existing = items.get(index);
            if (!existing.isPlaced()) {
                continue;
            }
            if (ignoredFootprint.matches(existing)) {
                continue;
            }
            int existingWidth = footprintWidth(existing, existing.rotated());
            int existingHeight = footprintHeight(existing, existing.rotated());
            if (overlaps(x, y, width, height, existing.gridX(), existing.gridY(), existingWidth, existingHeight)) {
                return "blocked_by index=" + index
                        + " key=" + existing.lookupKey()
                        + " item=" + existing.count() + "x " + existing.displayName()
                        + " at=(" + existing.gridX() + "," + existing.gridY() + ")"
                        + " footprint=" + existingWidth + "x" + existingHeight
                        + " rotated=" + existing.rotated()
                        + " existsInCurrentItems=true";
            }
        }
        return "unknown_no_overlap_found";
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
        return itemAtCell(x, y, excludedIndex, IgnoredFootprint.NONE);
    }

    private RaidInventoryItem itemAtCell(int x, int y, int excludedIndex, IgnoredFootprint ignoredFootprint) {
        int index = itemIndexAtCell(x, y);
        if (index < 0 || index == excludedIndex) {
            return null;
        }
        RaidInventoryItem item = items.get(index);
        return ignoredFootprint.matches(item) ? null : item;
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

    public String firstFitFailureDescription(RaidInventoryItem item) {
        if (item == null) {
            return "item=null";
        }
        if (gridWidth <= 0 || gridHeight <= 0) {
            return "grid disabled " + gridWidth + "x" + gridHeight;
        }

        StringBuilder builder = new StringBuilder();
        for (boolean rotated : rotationOptions(item)) {
            int width = footprintWidth(item, rotated);
            int height = footprintHeight(item, rotated);
            if (width > gridWidth || height > gridHeight) {
                if (!builder.isEmpty()) {
                    builder.append("; ");
                }
                builder.append("rotated=").append(rotated).append(" out_of_bounds footprint=").append(width).append("x").append(height).append(" grid=").append(gridWidth).append("x").append(gridHeight);
                continue;
            }
            for (int y = 0; y <= gridHeight - height; y++) {
                for (int x = 0; x <= gridWidth - width; x++) {
                    if (!canFit(item, x, y, rotated)) {
                        if (builder.length() < 600) {
                            if (!builder.isEmpty()) {
                                builder.append("; ");
                            }
                            builder.append("(").append(x).append(",").append(y).append(") ").append(fitFailureDescription(item, x, y, rotated, -1));
                        }
                    }
                }
            }
        }
        return builder.isEmpty() ? "no candidate cells checked" : builder.toString();
    }

    public String occupiedCellsDescription() {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (int index = 0; index < items.size(); index++) {
            RaidInventoryItem item = items.get(index);
            if (!item.isPlaced()) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(index)
                    .append(":")
                    .append(item.lookupKey())
                    .append("@(")
                    .append(item.gridX())
                    .append(",")
                    .append(item.gridY())
                    .append(") ")
                    .append(footprintWidth(item, item.rotated()))
                    .append("x")
                    .append(footprintHeight(item, item.rotated()));
            first = false;
        }
        return builder.append("]").toString();
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

    public boolean replaceAt(int index, RaidInventoryItem item) {
        if (index < 0 || index >= items.size() || item == null) {
            return false;
        }
        items.set(index, item);
        return true;
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

    public void sortItems(Comparator<RaidInventoryItem> comparator) {
        if (comparator != null) {
            items.sort(comparator);
        }
    }

    public boolean repackFirstFit(boolean ignoreWeight) {
        List<RaidInventoryItem> ordered = new ArrayList<>(items);
        items.clear();
        for (RaidInventoryItem item : ordered) {
            int moved = addPartialGridFirstFit(item.withoutPlacement(), ignoreWeight);
            if (moved < item.count()) {
                items.clear();
                items.addAll(ordered);
                return false;
            }
        }
        return true;
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
        return weightLimitedCount(item, false);
    }

    private int weightLimitedCount(RaidInventoryItem item, boolean ignoreWeight) {
        if (ignoreWeight) {
            return item.count();
        }
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

    private static String checkedCells(int x, int y, int width, int height) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (int cellY = y; cellY < y + height; cellY++) {
            for (int cellX = x; cellX < x + width; cellX++) {
                if (!first) {
                    builder.append(",");
                }
                builder.append("(").append(cellX).append(",").append(cellY).append(")");
                first = false;
            }
        }
        return builder.append("]").toString();
    }

    public record GridPlacement(int x, int y, boolean rotated) {
    }

    private record IgnoredFootprint(int x, int y, int width, int height, boolean active) {
        private static final IgnoredFootprint NONE = new IgnoredFootprint(-1, -1, 0, 0, false);

        private static IgnoredFootprint from(RaidInventoryItem item) {
            if (item == null || !item.isPlaced()) {
                return NONE;
            }
            return new IgnoredFootprint(item.gridX(), item.gridY(), footprintWidth(item, item.rotated()), footprintHeight(item, item.rotated()), true);
        }

        private boolean matches(RaidInventoryItem item) {
            return active
                    && item != null
                    && item.isPlaced()
                    && item.gridX() == x
                    && item.gridY() == y
                    && footprintWidth(item, item.rotated()) == width
                    && footprintHeight(item, item.rotated()) == height;
        }
    }
}
