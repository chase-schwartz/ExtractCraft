package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.client.ClientRaidState;
import com.chaseschwartz.extractcraft.client.ClientTimedActionState;
import com.chaseschwartz.extractcraft.client.GridMoveClientState;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashMenu;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.inventory.GridMoveResult;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashScreenOpener;
import com.chaseschwartz.extractcraft.raid.inventory.QuickUseService;
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
        registrar.playToClient(TimedActionSyncPayload.TYPE, TimedActionSyncPayload.STREAM_CODEC, (payload, context) ->
                ClientTimedActionState.handleSync(payload));
        registrar.playToClient(QuickUseStatePayload.TYPE, QuickUseStatePayload.STREAM_CODEC, (payload, context) ->
                com.chaseschwartz.extractcraft.client.ClientQuickUseState.handleSync(payload));
        registrar.playToServer(OpenRaidInventoryPayload.TYPE, OpenRaidInventoryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                if (RaidManager.isInRaid(player)) {
                    RaidInventoryScreenOpener.openGrid(player);
                } else {
                    syncRaidState(player, false);
                    BaseStashScreenOpener.open(player);
                }
            }
        });
        registrar.playToServer(OpenBaseStashInventoryPayload.TYPE, OpenBaseStashInventoryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && !RaidManager.isInRaid(player)) {
                BaseStashScreenOpener.open(player);
            }
        });
        registrar.playToServer(SelectRaidWeaponPayload.TYPE, SelectRaidWeaponPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
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
        registrar.playToServer(RequestQuickUseOptionsPayload.TYPE, RequestQuickUseOptionsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                QuickUseService.syncOptions(player);
            }
        });
        registrar.playToServer(SetQuickUseSelectionPayload.TYPE, SetQuickUseSelectionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                QuickUseService.setSelected(player, payload.itemId());
            }
        });
        registrar.playToServer(UseQuickUseSelectionPayload.TYPE, UseQuickUseSelectionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                QuickUseService.useSelected(player);
            }
        });
        registrar.playToServer(GridMoveRequestPayload.TYPE, GridMoveRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                GridMoveResult result = handleGridMoveRequest(player, payload);
                PacketDistributor.sendToPlayer(player, new GridMoveResultPayload(payload.transactionId(), result.success(), result.message()));
                QuickUseService.syncOptions(player);
            }
        });
        registrar.playToServer(BulkBaseInventoryActionPayload.TYPE, BulkBaseInventoryActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                GridMoveResult result = handleBulkBaseInventoryAction(player, payload);
                PacketDistributor.sendToPlayer(player, new GridMoveResultPayload(payload.transactionId(), result.success(), result.message()));
            }
        });
        registrar.playToServer(PickupManagedDropPayload.TYPE, PickupManagedDropPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                com.chaseschwartz.extractcraft.raid.inventory.ManagedDropService.handlePickupRequest(player, payload.entityId());
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

    private static GridMoveResult handleBulkBaseInventoryAction(ServerPlayer player, BulkBaseInventoryActionPayload payload) {
        if (player.containerMenu == null || player.containerMenu.containerId != payload.menuId()) {
            return GridMoveResult.failure("Menu changed before bulk action could commit.");
        }
        if (player.containerMenu instanceof BaseStashMenu menu) {
            return menu.handleBulkAction(player, payload.action(), payload.stashDisplayIndexes(), payload.baseSlotIds(), payload.baseIndexes());
        }
        return GridMoveResult.failure("Open the base inventory before using bulk actions.");
    }

    public static void syncRaidState(ServerPlayer player, boolean inRaid) {
        PacketDistributor.sendToPlayer(player, new RaidStateSyncPayload(inRaid));
    }
}
