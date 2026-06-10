package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.durability.InRaidRepairService;
import com.chaseschwartz.extractcraft.network.QuickUseStatePayload;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.timedaction.TimedActionService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuickUseService {
    public static final ResourceLocation HELMET_REBUILD_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "helmet_rebuild_kit");
    public static final ResourceLocation ARMOR_REBUILD_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "armor_rebuild_kit");
    public static final ResourceLocation PACK_REBUILD_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "pack_rebuild_kit");

    private static final Map<UUID, ResourceLocation> SELECTED_ITEMS = new java.util.HashMap<>();

    private QuickUseService() {
    }

    public static void syncOptions(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new QuickUseStatePayload(selectedString(player), options(player)));
    }

    public static void setSelected(ServerPlayer player, String itemId) {
        if (player == null) {
            return;
        }
        if (!RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("Quick-use is only available in raid."));
            syncOptions(player);
            return;
        }

        ResourceLocation id = parseItemId(itemId).orElse(null);
        if (id == null || !isEligible(id)) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }
        QuickUseSource source = findFirst(player, id).orElse(null);
        if (source == null) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }

        SELECTED_ITEMS.put(player.getUUID(), id);
        player.sendSystemMessage(Component.literal("Selected " + source.item().displayName() + "."));
        syncOptions(player);
    }

    public static void useSelected(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("Quick-use is only available in raid."));
            syncOptions(player);
            return;
        }
        if (TimedActionService.activeAction(player).isPresent()) {
            player.sendSystemMessage(Component.literal("Already performing an action."));
            syncOptions(player);
            return;
        }

        ResourceLocation selected = SELECTED_ITEMS.get(player.getUUID());
        if (selected == null) {
            player.sendSystemMessage(Component.literal("No quick-use item selected."));
            syncOptions(player);
            return;
        }
        if (!isEligible(selected)) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            SELECTED_ITEMS.remove(player.getUUID());
            syncOptions(player);
            return;
        }

        QuickUseSource source = findFirst(player, selected).orElse(null);
        if (source == null) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }

        RaidEquipmentSlot target = repairTargetFor(selected);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }

        GridMoveResult result = InRaidRepairService.startFromDrag(player, source.slot(), source.index(), target);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
        }
        syncOptions(player);
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            SELECTED_ITEMS.remove(player.getUUID());
        }
    }

    private static String selectedString(ServerPlayer player) {
        ResourceLocation selected = player == null ? null : SELECTED_ITEMS.get(player.getUUID());
        return selected == null ? "" : selected.toString();
    }

    private static List<QuickUseStatePayload.Option> options(ServerPlayer player) {
        if (player == null || !RaidManager.isInRaid(player)) {
            return List.of();
        }
        RaidInventory inventory = RaidInventoryManager.get(player);
        LinkedHashMap<ResourceLocation, QuickUseStatePayload.Option> options = new LinkedHashMap<>();
        collectOptions(options, inventory.backpack());
        collectOptions(options, inventory.vest());
        collectOptions(options, inventory.safeBox());
        return new ArrayList<>(options.values());
    }

    private static void collectOptions(LinkedHashMap<ResourceLocation, QuickUseStatePayload.Option> options, RaidStorageContainer storage) {
        for (RaidInventoryItem item : storage.items()) {
            ResourceLocation itemId = item.itemId();
            if (!isEligible(itemId)) {
                continue;
            }
            QuickUseStatePayload.Option previous = options.get(itemId);
            int count = item.count() + (previous == null ? 0 : previous.count());
            String displayName = previous == null ? item.displayName() : previous.displayName();
            options.put(itemId, new QuickUseStatePayload.Option(itemId.toString(), displayName, count));
        }
    }

    private static Optional<QuickUseSource> findFirst(ServerPlayer player, ResourceLocation itemId) {
        if (player == null || !RaidManager.isInRaid(player)) {
            return Optional.empty();
        }
        RaidInventory inventory = RaidInventoryManager.get(player);
        return findFirst(inventory.backpack(), RaidEquipmentSlot.BACKPACK, itemId)
                .or(() -> findFirst(inventory.vest(), RaidEquipmentSlot.VEST, itemId))
                .or(() -> findFirst(inventory.safeBox(), RaidEquipmentSlot.SAFE_BOX, itemId));
    }

    private static Optional<QuickUseSource> findFirst(RaidStorageContainer storage, RaidEquipmentSlot slot, ResourceLocation itemId) {
        List<RaidInventoryItem> items = storage.items();
        for (int i = 0; i < items.size(); i++) {
            RaidInventoryItem item = items.get(i);
            if (item.itemId().equals(itemId)) {
                return Optional.of(new QuickUseSource(slot, i, item));
            }
        }
        return Optional.empty();
    }

    private static boolean isEligible(ResourceLocation itemId) {
        return HELMET_REBUILD_KIT.equals(itemId) || ARMOR_REBUILD_KIT.equals(itemId) || PACK_REBUILD_KIT.equals(itemId);
    }

    private static RaidEquipmentSlot repairTargetFor(ResourceLocation itemId) {
        if (HELMET_REBUILD_KIT.equals(itemId)) {
            return RaidEquipmentSlot.HELMET;
        }
        if (ARMOR_REBUILD_KIT.equals(itemId)) {
            return RaidEquipmentSlot.ARMOR;
        }
        if (PACK_REBUILD_KIT.equals(itemId)) {
            return RaidEquipmentSlot.EQUIPPED_BACKPACK;
        }
        return null;
    }

    private static Optional<ResourceLocation> parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            return BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(id) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private record QuickUseSource(RaidEquipmentSlot slot, int index, RaidInventoryItem item) {
    }
}
