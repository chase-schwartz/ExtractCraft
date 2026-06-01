package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.itemidentity.ItemIdentity;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentityResolver;
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
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(stack).orElse(null);
        if (profile == null) {
            return new RaidInventory.AddResult(false, RaidEquipmentSlot.BACKPACK, itemId + " has no carry profile.");
        }

        int stackUnits = Math.max(1, (int) Math.ceil(stack.getCount() / (double) Math.max(1, stack.getMaxStackSize())));
        int slotCost = profile.slotCost() * stackUnits;
        double weight = profile.weight() * stack.getCount();
        int value = ItemValueRegistry.get(stack).map(entry -> entry.value() * stack.getCount()).orElse(0);
        RaidInventoryItem item = inventoryItem(stack, itemId, profile, slotCost, weight, value);
        return get(player).add(item, profile);
    }

    public static RaidInventory.AddResult addStackToBackpack(ServerPlayer player, ItemStack stack) {
        return addStackTo(player, stack, RaidEquipmentSlot.BACKPACK);
    }

    public static RaidInventory.AddResult addStackTo(ServerPlayer player, ItemStack stack, RaidEquipmentSlot slot) {
        if (stack.isEmpty()) {
            return new RaidInventory.AddResult(false, slot, "Cannot add an empty stack.");
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(stack).orElse(null);
        if (profile == null) {
            return new RaidInventory.AddResult(false, slot, itemId + " has no carry profile.");
        }

        int stackUnits = Math.max(1, (int) Math.ceil(stack.getCount() / (double) Math.max(1, stack.getMaxStackSize())));
        int slotCost = profile.slotCost() * stackUnits;
        double weight = profile.weight() * stack.getCount();
        int value = ItemValueRegistry.get(stack).map(entry -> entry.value() * stack.getCount()).orElse(0);
        RaidInventoryItem item = inventoryItem(stack, itemId, profile, slotCost, weight, value);
        RaidInventory inventory = get(player);
        return switch (slot) {
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> inventory.addToWeaponSlot(item, slot);
            case BACKPACK -> inventory.addToBackpack(item);
            case VEST -> inventory.addToVest(item, profile);
            case SAFE_BOX -> inventory.addToSafeBox(item, profile);
        };
    }

    public static RaidInventory.AddResult moveBetween(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        RaidInventory inventory = get(player);
        RaidInventoryItem item = inventory.itemAt(source, sourceIndex);
        if (item == null) {
            return new RaidInventory.AddResult(false, target, "Source item is no longer available.");
        }

        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            return new RaidInventory.AddResult(false, target, item.lookupKey() + " has no carry profile.");
        }

        return inventory.move(source, sourceIndex, target, profile);
    }

    private static RaidInventoryItem inventoryItem(ItemStack stack, ResourceLocation itemId, ItemCarryProfile profile, int slotCost, double weight, int value) {
        ItemIdentity identity = ItemIdentityResolver.resolve(stack);
        return new RaidInventoryItem(
                itemId,
                identity.normalizedKey(),
                stack.getHoverName().getString(),
                profile.category().name().toLowerCase(),
                stack.getCount(),
                slotCost,
                weight,
                value);
    }

    public static Optional<ItemCarryProfile> profileFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return ItemCarryProfileRegistry.get(stack);
    }

    public static int valueFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return ItemValueRegistry.get(stack).map(entry -> entry.value() * stack.getCount()).orElse(0);
    }
}
