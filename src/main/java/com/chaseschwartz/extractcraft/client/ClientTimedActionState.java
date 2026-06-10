package com.chaseschwartz.extractcraft.client;

import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.network.TimedActionSyncPayload;

import net.minecraft.client.Minecraft;

public final class ClientTimedActionState {
    private static ActiveAction activeAction;
    private static String terminalMessage = "";
    private static long terminalUntilMillis;

    private ClientTimedActionState() {
    }

    public static void clear() {
        activeAction = null;
        terminalMessage = "";
        terminalUntilMillis = 0L;
    }

    public static void handleSync(TimedActionSyncPayload payload) {
        if (payload.status() == TimedActionSyncPayload.STATUS_ACTIVE) {
            activeAction = new ActiveAction(payload.actionId(), payload.actionType(), payload.label(), Math.max(1, payload.durationTicks()), Math.max(0, payload.elapsedTicks()), clientTick());
            terminalMessage = "";
            terminalUntilMillis = 0L;
            return;
        }

        activeAction = null;
        terminalMessage = payload.status() == TimedActionSyncPayload.STATUS_COMPLETE ? "Complete" : "Canceled";
        if (payload.message() != null && !payload.message().isBlank()) {
            terminalMessage = payload.message();
        }
        terminalUntilMillis = System.currentTimeMillis() + 900L;
    }

    public static Optional<ActiveAction> activeAction() {
        return Optional.ofNullable(activeAction);
    }

    public static Optional<String> terminalMessage() {
        if (terminalMessage.isBlank() || System.currentTimeMillis() > terminalUntilMillis) {
            return Optional.empty();
        }
        return Optional.of(terminalMessage);
    }

    public static int estimatedElapsedTicks(ActiveAction action) {
        return Math.min(action.durationTicks(), action.syncedElapsedTicks() + Math.max(0, clientTick() - action.syncedClientTick()));
    }

    private static int clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : (int) minecraft.level.getGameTime();
    }

    public record ActiveAction(UUID actionId, String actionType, String label, int durationTicks, int syncedElapsedTicks, int syncedClientTick) {
    }
}
