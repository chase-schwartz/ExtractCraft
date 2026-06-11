package com.chaseschwartz.extractcraft.raid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.chaseschwartz.extractcraft.network.BleedStateSyncPayload;
import com.chaseschwartz.extractcraft.raid.inventory.QuickUseService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BleedStatusService {
    private static final int LIGHT_DAMAGE_INTERVAL_TICKS = 8 * 20;
    private static final int HEAVY_DAMAGE_INTERVAL_TICKS = 3 * 20;
    private static final Map<UUID, BleedState> STATES = new HashMap<>();
    private static boolean applyingBleedDamage;
    private static int damagePulseSequence;

    private BleedStatusService() {
    }

    public static BleedStatus status(ServerPlayer player) {
        if (player == null) {
            return BleedStatus.NONE;
        }
        BleedState state = STATES.get(player.getUUID());
        return state == null ? BleedStatus.NONE : state.status();
    }

    public static boolean isApplyingBleedDamage() {
        return applyingBleedDamage;
    }

    public static void rollForDamage(ServerPlayer player, float damage) {
        if (player == null || !RaidManager.isInRaid(player) || damage < 2.0F) {
            return;
        }
        if (damage >= 8.0F) {
            if (player.getRandom().nextDouble() < 0.05D) {
                apply(player, BleedStatus.HEAVY, true);
                return;
            }
            if (player.getRandom().nextDouble() < 0.20D) {
                apply(player, BleedStatus.LIGHT, true);
            }
            return;
        }
        if (damage >= 4.0F) {
            if (player.getRandom().nextDouble() < 0.12D) {
                apply(player, BleedStatus.LIGHT, true);
            }
            return;
        }
        if (player.getRandom().nextDouble() < 0.05D) {
            apply(player, BleedStatus.LIGHT, true);
        }
    }

    public static void apply(ServerPlayer player, BleedStatus status, boolean notify) {
        if (player == null || status == null || status == BleedStatus.NONE || !RaidManager.isInRaid(player)) {
            return;
        }
        BleedStatus current = status(player);
        if (current == BleedStatus.HEAVY && status == BleedStatus.LIGHT) {
            return;
        }
        if (current == status) {
            return;
        }
        long now = player.server.overworld().getGameTime();
        STATES.put(player.getUUID(), new BleedState(status, now + interval(status)));
        sync(player);
        QuickUseService.syncOptions(player);
        if (notify) {
            player.sendSystemMessage(Component.literal(status.label() + " applied."));
        }
    }

    public static boolean clear(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        boolean removed = STATES.remove(player.getUUID()) != null;
        sync(player);
        QuickUseService.syncOptions(player);
        return removed;
    }

    public static int clear(UUID playerId, MinecraftServer server) {
        boolean removed = STATES.remove(playerId) != null;
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            sync(player);
        }
        return removed ? 1 : 0;
    }

    public static int clearAll(MinecraftServer server) {
        int count = STATES.size();
        for (UUID playerId : STATES.keySet()) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new BleedStateSyncPayload(BleedStatus.NONE.name()));
            }
        }
        STATES.clear();
        return count;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || STATES.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (UUID playerId : java.util.List.copyOf(STATES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            BleedState state = STATES.get(playerId);
            if (player == null || state == null) {
                STATES.remove(playerId);
                continue;
            }
            if (!RaidManager.isInRaid(player) || !player.isAlive()) {
                clear(player);
                continue;
            }
            if (now < state.nextDamageGameTime()) {
                continue;
            }
            applyTickDamage(player);
            syncDamagePulse(player);
            STATES.put(playerId, new BleedState(state.status(), now + interval(state.status())));
        }
    }

    public static boolean canTreat(ServerPlayer player, TreatmentStrength strength) {
        BleedStatus status = status(player);
        return switch (strength) {
            case LIGHT_ONLY -> status == BleedStatus.LIGHT;
            case HEAVY_AND_LIGHT -> status.active();
        };
    }

    public static TreatmentResult validateTreatment(ServerPlayer player, TreatmentStrength strength) {
        BleedStatus status = status(player);
        if (!status.active()) {
            return TreatmentResult.failure("No bleeding to treat.");
        }
        if (status == BleedStatus.HEAVY && strength == TreatmentStrength.LIGHT_ONLY) {
            return TreatmentResult.failure("Requires stronger bleed treatment.");
        }
        return TreatmentResult.success(status);
    }

    private static void applyTickDamage(ServerPlayer player) {
        applyingBleedDamage = true;
        try {
            if (player.getHealth() > 1.0F) {
                player.setHealth(Math.max(1.0F, player.getHealth() - 1.0F));
            } else {
                player.hurt(player.damageSources().generic(), 1.0F);
            }
        } finally {
            applyingBleedDamage = false;
        }
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BleedStateSyncPayload(status(player).name()));
    }

    private static void syncDamagePulse(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BleedStateSyncPayload(status(player).name(), ++damagePulseSequence));
    }

    private static int interval(BleedStatus status) {
        return status == BleedStatus.HEAVY ? HEAVY_DAMAGE_INTERVAL_TICKS : LIGHT_DAMAGE_INTERVAL_TICKS;
    }

    public enum TreatmentStrength {
        LIGHT_ONLY,
        HEAVY_AND_LIGHT
    }

    public record TreatmentResult(boolean success, String message, BleedStatus treatedStatus) {
        public static TreatmentResult success(BleedStatus status) {
            return new TreatmentResult(true, "", status);
        }

        public static TreatmentResult failure(String message) {
            return new TreatmentResult(false, message, BleedStatus.NONE);
        }
    }

    private record BleedState(BleedStatus status, long nextDamageGameTime) {
    }
}
