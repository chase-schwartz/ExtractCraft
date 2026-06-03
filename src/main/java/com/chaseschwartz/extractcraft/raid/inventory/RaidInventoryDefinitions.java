package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public class RaidInventoryDefinitions {
    private static final Map<String, BackpackDefinition> BACKPACKS = Map.of(
            "no_backpack", new BackpackDefinition("no_backpack", "No Backpack", 0, 0, 0.0D),
            "small_backpack", new BackpackDefinition("small_backpack", "Small Backpack", 6, 6, 20.0D),
            "medium_backpack", new BackpackDefinition("medium_backpack", "Medium Backpack", 7, 7, 35.0D),
            "large_backpack", new BackpackDefinition("large_backpack", "Large Backpack", 8, 8, 50.0D));

    private static final Map<String, VestDefinition> VESTS = Map.of(
            "no_vest", new VestDefinition("no_vest", "No Vest", 0, 0, 0.0D, Set.of()),
            "basic_vest", new VestDefinition(
            "basic_vest",
            "Basic Vest",
            4,
            3,
            12.0D,
            Set.of(ItemCategory.AMMO, ItemCategory.MAGAZINES, ItemCategory.MEDICAL, ItemCategory.FOOD)));

    private static final Map<String, SafeContainerDefinition> SAFE_CONTAINERS = Map.of(
            "no_safe_container", new SafeContainerDefinition("no_safe_container", "No Safe Container", 0, 0, 0.0D),
            "alpha_safe_box", new SafeContainerDefinition("alpha_safe_box", "Alpha Safe Box", 3, 3, 8.0D));
    private static final RaidLoadout DEFAULT_LOADOUT = new RaidLoadout(BACKPACKS.get("no_backpack"), VESTS.get("no_vest"), SAFE_CONTAINERS.get("no_safe_container"));

    private RaidInventoryDefinitions() {
    }

    public static RaidLoadout defaultLoadout() {
        return DEFAULT_LOADOUT;
    }

    public static Optional<BackpackDefinition> backpack(String id) {
        return Optional.ofNullable(BACKPACKS.get(id));
    }

    public static Optional<VestDefinition> vest(String id) {
        return Optional.ofNullable(VESTS.get(id));
    }

    public static Optional<SafeContainerDefinition> safeContainer(String id) {
        return Optional.ofNullable(SAFE_CONTAINERS.get(id));
    }

    public static BackpackDefinition backpackForItem(RaidInventoryItem item) {
        if (item == null) {
            return BACKPACKS.get("no_backpack");
        }
        String key = normalizedItemText(item);
        if (key.contains("large_backpack") || key.contains("large backpack") || key.contains("chest")) {
            return BACKPACKS.get("large_backpack");
        }
        if (key.contains("medium_backpack") || key.contains("medium backpack") || key.contains("barrel")) {
            return BACKPACKS.get("medium_backpack");
        }
        if (key.contains("backpack") || key.contains("bundle")) {
            return BACKPACKS.get("small_backpack");
        }
        return BACKPACKS.get("no_backpack");
    }

    public static VestDefinition vestForItem(RaidInventoryItem item) {
        if (item == null) {
            return VESTS.get("no_vest");
        }
        String key = normalizedItemText(item);
        if (key.contains("vest") || key.contains("rig") || key.contains("leather_chestplate") || key.contains("chainmail_chestplate")) {
            return VESTS.get("basic_vest");
        }
        return VESTS.get("no_vest");
    }

    public static SafeContainerDefinition safeContainerForItem(RaidInventoryItem item) {
        if (item == null) {
            return SAFE_CONTAINERS.get("no_safe_container");
        }
        String key = normalizedItemText(item);
        if (key.contains("safe") || key.contains("secure") || key.contains("alpha") || key.contains("ender_chest")) {
            return SAFE_CONTAINERS.get("alpha_safe_box");
        }
        return SAFE_CONTAINERS.get("no_safe_container");
    }

    public static String backpackIds() {
        return String.join(", ", BACKPACKS.keySet());
    }

    private static String normalizedItemText(RaidInventoryItem item) {
        return (item.lookupKey() + " " + item.itemId() + " " + item.displayName()).toLowerCase(Locale.ROOT);
    }
}
