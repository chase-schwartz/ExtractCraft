package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RaidMaps {
    public static final RaidMapDefinition TEST_RAID = new RaidMapDefinition(
            "test_raid",
            "Test Raid",
            Level.OVERWORLD,
            new RaidPlatform(-8, 8, 99, -8, 8, 100, 103),
            new Vec3(0.5D, 100.0D, -6.5D),
            List.of(
                    new RaidLootChest(
                            new BlockPos(0, 100, 0),
                            List.of(
                                    new RaidLootItem(Items.BREAD, 4),
                                    new RaidLootItem(Items.IRON_INGOT, 2),
                                    new RaidLootItem(Items.EMERALD, 1),
                                    new RaidLootItem(Items.DIAMOND, 1))),
                    new RaidLootChest(
                            new BlockPos(0, 100, 4),
                            List.of(
                                    new RaidLootItem(Items.ARROW, 8),
                                    new RaidLootItem(Items.BOW, 1),
                                    new RaidLootItem(Items.APPLE, 2),
                                    new RaidLootItem(Items.GOLD_INGOT, 1)))),
            List.of(
                    new RaidMobSpawn(EntityType.ZOMBIE, 4.5D, 100.0D, 0.5D),
                    new RaidMobSpawn(EntityType.SKELETON, -4.5D, 100.0D, 0.5D)),
            List.of(
                    new RaidExtractionZone(-2.0D, 2.0D, 99.0D, 101.0D, 5.0D, 7.0D),
                    new RaidExtractionZone(-7.0D, -5.0D, 99.0D, 101.0D, -2.0D, 2.0D)),
            20 * 60);

    private RaidMaps() {
    }
}
