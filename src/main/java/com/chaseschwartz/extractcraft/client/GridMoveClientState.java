package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.network.GridMoveResultPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class GridMoveClientState {
    private GridMoveClientState() {
    }

    public static void handleResult(GridMoveResultPayload payload) {
        Screen screen = Minecraft.getInstance().screen;
        ExtractCraft.LOGGER.info("Grid move result received: tx={}, success={}, message={}, screen={}",
                payload.transactionId(),
                payload.success(),
                payload.message(),
                screen == null ? "none" : screen.getClass().getSimpleName());
        if (screen instanceof ActiveLootContainerScreen activeLootContainerScreen) {
            activeLootContainerScreen.handleGridMoveResult(payload.transactionId(), payload.success(), payload.message());
        } else if (screen instanceof BaseStashScreen baseStashScreen) {
            baseStashScreen.handleGridMoveResult(payload.transactionId(), payload.success(), payload.message());
        }
    }
}
