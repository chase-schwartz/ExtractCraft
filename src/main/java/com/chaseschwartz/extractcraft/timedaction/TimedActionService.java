package com.chaseschwartz.extractcraft.timedaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.durability.InRaidRepairService;
import com.chaseschwartz.extractcraft.network.TimedActionSyncPayload;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.inventory.QuickUseService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TimedActionService {
    private static final Map<UUID, TimedAction> ACTIVE_ACTIONS = new HashMap<>();
    private static final int PROGRESS_SYNC_INTERVAL_TICKS = 5;

    private TimedActionService() {
    }

    public static StartResult startDebugAction(ServerPlayer player, int durationTicks) {
        return start(player, TimedActionType.DEBUG_TEST, Math.max(1, durationTicks), "Debug timed action", true, true);
    }

    public static StartResult start(ServerPlayer player, TimedActionType type, int durationTicks, String label, boolean cancelOnDamage, boolean allowOutsideRaid) {
        return start(player, type, durationTicks, label, cancelOnDamage, allowOutsideRaid, Optional.empty(), Optional.empty());
    }

    public static StartResult start(ServerPlayer player, TimedActionType type, int durationTicks, String label, boolean cancelOnDamage, boolean allowOutsideRaid,
            Optional<String> sourceReference, Optional<String> targetReference) {
        if (player == null) {
            return StartResult.failure("No player.");
        }
        if (ACTIVE_ACTIONS.containsKey(player.getUUID())) {
            return StartResult.failure("Timed action already active.");
        }
        if (!player.isAlive()) {
            return StartResult.failure("Cannot start timed action while dead.");
        }
        if (!allowOutsideRaid && !RaidManager.isInRaid(player)) {
            return StartResult.failure("Timed action requires an active raid.");
        }

        long now = player.server.overworld().getGameTime();
        TimedAction action = new TimedAction(
                player.getUUID(),
                UUID.randomUUID(),
                type == null ? TimedActionType.UNKNOWN : type,
                now,
                Math.max(1, durationTicks),
                sourceReference == null ? Optional.empty() : sourceReference,
                targetReference == null ? Optional.empty() : targetReference,
                cancelOnDamage,
                false,
                false,
                allowOutsideRaid,
                label == null || label.isBlank() ? "Using..." : label);
        ACTIVE_ACTIONS.put(player.getUUID(), action);
        sync(player, action, now, TimedActionSyncPayload.STATUS_ACTIVE, "");
        return StartResult.success(action);
    }

    public static Optional<TimedAction> activeAction(ServerPlayer player) {
        return player == null ? Optional.empty() : Optional.ofNullable(ACTIVE_ACTIONS.get(player.getUUID()));
    }

    public static Optional<TimedAction> activeAction(UUID playerId) {
        return Optional.ofNullable(ACTIVE_ACTIONS.get(playerId));
    }

    public static boolean cancel(ServerPlayer player, String reason) {
        if (player == null) {
            return false;
        }
        TimedAction action = ACTIVE_ACTIONS.remove(player.getUUID());
        if (action == null) {
            return false;
        }
        sync(player, action, player.server.overworld().getGameTime(), TimedActionSyncPayload.STATUS_CANCELED, reason == null ? "Canceled" : reason);
        ExtractCraft.LOGGER.info("Canceled timed action {} for {}: {}", action.type(), player.getGameProfile().getName(), reason);
        return true;
    }

    public static boolean cancel(UUID playerId, MinecraftServer server, String reason) {
        TimedAction action = ACTIVE_ACTIONS.remove(playerId);
        if (action == null) {
            return false;
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            sync(player, action, server.overworld().getGameTime(), TimedActionSyncPayload.STATUS_CANCELED, reason == null ? "Canceled" : reason);
        }
        return true;
    }

    public static int cancelAll(MinecraftServer server, String reason) {
        List<UUID> playerIds = new ArrayList<>(ACTIVE_ACTIONS.keySet());
        int count = 0;
        for (UUID playerId : playerIds) {
            if (cancel(playerId, server, reason)) {
                count++;
            }
        }
        return count;
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<UUID> playerIds = new ArrayList<>(ACTIVE_ACTIONS.keySet());
        for (UUID playerId : playerIds) {
            TimedAction action = ACTIVE_ACTIONS.get(playerId);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                ACTIVE_ACTIONS.remove(playerId);
                continue;
            }
            if (!player.isAlive()) {
                cancel(player, "Action canceled.");
                continue;
            }
            if (!action.allowOutsideRaid() && !RaidManager.isInRaid(player)) {
                cancel(player, "Action canceled.");
                continue;
            }
            int elapsed = action.elapsedTicks(now);
            if (action.completeAt(now)) {
                ACTIVE_ACTIONS.remove(playerId);
                complete(player, action, now);
                continue;
            }
            if (elapsed % PROGRESS_SYNC_INTERVAL_TICKS == 0) {
                sync(player, action, now, TimedActionSyncPayload.STATUS_ACTIVE, "");
            }
        }
    }

    private static void complete(ServerPlayer player, TimedAction action, long now) {
        InRaidRepairService.CompletionResult repairResult = switch (action.type()) {
            case REPAIR_HELMET, REPAIR_ARMOR, REPAIR_BACKPACK -> InRaidRepairService.complete(player, action);
            default -> null;
        };
        QuickUseService.CompletionResult medicalResult = switch (action.type()) {
            case USE_MED -> QuickUseService.completeMedicalUse(player, action);
            case USE_BANDAGE -> QuickUseService.completeBleedTreatment(player, action);
            case TREAT_FRACTURE -> QuickUseService.completeFractureTreatment(player, action);
            default -> null;
        };
        if (repairResult != null && !repairResult.success()) {
            sync(player, action, now, TimedActionSyncPayload.STATUS_CANCELED, repairResult.message());
            ExtractCraft.LOGGER.info("Timed action {} failed completion for {}: {}", action.type(), player.getGameProfile().getName(), repairResult.message());
            return;
        }
        if (medicalResult != null && !medicalResult.success()) {
            sync(player, action, now, TimedActionSyncPayload.STATUS_CANCELED, medicalResult.message());
            ExtractCraft.LOGGER.info("Timed action {} failed completion for {}: {}", action.type(), player.getGameProfile().getName(), medicalResult.message());
            return;
        }
        sync(player, action, now, TimedActionSyncPayload.STATUS_COMPLETE, "Complete");
        QuickUseService.syncOptions(player);
        if (action.type() == TimedActionType.DEBUG_TEST) {
            player.sendSystemMessage(Component.literal("Debug timed action complete."));
        }
        ExtractCraft.LOGGER.info("Completed timed action {} for {}", action.type(), player.getGameProfile().getName());
    }

    private static void sync(ServerPlayer player, TimedAction action, long currentTick, int status, String message) {
        PacketDistributor.sendToPlayer(player, new TimedActionSyncPayload(
                action.actionId(),
                action.type().name(),
                action.label(),
                action.durationTicks(),
                Math.min(action.durationTicks(), action.elapsedTicks(currentTick)),
                status,
                message == null ? "" : message));
    }

    public record StartResult(boolean success, String message, Optional<TimedAction> action) {
        public static StartResult success(TimedAction action) {
            return new StartResult(true, "Timed action started.", Optional.of(action));
        }

        public static StartResult failure(String message) {
            return new StartResult(false, message, Optional.empty());
        }
    }
}
