package com.chaseschwartz.extractcraft.itemvalues;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

public record ItemValueEntry(
        ResourceLocation itemId,
        ItemCategory category,
        ItemRarity rarity,
        int value,
        boolean sellable,
        boolean questItem,
        List<String> notes,
        Optional<String> traderType,
        Optional<Integer> lootTier) {
    public ItemValueEntry {
        notes = List.copyOf(notes);
        traderType = traderType.map(String::trim).filter(text -> !text.isBlank());
    }
}
