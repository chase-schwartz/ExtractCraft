package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RaidMaps {
    public static final RaidMapDefinition TEST_RAID = new RaidMapDefinition(
            "test_raid",
            "Test Raid",
            Level.OVERWORLD,
            new RaidPlatform(-8, 8, 99, -8, 8, 100, 103),
            new Vec3(0.5D, 100.0D, -6.5D),
            new BlockPos(0, 100, 0),
            List.of(
                    new RaidMobSpawn(EntityType.ZOMBIE, 4.5D, 100.0D, 0.5D),
                    new RaidMobSpawn(EntityType.SKELETON, -4.5D, 100.0D, 0.5D)),
            new RaidExtractionZone(-2.0D, 2.0D, 99.0D, 101.0D, 5.0D, 7.0D),
            20 * 60);

    private RaidMaps() {
    }
}
