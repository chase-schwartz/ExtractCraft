package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.client.ClientRaidState;
import com.chaseschwartz.extractcraft.client.GridMoveClientState;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashMenu;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.inventory.GridMoveResult;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashScreenOpener;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryScreenOpener;
import com.chaseschwartz.extractcraft.raid.inventory.RaidWeaponService;

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
        registrar.playToClient(GridMoveResultPayload.TYPE, GridMoveResultPayload.STREAM_CODEC, (payload, context) ->
                GridMoveClientState.handleResult(payload));
        registrar.playToServer(OpenRaidInventoryPayload.TYPE, OpenRaidInventoryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && RaidManager.isInRaid(player)) {
                RaidInventoryScreenOpener.openGrid(player);
            }
        });
        registrar.playToServer(OpenBaseStashInventoryPayload.TYPE, OpenBaseStashInventoryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && !RaidManager.isInRaid(player)) {
                BaseStashScreenOpener.open(player);
            }
        });
        registrar.playToServer(SelectRaidWeaponPayload.TYPE, SelectRaidWeaponPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && RaidManager.isInRaid(player)) {
                RaidEquipmentSlot slot = switch (payload.selection()) {
                    case SelectRaidWeaponPayload.SELECT_PRIMARY -> RaidEquipmentSlot.PRIMARY_WEAPON;
                    case SelectRaidWeaponPayload.SELECT_SECONDARY -> RaidEquipmentSlot.SECONDARY_WEAPON;
                    default -> null;
                };
                if (slot != null) {
                    RaidWeaponService.equip(player, slot);
                } else if (payload.selection() == SelectRaidWeaponPayload.CYCLE_FORWARD) {
                    RaidWeaponService.cycle(player, 1);
                } else if (payload.selection() == SelectRaidWeaponPayload.CYCLE_BACKWARD) {
                    RaidWeaponService.cycle(player, -1);
                }
            }
        });
        registrar.playToServer(GridMoveRequestPayload.TYPE, GridMoveRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                GridMoveResult result = handleGridMoveRequest(player, payload);
                PacketDistributor.sendToPlayer(player, new GridMoveResultPayload(payload.transactionId(), result.success(), result.message()));
            }
        });
        registrar.playToClient(OpenVanillaInventoryPayload.TYPE, OpenVanillaInventoryPayload.STREAM_CODEC, (payload, context) ->
                ClientRaidState.openVanillaInventoryOnce());
    }

    private static GridMoveResult handleGridMoveRequest(ServerPlayer player, GridMoveRequestPayload payload) {
        if (player.containerMenu == null || player.containerMenu.containerId != payload.menuId()) {
            return GridMoveResult.failure("Menu changed before move could commit.");
        }
        if (player.containerMenu instanceof ActiveLootContainerMenu menu) {
            return menu.handleGridMoveRequest(player, payload.operation(), payload.sourceSlotId(), payload.sourceIndex(), payload.targetSlotId(), payload.targetCell());
        }
        if (player.containerMenu instanceof BaseStashMenu menu) {
            return menu.handleGridMoveRequest(player, payload.operation(), payload.sourceSlotId(), payload.sourceIndex(), payload.targetSlotId(), payload.targetCell());
        }
        return GridMoveResult.failure("No ExtractCraft grid menu is open.");
    }

    public static void syncRaidState(ServerPlayer player, boolean inRaid) {
        PacketDistributor.sendToPlayer(player, new RaidStateSyncPayload(inRaid));
    }
}
