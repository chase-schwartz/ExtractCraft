package com.chaseschwartz.extractcraft.raid;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RaidCommands {
    private static final double RAID_X = 0.5D;
    private static final double RAID_Y = 100.0D;
    private static final double RAID_Z = 0.5D;

    private RaidCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("startraid")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> startRaid(context.getSource())));

        dispatcher.register(Commands.literal("testraidextract")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> extractFromRaid(context.getSource())));
    }

    private static int startRaid(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("You are already in a test raid."));
            ExtractCraft.LOGGER.info("Player {} tried to start a raid while already in one", player.getGameProfile().getName());
            return 0;
        }

        MinecraftServer server = player.server;
        ServerLevel raidLevel = server.getLevel(Level.OVERWORLD);
        if (raidLevel == null) {
            player.sendSystemMessage(Component.literal("Unable to start test raid: Overworld is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to start test raid for {} because the Overworld is unavailable", player.getGameProfile().getName());
            return 0;
        }

        RaidManager.startRaid(player);
        Vec3 returnPosition = player.position();
        ExtractCraft.LOGGER.info("Starting test raid for {} from {} at {}, {}, {}",
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);

        player.teleportTo(raidLevel, RAID_X, RAID_Y, RAID_Z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Test raid started. Use /testraidextract to extract."));
        ExtractCraft.LOGGER.info("Teleported {} to test raid at {}, {}, {} in {}",
                player.getGameProfile().getName(),
                RAID_X,
                RAID_Y,
                RAID_Z,
                raidLevel.dimension().location());
        return 1;
    }

    private static int extractFromRaid(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RaidState raidState = RaidManager.getRaidState(player).orElse(null);

        if (raidState == null) {
            player.sendSystemMessage(Component.literal("You are not in a test raid."));
            ExtractCraft.LOGGER.info("Player {} tried to extract without an active raid", player.getGameProfile().getName());
            return 0;
        }

        MinecraftServer server = player.server;
        ServerLevel returnLevel = server.getLevel(raidState.returnDimension());
        if (returnLevel == null) {
            player.sendSystemMessage(Component.literal("Unable to extract: return dimension is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to extract {} because return dimension {} is unavailable",
                    player.getGameProfile().getName(),
                    raidState.returnDimension().location());
            return 0;
        }

        Vec3 returnPosition = raidState.returnPosition();
        player.teleportTo(returnLevel, returnPosition.x, returnPosition.y, returnPosition.z, raidState.returnYaw(), raidState.returnPitch());
        RaidManager.clearRaid(player);

        player.sendSystemMessage(Component.literal("Extracted from test raid."));
        ExtractCraft.LOGGER.info("Extracted {} to {} at {}, {}, {}",
                player.getGameProfile().getName(),
                returnLevel.dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);
        return 1;
    }
}
