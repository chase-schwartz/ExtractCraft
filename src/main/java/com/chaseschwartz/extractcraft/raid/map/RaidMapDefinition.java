package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RaidMapDefinition(String id, String name, ResourceKey<Level> dimension, RaidPlatform platform, Vec3 playerSpawn,
        List<RaidLootChest> lootChests, List<RaidMobSpawn> mobSpawns, List<RaidExtractionZone> extractionZones, int raidDurationTicks,
        Optional<RaidStructurePlacement> structurePlacement, RaidDevBounds cleanupBounds, boolean generatePlatform) {
    public RaidMapDefinition {
        lootChests = List.copyOf(lootChests);
        mobSpawns = List.copyOf(mobSpawns);
        extractionZones = List.copyOf(extractionZones);
        structurePlacement = structurePlacement == null ? Optional.empty() : structurePlacement;
    }
}
