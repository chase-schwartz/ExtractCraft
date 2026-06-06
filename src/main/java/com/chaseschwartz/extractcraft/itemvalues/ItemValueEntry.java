package com.chaseschwartz.extractcraft.itemvalues;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

// TODO economy: keep durable gear sellable through base values here, then apply
// final_sell_value = base_sell_value * (current_durability / pristine_max_durability)
// at the sale point. Repairs that lower max durability should also lower future max sell value.
public record ItemValueEntry(
        String lookupKey,
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
