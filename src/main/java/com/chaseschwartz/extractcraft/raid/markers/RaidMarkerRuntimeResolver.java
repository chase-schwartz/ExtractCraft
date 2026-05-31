package com.chaseschwartz.extractcraft.raid.markers;

import java.util.List;
import java.util.Set;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidExtractionZone;
import com.chaseschwartz.extractcraft.raid.map.RaidLootContainer;
import com.chaseschwartz.extractcraft.raid.map.RaidLootItem;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMobSpawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class RaidMarkerRuntimeResolver {
    private static final Set<String> MARKER_RUNTIME_MAP_IDS = Set.of("rocket_platform", "chaos_city");

    private RaidMarkerRuntimeResolver() {
    }

    public static RaidMapDefinition resolve(RaidMapDefinition raidMap) {
        if (!MARKER_RUNTIME_MAP_IDS.contains(raidMap.id())) {
            return raidMap;
        }

        return RaidMarkerService.loadSaved(raidMap.id())
                .map(layout -> applyMarkerOverrides(raidMap, layout))
                .orElse(raidMap);
    }

    private static RaidMapDefinition applyMarkerOverrides(RaidMapDefinition raidMap, RaidMarkerLayout layout) {
        List<RaidMarker> spawnMarkers = markersOfType(layout, RaidMarkerType.PLAYER_SPAWN);
        List<RaidMarker> extractionMarkers = markersOfType(layout, RaidMarkerType.EXTRACTION);
        List<RaidMarker> mobSpawnMarkers = markersOfType(layout, RaidMarkerType.MOB_SPAWN);
        List<RaidMarker> lootMarkers = markersOfType(layout, RaidMarkerType.LOOT);
        List<RaidMarker> rareLootMarkers = markersOfType(layout, RaidMarkerType.RARE_LOOT);

        Vec3 playerSpawn = raidMap.playerSpawn();
        if (!spawnMarkers.isEmpty()) {
            BlockPos spawnPos = spawnMarkers.getFirst().absolutePos();
            playerSpawn = new Vec3(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
            ExtractCraft.LOGGER.info("Applied PLAYER_SPAWN marker override for {} at {}, {}, {}",
                    raidMap.id(),
                    playerSpawn.x,
                    playerSpawn.y,
                    playerSpawn.z);
            if (spawnMarkers.size() > 1) {
                ExtractCraft.LOGGER.info("Ignored {} extra PLAYER_SPAWN markers for {}", spawnMarkers.size() - 1, raidMap.id());
            }
        }

        List<RaidExtractionZone> extractionZones = raidMap.extractionZones();
        if (!extractionMarkers.isEmpty()) {
            extractionZones = extractionMarkers.stream()
                    .map(marker -> extractionZoneFromMarker(marker.absolutePos()))
                    .toList();
            ExtractCraft.LOGGER.info("Applied {} EXTRACTION marker overrides for {}", extractionZones.size(), raidMap.id());
        }

        List<RaidMobSpawn> mobSpawns = raidMap.mobSpawns();
        if (!mobSpawnMarkers.isEmpty()) {
            mobSpawns = mobSpawnMarkers.stream()
                    .map(RaidMarkerRuntimeResolver::mobSpawnFromMarker)
                    .toList();
            ExtractCraft.LOGGER.info("Found {} MOB_SPAWN markers for {}; applied marker-derived zombie spawns", mobSpawns.size(), raidMap.id());
            for (RaidMobSpawn mobSpawn : mobSpawns) {
                ExtractCraft.LOGGER.info("Resolved MOB_SPAWN marker to zombie spawn at {}, {}, {}", mobSpawn.x(), mobSpawn.y(), mobSpawn.z());
            }
        }

        List<RaidLootContainer> lootContainers = raidMap.lootContainers();
        if (!lootMarkers.isEmpty() || !rareLootMarkers.isEmpty()) {
            lootContainers = new java.util.ArrayList<>(raidMap.lootContainers());
            lootMarkers.stream()
                    .map(marker -> lootContainerFromMarker(marker, commonLoot()))
                    .forEach(lootContainers::add);
            rareLootMarkers.stream()
                    .map(marker -> lootContainerFromMarker(marker, rareLoot()))
                    .forEach(lootContainers::add);
            ExtractCraft.LOGGER.info("Applied {} LOOT marker containers and {} RARE_LOOT marker containers for {}",
                    lootMarkers.size(),
                    rareLootMarkers.size(),
                    raidMap.id());
        }

        if (spawnMarkers.isEmpty() && extractionMarkers.isEmpty() && mobSpawnMarkers.isEmpty() && lootMarkers.isEmpty() && rareLootMarkers.isEmpty()) {
            ExtractCraft.LOGGER.info(
                    "Saved marker layout for {} did not contain PLAYER_SPAWN, EXTRACTION, MOB_SPAWN, LOOT, or RARE_LOOT markers; using hardcoded values",
                    raidMap.id());
            return raidMap;
        }

        return new RaidMapDefinition(
                raidMap.id(),
                raidMap.name(),
                raidMap.dimension(),
                raidMap.platform(),
                playerSpawn,
                raidMap.playerSpawnYaw(),
                raidMap.playerSpawnPitch(),
                raidMap.lootChests(),
                lootContainers,
                mobSpawns,
                extractionZones,
                raidMap.raidDurationTicks(),
                raidMap.source(),
                raidMap.renderExtractionMarkers());
    }

    private static List<RaidMarker> markersOfType(RaidMarkerLayout layout, RaidMarkerType markerType) {
        return layout.markers().stream()
                .filter(marker -> marker.type() == markerType)
                .toList();
    }

    private static RaidExtractionZone extractionZoneFromMarker(BlockPos markerPos) {
        return new RaidExtractionZone(
                markerPos.getX() - 1.0D,
                markerPos.getX() + 1.0D,
                markerPos.getY() - 1.0D,
                markerPos.getY() + 1.0D,
                markerPos.getZ() - 1.0D,
                markerPos.getZ() + 1.0D);
    }

    private static RaidMobSpawn mobSpawnFromMarker(RaidMarker marker) {
        BlockPos markerPos = marker.absolutePos();
        return new RaidMobSpawn(EntityType.ZOMBIE, markerPos.getX() + 0.5D, markerPos.getY() + 1.0D, markerPos.getZ() + 0.5D);
    }

    private static RaidLootContainer lootContainerFromMarker(RaidMarker marker, List<RaidLootItem> loot) {
        return new RaidLootContainer(marker.absolutePos(), Blocks.BARREL, loot);
    }

    private static List<RaidLootItem> commonLoot() {
        return List.of(
                new RaidLootItem(Items.BREAD, 4),
                new RaidLootItem(Items.ARROW, 8),
                new RaidLootItem(Items.IRON_INGOT, 2),
                new RaidLootItem(Items.APPLE, 2));
    }

    private static List<RaidLootItem> rareLoot() {
        return List.of(
                new RaidLootItem(Items.DIAMOND, 1),
                new RaidLootItem(Items.EMERALD, 2),
                new RaidLootItem(Items.GOLDEN_APPLE, 1),
                new RaidLootItem(Items.IRON_INGOT, 6));
    }
}
