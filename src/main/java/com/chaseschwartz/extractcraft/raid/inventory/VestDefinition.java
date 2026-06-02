package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.Set;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public record VestDefinition(String id, String name, int capacity, double maxWeight, int gridWidth, int gridHeight, Set<ItemCategory> allowedCategories) {
    public VestDefinition(String id, String name, int gridWidth, int gridHeight, double maxWeight, Set<ItemCategory> allowedCategories) {
        this(id, name, Math.max(0, gridWidth) * Math.max(0, gridHeight), maxWeight, gridWidth, gridHeight, allowedCategories);
    }

    public VestDefinition {
        capacity = Math.max(0, capacity);
        maxWeight = Math.max(0.0D, maxWeight);
        gridWidth = Math.max(0, gridWidth);
        gridHeight = Math.max(0, gridHeight);
        allowedCategories = Set.copyOf(allowedCategories);
    }
}
