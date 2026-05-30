package com.chaseschwartz.extractcraft.raid.markers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidDevBounds;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
        Path directory = markerDirectory();
        Files.createDirectories(directory);
        Path file = directory.resolve(layout.mapId() + "_markers.json");
        Files.writeString(file, layout.toJson(), StandardCharsets.UTF_8);
        return file;
    }

    public static Optional<RaidMarkerLayout> loadSaved(String mapId) {
        Path file = markerFile(mapId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String layoutMapId = getString(root, "mapId", mapId);
            BlockPos layoutOrigin = readBlockPos(root.getAsJsonObject("layoutOrigin"));
            JsonArray markerElements = root.getAsJsonArray("markers");
            List<RaidMarker> markers = new ArrayList<>();
            if (markerElements != null) {
                for (JsonElement markerElement : markerElements) {
                    if (!markerElement.isJsonObject()) {
                        continue;
                    }

                    JsonObject markerObject = markerElement.getAsJsonObject();
                    RaidMarkerType type = RaidMarkerType.bySerializedName(getString(markerObject, "type", "")).orElse(null);
                    JsonObject relativeObject = markerObject.getAsJsonObject("relative");
                    if (type == null || relativeObject == null) {
                        continue;
                    }

                    BlockPos relativePos = readBlockPos(relativeObject);
                    markers.add(new RaidMarker(type, layoutOrigin.offset(relativePos), relativePos));
                }
            }

            ExtractCraft.LOGGER.info("Loaded {} saved raid markers for map {} from {}", markers.size(), layoutMapId, file);
            return Optional.of(new RaidMarkerLayout(layoutMapId, layoutOrigin, markers));
        } catch (RuntimeException | IOException exception) {
            ExtractCraft.LOGGER.warn("Unable to load saved raid marker layout for map {} from {}; falling back to hardcoded map values", mapId, file, exception);
            return Optional.empty();
        }
    }

    public static Path markerFile(String mapId) {
        return markerDirectory().resolve(mapId + "_markers.json");
    }

    public static RenderResult renderSaved(ServerLevel level, RaidMarkerLayout layout) {
        List<RaidMarker> renderedMarkers = new ArrayList<>();
        List<RaidMarker> skippedMarkers = new ArrayList<>();

        for (RaidMarker marker : layout.markers()) {
            Block currentBlock = level.getBlockState(marker.absolutePos()).getBlock();
            if (!isSafeRenderReplaceBlock(currentBlock)) {
                skippedMarkers.add(marker);
                ExtractCraft.LOGGER.warn("Skipped rendering raid marker {} at {}, {}, {} because current block {} is not safe to overwrite",
                        marker.type().serializedName(),
                        marker.absolutePos().getX(),
                        marker.absolutePos().getY(),
                        marker.absolutePos().getZ(),
                        currentBlock);
                continue;
            }

            level.setBlock(marker.absolutePos(), marker.type().block().defaultBlockState(), 3);
            renderedMarkers.add(marker);
            ExtractCraft.LOGGER.info("Rendered raid marker {} at {}, {}, {}",
                    marker.type().serializedName(),
                    marker.absolutePos().getX(),
                    marker.absolutePos().getY(),
                    marker.absolutePos().getZ());
        }

        ExtractCraft.LOGGER.info("Rendered {} raid markers and skipped {} markers for map {}",
                renderedMarkers.size(),
                skippedMarkers.size(),
                layout.mapId());
        return new RenderResult(new RaidMarkerLayout(layout.mapId(), layout.layoutOrigin(), renderedMarkers), skippedMarkers);
    }

    public static RaidMarkerLayout clear(ServerLevel level, RaidMapDefinition raidMap) {
        RaidDevBounds bounds = raidMap.source().authoringBounds()
                .orElseThrow(() -> new IllegalStateException("Raid map has no marker authoring bounds: " + raidMap.id()));
        BlockPos layoutOrigin = raidMap.source().layoutOrigin();
        List<RaidMarker> clearedMarkers = new ArrayList<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    position.set(x, y, z);
                    Block block = level.getBlockState(position).getBlock();
                    RaidMarkerType.byBlock(block).ifPresent(type -> {
                        BlockPos absolutePos = position.immutable();
                        clearedMarkers.add(new RaidMarker(type, absolutePos, absolutePos.subtract(layoutOrigin)));
                        level.setBlock(absolutePos, Blocks.AIR.defaultBlockState(), 3);
                        ExtractCraft.LOGGER.info("Cleared raid marker {} at {}, {}, {}",
                                type.serializedName(),
                                absolutePos.getX(),
                                absolutePos.getY(),
                                absolutePos.getZ());
                    });
                }
            }
        }

        ExtractCraft.LOGGER.info("Cleared {} raid markers for map {} in {} from x {}..{}, y {}..{}, z {}..{}",
                clearedMarkers.size(),
                raidMap.id(),
                level.dimension().location(),
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                bounds.minZ(),
                bounds.maxZ());
        return new RaidMarkerLayout(raidMap.id(), layoutOrigin, clearedMarkers);
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

    private static boolean isSafeRenderReplaceBlock(Block block) {
        return block == Blocks.AIR
                || block == Blocks.BARREL
                || block == Blocks.CHEST
                || RaidMarkerType.byBlock(block).isPresent();
    }

    private static Path markerDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("extractcraft").resolve("raid_markers");
    }

    private static BlockPos readBlockPos(JsonObject jsonObject) {
        return new BlockPos(jsonObject.get("x").getAsInt(), jsonObject.get("y").getAsInt(), jsonObject.get("z").getAsInt());
    }

    private static String getString(JsonObject jsonObject, String key, String fallback) {
        JsonElement element = jsonObject.get(key);
        return element == null ? fallback : element.getAsString();
    }

    public record RenderResult(RaidMarkerLayout renderedLayout, List<RaidMarker> skippedMarkers) {
        public RenderResult {
            skippedMarkers = List.copyOf(skippedMarkers);
        }
    }
}
