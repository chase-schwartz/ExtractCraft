package com.chaseschwartz.extractcraft.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidExtractionZone;
import com.chaseschwartz.extractcraft.raid.map.RaidLootChest;
import com.chaseschwartz.extractcraft.raid.map.RaidLootItem;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMaps;
import com.chaseschwartz.extractcraft.raid.map.RaidMobSpawn;
import com.chaseschwartz.extractcraft.raid.map.RaidPlatform;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

public class RaidCommands {
    private RaidCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("startraid")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> startRaid(context.getSource(), RaidMaps.defaultMap()))
                .then(Commands.argument("map_id", StringArgumentType.word())
                        .executes(context -> startRaid(context.getSource(), StringArgumentType.getString(context, "map_id")))));

        dispatcher.register(Commands.literal("testraidextract")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> extractFromRaid(context.getSource())));
    }

    private static int startRaid(CommandSourceStack source, String mapId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("You are already in a test raid."));
            ExtractCraft.LOGGER.info("Player {} tried to start a raid while already in one", player.getGameProfile().getName());
            return 0;
        }

        RaidMapDefinition raidMap = RaidMaps.byId(mapId).orElse(null);
        if (raidMap == null) {
            player.sendSystemMessage(Component.literal("Unknown raid map '" + mapId + "'. Available maps: " + RaidMaps.availableMapIds()));
            ExtractCraft.LOGGER.info("Player {} tried to start unknown raid map '{}'", player.getGameProfile().getName(), mapId);
            return 0;
        }

        return startRaid(source, raidMap);
    }

    private static int startRaid(CommandSourceStack source, RaidMapDefinition raidMap) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("You are already in a test raid."));
            ExtractCraft.LOGGER.info("Player {} tried to start a raid while already in one", player.getGameProfile().getName());
            return 0;
        }

        MinecraftServer server = player.server;
        ServerLevel raidLevel = server.getLevel(raidMap.dimension());
        if (raidLevel == null) {
            player.sendSystemMessage(Component.literal("Unable to start test raid: raid dimension is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to start test raid for {} because {} is unavailable",
                    player.getGameProfile().getName(),
                    raidMap.dimension().location());
            return 0;
        }

        prepareTestRaidPlatform(raidLevel, raidMap);
        List<UUID> raidMobIds = spawnTestRaidMobs(raidLevel, raidMap);
        RaidManager.startRaid(player, raidMobIds, raidMap);
        Vec3 returnPosition = player.position();
        ExtractCraft.LOGGER.info("Starting test raid for {} from {} at {}, {}, {}",
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);

        Vec3 raidSpawn = raidMap.playerSpawn();
        player.teleportTo(raidLevel, raidSpawn.x, raidSpawn.y, raidSpawn.z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Test raid started. Time limit: 60 seconds. Use /testraidextract to extract."));
        ExtractCraft.LOGGER.info("Teleported {} to test raid at {}, {}, {} in {}",
                player.getGameProfile().getName(),
                raidSpawn.x,
                raidSpawn.y,
                raidSpawn.z,
                raidLevel.dimension().location());
        return 1;
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

    private static int extractFromRaid(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!RaidManager.extractPlayer(player, "debug command")) {
            return 0;
        }

        player.sendSystemMessage(Component.literal("Extracted from test raid."));
        return 1;
    }
}
