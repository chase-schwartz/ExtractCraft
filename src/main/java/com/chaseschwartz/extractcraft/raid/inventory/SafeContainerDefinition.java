package com.chaseschwartz.extractcraft.raid.inventory;

public record SafeContainerDefinition(String id, String name, int capacity, double maxWeight, int gridWidth, int gridHeight) {
    public SafeContainerDefinition(String id, String name, int gridWidth, int gridHeight, double maxWeight) {
        this(id, name, Math.max(0, gridWidth) * Math.max(0, gridHeight), maxWeight, gridWidth, gridHeight);
    }

    public SafeContainerDefinition {
        capacity = Math.max(0, capacity);
        maxWeight = Math.max(0.0D, maxWeight);
        gridWidth = Math.max(0, gridWidth);
        gridHeight = Math.max(0, gridHeight);
    }
}
