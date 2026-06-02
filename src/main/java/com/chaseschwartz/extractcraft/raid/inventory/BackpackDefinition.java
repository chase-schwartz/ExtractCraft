package com.chaseschwartz.extractcraft.raid.inventory;

public record BackpackDefinition(String id, String name, int capacity, double maxWeight, int gridWidth, int gridHeight) {
    public BackpackDefinition(String id, String name, int gridWidth, int gridHeight, double maxWeight) {
        this(id, name, Math.max(0, gridWidth) * Math.max(0, gridHeight), maxWeight, gridWidth, gridHeight);
    }

    public BackpackDefinition {
        capacity = Math.max(0, capacity);
        maxWeight = Math.max(0.0D, maxWeight);
        gridWidth = Math.max(0, gridWidth);
        gridHeight = Math.max(0, gridHeight);
    }
}
