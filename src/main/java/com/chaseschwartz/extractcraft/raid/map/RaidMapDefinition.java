package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RaidMapDefinition(String id, String name, ResourceKey<Level> dimension, RaidPlatform platform, Vec3 playerSpawn,
        BlockPos chestPos, List<RaidMobSpawn> mobSpawns, RaidExtractionZone extractionZone, int raidDurationTicks) {
    public RaidMapDefinition {
        mobSpawns = List.copyOf(mobSpawns);
    }
}
