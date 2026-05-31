package com.chaseschwartz.extractcraft.raid.containers;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record RaidContainerEntry(
        ResourceLocation dimensionId,
        BlockPos pos,
        ResourceLocation blockId,
        String sourceType,
        Optional<String> clusterId,
        boolean activeLootContainer,
        Optional<String> lootTableId,
        Optional<Integer> lootTier,
        Map<String, String> metadata) {
    public RaidContainerEntry {
        sourceType = sourceType == null || sourceType.isBlank() ? "scanned" : sourceType;
        clusterId = clusterId == null ? Optional.empty() : clusterId;
        lootTableId = lootTableId == null ? Optional.empty() : lootTableId;
        lootTier = lootTier == null ? Optional.empty() : lootTier;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public RaidContainerEntry withSelection(String clusterId, boolean active, String lootTableId, int lootTier) {
        return new RaidContainerEntry(dimensionId, pos, blockId, sourceType, Optional.of(clusterId), active, Optional.of(lootTableId), Optional.of(lootTier), metadata);
    }
}
