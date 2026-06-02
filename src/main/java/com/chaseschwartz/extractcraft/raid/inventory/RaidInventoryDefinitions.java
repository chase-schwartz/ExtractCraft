package com.chaseschwartz.extractcraft.raid.inventory;

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

    private static final VestDefinition BASIC_VEST = new VestDefinition(
            "basic_vest",
            "Basic Vest",
            4,
            3,
            12.0D,
            Set.of(ItemCategory.AMMO, ItemCategory.MAGAZINES, ItemCategory.MEDICAL, ItemCategory.FOOD));

    private static final SafeContainerDefinition ALPHA_SAFE_BOX = new SafeContainerDefinition("alpha_safe_box", "Alpha Safe Box", 3, 3, 8.0D);
    private static final RaidLoadout DEFAULT_LOADOUT = new RaidLoadout(BACKPACKS.get("no_backpack"), BASIC_VEST, ALPHA_SAFE_BOX);

    private RaidInventoryDefinitions() {
    }

    public static RaidLoadout defaultLoadout() {
        return DEFAULT_LOADOUT;
    }

    public static Optional<BackpackDefinition> backpack(String id) {
        return Optional.ofNullable(BACKPACKS.get(id));
    }

    public static String backpackIds() {
        return String.join(", ", BACKPACKS.keySet());
    }
}
