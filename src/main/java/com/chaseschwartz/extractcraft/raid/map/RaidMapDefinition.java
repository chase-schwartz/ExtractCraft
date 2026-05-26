package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RaidMapDefinition(String id, String name, ResourceKey<Level> dimension, RaidPlatform platform, Vec3 playerSpawn,
        List<RaidLootChest> lootChests, List<RaidMobSpawn> mobSpawns, List<RaidExtractionZone> extractionZones, int raidDurationTicks) {
    public RaidMapDefinition {
        lootChests = List.copyOf(lootChests);
        mobSpawns = List.copyOf(mobSpawns);
        extractionZones = List.copyOf(extractionZones);
    }
}
