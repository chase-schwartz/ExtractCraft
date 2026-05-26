package com.chaseschwartz.extractcraft.raid;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ExtractionZoneHandler {
    private static final int REQUIRED_EXTRACTION_TICKS = 100;
    private static final double MIN_X = -2.0D;
    private static final double MAX_X = 2.0D;
    private static final double MIN_Y = 99.0D;
    private static final double MAX_Y = 101.0D;
    private static final double MIN_Z = -2.0D;
    private static final double MAX_Z = 2.0D;

    private final Map<UUID, Integer> extractionTicks = new HashMap<>();

    @SubscribeEvent
    public void onServerPostTick(ServerTickEvent.Post event) {
        clearInactiveRaidCountdowns();

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!RaidManager.isInRaid(player)) {
                extractionTicks.remove(player.getUUID());
                continue;
            }

            if (!isInExtractionZone(player)) {
                cancelCountdownIfActive(player);
                continue;
            }

            tickExtractionCountdown(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean clearedRaid = RaidManager.clearRaid(player.getUUID());
        boolean clearedCountdown = extractionTicks.remove(player.getUUID()) != null;
        if (clearedRaid || clearedCountdown) {
            ExtractCraft.LOGGER.info("Cleared stale test raid state for {} on logout", player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        int clearedRaids = RaidManager.clearAllRaids();
        int clearedCountdowns = extractionTicks.size();
        extractionTicks.clear();

        if (clearedRaids > 0 || clearedCountdowns > 0) {
            ExtractCraft.LOGGER.info("Cleared {} active test raid states and {} extraction countdowns on server stop", clearedRaids, clearedCountdowns);
        }
    }

    private void clearInactiveRaidCountdowns() {
        Iterator<UUID> iterator = extractionTicks.keySet().iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            if (!RaidManager.isInRaid(playerId)) {
                iterator.remove();
            }
        }
    }

    private boolean isInExtractionZone(ServerPlayer player) {
        if (player.serverLevel().dimension() != Level.OVERWORLD) {
            return false;
        }

        Vec3 position = player.position();
        return position.x >= MIN_X && position.x <= MAX_X
                && position.y >= MIN_Y && position.y <= MAX_Y
                && position.z >= MIN_Z && position.z <= MAX_Z;
    }

    private void cancelCountdownIfActive(ServerPlayer player) {
        if (extractionTicks.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("Extraction canceled."));
            ExtractCraft.LOGGER.info("Extraction countdown canceled for {} after leaving the test extraction zone", player.getGameProfile().getName());
        }
    }

    private void tickExtractionCountdown(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Integer currentTicks = extractionTicks.get(playerId);

        if (currentTicks == null) {
            extractionTicks.put(playerId, 1);
            player.sendSystemMessage(Component.literal("Extraction started. Hold position for 5 seconds."));
            ExtractCraft.LOGGER.info("Extraction countdown started for {} in the test extraction zone", player.getGameProfile().getName());
            return;
        }

        int nextTicks = currentTicks + 1;
        if (nextTicks < REQUIRED_EXTRACTION_TICKS) {
            extractionTicks.put(playerId, nextTicks);
            return;
        }

        extractionTicks.remove(playerId);
        if (RaidManager.extractPlayer(player, "test extraction zone")) {
            player.sendSystemMessage(Component.literal("Extraction successful."));
            ExtractCraft.LOGGER.info("Extraction countdown completed for {}", player.getGameProfile().getName());
        }
    }
}
