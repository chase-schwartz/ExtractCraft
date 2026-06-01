package com.chaseschwartz.extractcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class ClientRaidState {
    private static boolean inRaid;
    private static boolean vanillaInventoryBypass;

    private ClientRaidState() {
    }

    public static boolean isInRaid() {
        return inRaid;
    }

    public static void setInRaid(boolean value) {
        inRaid = value;
    }

    public static boolean consumeVanillaInventoryBypass() {
        if (!vanillaInventoryBypass) {
            return false;
        }
        vanillaInventoryBypass = false;
        return true;
    }

    public static void openVanillaInventoryOnce() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        vanillaInventoryBypass = true;
        minecraft.setScreen(new InventoryScreen(minecraft.player));
    }
}
