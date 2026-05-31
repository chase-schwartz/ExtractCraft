package com.chaseschwartz.extractcraft.itemidentity;

import java.util.Optional;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

import net.minecraft.resources.ResourceLocation;

public record ItemIdentity(
        ResourceLocation baseItemId,
        Optional<ResourceLocation> variantId,
        String normalizedKey,
        String displayName,
        Optional<ItemCategory> categoryHint) {
}
