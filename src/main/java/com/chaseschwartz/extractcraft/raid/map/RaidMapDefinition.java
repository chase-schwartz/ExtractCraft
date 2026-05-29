package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RaidMapDefinition(String id, String name, ResourceKey<Level> dimension, RaidPlatform platform, Vec3 playerSpawn,
        float playerSpawnYaw, float playerSpawnPitch, List<RaidLootChest> lootChests, List<RaidLootContainer> lootContainers,
        List<RaidMobSpawn> mobSpawns, List<RaidExtractionZone> extractionZones, int raidDurationTicks, RaidMapSource source, boolean renderExtractionMarkers) {
    public RaidMapDefinition {
        lootChests = List.copyOf(lootChests);
        lootContainers = List.copyOf(lootContainers);
        mobSpawns = List.copyOf(mobSpawns);
        extractionZones = List.copyOf(extractionZones);
    }
}
