package com.chaseschwartz.extractcraft.durability;

import net.minecraft.resources.ResourceLocation;

public record DurabilityProfile(
        ResourceLocation itemId,
        String type,
        int tier,
        int pristineMaxDurability) {
    public DurabilityProfile {
        type = type == null || type.isBlank() ? "equipment" : type.trim();
        tier = Math.max(0, tier);
        pristineMaxDurability = Math.max(1, pristineMaxDurability);
    }
}
