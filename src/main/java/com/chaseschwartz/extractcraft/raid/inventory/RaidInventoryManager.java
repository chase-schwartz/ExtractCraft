package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class RaidInventoryManager {
    private static final Map<UUID, RaidInventory> INVENTORIES = new HashMap<>();

    private RaidInventoryManager() {
    }

    public static RaidInventory get(ServerPlayer player) {
        return INVENTORIES.computeIfAbsent(player.getUUID(), ignored -> new RaidInventory(RaidInventoryDefinitions.defaultLoadout()));
    }

    public static void clear(ServerPlayer player) {
        get(player).clear();
    }

    public static RaidInventory.AddResult addHeld(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return new RaidInventory.AddResult(false, RaidEquipmentSlot.BACKPACK, "Hold an item to add it to the raid inventory model.");
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(itemId).orElse(null);
        if (profile == null) {
            return new RaidInventory.AddResult(false, RaidEquipmentSlot.BACKPACK, itemId + " has no carry profile.");
        }

        int stackUnits = Math.max(1, (int) Math.ceil(stack.getCount() / (double) Math.max(1, stack.getMaxStackSize())));
        int slotCost = profile.slotCost() * stackUnits;
        double weight = profile.weight() * stack.getCount();
        int value = ItemValueRegistry.get(itemId).map(entry -> entry.value() * stack.getCount()).orElse(0);
        RaidInventoryItem item = new RaidInventoryItem(itemId, stack.getCount(), slotCost, weight, value);
        return get(player).add(item, profile);
    }
}
