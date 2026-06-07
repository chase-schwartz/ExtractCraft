package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public class RaidInventory {
    private RaidLoadout loadout;
    private RaidInventoryItem helmet;
    private RaidInventoryItem armor;
    private RaidInventoryItem equippedBackpack;
    private RaidInventoryItem equippedVest;
    private RaidInventoryItem equippedSafeContainer;
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
        int backpackMoved = backpack.addPartialGridFirstFit(item, true);
        if (backpackMoved > 0) {
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, movedMessage("backpack", backpackMoved, item), backpackMoved);
        }

        if (!isGun(item)) {
            int vestMoved = vest.addPartialGridFirstFit(item, true);
            if (vestMoved > 0) {
                return new AddResult(true, RaidEquipmentSlot.VEST, movedMessage("vest", vestMoved, item), vestMoved);
            }
        }

        if (profile.allowInSafeBox() && profile.category() != ItemCategory.GUNS && profile.category() != ItemCategory.ARMOR) {
            int safeMoved = safeBox.addPartialGridFirstFit(item, true);
            if (safeMoved > 0) {
                return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, movedMessage("safe box", safeMoved, item), safeMoved);
            }
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "No raid inventory container has enough compatible capacity.");
    }

    public AddResult addToBackpack(RaidInventoryItem item) {
        int moved = backpack.addPartialGridFirstFit(item, true);
        if (moved > 0) {
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, movedMessage("backpack", moved, item), moved);
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "Backpack does not have enough grid space.");
    }

    public AddResult addToBackpackAt(RaidInventoryItem item, int x, int y, boolean rotated) {
        int moved = backpack.addPartialAt(item, x, y, rotated, -1, true);
        if (moved > 0) {
            return new AddResult(true, RaidEquipmentSlot.BACKPACK, movedMessage("backpack", moved, item), moved);
        }

        return new AddResult(false, RaidEquipmentSlot.BACKPACK, "Backpack target cell is blocked or does not have enough capacity.");
    }

    public AddResult addToVest(RaidInventoryItem item, ItemCarryProfile profile) {
        if (isGun(item)) {
            return new AddResult(false, RaidEquipmentSlot.VEST, "Guns must be equipped or carried in backpack.");
        }
        int moved = vest.addPartialGridFirstFit(item, true);
        if (moved > 0) {
            return new AddResult(true, RaidEquipmentSlot.VEST, movedMessage("vest", moved, item), moved);
        }

        return new AddResult(false, RaidEquipmentSlot.VEST, "Vest does not have enough grid space.");
    }

    public AddResult addToVestAt(RaidInventoryItem item, ItemCarryProfile profile, int x, int y, boolean rotated) {
        if (isGun(item)) {
            return new AddResult(false, RaidEquipmentSlot.VEST, "Guns must be equipped or carried in backpack.");
        }
        int moved = vest.addPartialAt(item, x, y, rotated, -1, true);
        if (moved > 0) {
            return new AddResult(true, RaidEquipmentSlot.VEST, movedMessage("vest", moved, item), moved);
        }

        return new AddResult(false, RaidEquipmentSlot.VEST, "Vest target cell is blocked or does not have enough capacity.");
    }

    public AddResult addToSafeBox(RaidInventoryItem item, ItemCarryProfile profile) {
        if (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR) {
            return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Item is not allowed in safe box.");
        }
        int moved = safeBox.addPartialGridFirstFit(item, true);
        if (moved > 0) {
            return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, movedMessage("safe box", moved, item), moved);
        }

        return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Safe box does not have enough grid space.");
    }

    public AddResult addToSafeBoxAt(RaidInventoryItem item, ItemCarryProfile profile, int x, int y, boolean rotated) {
        if (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR) {
            return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Item is not allowed in safe box.");
        }
        int moved = safeBox.addPartialAt(item, x, y, rotated, -1, true);
        if (moved > 0) {
            return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, movedMessage("safe box", moved, item), moved);
        }

        return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Safe box target cell is blocked or does not have enough capacity.");
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
        return new AddResult(true, slot, "Equipped " + item.displayName() + " in " + weaponSlotName(slot) + ".", item.count());
    }

    public void setWeaponSlot(RaidEquipmentSlot slot, RaidInventoryItem item) {
        if (slot != RaidEquipmentSlot.PRIMARY_WEAPON && slot != RaidEquipmentSlot.SECONDARY_WEAPON) {
            throw new IllegalArgumentException("Invalid weapon slot " + slot);
        }
        setWeaponItem(slot, item);
    }

    public AddResult move(RaidEquipmentSlot sourceSlot, int sourceIndex, RaidEquipmentSlot targetSlot, ItemCarryProfile profile) {
        RaidInventoryItem item = itemAt(sourceSlot, sourceIndex);
        if (item == null) {
            return new AddResult(false, targetSlot, "Source item is no longer available.");
        }

        if (sourceSlot == targetSlot && isStorageSlot(sourceSlot)) {
            RaidStorageContainer targetStorage = storage(sourceSlot);
            RaidInventoryItem removed = targetStorage.removeAt(sourceIndex);
            if (removed == null) {
                return new AddResult(false, targetSlot, "Source item is no longer available.");
            }
            int moved = targetStorage.addPartialGridFirstFit(removed, true);
            if (moved < removed.count()) {
                targetStorage.addPartialGridFirstFit(removed.withCount(removed.count() - moved), true);
            }
            return new AddResult(moved > 0, targetSlot, moved > 0 ? "Stacked item in " + targetSlot.name().toLowerCase() + "." : "No compatible stack available.", moved);
        }

        AddResult result = switch (targetSlot) {
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER -> equipItem(item, targetSlot);
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> addToWeaponSlot(item, targetSlot);
            case BACKPACK -> addToBackpack(item);
            case VEST -> addToVest(item, profile);
            case SAFE_BOX -> addToSafeBox(item, profile);
        };
        if (result.movedCount() <= 0) {
            return result;
        }

        removeCountAt(sourceSlot, sourceIndex, result.movedCount());
        return result;
    }

    public AddResult moveToCell(RaidEquipmentSlot sourceSlot, int sourceIndex, RaidEquipmentSlot targetSlot, ItemCarryProfile profile, int x, int y, boolean rotated) {
        RaidInventoryItem item = itemAt(sourceSlot, sourceIndex);
        if (item == null) {
            return new AddResult(false, targetSlot, "Source item is no longer available.");
        }
        if (!isStorageSlot(targetSlot)) {
            return move(sourceSlot, sourceIndex, targetSlot, profile);
        }
        if (sourceSlot == targetSlot && isStorageSlot(sourceSlot)) {
            RaidStorageContainer targetStorage = storage(sourceSlot);
            RaidInventoryItem removed = targetStorage.removeAt(sourceIndex);
            if (removed == null) {
                return new AddResult(false, targetSlot, "Source item is no longer available.");
            }
            int moved = targetStorage.addPartialAt(removed, x, y, rotated, -1, true);
            if (moved < removed.count()) {
                targetStorage.addPartialAt(removed.withCount(removed.count() - moved), removed.gridX(), removed.gridY(), removed.rotated(), -1, true);
            }
            return new AddResult(moved > 0, targetSlot, moved > 0 ? "Moved item in " + targetSlot.name().toLowerCase() + "." : "Target cell is blocked.", moved);
        }

        AddResult result = switch (targetSlot) {
            case BACKPACK -> addToBackpackAt(item, x, y, rotated);
            case VEST -> addToVestAt(item, profile, x, y, rotated);
            case SAFE_BOX -> addToSafeBoxAt(item, profile, x, y, rotated);
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER -> equipItem(item, targetSlot);
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalArgumentException("Weapon slots are not grid targets.");
        };
        if (result.movedCount() <= 0) {
            return result;
        }

        removeCountAt(sourceSlot, sourceIndex, result.movedCount());
        return result;
    }

    public void clear() {
        helmet = null;
        armor = null;
        equippedBackpack = null;
        equippedVest = null;
        equippedSafeContainer = null;
        primaryWeapon = null;
        secondaryWeapon = null;
        selectedWeaponSlot = null;
        backpack.clear();
        vest.clear();
        safeBox.clear();
    }

    public AddResult setBackpack(BackpackDefinition definition) {
        RaidStorageContainer replacement = storageFor(definition);
        for (RaidInventoryItem item : backpack.items()) {
            if (replacement.addPartial(item) < item.count()) {
                return new AddResult(false, RaidEquipmentSlot.BACKPACK, "Backpack change rejected: existing contents do not fit in " + definition.name() + ".");
            }
        }

        this.loadout = new RaidLoadout(definition, loadout.vest(), loadout.safeBox());
        this.backpack = replacement;
        return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Raid backpack set to " + definition.name() + ".", 0);
    }

    public AddResult setVest(VestDefinition definition) {
        RaidStorageContainer replacement = storageFor(definition);
        for (RaidInventoryItem item : vest.items()) {
            if (replacement.addPartialPreservingPlacement(item, true) < item.count()) {
                return new AddResult(false, RaidEquipmentSlot.VEST, "Vest change rejected: existing contents do not fit in " + definition.name() + ".");
            }
        }

        this.loadout = new RaidLoadout(loadout.backpack(), definition, loadout.safeBox());
        this.vest = replacement;
        return new AddResult(true, RaidEquipmentSlot.VEST, "Raid vest set to " + definition.name() + ".", 0);
    }

    public AddResult setSafeBox(SafeContainerDefinition definition) {
        RaidStorageContainer replacement = storageFor(definition);
        for (RaidInventoryItem item : safeBox.items()) {
            if (replacement.addPartialPreservingPlacement(item, true) < item.count()) {
                return new AddResult(false, RaidEquipmentSlot.SAFE_BOX, "Safe container change rejected: existing contents do not fit in " + definition.name() + ".");
            }
        }

        this.loadout = new RaidLoadout(loadout.backpack(), loadout.vest(), definition);
        this.safeBox = replacement;
        return new AddResult(true, RaidEquipmentSlot.SAFE_BOX, "Raid safe container set to " + definition.name() + ".", 0);
    }

    public AddResult setEquipmentSlot(RaidEquipmentSlot slot, RaidInventoryItem item) {
        if (!isEquipmentSlot(slot)) {
            throw new IllegalArgumentException("Invalid equipment slot " + slot);
        }
        RaidInventoryItem previous = equipmentItem(slot);
        setEquipmentItem(slot, item);
        AddResult result = applyEquipmentLoadout();
        if (!result.success()) {
            setEquipmentItem(slot, previous);
            applyEquipmentLoadout();
            return result;
        }
        return new AddResult(true, slot, item == null ? "Cleared " + equipmentSlotName(slot) + "." : "Equipped " + item.displayName() + " in " + equipmentSlotName(slot) + ".", item == null ? 0 : item.count());
    }

    public AddResult equipItem(RaidInventoryItem item, RaidEquipmentSlot slot) {
        if (!isEquipmentSlot(slot)) {
            return new AddResult(false, slot, "Invalid equipment slot.");
        }
        if (item == null) {
            return new AddResult(false, slot, "Cannot equip an empty item.");
        }
        if (equipmentItem(slot) != null) {
            return new AddResult(false, slot, equipmentSlotName(slot) + " is already occupied.");
        }
        if (!canEquipItem(item, slot)) {
            return new AddResult(false, slot, item.displayName() + " cannot be equipped in " + equipmentSlotName(slot) + ".");
        }
        return setEquipmentSlot(slot, item);
    }

    public RaidInventoryItem equipmentItem(RaidEquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> helmet;
            case ARMOR -> armor;
            case EQUIPPED_BACKPACK -> equippedBackpack;
            case EQUIPPED_VEST -> equippedVest;
            case EQUIPPED_SAFE_CONTAINER -> equippedSafeContainer;
            case PRIMARY_WEAPON, SECONDARY_WEAPON, BACKPACK, VEST, SAFE_BOX -> null;
        };
    }

    private AddResult applyEquipmentLoadout() {
        RaidLoadout newLoadout = new RaidLoadout(
                RaidInventoryDefinitions.backpackForItem(equippedBackpack),
                RaidInventoryDefinitions.vestForItem(equippedVest),
                RaidInventoryDefinitions.safeContainerForItem(equippedSafeContainer));
        return replaceLoadoutPreservingContents(newLoadout);
    }

    private AddResult replaceLoadoutPreservingContents(RaidLoadout newLoadout) {
        RaidStorageContainer newBackpack = storageFor(newLoadout.backpack());
        RaidStorageContainer newVest = storageFor(newLoadout.vest());
        RaidStorageContainer newSafeBox = storageFor(newLoadout.safeBox());
        if (!copyStorageItems(backpack, newBackpack)) {
            return new AddResult(false, RaidEquipmentSlot.EQUIPPED_BACKPACK, "Backpack change rejected: existing contents do not fit in " + newLoadout.backpack().name() + ".");
        }
        if (!copyStorageItems(vest, newVest)) {
            return new AddResult(false, RaidEquipmentSlot.EQUIPPED_VEST, "Vest change rejected: existing contents do not fit in " + newLoadout.vest().name() + ".");
        }
        if (!copyStorageItems(safeBox, newSafeBox)) {
            return new AddResult(false, RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER, "Safe container change rejected: existing contents do not fit in " + newLoadout.safeBox().name() + ".");
        }

        this.loadout = newLoadout;
        this.backpack = newBackpack;
        this.vest = newVest;
        this.safeBox = newSafeBox;
        return new AddResult(true, RaidEquipmentSlot.BACKPACK, "Equipment grids updated.", 0);
    }

    private static boolean copyStorageItems(RaidStorageContainer source, RaidStorageContainer target) {
        for (RaidInventoryItem item : source.items()) {
            if (target.addPartialPreservingPlacement(item, true) < item.count()) {
                return false;
            }
        }
        return true;
    }

    private void setLoadout(RaidLoadout newLoadout) {
        this.loadout = newLoadout;
        this.backpack = storageFor(newLoadout.backpack());
        this.vest = storageFor(newLoadout.vest());
        this.safeBox = storageFor(newLoadout.safeBox());
    }

    private static RaidStorageContainer storageFor(BackpackDefinition definition) {
        return new RaidStorageContainer(definition.id(), definition.name(), definition.capacity(), definition.maxWeight(), definition.gridWidth(), definition.gridHeight());
    }

    private static RaidStorageContainer storageFor(VestDefinition definition) {
        return new RaidStorageContainer(definition.id(), definition.name(), definition.capacity(), definition.maxWeight(), definition.gridWidth(), definition.gridHeight());
    }

    private static RaidStorageContainer storageFor(SafeContainerDefinition definition) {
        return new RaidStorageContainer(definition.id(), definition.name(), definition.capacity(), definition.maxWeight(), definition.gridWidth(), definition.gridHeight());
    }

    public double totalWeight() {
        return weaponWeight(primaryWeapon) + weaponWeight(secondaryWeapon) + backpack.usedWeight() + vest.usedWeight() + safeBox.usedWeight();
    }

    public double maxCarryWeight() {
        return backpack.maxWeight() + vest.maxWeight() + safeBox.maxWeight();
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
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalArgumentException("Non-storage slots are not storage containers.");
        };
    }

    public RaidInventoryItem itemAt(RaidEquipmentSlot slot, int sourceIndex) {
        return switch (slot) {
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER -> equipmentItem(slot);
            case PRIMARY_WEAPON -> primaryWeapon;
            case SECONDARY_WEAPON -> secondaryWeapon;
            case BACKPACK -> backpack.itemAt(sourceIndex);
            case VEST -> vest.itemAt(sourceIndex);
            case SAFE_BOX -> safeBox.itemAt(sourceIndex);
        };
    }

    public RaidInventoryItem removeAt(RaidEquipmentSlot slot, int sourceIndex) {
        return switch (slot) {
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER -> removeEquipmentItem(slot);
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

    public AddResult replaceItemAt(RaidEquipmentSlot slot, int sourceIndex, RaidInventoryItem item) {
        if (item == null) {
            return new AddResult(false, slot, "Cannot replace with an empty item.");
        }
        if (isEquipmentSlot(slot)) {
            return setEquipmentSlot(slot, item);
        }
        boolean replaced = switch (slot) {
            case BACKPACK -> backpack.replaceAt(sourceIndex, item);
            case VEST -> vest.replaceAt(sourceIndex, item);
            case SAFE_BOX -> safeBox.replaceAt(sourceIndex, item);
            case PRIMARY_WEAPON, SECONDARY_WEAPON, HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER -> false;
        };
        return new AddResult(replaced, slot, replaced ? "Updated " + item.displayName() + "." : "Source item is no longer available.", replaced ? item.count() : 0);
    }

    public RaidInventoryItem removeCountAt(RaidEquipmentSlot slot, int sourceIndex, int count) {
        return switch (slot) {
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER -> removeAt(slot, sourceIndex);
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> removeAt(slot, sourceIndex);
            case BACKPACK -> backpack.removeCountAt(sourceIndex, count);
            case VEST -> vest.removeCountAt(sourceIndex, count);
            case SAFE_BOX -> safeBox.removeCountAt(sourceIndex, count);
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

    private void setEquipmentItem(RaidEquipmentSlot slot, RaidInventoryItem item) {
        switch (slot) {
            case HELMET -> helmet = item;
            case ARMOR -> armor = item;
            case EQUIPPED_BACKPACK -> equippedBackpack = item;
            case EQUIPPED_VEST -> equippedVest = item;
            case EQUIPPED_SAFE_CONTAINER -> equippedSafeContainer = item;
            case PRIMARY_WEAPON, SECONDARY_WEAPON, BACKPACK, VEST, SAFE_BOX -> throw new IllegalArgumentException("Invalid equipment slot " + slot);
        }
    }

    private RaidInventoryItem removeEquipmentItem(RaidEquipmentSlot slot) {
        RaidInventoryItem item = equipmentItem(slot);
        if (item == null) {
            return null;
        }
        setEquipmentItem(slot, null);
        AddResult result = applyEquipmentLoadout();
        if (!result.success()) {
            setEquipmentItem(slot, item);
            applyEquipmentLoadout();
            return null;
        }
        return item;
    }

    private static boolean isGun(RaidInventoryItem item) {
        return "guns".equalsIgnoreCase(item.category()) || item.lookupKey().startsWith("tacz:modern_kinetic_gun");
    }

    private static String weaponSlotName(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.PRIMARY_WEAPON ? "primary weapon" : "secondary weapon";
    }

    private static boolean isStorageSlot(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.BACKPACK || slot == RaidEquipmentSlot.VEST || slot == RaidEquipmentSlot.SAFE_BOX;
    }

    public static boolean isEquipmentSlot(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.HELMET
                || slot == RaidEquipmentSlot.ARMOR
                || slot == RaidEquipmentSlot.EQUIPPED_BACKPACK
                || slot == RaidEquipmentSlot.EQUIPPED_VEST
                || slot == RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER;
    }

    public static boolean canEquipItem(RaidInventoryItem item, RaidEquipmentSlot slot) {
        if (item == null) {
            return false;
        }
        String key = (item.lookupKey() + " " + item.itemId() + " " + item.displayName()).toLowerCase(java.util.Locale.ROOT);
        return switch (slot) {
            case HELMET -> key.contains("helmet");
            case ARMOR -> "armor".equalsIgnoreCase(item.category())
                    && (key.contains("chestplate") || key.contains("armor") || key.contains("plate_carrier"));
            case EQUIPPED_BACKPACK -> RaidInventoryDefinitions.backpackForItem(item).gridWidth() > 0;
            case EQUIPPED_VEST -> RaidInventoryDefinitions.vestForItem(item).gridWidth() > 0;
            case EQUIPPED_SAFE_CONTAINER -> RaidInventoryDefinitions.safeContainerForItem(item).gridWidth() > 0;
            case PRIMARY_WEAPON, SECONDARY_WEAPON, BACKPACK, VEST, SAFE_BOX -> false;
        };
    }

    private static String equipmentSlotName(RaidEquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> "helmet";
            case ARMOR -> "armor";
            case EQUIPPED_BACKPACK -> "backpack slot";
            case EQUIPPED_VEST -> "vest slot";
            case EQUIPPED_SAFE_CONTAINER -> "safe container slot";
            case PRIMARY_WEAPON, SECONDARY_WEAPON, BACKPACK, VEST, SAFE_BOX -> slot.name().toLowerCase();
        };
    }

    private static String movedMessage(String target, int moved, RaidInventoryItem item) {
        return "Moved " + moved + "x " + item.displayName() + " to " + target + ".";
    }

    private static double weaponWeight(RaidInventoryItem item) {
        return item == null ? 0.0D : item.totalWeight();
    }

    private static int weaponValue(RaidInventoryItem item) {
        return item == null ? 0 : item.totalValue();
    }

    public record AddResult(boolean success, RaidEquipmentSlot slot, String message, int movedCount) {
        public AddResult(boolean success, RaidEquipmentSlot slot, String message) {
            this(success, slot, message, 0);
        }
    }
}
