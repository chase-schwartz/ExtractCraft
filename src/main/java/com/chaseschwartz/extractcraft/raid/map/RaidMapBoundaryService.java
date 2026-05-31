package com.chaseschwartz.extractcraft.raid.map;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class RaidMapBoundaryService {
    private static final String SUPPORTED_MAP_ID = "chaos_city";
    private static final int BLOCKS_PER_TICK = 20_000;
    private static final int VISIBLE_STRIP_WIDTH = 8;
    private static final int VISIBLE_MIN_Y = 0;
    private static final int VISIBLE_WALL_HEIGHT = 28;
    private static final int BARRIER_MIN_Y = 0;
    private static final int BARRIER_EXTRA_HEIGHT = 20;
    private static BoundaryJob activeJob;

    public static StartResult startBoundaryBuild(MinecraftServer server, RaidMapDefinition raidMap) {
        if (!SUPPORTED_MAP_ID.equals(raidMap.id())) {
            return new StartResult(false, "Map '" + raidMap.id() + "' does not have a generated boundary definition yet.");
        }
        if (activeJob != null) {
            return new StartResult(false, "A raid map boundary build is already running: " + activeJob.progressMessage());
        }
        if (!RaidMapBakeService.isBaked(raidMap.id())) {
            return new StartResult(false, "Raid map '" + raidMap.id() + "' is not baked yet. Run /raidmap bake " + raidMap.id() + " first.");
        }

        ServerLevel level = server.getLevel(raidMap.dimension());
        if (level == null) {
            return new StartResult(false, "Target raid dimension is unavailable: " + raidMap.dimension().location());
        }

        activeJob = new BoundaryJob(raidMap, level);
        ExtractCraft.LOGGER.info("Started boundary build for {} in {} using bounds {}", raidMap.id(), raidMap.dimension().location(), RaidMaps.CHAOS_CITY_BOUNDS);
        return new StartResult(true, "Started boundary build for " + raidMap.id() + ". Run /raidmap boundarystatus for progress.");
    }

    public static String statusMessage() {
        if (activeJob == null) {
            return "No raid map boundary build is currently running.";
        }
        return activeJob.progressMessage();
    }

    @SubscribeEvent
    public void onServerPostTick(ServerTickEvent.Post event) {
        if (activeJob == null) {
            return;
        }

        if (activeJob.tick()) {
            ExtractCraft.LOGGER.info("Completed raid map boundary build: {}", activeJob.progressMessage());
            activeJob = null;
        }
    }

    public record StartResult(boolean started, String message) {
    }

    private enum Stage {
        VISIBLE_PERIMETER,
        HARD_BARRIER,
        COMPLETE
    }

    private static class BoundaryJob {
        private final RaidMapDefinition raidMap;
        private final ServerLevel level;
        private final RaidDevBounds bounds = RaidMaps.CHAOS_CITY_BOUNDS;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private final long totalWork;
        private Stage stage = Stage.VISIBLE_PERIMETER;
        private int currentX;
        private int currentZ;
        private int currentY;
        private long processed;
        private long placedVisibleBlocks;
        private long placedBarrierBlocks;
        private long lastLogProcessed;

        private BoundaryJob(RaidMapDefinition raidMap, ServerLevel level) {
            this.raidMap = raidMap;
            this.level = level;
            this.currentX = bounds.minX();
            this.currentZ = bounds.minZ();
            this.currentY = VISIBLE_MIN_Y;
            this.totalWork = estimateVisibleWork() + estimateBarrierWork();
        }

        private boolean tick() {
            int processedThisTick = 0;
            while (processedThisTick < BLOCKS_PER_TICK && stage != Stage.COMPLETE) {
                processCurrent();
                processed++;
                processedThisTick++;
            }

            if (processed - lastLogProcessed >= 250_000 || stage == Stage.COMPLETE) {
                lastLogProcessed = processed;
                ExtractCraft.LOGGER.info(progressMessage());
                for (var player : level.getServer().getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal(progressMessage()));
                }
            }

            return stage == Stage.COMPLETE;
        }

        private void processCurrent() {
            if (stage == Stage.VISIBLE_PERIMETER) {
                processVisiblePerimeter();
                return;
            }

            if (stage == Stage.HARD_BARRIER) {
                processHardBarrier();
            }
        }

        private void processVisiblePerimeter() {
            int distanceToEdge = distanceToEdge(currentX, currentZ);
            BlockState state = visibleState(distanceToEdge, currentY, currentX, currentZ);
            if (state != null) {
                mutablePos.set(currentX, currentY, currentZ);
                level.setBlock(mutablePos, state, 3);
                placedVisibleBlocks++;
            }

            currentY++;
            if (currentY <= bounds.minY() + VISIBLE_WALL_HEIGHT) {
                return;
            }
            currentY = VISIBLE_MIN_Y;
            advanceVisibleColumn();
            if (stage == Stage.VISIBLE_PERIMETER) {
                return;
            }

            stage = Stage.HARD_BARRIER;
            currentX = bounds.minX();
            currentZ = bounds.minZ();
            currentY = BARRIER_MIN_Y;
        }

        private void processHardBarrier() {
            if (isOuterEdge(currentX, currentZ)) {
                mutablePos.set(currentX, currentY, currentZ);
                level.setBlock(mutablePos, Blocks.BARRIER.defaultBlockState(), 3);
                placedBarrierBlocks++;
            }

            currentY++;
            if (currentY <= bounds.maxY() + BARRIER_EXTRA_HEIGHT) {
                return;
            }
            currentY = BARRIER_MIN_Y;
            advanceBarrierColumn();
            if (stage == Stage.HARD_BARRIER) {
                return;
            }

            stage = Stage.COMPLETE;
        }

        private void advanceVisibleColumn() {
            do {
                currentZ++;
                if (currentZ <= bounds.maxZ()) {
                    continue;
                }
                currentZ = bounds.minZ();
                currentX++;
                if (currentX > bounds.maxX()) {
                    stage = Stage.HARD_BARRIER;
                    return;
                }
            } while (distanceToEdge(currentX, currentZ) >= VISIBLE_STRIP_WIDTH);
        }

        private void advanceBarrierColumn() {
            do {
                currentZ++;
                if (currentZ <= bounds.maxZ()) {
                    continue;
                }
                currentZ = bounds.minZ();
                currentX++;
                if (currentX > bounds.maxX()) {
                    stage = Stage.COMPLETE;
                    return;
                }
            } while (!isOuterEdge(currentX, currentZ));
        }

        private BlockState visibleState(int distanceToEdge, int y, int x, int z) {
            if (y < bounds.minY()) {
                return foundationState(distanceToEdge, y, x, z);
            }

            int relativeY = y - bounds.minY();
            if (distanceToEdge <= 2) {
                if (relativeY <= 21) {
                    int variant = positiveHash(x, y, z) % 5;
                    if (variant == 0) {
                        return Blocks.STONE_BRICKS.defaultBlockState();
                    }
                    if (variant == 1) {
                        return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                    }
                    if (variant == 2) {
                        return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                    }
                    return Blocks.GRAY_CONCRETE.defaultBlockState();
                }
                if (relativeY <= 27 && (relativeY % 2 == 0 || distanceToEdge == 1)) {
                    return Blocks.IRON_BARS.defaultBlockState();
                }
                return null;
            }

            int rubbleHeight = 1 + positiveHash(x, 17, z) % Math.max(1, 7 - distanceToEdge);
            if (relativeY <= rubbleHeight) {
                int variant = positiveHash(x, y, z) % 6;
                if (variant == 0) {
                    return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                }
                if (variant == 1) {
                    return Blocks.STONE_BRICKS.defaultBlockState();
                }
                if (variant == 2) {
                    return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                }
                return Blocks.GRAY_CONCRETE.defaultBlockState();
            }
            return null;
        }

        private BlockState foundationState(int distanceToEdge, int y, int x, int z) {
            if (distanceToEdge <= 2) {
                int variant = positiveHash(x, y, z) % 5;
                if (variant == 0) {
                    return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                }
                if (variant == 1) {
                    return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                }
                return Blocks.GRAY_CONCRETE.defaultBlockState();
            }

            if (distanceToEdge <= 4) {
                int variant = positiveHash(x, y, z) % 6;
                if (variant == 0) {
                    return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                }
                if (variant == 1) {
                    return Blocks.STONE_BRICKS.defaultBlockState();
                }
                return Blocks.GRAY_CONCRETE.defaultBlockState();
            }

            int rubbleDepth = (VISIBLE_STRIP_WIDTH - distanceToEdge) * 4;
            if (y >= bounds.minY() - rubbleDepth) {
                int variant = positiveHash(x, y, z) % 4;
                if (variant == 0) {
                    return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                }
                return Blocks.GRAY_CONCRETE.defaultBlockState();
            }

            return null;
        }

        private int distanceToEdge(int x, int z) {
            int xDistance = Math.min(x - bounds.minX(), bounds.maxX() - x);
            int zDistance = Math.min(z - bounds.minZ(), bounds.maxZ() - z);
            return Math.min(xDistance, zDistance);
        }

        private boolean isOuterEdge(int x, int z) {
            return x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ();
        }

        private int positiveHash(int x, int y, int z) {
            int hash = x * 73428767 ^ y * 91227153 ^ z * 42317861;
            return hash & Integer.MAX_VALUE;
        }

        private long estimateVisibleWork() {
            long xSize = bounds.maxX() - bounds.minX() + 1L;
            long zSize = bounds.maxZ() - bounds.minZ() + 1L;
            long innerX = Math.max(0L, xSize - VISIBLE_STRIP_WIDTH * 2L);
            long innerZ = Math.max(0L, zSize - VISIBLE_STRIP_WIDTH * 2L);
            long stripColumns = xSize * zSize - innerX * innerZ;
            return stripColumns * (bounds.minY() + VISIBLE_WALL_HEIGHT - VISIBLE_MIN_Y + 1L);
        }

        private long estimateBarrierWork() {
            long xSize = bounds.maxX() - bounds.minX() + 1L;
            long zSize = bounds.maxZ() - bounds.minZ() + 1L;
            long edgeColumns = xSize * 2L + zSize * 2L - 4L;
            return edgeColumns * (bounds.maxY() + BARRIER_EXTRA_HEIGHT - BARRIER_MIN_Y + 1L);
        }

        private String progressMessage() {
            double percent = totalWork == 0 ? 100.0D : Math.min(100.0D, processed * 100.0D / totalWork);
            return String.format("Building %s boundary: %.2f%% (%s, %d visible blocks, %d barrier blocks)",
                    raidMap.id(),
                    percent,
                    stage,
                    placedVisibleBlocks,
                    placedBarrierBlocks);
        }
    }
}
