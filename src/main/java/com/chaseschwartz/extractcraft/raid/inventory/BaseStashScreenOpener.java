package com.chaseschwartz.extractcraft.raid.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class BaseStashScreenOpener {
    private BaseStashScreenOpener() {
    }

    public static void open(ServerPlayer player) {
        RaidWeaponService.syncSelectedWeaponFromHand(player);
        PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new BaseStashMenu(containerId, inventory, player, data),
                Component.literal("Base Inventory + Stash")),
                buffer -> {
                    buffer.writeVarInt(data.stash().capacity());
                    buffer.writeVarInt(data.stashLevel());
                    buffer.writeVarInt(data.credits());
                    buffer.writeVarInt(data.baseInventory().backpack().gridWidth());
                    buffer.writeVarInt(data.baseInventory().backpack().gridHeight());
                    buffer.writeVarInt(data.baseInventory().vest().gridWidth());
                    buffer.writeVarInt(data.baseInventory().vest().gridHeight());
                    buffer.writeVarInt(data.baseInventory().safeBox().gridWidth());
                    buffer.writeVarInt(data.baseInventory().safeBox().gridHeight());
                });
    }
}
