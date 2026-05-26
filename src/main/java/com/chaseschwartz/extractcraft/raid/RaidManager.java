package com.chaseschwartz.extractcraft.raid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class RaidManager {
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

    public static void startRaid(ServerPlayer player, List<UUID> raidMobIds, RaidMapDefinition raidMap) {
        long expiresAtGameTime = player.server.overworld().getGameTime() + raidMap.raidDurationTicks();
        ACTIVE_RAIDS.put(player.getUUID(), new RaidState(player.serverLevel().dimension(), player.position(), player.getYRot(), player.getXRot(),
                InventorySnapshot.capture(player), expiresAtGameTime, raidMap.raidDurationTicks() / 20 + 1, raidMobIds, raidMap));
        ExtractCraft.LOGGER.info("Started test raid timer for {}; expires at game time {}", player.getGameProfile().getName(), expiresAtGameTime);
    }

    public static Optional<RaidState> getRaidState(ServerPlayer player) {
        return Optional.ofNullable(ACTIVE_RAIDS.get(player.getUUID()));
    }

    public static void clearPlayerState(UUID playerId, MinecraftServer server) {
        cleanupRaidMobs(server, ACTIVE_RAIDS.remove(playerId), "player state clear");
        cleanupRaidMobs(server, PENDING_FAILED_RETURNS.remove(playerId), "player state clear");
    }

    public static boolean clearPlayerStateIfPresent(UUID playerId, MinecraftServer server) {
        RaidState activeRaid = ACTIVE_RAIDS.remove(playerId);
        RaidState pendingFailedReturn = PENDING_FAILED_RETURNS.remove(playerId);
        boolean hadActiveRaid = activeRaid != null;
        boolean hadPendingFailedReturn = pendingFailedReturn != null;
        cleanupRaidMobs(server, activeRaid, "player state clear");
        cleanupRaidMobs(server, pendingFailedReturn, "player state clear");
        return hadActiveRaid || hadPendingFailedReturn;
    }

    public static int clearAll(MinecraftServer server) {
        int clearedCount = ACTIVE_RAIDS.size() + PENDING_FAILED_RETURNS.size();
        ACTIVE_RAIDS.values().forEach(raidState -> cleanupRaidMobs(server, raidState, "server stop"));
        PENDING_FAILED_RETURNS.values().forEach(raidState -> cleanupRaidMobs(server, raidState, "server stop"));
        ACTIVE_RAIDS.clear();
        PENDING_FAILED_RETURNS.clear();
        return clearedCount;
    }

    public static boolean failRaid(ServerPlayer player) {
        RaidState raidState = ACTIVE_RAIDS.remove(player.getUUID());
        if (raidState == null) {
            return false;
        }

        cleanupRaidMobs(player.server, raidState, "raid death failure");
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

        cleanupRaidMobs(player.server, raidState, "failed raid return");
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

        cleanupRaidMobs(player.server, raidState, "immediate raid failure");
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
                        warningSeconds,
                        raidState.raidMobIds(),
                        raidState.raidMap()));
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

        cleanupRaidMobs(player.server, raidState, "successful extraction");
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
        ACTIVE_RAIDS.remove(player.getUUID());

        ExtractCraft.LOGGER.info("Extracted {} via {} to {} at {}, {}, {}",
                player.getGameProfile().getName(),
                reason,
                returnLevel.dimension().location(),
                returnPosition.x,
                returnPosition.y,
                returnPosition.z);
        return true;
    }

    private static void cleanupRaidMobs(MinecraftServer server, RaidState raidState, String reason) {
        if (raidState == null || raidState.raidMobIds().isEmpty()) {
            return;
        }

        int cleanedCount = 0;
        for (UUID mobId : raidState.raidMobIds()) {
            Entity entity = findEntity(server, mobId);
            if (entity == null) {
                continue;
            }

            entity.discard();
            cleanedCount++;
        }

        if (cleanedCount > 0) {
            ExtractCraft.LOGGER.info("Cleaned up {} remaining test raid mobs for {}", cleanedCount, reason);
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }

        return null;
    }
}
