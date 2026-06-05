package com.chaseschwartz.extractcraft.raid.containers;

import com.chaseschwartz.extractcraft.itemvalues.ItemRarity;

public final class LootRevealTiming {
    private LootRevealTiming() {
    }

    public static long delayMs(ItemRarity rarity) {
        if (rarity == null) {
            return 2_000L;
        }
        return switch (rarity) {
            case COMMON, BLUE -> 2_000L;
            case UNCOMMON -> 2_400L;
            case RARE, PURPLE -> 3_000L;
            case EPIC, GOLD -> 3_700L;
            case RED, LEGENDARY -> 4_500L;
            default -> 2_000L;
        };
    }
}
