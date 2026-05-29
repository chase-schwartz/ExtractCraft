package com.chaseschwartz.extractcraft.raid.markers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidDevBounds;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;

public class RaidMarkerService {
    private RaidMarkerService() {
    }

    public static RaidMarkerLayout scan(ServerLevel level, RaidMapDefinition raidMap) {
        RaidDevBounds bounds = raidMap.source().authoringBounds()
                .orElseThrow(() -> new IllegalStateException("Raid map has no marker authoring bounds: " + raidMap.id()));
        BlockPos layoutOrigin = raidMap.source().layoutOrigin();
        List<RaidMarker> markers = new ArrayList<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    position.set(x, y, z);
                    Block block = level.getBlockState(position).getBlock();
                    RaidMarkerType.byBlock(block).ifPresent(type -> {
                        BlockPos absolutePos = position.immutable();
                        markers.add(new RaidMarker(type, absolutePos, absolutePos.subtract(layoutOrigin)));
                    });
                }
            }
        }

        RaidMarkerLayout layout = new RaidMarkerLayout(raidMap.id(), layoutOrigin, markers);
        logScan(level, raidMap, bounds, layout);
        return layout;
    }

    public static Path save(RaidMarkerLayout layout) throws IOException {
        Path directory = FMLPaths.GAMEDIR.get().resolve("extractcraft").resolve("raid_markers");
        Files.createDirectories(directory);
        Path file = directory.resolve(layout.mapId() + "_markers.json");
        Files.writeString(file, layout.toJson(), StandardCharsets.UTF_8);
        return file;
    }

    public static Map<RaidMarkerType, Long> countsByType(RaidMarkerLayout layout) {
        Map<RaidMarkerType, Long> counts = new EnumMap<>(RaidMarkerType.class);
        for (RaidMarkerType type : RaidMarkerType.values()) {
            counts.put(type, 0L);
        }
        for (RaidMarker marker : layout.markers()) {
            counts.put(marker.type(), counts.get(marker.type()) + 1L);
        }
        return counts;
    }

    private static void logScan(ServerLevel level, RaidMapDefinition raidMap, RaidDevBounds bounds, RaidMarkerLayout layout) {
        ExtractCraft.LOGGER.info("Scanned raid markers for map {} in {} from x {}..{}, y {}..{}, z {}..{} and found {} markers",
                raidMap.id(),
                level.dimension().location(),
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                bounds.minZ(),
                bounds.maxZ(),
                layout.markers().size());
        for (RaidMarker marker : layout.markers()) {
            ExtractCraft.LOGGER.info("Found raid marker {} at absolute {}, {}, {} relative {}, {}, {}",
                    marker.type().serializedName(),
                    marker.absolutePos().getX(),
                    marker.absolutePos().getY(),
                    marker.absolutePos().getZ(),
                    marker.relativePos().getX(),
                    marker.relativePos().getY(),
                    marker.relativePos().getZ());
        }
    }
}
