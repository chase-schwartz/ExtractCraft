package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public class RaidInventory {
    private RaidLoadout loadout;
    private RaidStorageContainer backpack;
    private RaidStorageContainer vest;
    private RaidStorageContainer safeBox;

    public RaidInventory(RaidLoadout loadout) {
        setLoadout(loadout);
    }

    public AddResult add(RaidInventoryItem item, ItemCarryProfile profile) {
        if (profile.allowInVest() && loadout.vest().allowedCategories().contains(profile.category()) && vest.canAdd(item)) {
            vest.add(item);
            return new AddResult(true, RaidEquipmentSlot.VEST, "Added to vest.");
        }

        if (backpack.canAdd(item)) {
            backpack.add(item);
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Added to backpack.");
        }

        if (profile.allowInSafeBox() && safeBox.canAdd(item) && profile.category() != ItemCategory.GUNS && profile.category() != ItemCategory.ARMOR) {
            safeBox.add(item);
            return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, "Added to safe box.");
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "No raid inventory container has enough compatible capacity.");
    }

    public AddResult addToBackpack(RaidInventoryItem item) {
        if (backpack.canAdd(item)) {
            backpack.add(item);
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Added to backpack.");
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "Backpack does not have enough capacity or weight allowance.");
    }

    public AddResult addToVest(RaidInventoryItem item, ItemCarryProfile profile) {
        if (!profile.allowInVest() || !loadout.vest().allowedCategories().contains(profile.category())) {
            return new AddResult(false, RaidEquipmentSlot.VEST, "Item is not allowed in vest.");
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

    public void clear() {
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
        return backpack.usedWeight() + vest.usedWeight() + safeBox.usedWeight();
    }

    public int totalValue() {
        return backpack.totalValue() + vest.totalValue() + safeBox.totalValue();
    }

    public RaidLoadout loadout() {
        return loadout;
    }

    public RaidStorageContainer backpack() {
        return backpack;
    }

    public RaidStorageContainer vest() {
        return vest;
    }

    public RaidStorageContainer safeBox() {
        return safeBox;
    }

    public record AddResult(boolean success, RaidEquipmentSlot slot, String message) {
    }
}
