package com.chaseschwartz.extractcraft.gameplay;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class ExtractCraftGameplayRulesHandler {
    private static final int STABLE_FOOD_LEVEL = 10;

    @SubscribeEvent
    public void onPlayerPreTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            suppressHunger(player);
        }
    }

    @SubscribeEvent
    public void onPlayerPostTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            suppressHunger(player);
        }
    }

    public static void suppressHunger(ServerPlayer player) {
        player.getFoodData().setFoodLevel(STABLE_FOOD_LEVEL);
        player.getFoodData().setSaturation(0.0F);
        player.getFoodData().setExhaustion(0.0F);
    }
}
