package com.chaseschwartz.extractcraft.client;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class LootContainerOutlineRenderer {
    private static final Path CONTAINER_LAYOUT_DIR = Path.of("run", "extractcraft", "raid_maps");
    private static final long RELOAD_INTERVAL_TICKS = 40L;
    private static final double OUTLINE_RADIUS = 20.0D;
    private static final double OUTLINE_RADIUS_SQ = OUTLINE_RADIUS * OUTLINE_RADIUS;
    private static final double NEARBY_INFLATE = 0.006D;
    private static final double TARGETED_INFLATE = 0.008D;
    private static final double NEARBY_EDGE_THICKNESS = 0.035D;
    private static final double TARGETED_EDGE_THICKNESS = 0.048D;
    private static final float NEARBY_RED = 0.30F;
    private static final float NEARBY_GREEN = 0.92F;
    private static final float NEARBY_BLUE = 1.0F;
    private static final float TARGETED_RED = 0.78F;
    private static final float TARGETED_GREEN = 1.0F;
    private static final float TARGETED_BLUE = 1.0F;
    private static final float NEARBY_ALPHA = 0.62F;
    private static final float TARGETED_ALPHA = 0.88F;
    private static final double NEARBY_PIP_SIZE = 0.09D;
    private static final double TARGETED_PIP_SIZE = 0.13D;

    private static final Map<ResourceLocation, Set<BlockPos>> ACTIVE_CONTAINERS_BY_DIMENSION = new HashMap<>();
    private static long nextReloadGameTime;

    private LootContainerOutlineRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (gameTime >= nextReloadGameTime) {
            reloadActiveContainers();
            nextReloadGameTime = gameTime + RELOAD_INTERVAL_TICKS;
        }

        Set<BlockPos> activeContainers = ACTIVE_CONTAINERS_BY_DIMENSION.get(minecraft.level.dimension().location());
        if (activeContainers == null || activeContainers.isEmpty()) {
            return;
        }

        BlockPos targetedPos = targetedBlock(minecraft, activeContainers);
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        int rendered = 0;

        for (BlockPos pos : activeContainers) {
            if (distanceToPlayerSqr(minecraft.player.position(), pos) > OUTLINE_RADIUS_SQ) {
                continue;
            }

            boolean targeted = pos.equals(targetedPos);
            double inflate = targeted ? TARGETED_INFLATE : NEARBY_INFLATE;
            double edgeThickness = targeted ? TARGETED_EDGE_THICKNESS : NEARBY_EDGE_THICKNESS;
            float red = targeted ? TARGETED_RED : NEARBY_RED;
            float green = targeted ? TARGETED_GREEN : NEARBY_GREEN;
            float blue = targeted ? TARGETED_BLUE : NEARBY_BLUE;
            float alpha = targeted ? TARGETED_ALPHA : NEARBY_ALPHA;
            AABB box = new AABB(pos).inflate(inflate)
                    .move(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

            renderThickOutline(poseStack, bufferSource, box, edgeThickness, red, green, blue, alpha);
            renderPip(poseStack, bufferSource, box, targeted ? TARGETED_PIP_SIZE : NEARBY_PIP_SIZE, red, green, blue, Math.min(1.0F, alpha + 0.08F));
            rendered++;
        }

        if (rendered > 0) {
            bufferSource.endBatch();
        }
    }

    private static void renderThickOutline(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            AABB box,
            double thickness,
            float red,
            float green,
            float blue,
            float alpha) {
        double half = thickness / 2.0D;

        renderEdgeX(poseStack, bufferSource, box, box.minY, box.minZ, half, red, green, blue, alpha);
        renderEdgeX(poseStack, bufferSource, box, box.minY, box.maxZ, half, red, green, blue, alpha);
        renderEdgeX(poseStack, bufferSource, box, box.maxY, box.minZ, half, red, green, blue, alpha);
        renderEdgeX(poseStack, bufferSource, box, box.maxY, box.maxZ, half, red, green, blue, alpha);

        renderEdgeY(poseStack, bufferSource, box, box.minX, box.minZ, half, red, green, blue, alpha);
        renderEdgeY(poseStack, bufferSource, box, box.minX, box.maxZ, half, red, green, blue, alpha);
        renderEdgeY(poseStack, bufferSource, box, box.maxX, box.minZ, half, red, green, blue, alpha);
        renderEdgeY(poseStack, bufferSource, box, box.maxX, box.maxZ, half, red, green, blue, alpha);

        renderEdgeZ(poseStack, bufferSource, box, box.minX, box.minY, half, red, green, blue, alpha);
        renderEdgeZ(poseStack, bufferSource, box, box.minX, box.maxY, half, red, green, blue, alpha);
        renderEdgeZ(poseStack, bufferSource, box, box.maxX, box.minY, half, red, green, blue, alpha);
        renderEdgeZ(poseStack, bufferSource, box, box.maxX, box.maxY, half, red, green, blue, alpha);
    }

    private static void renderEdgeX(PoseStack poseStack, MultiBufferSource bufferSource, AABB box, double y, double z, double half, float red, float green, float blue, float alpha) {
        DebugRenderer.renderFilledBox(poseStack, bufferSource, box.minX, y - half, z - half, box.maxX, y + half, z + half, red, green, blue, alpha);
    }

    private static void renderEdgeY(PoseStack poseStack, MultiBufferSource bufferSource, AABB box, double x, double z, double half, float red, float green, float blue, float alpha) {
        DebugRenderer.renderFilledBox(poseStack, bufferSource, x - half, box.minY, z - half, x + half, box.maxY, z + half, red, green, blue, alpha);
    }

    private static void renderEdgeZ(PoseStack poseStack, MultiBufferSource bufferSource, AABB box, double x, double y, double half, float red, float green, float blue, float alpha) {
        DebugRenderer.renderFilledBox(poseStack, bufferSource, x - half, y - half, box.minZ, x + half, y + half, box.maxZ, red, green, blue, alpha);
    }

    private static void renderPip(PoseStack poseStack, MultiBufferSource bufferSource, AABB box, double size, float red, float green, float blue, float alpha) {
        double half = size / 2.0D;
        double centerX = (box.minX + box.maxX) / 2.0D;
        double centerY = box.maxY + 0.16D;
        double centerZ = (box.minZ + box.maxZ) / 2.0D;
        DebugRenderer.renderFilledBox(
                poseStack,
                bufferSource,
                centerX - half,
                centerY - half,
                centerZ - half,
                centerX + half,
                centerY + half,
                centerZ + half,
                red,
                green,
                blue,
                alpha);
    }

    private static void reloadActiveContainers() {
        Map<ResourceLocation, Set<BlockPos>> loaded = new HashMap<>();
        if (!Files.isDirectory(CONTAINER_LAYOUT_DIR)) {
            ACTIVE_CONTAINERS_BY_DIMENSION.clear();
            return;
        }

        try (var paths = Files.list(CONTAINER_LAYOUT_DIR)) {
            paths.filter(path -> path.getFileName().toString().endsWith("_containers.json"))
                    .forEach(path -> loadLayout(path, loaded));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to scan active loot container layout directory {}", CONTAINER_LAYOUT_DIR, exception);
        }

        ACTIVE_CONTAINERS_BY_DIMENSION.clear();
        ACTIVE_CONTAINERS_BY_DIMENSION.putAll(loaded);
    }

    private static void loadLayout(Path path, Map<ResourceLocation, Set<BlockPos>> loaded) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("containers") || !root.get("containers").isJsonArray()) {
                return;
            }

            for (JsonElement element : root.getAsJsonArray("containers")) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("activeLootContainer") || !object.get("activeLootContainer").getAsBoolean()) {
                    continue;
                }

                ResourceLocation dimensionId = ResourceLocation.parse(object.get("dimension").getAsString());
                BlockPos pos = jsonToPos(object.getAsJsonObject("pos"));
                loaded.computeIfAbsent(dimensionId, ignored -> new HashSet<>()).add(pos);
            }
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to load active loot container outlines from {}", path, exception);
        }
    }

    private static BlockPos jsonToPos(JsonObject object) {
        return new BlockPos(object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
    }

    private static BlockPos targetedBlock(Minecraft minecraft, Set<BlockPos> activeContainers) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult && activeContainers.contains(blockHitResult.getBlockPos())) {
            return blockHitResult.getBlockPos();
        }
        return null;
    }

    private static double distanceToPlayerSqr(Vec3 playerPosition, BlockPos pos) {
        double dx = pos.getX() + 0.5D - playerPosition.x;
        double dy = pos.getY() + 0.5D - playerPosition.y;
        double dz = pos.getZ() + 0.5D - playerPosition.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
