package com.chaseschwartz.extractcraft.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidDevBounds;
import com.chaseschwartz.extractcraft.raid.map.RaidExtractionZone;
import com.chaseschwartz.extractcraft.raid.map.RaidLootChest;
import com.chaseschwartz.extractcraft.raid.map.RaidLootItem;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMaps;
import com.chaseschwartz.extractcraft.raid.map.RaidMobSpawn;
import com.chaseschwartz.extractcraft.raid.map.RaidPlatform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

public class RaidMapSetupService {
    private RaidMapSetupService() {
    }

    public static List<UUID> prepare(ServerLevel raidLevel, RaidMapDefinition raidMap) {
        clearDevRaidArea(raidLevel);
        prepareTestRaidPlatform(raidLevel, raidMap);
        return spawnTestRaidMobs(raidLevel, raidMap);
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

        for (RaidLootChest lootChest : raidMap.lootChests()) {
            placeAndFillLootChest(raidLevel, lootChest);
        }
    }

    private static void clearDevRaidArea(ServerLevel raidLevel) {
        RaidDevBounds bounds = RaidMaps.devCleanupBounds();
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

        clearDroppedItems(raidLevel, bounds);
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
}
