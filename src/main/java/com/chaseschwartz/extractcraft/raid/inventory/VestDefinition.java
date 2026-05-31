package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.Set;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public record VestDefinition(String id, String name, int capacity, double maxWeight, Set<ItemCategory> allowedCategories) {
    public VestDefinition {
        allowedCategories = Set.copyOf(allowedCategories);
    }
}
