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
    public static final int RAID_DURATION_TICKS = 20 * 60;
    private static final int RAID_DURATION_SECONDS = RAID_DURATION_TICKS / 20;
    private static final int[] TIMER_WARNING_SECONDS = { 45, 30, 15, 10, 5, 4, 3, 2, 1 };

    private static final Map<UUID, RaidState> ACTIVE_RAIDS = new HashMap<>();
    private static final Map<UUID, RaidState> PENDING_FAILED_RETURNS = new HashMap<>();

    private RaidManager() {
    }

    public static boolean isInRaid(ServerPlayer player) {
        return ACTIVE_RAIDS.containsKey(player.getUUID());
    }

    public static boolean isInRaid(UUID playerId) {
        return ACTIVE_RAIDS.containsKey(playerId);
    }

    public static boolean hasPendingFailedReturn(UUID playerId) {
        return PENDING_FAILED_RETURNS.containsKey(playerId);
    }

    public static void startRaid(ServerPlayer player) {
        long expiresAtGameTime = player.server.overworld().getGameTime() + RAID_DURATION_TICKS;
        ACTIVE_RAIDS.put(player.getUUID(), new RaidState(player.serverLevel().dimension(), player.position(), player.getYRot(), player.getXRot(),
                InventorySnapshot.capture(player), expiresAtGameTime, RAID_DURATION_SECONDS + 1));
        ExtractCraft.LOGGER.info("Started test raid timer for {}; expires at game time {}", player.getGameProfile().getName(), expiresAtGameTime);
    }

    public static Optional<RaidState> getRaidState(ServerPlayer player) {
        return Optional.ofNullable(ACTIVE_RAIDS.get(player.getUUID()));
    }

    public static void clearPlayerState(UUID playerId) {
        ACTIVE_RAIDS.remove(playerId);
        PENDING_FAILED_RETURNS.remove(playerId);
    }

    public static boolean clearPlayerStateIfPresent(UUID playerId) {
        boolean hadActiveRaid = ACTIVE_RAIDS.remove(playerId) != null;
        boolean hadPendingFailedReturn = PENDING_FAILED_RETURNS.remove(playerId) != null;
        return hadActiveRaid || hadPendingFailedReturn;
    }

    public static int clearAll() {
        int clearedCount = ACTIVE_RAIDS.size() + PENDING_FAILED_RETURNS.size();
        ACTIVE_RAIDS.clear();
        PENDING_FAILED_RETURNS.clear();
        return clearedCount;
    }

    public static boolean failRaid(ServerPlayer player) {
        RaidState raidState = ACTIVE_RAIDS.remove(player.getUUID());
        if (raidState == null) {
            return false;
        }

        PENDING_FAILED_RETURNS.put(player.getUUID(), raidState);
        ExtractCraft.LOGGER.info("Raid failed for {}; queued return to {} at {}, {}, {} after respawn",
                player.getGameProfile().getName(),
                raidState.returnDimension().location(),
                raidState.returnPosition().x,
                raidState.returnPosition().y,
                raidState.returnPosition().z);
        return true;
    }

    public static boolean completeFailedReturn(ServerPlayer player) {
        RaidState raidState = PENDING_FAILED_RETURNS.remove(player.getUUID());
        if (raidState == null) {
            return false;
        }

        MinecraftServer server = player.server;
        ServerLevel returnLevel = server.getLevel(raidState.returnDimension());
        if (returnLevel == null) {
            player.sendSystemMessage(Component.literal("Raid failed, but return dimension is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to return {} after failed raid because return dimension {} is unavailable",
                    player.getGameProfile().getName(),
                    raidState.returnDimension().location());
            return false;
        }

        Vec3 returnPosition = raidState.returnPosition();
        player.teleportTo(returnLevel, returnPosition.x, returnPosition.y, returnPosition.z, raidState.returnYaw(), raidState.returnPitch());
        raidState.inventorySnapshot().restore(player);
        player.sendSystemMessage(Component.literal("Raid failed."));

        ExtractCraft.LOGGER.info("Returned {} after failed raid to {} at {}, {}, {}",
                player.getGameProfile().getName(),
                returnLevel.dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);
        return true;
    }

    public static boolean failRaidAndReturnNow(ServerPlayer player, String reason) {
        RaidState raidState = ACTIVE_RAIDS.remove(player.getUUID());
        if (raidState == null) {
            return false;
        }

        MinecraftServer server = player.server;
        ServerLevel returnLevel = server.getLevel(raidState.returnDimension());
        if (returnLevel == null) {
            player.sendSystemMessage(Component.literal("Raid failed, but return dimension is unavailable."));
            ExtractCraft.LOGGER.warn("Unable to immediately return {} after failed raid via {} because return dimension {} is unavailable",
                    player.getGameProfile().getName(),
                    reason,
                    raidState.returnDimension().location());
            return false;
        }

        Vec3 returnPosition = raidState.returnPosition();
        player.teleportTo(returnLevel, returnPosition.x, returnPosition.y, returnPosition.z, raidState.returnYaw(), raidState.returnPitch());
        raidState.inventorySnapshot().restore(player);
        player.sendSystemMessage(Component.literal("Raid failed: " + reason + "."));

        ExtractCraft.LOGGER.info("Failed raid for {} via {}; returned to {} at {}, {}, {} and restored starting inventory",
                player.getGameProfile().getName(),
                reason,
                returnLevel.dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);
        return true;
    }

    public static boolean hasExpired(ServerPlayer player, long currentGameTime) {
        RaidState raidState = ACTIVE_RAIDS.get(player.getUUID());
        return raidState != null && currentGameTime >= raidState.expiresAtGameTime();
    }

    public static void sendTimerWarningIfNeeded(ServerPlayer player, long currentGameTime) {
        RaidState raidState = ACTIVE_RAIDS.get(player.getUUID());
        if (raidState == null) {
            return;
        }

        long ticksRemaining = Math.max(0, raidState.expiresAtGameTime() - currentGameTime);
        int secondsRemaining = (int) Math.ceil(ticksRemaining / 20.0D);

        for (int warningSeconds : TIMER_WARNING_SECONDS) {
            if (secondsRemaining <= warningSeconds && raidState.lastTimerWarningSeconds() > warningSeconds) {
                ACTIVE_RAIDS.put(player.getUUID(), new RaidState(
                        raidState.returnDimension(),
                        raidState.returnPosition(),
                        raidState.returnYaw(),
                        raidState.returnPitch(),
                        raidState.inventorySnapshot(),
                        raidState.expiresAtGameTime(),
                        warningSeconds));
                player.sendSystemMessage(Component.literal("Raid time remaining: " + warningSeconds + " seconds."));
                ExtractCraft.LOGGER.info("Sent {} second raid timer warning to {}", warningSeconds, player.getGameProfile().getName());
                return;
            }
        }
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
        clearPlayerState(player.getUUID());

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
