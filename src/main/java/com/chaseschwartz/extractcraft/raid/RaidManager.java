package com.chaseschwartz.extractcraft.raid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class RaidManager {
    private static final Map<UUID, RaidState> ACTIVE_RAIDS = new HashMap<>();

    private RaidManager() {
    }

    public static boolean isInRaid(ServerPlayer player) {
        return ACTIVE_RAIDS.containsKey(player.getUUID());
    }

    public static boolean isInRaid(UUID playerId) {
        return ACTIVE_RAIDS.containsKey(playerId);
    }

    public static void startRaid(ServerPlayer player) {
        ACTIVE_RAIDS.put(player.getUUID(), new RaidState(player.serverLevel().dimension(), player.position(), player.getYRot(), player.getXRot()));
    }

    public static Optional<RaidState> getRaidState(ServerPlayer player) {
        return Optional.ofNullable(ACTIVE_RAIDS.get(player.getUUID()));
    }

    public static void clearRaid(ServerPlayer player) {
        ACTIVE_RAIDS.remove(player.getUUID());
    }

    public static boolean clearRaid(UUID playerId) {
        return ACTIVE_RAIDS.remove(playerId) != null;
    }

    public static int clearAllRaids() {
        int clearedCount = ACTIVE_RAIDS.size();
        ACTIVE_RAIDS.clear();
        return clearedCount;
    }

    public static boolean extractPlayer(ServerPlayer player, String reason) {
        RaidState raidState = ACTIVE_RAIDS.get(player.getUUID());

        if (raidState == null) {
            player.sendSystemMessage(Component.literal("You are not in a test raid."));
            ExtractCraft.LOGGER.info("Player {} tried to extract without an active raid", player.getGameProfile().getName());
            return false;
        }

        MinecraftServer server = player.server;
        ServerLevel returnLevel = server.getLevel(raidState.returnDimension());
        if (returnLevel == null) {
            player.sendSystemMessage(Component.literal("Unable to extract: return dimension is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to extract {} via {} because return dimension {} is unavailable",
                    player.getGameProfile().getName(),
                    reason,
                    raidState.returnDimension().location());
            return false;
        }

        Vec3 returnPosition = raidState.returnPosition();
        player.teleportTo(returnLevel, returnPosition.x, returnPosition.y, returnPosition.z, raidState.returnYaw(), raidState.returnPitch());
        clearRaid(player);

        ExtractCraft.LOGGER.info("Extracted {} via {} to {} at {}, {}, {}",
                player.getGameProfile().getName(),
                reason,
                returnLevel.dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);
        return true;
    }
}
