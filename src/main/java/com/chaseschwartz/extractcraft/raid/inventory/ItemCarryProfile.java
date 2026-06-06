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
        List<String> notes,
        Optional<Integer> tier,
        Optional<String> equipmentSlot,
        Optional<String> storageGridDefinition,
        Optional<Double> maxCarryWeight,
        boolean durabilityEnabled,
        Optional<Integer> maxDurability,
        Optional<String> durabilityType,
        Optional<String> repairCategory,
        Optional<Integer> repairAmount,
        Optional<String> repairTargetCategory,
        Optional<Integer> armorRating,
        Optional<Integer> healAmount,
        Optional<Integer> useTimeTicks,
        boolean consumable,
        boolean fixesBleed,
        boolean fixesBrokenBone) {
    public ItemCarryProfile {
        slotCost = Math.max(1, slotCost);
        weight = Math.max(0.0D, weight);
        gridWidth = gridWidth.filter(value -> value > 0);
        gridHeight = gridHeight.filter(value -> value > 0);
        notes = List.copyOf(notes);
        tier = tier.filter(value -> value > 0);
        maxCarryWeight = maxCarryWeight.filter(value -> value > 0.0D);
        maxDurability = maxDurability.filter(value -> value > 0);
        durabilityType = durabilityType.map(String::trim).filter(value -> !value.isBlank());
        repairAmount = repairAmount.filter(value -> value > 0);
        armorRating = armorRating.filter(value -> value > 0);
        healAmount = healAmount.filter(value -> value > 0);
        useTimeTicks = useTimeTicks.filter(value -> value > 0);
    }

    public ItemCarryProfile(
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
        this(
                itemId,
                category,
                weight,
                slotCost,
                gridWidth,
                gridHeight,
                canRotate,
                allowInSafeBox,
                allowInVest,
                notes,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                false);
    }
}
