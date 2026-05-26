package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RaidMaps {
    public static final RaidMapDefinition TEST_RAID = new RaidMapDefinition(
            "test",
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

    public static final RaidMapDefinition COMPACT_RAID = new RaidMapDefinition(
            "compact",
            "Compact Test Raid",
            Level.OVERWORLD,
            new RaidPlatform(-6, 6, 99, -6, 6, 100, 103),
            new Vec3(0.5D, 100.0D, -4.5D),
            List.of(
                    new RaidLootChest(
                            new BlockPos(0, 100, 0),
                            List.of(
                                    new RaidLootItem(Items.BREAD, 4),
                                    new RaidLootItem(Items.IRON_INGOT, 1),
                                    new RaidLootItem(Items.EMERALD, 1))),
                    new RaidLootChest(
                            new BlockPos(3, 100, 2),
                            List.of(
                                    new RaidLootItem(Items.ARROW, 8),
                                    new RaidLootItem(Items.APPLE, 2)))),
            List.of(
                    new RaidMobSpawn(EntityType.ZOMBIE, 3.5D, 100.0D, 0.5D),
                    new RaidMobSpawn(EntityType.SKELETON, -3.5D, 100.0D, 0.5D)),
            List.of(
                    new RaidExtractionZone(-1.0D, 1.0D, 99.0D, 101.0D, 4.0D, 5.0D),
                    new RaidExtractionZone(-5.0D, -4.0D, 99.0D, 101.0D, -1.0D, 1.0D)),
            20 * 60);

    private static final Map<String, RaidMapDefinition> MAPS_BY_ID = List.of(TEST_RAID, COMPACT_RAID).stream()
            .collect(Collectors.toUnmodifiableMap(RaidMapDefinition::id, raidMap -> raidMap));

    private RaidMaps() {
    }

    public static RaidMapDefinition defaultMap() {
        return TEST_RAID;
    }

    public static Optional<RaidMapDefinition> byId(String id) {
        return Optional.ofNullable(MAPS_BY_ID.get(id));
    }

    public static String availableMapIds() {
        return String.join(", ", MAPS_BY_ID.keySet());
    }
}
