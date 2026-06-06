package com.chaseschwartz.extractcraft.durability;

public record DurabilityData(
        String type,
        int pristineMaxDurability,
        int currentMaxDurability,
        int currentDurability,
        int repairCount) {
    public DurabilityData {
        type = type == null || type.isBlank() ? "equipment" : type.trim();
        pristineMaxDurability = Math.max(1, pristineMaxDurability);
        currentMaxDurability = clamp(currentMaxDurability, 1, pristineMaxDurability);
        currentDurability = clamp(currentDurability, 0, currentMaxDurability);
        repairCount = Math.max(0, repairCount);
    }

    public double conditionRatio() {
        return currentMaxDurability <= 0 ? 1.0D : currentDurability / (double) currentMaxDurability;
    }

    public double pristineRatio() {
        return pristineMaxDurability <= 0 ? 1.0D : currentDurability / (double) pristineMaxDurability;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
