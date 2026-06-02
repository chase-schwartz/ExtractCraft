package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.List;
import java.util.Optional;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

import net.minecraft.resources.ResourceLocation;

public record ItemCarryProfile(
        ResourceLocation itemId,
        ItemCategory category,
        double weight,
        int slotCost,
        Optional<Integer> gridWidth,
        Optional<Integer> gridHeight,
        boolean canRotate,
        boolean allowInSafeBox,
        boolean allowInVest,
        List<String> notes) {
    public ItemCarryProfile {
        slotCost = Math.max(1, slotCost);
        weight = Math.max(0.0D, weight);
        gridWidth = gridWidth.filter(value -> value > 0);
        gridHeight = gridHeight.filter(value -> value > 0);
        notes = List.copyOf(notes);
    }
}
