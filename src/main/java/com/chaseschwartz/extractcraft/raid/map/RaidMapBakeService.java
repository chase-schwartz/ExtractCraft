package com.chaseschwartz.extractcraft.raid.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class RaidMapBakeService {
    private static final String SUPPORTED_MAP_ID = "chaos_city";
    private static final int BLOCKS_PER_TICK = 10_000;
    private static BakeJob activeJob;

    public static StartResult startBake(MinecraftServer server, RaidMapDefinition raidMap, boolean force) {
        if (!SUPPORTED_MAP_ID.equals(raidMap.id())) {
            return new StartResult(false, "Map '" + raidMap.id() + "' does not have a bake/import definition yet.");
        }
        if (activeJob != null) {
            return new StartResult(false, "A raid map bake is already running: " + activeJob.progressMessage());
        }
        if (isBaked(raidMap.id()) && !force) {
            return new StartResult(false, "Raid map '" + raidMap.id() + "' is already marked baked. Use /raidmap bake " + raidMap.id() + " force to overwrite target blocks.");
        }

        ServerLevel sourceLevel = server.getLevel(Level.OVERWORLD);
        ServerLevel targetLevel = server.getLevel(raidMap.dimension());
        if (sourceLevel == null) {
            return new StartResult(false, "Source Overworld is unavailable. Open the test9 save before baking chaos_city.");
        }
        if (targetLevel == null) {
            return new StartResult(false, "Target raid dimension is unavailable: " + raidMap.dimension().location() + ". Restart the game/server after adding the dimension data.");
        }

        activeJob = new BakeJob(raidMap, sourceLevel, targetLevel, force);
        ExtractCraft.LOGGER.info("Started bake for {} from current Overworld source bounds {} into {} target bounds {} (force={})",
                raidMap.id(), RaidMaps.CHAOS_CITY_SOURCE_BOUNDS, raidMap.dimension().location(), RaidMaps.CHAOS_CITY_BOUNDS, force);
        return new StartResult(true, "Started baking " + raidMap.id() + " from the current Overworld. Run /raidmap bakestatus for progress.");
    }

    public static boolean isBaked(String mapId) {
        return Files.exists(bakeMetadataPath(mapId));
    }

    public static String statusMessage() {
        if (activeJob == null) {
            return "No raid map bake is currently running.";
        }
        return activeJob.progressMessage();
    }

    @SubscribeEvent
    public void onServerPostTick(ServerTickEvent.Post event) {
        if (activeJob == null) {
            return;
        }

        if (activeJob.tick()) {
            try {
                writeBakeMetadata(activeJob);
                ExtractCraft.LOGGER.info("Completed raid map bake: {}", activeJob.progressMessage());
            } catch (IOException exception) {
                ExtractCraft.LOGGER.error("Completed raid map bake but failed to write metadata for {}", activeJob.raidMap.id(), exception);
            }
            activeJob = null;
        }
    }

    private static void writeBakeMetadata(BakeJob job) throws IOException {
        Path path = bakeMetadataPath(job.raidMap.id());
        Files.createDirectories(path.getParent());

        JsonObject root = new JsonObject();
        root.addProperty("mapId", job.raidMap.id());
        root.addProperty("sourceDimension", Level.OVERWORLD.location().toString());
        root.addProperty("targetDimension", job.raidMap.dimension().location().toString());
        root.addProperty("scannedBlocks", job.scannedBlocks);
        root.addProperty("copiedBlocks", job.copiedBlocks);
        root.addProperty("copiedBlockEntities", job.copiedBlockEntities);
        root.addProperty("skippedBlockEntities", job.skippedBlockEntities);
        root.addProperty("force", job.force);
        root.add("sourceBounds", boundsToJson(RaidMaps.CHAOS_CITY_SOURCE_BOUNDS));
        root.add("targetBounds", boundsToJson(RaidMaps.CHAOS_CITY_BOUNDS));
        Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
    }

    private static JsonObject boundsToJson(RaidDevBounds bounds) {
        JsonObject json = new JsonObject();
        json.addProperty("minX", bounds.minX());
        json.addProperty("maxX", bounds.maxX());
        json.addProperty("minY", bounds.minY());
        json.addProperty("maxY", bounds.maxY());
        json.addProperty("minZ", bounds.minZ());
        json.addProperty("maxZ", bounds.maxZ());
        return json;
    }

    private static Path bakeMetadataPath(String mapId) {
        return FMLPaths.GAMEDIR.get().resolve("extractcraft").resolve("raid_maps").resolve(mapId + "_bake.json");
    }

    public record StartResult(boolean started, String message) {
    }

    private static class BakeJob {
        private final RaidMapDefinition raidMap;
        private final ServerLevel sourceLevel;
        private final ServerLevel targetLevel;
        private final boolean force;
        private final long totalBlocks;
        private final BlockPos.MutableBlockPos sourcePos = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
        private int currentX = RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minX();
        private int currentY = RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minY();
        private int currentZ = RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minZ();
        private long scannedBlocks;
        private long copiedBlocks;
        private long copiedBlockEntities;
        private long skippedBlockEntities;
        private long lastLogScanned;

        private BakeJob(RaidMapDefinition raidMap, ServerLevel sourceLevel, ServerLevel targetLevel, boolean force) {
            this.raidMap = raidMap;
            this.sourceLevel = sourceLevel;
            this.targetLevel = targetLevel;
            this.force = force;
            this.totalBlocks = (long) (RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.maxX() - RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minX() + 1)
                    * (RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.maxY() - RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minY() + 1)
                    * (RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.maxZ() - RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minZ() + 1);
        }

        private boolean tick() {
            int processedThisTick = 0;
            while (processedThisTick < BLOCKS_PER_TICK && !isComplete()) {
                copyCurrentBlock();
                advance();
                scannedBlocks++;
                processedThisTick++;
            }

            if (scannedBlocks - lastLogScanned >= 1_000_000 || isComplete()) {
                lastLogScanned = scannedBlocks;
                ExtractCraft.LOGGER.info(progressMessage());
                for (var player : targetLevel.getServer().getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal(progressMessage()));
                }
            }

            return isComplete();
        }

        private void copyCurrentBlock() {
            RaidDevBounds sourceBounds = RaidMaps.CHAOS_CITY_SOURCE_BOUNDS;
            RaidDevBounds targetBounds = RaidMaps.CHAOS_CITY_BOUNDS;
            sourcePos.set(currentX, currentY, currentZ);
            targetPos.set(
                    targetBounds.minX() + (currentX - sourceBounds.minX()),
                    targetBounds.minY() + (currentY - sourceBounds.minY()),
                    targetBounds.minZ() + (currentZ - sourceBounds.minZ()));

            BlockState sourceState = sourceLevel.getBlockState(sourcePos);
            if (!force && sourceState.isAir()) {
                return;
            }

            targetLevel.setBlock(targetPos, sourceState, 3);
            copiedBlocks++;
            if (sourceState.isAir()) {
                return;
            }

            BlockEntity sourceBlockEntity = sourceLevel.getBlockEntity(sourcePos);
            if (sourceBlockEntity == null) {
                return;
            }

            try {
                CompoundTag tag = sourceBlockEntity.saveWithFullMetadata(sourceLevel.registryAccess());
                tag.putInt("x", targetPos.getX());
                tag.putInt("y", targetPos.getY());
                tag.putInt("z", targetPos.getZ());
                BlockEntity targetBlockEntity = BlockEntity.loadStatic(targetPos.immutable(), sourceState, tag, targetLevel.registryAccess());
                if (targetBlockEntity == null) {
                    skippedBlockEntities++;
                    targetLevel.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                    return;
                }

                targetLevel.setBlockEntity(targetBlockEntity);
                targetBlockEntity.setChanged();
                copiedBlockEntities++;
            } catch (RuntimeException exception) {
                skippedBlockEntities++;
                ExtractCraft.LOGGER.warn("Skipped block entity while baking {} from {}, {}, {} to {}, {}, {}",
                        raidMap.id(),
                        sourcePos.getX(),
                        sourcePos.getY(),
                        sourcePos.getZ(),
                        targetPos.getX(),
                        targetPos.getY(),
                        targetPos.getZ(),
                        exception);
            }
        }

        private void advance() {
            currentZ++;
            if (currentZ <= RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.maxZ()) {
                return;
            }
            currentZ = RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minZ();
            currentX++;
            if (currentX <= RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.maxX()) {
                return;
            }
            currentX = RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.minX();
            currentY++;
        }

        private boolean isComplete() {
            return currentY > RaidMaps.CHAOS_CITY_SOURCE_BOUNDS.maxY();
        }

        private String progressMessage() {
            double percent = totalBlocks == 0 ? 100.0D : (scannedBlocks * 100.0D / totalBlocks);
            return String.format("Baking %s: %.2f%% (%d/%d scanned, %d copied, %d block entities, %d skipped block entities)",
                    raidMap.id(),
                    percent,
                    scannedBlocks,
                    totalBlocks,
                    copiedBlocks,
                    copiedBlockEntities,
                    skippedBlockEntities);
        }
    }
}
