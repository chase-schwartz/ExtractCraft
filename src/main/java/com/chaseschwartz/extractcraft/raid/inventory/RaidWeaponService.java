package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.raid.RaidManager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class RaidWeaponService {
    public static final int BRIDGE_HOTBAR_SLOT = 8;
    private static final ResourceLocation TACZ_GUN = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");
    private static final String TACZ_DUMMY_AMMO = "DummyAmmo";
    private static final String TACZ_MAX_DUMMY_AMMO = "MaxDummyAmmo";
    private static boolean debugInfiniteAmmo;

    private RaidWeaponService() {
    }

    public static boolean equip(ServerPlayer player, RaidEquipmentSlot targetSlot) {
        if (targetSlot != RaidEquipmentSlot.PRIMARY_WEAPON && targetSlot != RaidEquipmentSlot.SECONDARY_WEAPON) {
            player.sendSystemMessage(Component.literal("Invalid raid weapon slot."));
            return false;
        }
        if (!RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("Raid weapon equip is only active during raids."));
            return false;
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        syncSelectedWeaponFromHand(player, inventory);

        RaidInventoryItem weapon = inventory.itemAt(targetSlot, 0);
        if (weapon == null) {
            inventory.setSelectedWeaponSlot(null);
            setBridgeHand(player, ItemStack.EMPTY);
            player.sendSystemMessage(Component.literal("No weapon equipped in " + slotName(targetSlot) + "."));
            return true;
        }

        ItemStack stack = weapon.toItemStack();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unable to rebuild " + slotName(targetSlot) + " weapon stack."));
            return false;
        }

        inventory.setSelectedWeaponSlot(targetSlot);
        setBridgeHand(player, prepareBridgeStack(stack));
        player.sendSystemMessage(Component.literal("Equipped " + stack.getHoverName().getString() + " from " + slotName(targetSlot) + "."));
        return true;
    }

    public static boolean holster(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        syncSelectedWeaponFromHand(player, inventory);
        inventory.setSelectedWeaponSlot(null);
        setBridgeHand(player, ItemStack.EMPTY);
        player.sendSystemMessage(Component.literal("Holstered raid weapon."));
        return true;
    }

    public static boolean cycle(ServerPlayer player, int direction) {
        if (!RaidManager.isInRaid(player)) {
            return false;
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        boolean hasPrimary = inventory.primaryWeapon() != null;
        boolean hasSecondary = inventory.secondaryWeapon() != null;
        if (!hasPrimary && !hasSecondary) {
            return false;
        }

        RaidEquipmentSlot selected = inventory.selectedWeaponSlot();
        RaidEquipmentSlot target;
        if (selected == RaidEquipmentSlot.PRIMARY_WEAPON && hasSecondary) {
            target = RaidEquipmentSlot.SECONDARY_WEAPON;
        } else if (selected == RaidEquipmentSlot.SECONDARY_WEAPON && hasPrimary) {
            target = RaidEquipmentSlot.PRIMARY_WEAPON;
        } else if (direction < 0) {
            target = hasSecondary ? RaidEquipmentSlot.SECONDARY_WEAPON : RaidEquipmentSlot.PRIMARY_WEAPON;
        } else {
            target = hasPrimary ? RaidEquipmentSlot.PRIMARY_WEAPON : RaidEquipmentSlot.SECONDARY_WEAPON;
        }

        return equip(player, target);
    }

    public static void syncSelectedWeaponFromHand(ServerPlayer player) {
        syncSelectedWeaponFromHand(player, RaidInventoryManager.get(player));
    }

    public static void syncAndClearBridge(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        syncSelectedWeaponFromHand(player, inventory);
        inventory.setSelectedWeaponSlot(null);
        setBridgeHand(player, ItemStack.EMPTY);
    }

    private static void syncSelectedWeaponFromHand(ServerPlayer player, RaidInventory inventory) {
        RaidEquipmentSlot selected = inventory.selectedWeaponSlot();
        if (selected == null) {
            return;
        }

        ItemStack held = bridgeStack(player);
        if (held.isEmpty()) {
            inventory.setWeaponSlot(selected, null);
            return;
        }

        RaidInventory.AddResult result = RaidInventoryManager.stackAsItem(player, held)
                .map(item -> {
                    inventory.setWeaponSlot(selected, item);
                    return new RaidInventory.AddResult(true, selected, "Synced held weapon.");
                })
                .orElseGet(() -> new RaidInventory.AddResult(false, selected, "Held weapon has no carry profile."));
        if (!result.success()) {
            player.sendSystemMessage(Component.literal("Unable to sync held raid weapon: " + result.message()));
        }
    }

    public static String status(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        ItemStack held = bridgeStack(player);
        return "selected=" + (inventory.selectedWeaponSlot() == null ? "none" : slotName(inventory.selectedWeaponSlot()))
                + ", primary=" + itemName(inventory.primaryWeapon())
                + ", secondary=" + itemName(inventory.secondaryWeapon())
                + ", bridgeSlot=" + (BRIDGE_HOTBAR_SLOT + 1)
                + ", selectedHotbar=" + (player.getInventory().selected + 1)
                + ", debugInfiniteAmmo=" + debugInfiniteAmmo
                + ", bridgeHeld=" + (held.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(held.getItem()) + " / " + held.getHoverName().getString());
    }

    public static void setDebugInfiniteAmmo(boolean enabled) {
        debugInfiniteAmmo = enabled;
    }

    public static boolean debugInfiniteAmmo() {
        return debugInfiniteAmmo;
    }

    public static void sendAmmoDebug(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        ItemStack bridgeStack = bridgeStack(player);
        CustomData customData = bridgeStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
        player.sendSystemMessage(Component.literal("Raid weapon ammo debug:"));
        player.sendSystemMessage(Component.literal("gameMode=" + player.gameMode.getGameModeForPlayer().getName()
                + ", instabuild=" + player.getAbilities().instabuild
                + ", creativeInfiniteLikely=" + player.gameMode.getGameModeForPlayer().isCreative()));
        player.sendSystemMessage(Component.literal("selectedRaidWeapon=" + (inventory.selectedWeaponSlot() == null ? "none" : slotName(inventory.selectedWeaponSlot()))
                + ", bridgeSlot=" + (BRIDGE_HOTBAR_SLOT + 1)
                + ", debugInfiniteAmmo=" + debugInfiniteAmmo));
        player.sendSystemMessage(Component.literal("bridgeItem=" + (bridgeStack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(bridgeStack.getItem()) + " / " + bridgeStack.getHoverName().getString())));
        player.sendSystemMessage(Component.literal("GunId=" + tag.getString("GunId")
                + ", GunCurrentAmmoCount=" + tag.getInt("GunCurrentAmmoCount")
                + ", HasBulletInBarrel=" + tag.getBoolean("HasBulletInBarrel")
                + ", GunFireMode=" + tag.getString("GunFireMode")));
        player.sendSystemMessage(Component.literal("DummyAmmo=" + (tag.contains(TACZ_DUMMY_AMMO) ? tag.getInt(TACZ_DUMMY_AMMO) : "none")
                + ", MaxDummyAmmo=" + (tag.contains(TACZ_MAX_DUMMY_AMMO) ? tag.getInt(TACZ_MAX_DUMMY_AMMO) : "none")));
        player.sendSystemMessage(Component.literal("customData=" + (customData == null ? "none" : truncate(customData.copyTag().toString(), 220))));
        player.sendSystemMessage(Component.literal("components=" + truncate(bridgeStack.getComponents().toString(), 220)));
        player.sendSystemMessage(Component.literal("Backpack ammo: " + ammoSummary(inventory.backpack())));
        player.sendSystemMessage(Component.literal("Vest ammo: " + ammoSummary(inventory.vest())));
        player.sendSystemMessage(Component.literal("Safe Box ammo: " + ammoSummary(inventory.safeBox()) + " (not usable for future reload bridge)"));
        player.sendSystemMessage(Component.literal("compatibleAmmoId=unknown without TaCZ gun/ammo resource lookup; next pass should use TaCZ compileOnly API/events."));
    }

    private static void setBridgeHand(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        inventory.selected = BRIDGE_HOTBAR_SLOT;
        inventory.setItem(BRIDGE_HOTBAR_SLOT, stack.copy());
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void enforceBridgeSlot(ServerPlayer player) {
        if (!RaidManager.isInRaid(player)) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory.selected != BRIDGE_HOTBAR_SLOT) {
            inventory.selected = BRIDGE_HOTBAR_SLOT;
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static ItemStack bridgeStack(ServerPlayer player) {
        return player.getInventory().getItem(BRIDGE_HOTBAR_SLOT);
    }

    private static ItemStack prepareBridgeStack(ItemStack source) {
        ItemStack stack = source.copy();
        if (!isTaczGun(stack)) {
            return stack;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
        if (debugInfiniteAmmo) {
            tag.putInt(TACZ_DUMMY_AMMO, 9999);
            tag.putInt(TACZ_MAX_DUMMY_AMMO, 9999);
        } else {
            tag.remove(TACZ_DUMMY_AMMO);
            tag.remove(TACZ_MAX_DUMMY_AMMO);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static boolean isTaczGun(ItemStack stack) {
        return !stack.isEmpty() && TACZ_GUN.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static String ammoSummary(RaidStorageContainer container) {
        StringBuilder summary = new StringBuilder();
        for (RaidInventoryItem item : container.items()) {
            if (isAmmoItem(item)) {
                if (!summary.isEmpty()) {
                    summary.append("; ");
                }
                summary.append(item.displayName()).append(" x").append(item.count()).append(" [").append(item.lookupKey()).append("]");
            }
        }
        return summary.isEmpty() ? "none" : truncate(summary.toString(), 220);
    }

    private static boolean isAmmoItem(RaidInventoryItem item) {
        return "ammo".equalsIgnoreCase(item.category())
                || item.lookupKey().startsWith("tacz:ammo")
                || item.lookupKey().startsWith("tacz:ammo_box");
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String itemName(RaidInventoryItem item) {
        return item == null ? "empty" : item.displayName() + " (" + item.lookupKey() + ")";
    }

    private static String slotName(RaidEquipmentSlot slot) {
        return switch (slot) {
            case PRIMARY_WEAPON -> "primary";
            case SECONDARY_WEAPON -> "secondary";
            default -> slot.name().toLowerCase();
        };
    }
}
