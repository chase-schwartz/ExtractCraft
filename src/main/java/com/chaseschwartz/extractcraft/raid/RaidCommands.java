package com.chaseschwartz.extractcraft.raid;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMaps;
import com.chaseschwartz.extractcraft.raid.markers.RaidMarkerRuntimeResolver;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

        RaidMapDefinition resolvedRaidMap = RaidMarkerRuntimeResolver.resolve(raidMap);
        MinecraftServer server = player.server;
        ServerLevel raidLevel = server.getLevel(resolvedRaidMap.dimension());
        if (raidLevel == null) {
            player.sendSystemMessage(Component.literal("Unable to start test raid: raid dimension is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to start test raid for {} because {} is unavailable",
                    player.getGameProfile().getName(),
                    resolvedRaidMap.dimension().location());
            return 0;
        }

        RaidMapSetupService.SetupResult setupResult = RaidMapSetupService.prepare(raidLevel, resolvedRaidMap);
        if (!setupResult.success()) {
            player.sendSystemMessage(Component.literal("Unable to start raid map '" + resolvedRaidMap.id() + "': " + setupResult.errorMessage()));
            ExtractCraft.LOGGER.warn("Unable to start raid map {} for {}: {}",
                    resolvedRaidMap.id(),
                    player.getGameProfile().getName(),
                    setupResult.errorMessage());
            return 0;
        }

        RaidManager.startRaid(player, setupResult.raidMobIds(), resolvedRaidMap);
        Vec3 returnPosition = player.position();
        ExtractCraft.LOGGER.info("Starting test raid for {} from {} at {}, {}, {}",
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);

        Vec3 raidSpawn = resolvedRaidMap.playerSpawn();
        player.teleportTo(raidLevel, raidSpawn.x, raidSpawn.y, raidSpawn.z, resolvedRaidMap.playerSpawnYaw(), resolvedRaidMap.playerSpawnPitch());
        player.sendSystemMessage(Component.literal("Test raid started. Time limit: " + (resolvedRaidMap.raidDurationTicks() / 20) + " seconds. Use /testraidextract to extract."));
        ExtractCraft.LOGGER.info("Teleported {} to test raid at {}, {}, {} in {}",
                player.getGameProfile().getName(),
                raidSpawn.x,
                raidSpawn.y,
                raidSpawn.z,
                raidLevel.dimension().location());
        return 1;
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
