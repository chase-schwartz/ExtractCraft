package com.chaseschwartz.extractcraft.raid.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class GridDisplayMetadata {
    private static final String ROOT = "ExtractCraftGrid";

    private GridDisplayMetadata() {
    }

    public static ItemStack stamp(ItemStack stack, RaidInventoryItem item, int sourceIndex) {
        return stamp(stack, item, sourceIndex, true);
    }

    public static ItemStack stamp(ItemStack stack, RaidInventoryItem item, int sourceIndex, boolean anchor) {
        if (stack.isEmpty() || item == null) {
            return stack;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
        CompoundTag grid = new CompoundTag();
        grid.putInt("sourceIndex", sourceIndex);
        grid.putInt("gridWidth", item.gridWidth());
        grid.putInt("gridHeight", item.gridHeight());
        grid.putInt("gridX", item.gridX());
        grid.putInt("gridY", item.gridY());
        grid.putBoolean("rotated", item.rotated());
        grid.putBoolean("canRotate", item.canRotate());
        grid.putBoolean("anchor", anchor);
        tag.put(ROOT, grid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static ItemStack stamp(ItemStack stack, Metadata metadata, boolean anchor) {
        if (stack.isEmpty() || metadata == null || !metadata.present()) {
            return stack;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
        CompoundTag grid = new CompoundTag();
        grid.putInt("sourceIndex", metadata.sourceIndex());
        grid.putInt("gridWidth", metadata.gridWidth());
        grid.putInt("gridHeight", metadata.gridHeight());
        grid.putInt("gridX", metadata.gridX());
        grid.putInt("gridY", metadata.gridY());
        grid.putBoolean("rotated", metadata.rotated());
        grid.putBoolean("canRotate", metadata.canRotate());
        grid.putBoolean("anchor", anchor);
        tag.put(ROOT, grid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static Metadata read(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Metadata.EMPTY;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(ROOT)) {
            return Metadata.EMPTY;
        }
        CompoundTag grid = tag.getCompound(ROOT);
        return new Metadata(
                grid.getInt("sourceIndex"),
                Math.max(1, grid.getInt("gridWidth")),
                Math.max(1, grid.getInt("gridHeight")),
                grid.getInt("gridX"),
                grid.getInt("gridY"),
                grid.getBoolean("rotated"),
                grid.getBoolean("canRotate"),
                !grid.contains("anchor") || grid.getBoolean("anchor"));
    }

    public record Metadata(int sourceIndex, int gridWidth, int gridHeight, int gridX, int gridY, boolean rotated, boolean canRotate, boolean anchor) {
        public static final Metadata EMPTY = new Metadata(-1, 1, 1, -1, -1, false, true, true);

        public boolean present() {
            return sourceIndex >= 0;
        }

        public int footprintWidth() {
            return Math.max(1, rotated ? gridHeight : gridWidth);
        }

        public int footprintHeight() {
            return Math.max(1, rotated ? gridWidth : gridHeight);
        }
    }
}
