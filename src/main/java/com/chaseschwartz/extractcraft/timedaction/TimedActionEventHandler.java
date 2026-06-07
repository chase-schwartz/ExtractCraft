package com.chaseschwartz.extractcraft.timedaction;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class TimedActionEventHandler {
    @SubscribeEvent
    public void onServerPostTick(ServerTickEvent.Post event) {
        TimedActionService.tick(event.getServer());
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0.0F) {
            TimedActionService.activeAction(player)
                    .filter(TimedAction::cancelOnDamage)
                    .ifPresent(action -> {
                        TimedActionService.cancel(player, "Action canceled.");
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Action canceled."));
                    });
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TimedActionService.cancel(player, "Action canceled.");
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TimedActionService.cancel(player, "Action canceled.");
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        TimedActionService.cancelAll(event.getServer(), "Action canceled.");
    }
}
