package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

public class RaidMaps {
    private static final RaidDevBounds DEV_CLEANUP_BOUNDS = new RaidDevBounds(-8, 8, 99, 103, -8, 8);
    private static final RaidDevBounds ROCKET_PLATFORM_DROPS_BOUNDS = new RaidDevBounds(1019, 1063, 101, 116, -305, -283);
    private static final RaidDevBounds ROCKET_PLATFORM_AUTHORING_BOUNDS = new RaidDevBounds(800, 1281, 40, 225, -484, 33);

    public static final RaidMapDefinition TEST_RAID = new RaidMapDefinition(
            "test",
            "Test Raid",
            Level.OVERWORLD,
            new RaidPlatform(-8, 8, 99, -8, 8, 100, 103),
            new Vec3(0.5D, 100.0D, -6.5D),
            0.0F,
            0.0F,
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
            20 * 60,
            RaidMapSource.generatedPlatform(DEV_CLEANUP_BOUNDS, new BlockPos(0, 99, 0)),
            true);

    public static final RaidMapDefinition COMPACT_RAID = new RaidMapDefinition(
            "compact",
            "Compact Test Raid",
            Level.OVERWORLD,
            new RaidPlatform(-6, 6, 99, -6, 6, 100, 103),
            new Vec3(0.5D, 100.0D, -4.5D),
            0.0F,
            0.0F,
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
            20 * 60,
            RaidMapSource.generatedPlatform(DEV_CLEANUP_BOUNDS, new BlockPos(0, 99, 0)),
            true);

    public static final RaidMapDefinition CITY_BLOCK = new RaidMapDefinition(
            "city_block",
            "City Block 32",
            Level.OVERWORLD,
            new RaidPlatform(-16, 15, 99, -16, 15, 100, 103),
            new Vec3(0.5D, 100.0D, -12.5D),
            0.0F,
            0.0F,
            List.of(
                    new RaidLootChest(
                            new BlockPos(0, 100, 0),
                            List.of(
                                    new RaidLootItem(Items.BREAD, 4),
                                    new RaidLootItem(Items.IRON_INGOT, 2),
                                    new RaidLootItem(Items.EMERALD, 1))),
                    new RaidLootChest(
                            new BlockPos(8, 100, 6),
                            List.of(
                                    new RaidLootItem(Items.ARROW, 12),
                                    new RaidLootItem(Items.BOW, 1),
                                    new RaidLootItem(Items.APPLE, 2)))),
            List.of(
                    new RaidMobSpawn(EntityType.ZOMBIE, 6.5D, 100.0D, 0.5D),
                    new RaidMobSpawn(EntityType.SKELETON, -6.5D, 100.0D, 2.5D)),
            List.of(
                    new RaidExtractionZone(-2.0D, 2.0D, 99.0D, 101.0D, 11.0D, 13.0D),
                    new RaidExtractionZone(-13.0D, -11.0D, 99.0D, 101.0D, -2.0D, 2.0D)),
            20 * 60,
            RaidMapSource.structureTemplate(new RaidStructurePlacement(
                    ResourceLocation.fromNamespaceAndPath("extractcraft", "city/city_block_32"),
                    new BlockPos(-16, 99, -16),
                    Rotation.NONE,
                    Mirror.NONE,
                    false),
                    new RaidDevBounds(-18, 18, 90, 150, -18, 18)),
            true);

    public static final RaidMapDefinition ROCKET_PLATFORM = new RaidMapDefinition(
            "rocket_platform",
            "Rocket Platform",
            Level.OVERWORLD,
            new RaidPlatform(1019, 1063, 104, -305, -283, 105, 116),
            new Vec3(1060.0D, 104.0D, -349.0D),
            0.0F,
            0.0F,
            List.of(),
            List.of(),
            List.of(
                    new RaidExtractionZone(1231.0D, 1233.0D, 103.0D, 106.0D, -63.0D, -61.0D)),
            20 * 60,
            RaidMapSource.existingWorldArea(new BlockPos(820, 40, 75), Optional.of(ROCKET_PLATFORM_DROPS_BOUNDS), Optional.of(ROCKET_PLATFORM_AUTHORING_BOUNDS)),
            false);

    private static final Map<String, RaidMapDefinition> MAPS_BY_ID = List.of(TEST_RAID, COMPACT_RAID, CITY_BLOCK, ROCKET_PLATFORM).stream()
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

    public static RaidDevBounds devCleanupBounds() {
        return DEV_CLEANUP_BOUNDS;
    }
}
