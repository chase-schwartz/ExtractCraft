package com.chaseschwartz.extractcraft.itemvalues;

import net.minecraft.ChatFormatting;

public final class RarityPresentation {
    private RarityPresentation() {
    }

    public static String label(ItemRarity rarity) {
        return switch (normalize(rarity)) {
            case COMMON -> "Common";
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case RED -> "Red";
            default -> "Common";
        };
    }

    public static ChatFormatting textColor(ItemRarity rarity) {
        return switch (normalize(rarity)) {
            case COMMON -> ChatFormatting.GRAY;
            case UNCOMMON -> ChatFormatting.AQUA;
            case RARE -> ChatFormatting.LIGHT_PURPLE;
            case EPIC -> ChatFormatting.GOLD;
            case RED -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    public static int slotTint(ItemRarity rarity) {
        return switch (normalize(rarity)) {
            case COMMON -> 0x203E4652;
            case UNCOMMON -> 0x503A8DFF;
            case RARE -> 0x585D3EA8;
            case EPIC -> 0x5CB58A2E;
            case RED -> 0x62B74343;
            default -> 0x203E4652;
        };
    }

    private static ItemRarity normalize(ItemRarity rarity) {
        if (rarity == null) {
            return ItemRarity.COMMON;
        }
        return switch (rarity) {
            case BLUE, UNCOMMON -> ItemRarity.UNCOMMON;
            case PURPLE, RARE -> ItemRarity.RARE;
            case GOLD, EPIC -> ItemRarity.EPIC;
            case RED, LEGENDARY, QUEST -> ItemRarity.RED;
            case COMMON -> ItemRarity.COMMON;
        };
    }
}
