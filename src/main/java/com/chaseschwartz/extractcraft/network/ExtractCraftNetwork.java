package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.client.ClientRaidState;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryScreenOpener;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ExtractCraftNetwork {
    private ExtractCraftNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RaidStateSyncPayload.TYPE, RaidStateSyncPayload.STREAM_CODEC, (payload, context) ->
                ClientRaidState.setInRaid(payload.inRaid()));
        registrar.playToServer(OpenRaidInventoryPayload.TYPE, OpenRaidInventoryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && RaidManager.isInRaid(player)) {
                RaidInventoryScreenOpener.openGrid(player);
            }
        });
        registrar.playToClient(OpenVanillaInventoryPayload.TYPE, OpenVanillaInventoryPayload.STREAM_CODEC, (payload, context) ->
                ClientRaidState.openVanillaInventoryOnce());
    }

    public static void syncRaidState(ServerPlayer player, boolean inRaid) {
        PacketDistributor.sendToPlayer(player, new RaidStateSyncPayload(inRaid));
    }
}
