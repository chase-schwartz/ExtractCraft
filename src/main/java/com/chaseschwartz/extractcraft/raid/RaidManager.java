package com.chaseschwartz.extractcraft.raid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

public class RaidManager {
    private static final Map<UUID, RaidState> ACTIVE_RAIDS = new HashMap<>();

    private RaidManager() {
    }

    public static boolean isInRaid(ServerPlayer player) {
        return ACTIVE_RAIDS.containsKey(player.getUUID());
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
}
