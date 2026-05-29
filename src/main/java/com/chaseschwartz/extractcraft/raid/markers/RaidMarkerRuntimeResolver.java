package com.chaseschwartz.extractcraft.raid.markers;

import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidExtractionZone;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMobSpawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class RaidMarkerRuntimeResolver {
    private static final String ROCKET_PLATFORM_MAP_ID = "rocket_platform";

    private RaidMarkerRuntimeResolver() {
    }

    public static RaidMapDefinition resolve(RaidMapDefinition raidMap) {
        if (!ROCKET_PLATFORM_MAP_ID.equals(raidMap.id())) {
            return raidMap;
        }

        return RaidMarkerService.loadSaved(raidMap.id())
                .map(layout -> applyRocketPlatformOverrides(raidMap, layout))
                .orElse(raidMap);
    }

    private static RaidMapDefinition applyRocketPlatformOverrides(RaidMapDefinition raidMap, RaidMarkerLayout layout) {
        List<RaidMarker> spawnMarkers = markersOfType(layout, RaidMarkerType.PLAYER_SPAWN);
        List<RaidMarker> extractionMarkers = markersOfType(layout, RaidMarkerType.EXTRACTION);
        List<RaidMarker> mobSpawnMarkers = markersOfType(layout, RaidMarkerType.MOB_SPAWN);

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

        if (spawnMarkers.isEmpty() && extractionMarkers.isEmpty() && mobSpawnMarkers.isEmpty()) {
            ExtractCraft.LOGGER.info("Saved marker layout for {} did not contain PLAYER_SPAWN, EXTRACTION, or MOB_SPAWN markers; using hardcoded values", raidMap.id());
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
}
