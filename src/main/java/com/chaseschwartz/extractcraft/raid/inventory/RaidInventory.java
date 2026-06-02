package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public class RaidInventory {
    private RaidLoadout loadout;
    private RaidInventoryItem primaryWeapon;
    private RaidInventoryItem secondaryWeapon;
    private RaidEquipmentSlot selectedWeaponSlot;
    private RaidStorageContainer backpack;
    private RaidStorageContainer vest;
    private RaidStorageContainer safeBox;

    public RaidInventory(RaidLoadout loadout) {
        setLoadout(loadout);
    }

    public AddResult add(RaidInventoryItem item, ItemCarryProfile profile) {
        if (backpack.canAdd(item)) {
            backpack.add(item);
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Added to backpack.");
        }

        if (!isGun(item) && vest.canAdd(item)) {
            vest.add(item);
            return new AddResult(true, RaidEquipmentSlot.VEST, "Added to vest.");
        }

        if (profile.allowInSafeBox() && safeBox.canAdd(item) && profile.category() != ItemCategory.GUNS && profile.category() != ItemCategory.ARMOR) {
            safeBox.add(item);
            return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, "Added to safe box.");
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "No raid inventory container has enough compatible capacity.");
    }

    public AddResult addToBackpack(RaidInventoryItem item) {
        if (isGun(item)) {
            if (backpack.canAdd(item)) {
                backpack.add(item);
                return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Added carried gun to backpack.");
            }

            return new AddResult(false, RaidEquipmentSlot.BACKPACK, "Backpack does not have enough capacity or weight allowance.");
        }
        if (backpack.canAdd(item)) {
            backpack.add(item);
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Added to backpack.");
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "Backpack does not have enough capacity or weight allowance.");
    }

    public AddResult addToVest(RaidInventoryItem item, ItemCarryProfile profile) {
        if (isGun(item)) {
            return new AddResult(false, RaidEquipmentSlot.VEST, "Guns must be equipped or carried in backpack.");
        }
        if (vest.canAdd(item)) {
            vest.add(item);
            return new AddResult(true, RaidEquipmentSlot.VEST, "Added to vest.");
        }

        return new AddResult(false, RaidEquipmentSlot.VEST, "Vest does not have enough capacity or weight allowance.");
    }

    public AddResult addToSafeBox(RaidInventoryItem item, ItemCarryProfile profile) {
        if (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR) {
            return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Item is not allowed in safe box.");
        }
        if (safeBox.canAdd(item)) {
            safeBox.add(item);
            return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, "Added to safe box.");
        }

        return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Safe box does not have enough capacity or weight allowance.");
    }

    public AddResult addToWeaponSlot(RaidInventoryItem item, RaidEquipmentSlot slot) {
        if (slot != RaidEquipmentSlot.PRIMARY_WEAPON && slot != RaidEquipmentSlot.SECONDARY_WEAPON) {
            return new AddResult(false, slot, "Invalid weapon slot.");
        }
        if (!isGun(item)) {
            return new AddResult(false, slot, "Only guns can be equipped in weapon slots.");
        }
        if (weaponItem(slot) != null) {
            return new AddResult(false, slot, weaponSlotName(slot) + " is already occupied.");
        }

        setWeaponItem(slot, item);
        return new AddResult(true, slot, "Equipped " + item.displayName() + " in " + weaponSlotName(slot) + ".");
    }

    public void setWeaponSlot(RaidEquipmentSlot slot, RaidInventoryItem item) {
        if (slot != RaidEquipmentSlot.PRIMARY_WEAPON && slot != RaidEquipmentSlot.SECONDARY_WEAPON) {
            throw new IllegalArgumentException("Invalid weapon slot " + slot);
        }
        setWeaponItem(slot, item);
    }

    public AddResult move(RaidEquipmentSlot sourceSlot, int sourceIndex, RaidEquipmentSlot targetSlot, ItemCarryProfile profile) {
        if (sourceSlot == targetSlot) {
            return new AddResult(true, targetSlot, "Item is already in " + targetSlot.name().toLowerCase() + ".");
        }

        RaidInventoryItem item = itemAt(sourceSlot, sourceIndex);
        if (item == null) {
            return new AddResult(false, targetSlot, "Source item is no longer available.");
        }

        AddResult result = switch (targetSlot) {
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> addToWeaponSlot(item, targetSlot);
            case BACKPACK -> addToBackpack(item);
            case VEST -> addToVest(item, profile);
            case SAFE_BOX -> addToSafeBox(item, profile);
        };
        if (!result.success()) {
            return result;
        }

        removeAt(sourceSlot, sourceIndex);
        return result;
    }

    public void clear() {
        primaryWeapon = null;
        secondaryWeapon = null;
        selectedWeaponSlot = null;
        backpack.clear();
        vest.clear();
        safeBox.clear();
    }

    public void setBackpack(BackpackDefinition definition) {
        setLoadout(new RaidLoadout(definition, loadout.vest(), loadout.safeBox()));
    }

    private void setLoadout(RaidLoadout newLoadout) {
        this.loadout = newLoadout;
        this.backpack = new RaidStorageContainer(newLoadout.backpack().id(), newLoadout.backpack().name(), newLoadout.backpack().capacity(), newLoadout.backpack().maxWeight());
        this.vest = new RaidStorageContainer(newLoadout.vest().id(), newLoadout.vest().name(), newLoadout.vest().capacity(), newLoadout.vest().maxWeight());
        this.safeBox = new RaidStorageContainer(newLoadout.safeBox().id(), newLoadout.safeBox().name(), newLoadout.safeBox().capacity(), newLoadout.safeBox().maxWeight());
    }

    public double totalWeight() {
        return weaponWeight(primaryWeapon) + weaponWeight(secondaryWeapon) + backpack.usedWeight() + vest.usedWeight() + safeBox.usedWeight();
    }

    public int totalValue() {
        return weaponValue(primaryWeapon) + weaponValue(secondaryWeapon) + backpack.totalValue() + vest.totalValue() + safeBox.totalValue();
    }

    public RaidLoadout loadout() {
        return loadout;
    }

    public RaidStorageContainer backpack() {
        return backpack;
    }

    public RaidInventoryItem primaryWeapon() {
        return primaryWeapon;
    }

    public RaidInventoryItem secondaryWeapon() {
        return secondaryWeapon;
    }

    public RaidEquipmentSlot selectedWeaponSlot() {
        return selectedWeaponSlot;
    }

    public void setSelectedWeaponSlot(RaidEquipmentSlot selectedWeaponSlot) {
        if (selectedWeaponSlot != null && selectedWeaponSlot != RaidEquipmentSlot.PRIMARY_WEAPON && selectedWeaponSlot != RaidEquipmentSlot.SECONDARY_WEAPON) {
            throw new IllegalArgumentException("Invalid selected weapon slot " + selectedWeaponSlot);
        }
        this.selectedWeaponSlot = selectedWeaponSlot;
    }

    public RaidStorageContainer vest() {
        return vest;
    }

    public RaidStorageContainer safeBox() {
        return safeBox;
    }

    private RaidStorageContainer storage(RaidEquipmentSlot slot) {
        return switch (slot) {
            case BACKPACK -> backpack;
            case VEST -> vest;
            case SAFE_BOX -> safeBox;
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalArgumentException("Weapon slots are not storage containers.");
        };
    }

    public RaidInventoryItem itemAt(RaidEquipmentSlot slot, int sourceIndex) {
        return switch (slot) {
            case PRIMARY_WEAPON -> primaryWeapon;
            case SECONDARY_WEAPON -> secondaryWeapon;
            case BACKPACK -> backpack.itemAt(sourceIndex);
            case VEST -> vest.itemAt(sourceIndex);
            case SAFE_BOX -> safeBox.itemAt(sourceIndex);
        };
    }

    public RaidInventoryItem removeAt(RaidEquipmentSlot slot, int sourceIndex) {
        return switch (slot) {
            case PRIMARY_WEAPON -> {
                RaidInventoryItem item = primaryWeapon;
                primaryWeapon = null;
                yield item;
            }
            case SECONDARY_WEAPON -> {
                RaidInventoryItem item = secondaryWeapon;
                secondaryWeapon = null;
                yield item;
            }
            case BACKPACK -> backpack.removeAt(sourceIndex);
            case VEST -> vest.removeAt(sourceIndex);
            case SAFE_BOX -> safeBox.removeAt(sourceIndex);
        };
    }

    private RaidInventoryItem weaponItem(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.PRIMARY_WEAPON ? primaryWeapon : secondaryWeapon;
    }

    private void setWeaponItem(RaidEquipmentSlot slot, RaidInventoryItem item) {
        if (slot == RaidEquipmentSlot.PRIMARY_WEAPON) {
            primaryWeapon = item;
        } else if (slot == RaidEquipmentSlot.SECONDARY_WEAPON) {
            secondaryWeapon = item;
        }
    }

    private static boolean isGun(RaidInventoryItem item) {
        return "guns".equalsIgnoreCase(item.category()) || item.lookupKey().startsWith("tacz:modern_kinetic_gun");
    }

    private static String weaponSlotName(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.PRIMARY_WEAPON ? "primary weapon" : "secondary weapon";
    }

    private static double weaponWeight(RaidInventoryItem item) {
        return item == null ? 0.0D : item.totalWeight();
    }

    private static int weaponValue(RaidInventoryItem item) {
        return item == null ? 0 : item.totalValue();
    }

    public record AddResult(boolean success, RaidEquipmentSlot slot, String message) {
    }
}
