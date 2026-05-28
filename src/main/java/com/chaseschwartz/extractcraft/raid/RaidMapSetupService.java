package com.chaseschwartz.extractcraft.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidDevBounds;
import com.chaseschwartz.extractcraft.raid.map.RaidExtractionZone;
import com.chaseschwartz.extractcraft.raid.map.RaidLootChest;
import com.chaseschwartz.extractcraft.raid.map.RaidLootItem;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMobSpawn;
import com.chaseschwartz.extractcraft.raid.map.RaidPlatform;
import com.chaseschwartz.extractcraft.raid.map.RaidMapSource;
import com.chaseschwartz.extractcraft.raid.map.RaidStructurePlacement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

public class RaidMapSetupService {
    private RaidMapSetupService() {
    }

    public static SetupResult prepare(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        RaidMapSource source = raidMap.source();
        Optional<StructureTemplate> structureTemplate = loadStructureTemplate(raidLevel, raidMap);
        if (source.structurePlacement().isPresent() && structureTemplate.isEmpty()) {
            String errorMessage = "Missing raid structure template: " + source.structurePlacement().get().templateId();
            ExtractCraft.LOGGER.warn("Unable to prepare raid map {}: {}", raidMap.id(), errorMessage);
            return SetupResult.failure(errorMessage);
        }

        source.cleanupBounds().ifPresent(bounds -> prepareCleanup(raidLevel, raidMap, bounds));
        if (source.shouldGeneratePlatform()) {
            prepareTestRaidPlatform(raidLevel, raidMap);
        }
        structureTemplate.ifPresent(template -> placeStructureTemplate(raidLevel, raidMap, template));
        if (raidMap.renderExtractionMarkers()) {
            renderExtractionMarkers(raidLevel, raidMap);
        }
        placeLootChests(raidLevel, raidMap);
        return SetupResult.success(spawnTestRaidMobs(raidLevel, raidMap));
    }

    private static void prepareTestRaidPlatform(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        RaidPlatform platform = raidMap.platform();

        for (int x = platform.minX(); x <= platform.maxX(); x++) {
            for (int z = platform.minZ(); z <= platform.maxZ(); z++) {
                raidLevel.setBlock(position.set(x, platform.floorY(), z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);

                for (int y = platform.airMinY(); y <= platform.airMaxY(); y++) {
                    raidLevel.setBlock(position.set(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void renderExtractionMarkers(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        RaidPlatform platform = raidMap.platform();
        for (RaidExtractionZone extractionZone : raidMap.extractionZones()) {
            for (int x = (int) extractionZone.minX(); x <= (int) extractionZone.maxX(); x++) {
                for (int z = (int) extractionZone.minZ(); z <= (int) extractionZone.maxZ(); z++) {
                    raidLevel.setBlock(position.set(x, platform.floorY(), z), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                }
            }
        }

        ExtractCraft.LOGGER.info("Prepared temporary test raid platform for {} in {} from x {}..{}, y {}, z {}..{}",
                raidMap.id(),
                raidLevel.dimension().location(),
                platform.minX(),
                platform.maxX(),
                platform.floorY(),
                platform.minZ(),
                platform.maxZ());
    }

    private static void placeLootChests(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        for (RaidLootChest lootChest : raidMap.lootChests()) {
            placeAndFillLootChest(raidLevel, lootChest);
        }
    }

    private static void prepareCleanup(ServerLevel raidLevel, RaidMapDefinition raidMap, RaidDevBounds bounds) {
        if (raidMap.source().shouldClearTerrainBlocks()) {
            clearDevRaidArea(raidLevel, bounds);
        } else {
            ExtractCraft.LOGGER.info("Skipped terrain cleanup for existing-world raid map {} in {}", raidMap.id(), raidLevel.dimension().location());
        }

        clearDroppedItems(raidLevel, bounds);
    }

    private static void clearDevRaidArea(ServerLevel raidLevel, RaidDevBounds bounds) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    raidLevel.setBlock(position.set(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        ExtractCraft.LOGGER.info("Cleared dev raid area in {} from x {}..{}, y {}..{}, z {}..{}",
                raidLevel.dimension().location(),
                bounds.minX(),
                bounds.maxX(),
                bounds.minY(),
                bounds.maxY(),
                bounds.minZ(),
                bounds.maxZ());
    }

    private static Optional<StructureTemplate> loadStructureTemplate(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        if (raidMap.source().structurePlacement().isEmpty()) {
            return Optional.empty();
        }

        return raidLevel.getServer().getStructureManager().get(raidMap.source().structurePlacement().get().templateId());
    }

    private static void placeStructureTemplate(ServerLevel raidLevel, RaidMapDefinition raidMap, StructureTemplate template) {
        RaidStructurePlacement structurePlacement = raidMap.source().structurePlacement().orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(structurePlacement.rotation())
                .setMirror(structurePlacement.mirror())
                .setIgnoreEntities(!structurePlacement.includeEntities());

        template.placeInWorld(
                raidLevel,
                structurePlacement.origin(),
                structurePlacement.origin(),
                settings,
                raidLevel.getRandom(),
                3);

        ExtractCraft.LOGGER.info("Placed raid structure template {} for map {} at {}, {}, {} in {}",
                structurePlacement.templateId(),
                raidMap.id(),
                structurePlacement.origin().getX(),
                structurePlacement.origin().getY(),
                structurePlacement.origin().getZ(),
                raidLevel.dimension().location());
    }

    private static void clearDroppedItems(ServerLevel raidLevel, RaidDevBounds bounds) {
        AABB cleanupBox = new AABB(
                bounds.minX() - 1.0D,
                bounds.minY() - 1.0D,
                bounds.minZ() - 1.0D,
                bounds.maxX() + 1.0D,
                bounds.maxY() + 2.0D,
                bounds.maxZ() + 1.0D);

        List<ItemEntity> droppedItems = raidLevel.getEntitiesOfClass(ItemEntity.class, cleanupBox);
        for (ItemEntity droppedItem : droppedItems) {
            droppedItem.discard();
        }

        if (!droppedItems.isEmpty()) {
            ExtractCraft.LOGGER.info("Removed {} stale dropped item entities from dev raid area in {}", droppedItems.size(), raidLevel.dimension().location());
        }
    }

    private static void placeAndFillLootChest(ServerLevel raidLevel, RaidLootChest lootChest) {
        BlockPos chestPos = lootChest.pos();
        raidLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (raidLevel.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            int slot = 0;
            for (RaidLootItem lootItem : lootChest.loot()) {
                chest.setItem(slot, lootItem.createStack());
                slot++;
            }
            chest.setChanged();

            ExtractCraft.LOGGER.info("Placed and filled temporary test raid loot chest at {}, {}, {} in {}",
                    chestPos.getX(),
                    chestPos.getY(),
                    chestPos.getZ(),
                    raidLevel.dimension().location());
        } else {
            ExtractCraft.LOGGER.warn("Unable to fill temporary test raid loot chest at {}, {}, {} in {}",
                    chestPos.getX(),
                    chestPos.getY(),
                    chestPos.getZ(),
                    raidLevel.dimension().location());
        }
    }

    private static List<UUID> spawnTestRaidMobs(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        List<UUID> raidMobIds = new ArrayList<>();
        for (RaidMobSpawn mobSpawn : raidMap.mobSpawns()) {
            spawnTestRaidMob(raidLevel, mobSpawn.entityType(), mobSpawn.x(), mobSpawn.y(), mobSpawn.z(), raidMobIds);
        }
        return raidMobIds;
    }

    private static void spawnTestRaidMob(ServerLevel raidLevel, EntityType<? extends Mob> entityType, double x, double y, double z, List<UUID> raidMobIds) {
        Mob mob = entityType.create(raidLevel);
        if (mob == null) {
            ExtractCraft.LOGGER.warn("Unable to create test raid mob {} at {}, {}, {} in {}",
                    entityType,
                    x,
                    y,
                    z,
                    raidLevel.dimension().location());
            return;
        }

        mob.moveTo(x, y, z, 0.0F, 0.0F);
        mob.setPersistenceRequired();
        if (raidLevel.addFreshEntity(mob)) {
            raidMobIds.add(mob.getUUID());
            ExtractCraft.LOGGER.info("Spawned test raid mob {} at {}, {}, {} in {}",
                    entityType,
                    x,
                    y,
                    z,
                    raidLevel.dimension().location());
        } else {
            ExtractCraft.LOGGER.warn("Unable to add test raid mob {} at {}, {}, {} in {}",
                    entityType,
                    x,
                    y,
                    z,
                    raidLevel.dimension().location());
        }
    }

    public record SetupResult(boolean success, List<UUID> raidMobIds, String errorMessage) {
        public SetupResult {
            raidMobIds = List.copyOf(raidMobIds);
        }

        public static SetupResult success(List<UUID> raidMobIds) {
            return new SetupResult(true, raidMobIds, "");
        }

        public static SetupResult failure(String errorMessage) {
            return new SetupResult(false, List.of(), errorMessage);
        }
    }
}
