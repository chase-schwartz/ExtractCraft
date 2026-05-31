package com.chaseschwartz.extractcraft.itemvalues;

public enum ItemRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    QUEST;

    public static ItemRarity fromJson(String value) {
        if (value == null || value.isBlank()) {
            return COMMON;
        }

        return ItemRarity.valueOf(value.trim().toUpperCase());
    }
}
