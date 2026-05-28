package com.chaseschwartz.extractcraft.raid.map;

import java.util.Optional;

import net.minecraft.core.BlockPos;

public record RaidMapSource(RaidMapSourceType type, Optional<RaidStructurePlacement> structurePlacement, Optional<RaidDevBounds> cleanupBounds,
        BlockPos layoutOrigin) {
    public RaidMapSource {
        structurePlacement = structurePlacement == null ? Optional.empty() : structurePlacement;
        cleanupBounds = cleanupBounds == null ? Optional.empty() : cleanupBounds;
    }

    public static RaidMapSource generatedPlatform(RaidDevBounds cleanupBounds, BlockPos layoutOrigin) {
        return new RaidMapSource(RaidMapSourceType.GENERATED_PLATFORM, Optional.empty(), Optional.of(cleanupBounds), layoutOrigin);
    }

    public static RaidMapSource structureTemplate(RaidStructurePlacement structurePlacement, RaidDevBounds cleanupBounds) {
        return new RaidMapSource(RaidMapSourceType.STRUCTURE_TEMPLATE, Optional.of(structurePlacement), Optional.of(cleanupBounds), structurePlacement.origin());
    }

    public static RaidMapSource existingWorldArea(BlockPos layoutOrigin, Optional<RaidDevBounds> cleanupBounds) {
        return new RaidMapSource(RaidMapSourceType.EXISTING_WORLD_AREA, Optional.empty(), cleanupBounds, layoutOrigin);
    }

    public boolean shouldClearTerrainBlocks() {
        return type == RaidMapSourceType.GENERATED_PLATFORM || type == RaidMapSourceType.STRUCTURE_TEMPLATE;
    }

    public boolean shouldGeneratePlatform() {
        return type == RaidMapSourceType.GENERATED_PLATFORM;
    }
}
