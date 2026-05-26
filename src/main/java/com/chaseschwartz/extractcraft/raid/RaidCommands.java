package com.chaseschwartz.extractcraft.raid;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

        prepareTestRaidPlatform(raidLevel);
        RaidManager.startRaid(player);
        Vec3 returnPosition = player.position();
        ExtractCraft.LOGGER.info("Starting test raid for {} from {} at {}, {}, {}",
                player.getGameProfile().getName(),
                player.serverLevel().dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);

        player.teleportTo(raidLevel, RAID_X, RAID_Y, RAID_Z, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Test raid started. Time limit: 60 seconds. Use /testraidextract to extract."));
        ExtractCraft.LOGGER.info("Teleported {} to test raid at {}, {}, {} in {}",
                player.getGameProfile().getName(),
                RAID_X,
                RAID_Y,
                RAID_Z,
                raidLevel.dimension().location());
        return 1;
    }

    private static void prepareTestRaidPlatform(ServerLevel raidLevel) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos chestPos = new BlockPos(2, 100, 0);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                raidLevel.setBlock(position.set(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);

                for (int y = 100; y <= 102; y++) {
                    raidLevel.setBlock(position.set(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        ExtractCraft.LOGGER.info("Prepared temporary test raid platform in {} from x -2..2, y 99, z -2..2",
                raidLevel.dimension().location());

        raidLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (raidLevel.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            chest.setItem(0, new ItemStack(Items.BREAD, 4));
            chest.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
            chest.setItem(2, new ItemStack(Items.EMERALD, 1));
            chest.setItem(3, new ItemStack(Items.DIAMOND, 1));
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

    private static int extractFromRaid(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!RaidManager.extractPlayer(player, "debug command")) {
            return 0;
        }

        player.sendSystemMessage(Component.literal("Extracted from test raid."));
        return 1;
    }
}
