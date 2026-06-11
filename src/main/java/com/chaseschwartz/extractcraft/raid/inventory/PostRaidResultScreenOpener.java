package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.network.ExtractCraftNetwork;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class PostRaidResultScreenOpener {
    private PostRaidResultScreenOpener() {
    }

    public static void open(ServerPlayer player) {
        ExtractCraftNetwork.syncRaidState(player, false);
        RaidResultService.pendingResult(player)
                .filter(RaidResultService.PendingRaidResult::success)
                .ifPresent(pending -> player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new PostRaidResultMenu(containerId, inventory, player, pending),
                        Component.literal("Raid Successful")),
                        buffer -> PostRaidResultMenu.ResultSnapshot.from(pending).write(buffer)));
    }

    public static void openFailure(ServerPlayer player) {
        ExtractCraftNetwork.syncRaidState(player, false);
        RaidResultService.pendingFailureScreen(player)
                .ifPresent(failed -> player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new PostRaidResultMenu(containerId, inventory, player, failed),
                        Component.literal("Raid Failed")),
                        buffer -> PostRaidResultMenu.ResultSnapshot.from(failed).write(buffer)));
    }
}
