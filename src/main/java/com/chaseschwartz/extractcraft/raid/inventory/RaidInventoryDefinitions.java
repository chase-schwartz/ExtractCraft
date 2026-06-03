package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

public class RaidInventoryDefinitions {
    private static final Map<String, BackpackDefinition> BACKPACKS = Map.of(
            "no_backpack", new BackpackDefinition("no_backpack", "No Backpack", 0, 0, 0.0D),
            "sparrow_sling_pack", new BackpackDefinition("sparrow_sling_pack", "Sparrow Sling Pack", 4, 4, 14.0D),
            "fieldrunner_pack", new BackpackDefinition("fieldrunner_pack", "Fieldrunner Pack", 5, 5, 22.0D),
            "mule_tactical_pack", new BackpackDefinition("mule_tactical_pack", "Mule Tactical Pack", 6, 6, 32.0D),
            "atlas_raid_pack", new BackpackDefinition("atlas_raid_pack", "Atlas Raid Pack", 7, 7, 44.0D),
            "atlas_raid_pack_mk2", new BackpackDefinition("atlas_raid_pack_mk2", "Atlas Raid Pack Mk II", 8, 8, 56.0D),
            "small_backpack", new BackpackDefinition("small_backpack", "Small Backpack", 6, 6, 20.0D),
            "medium_backpack", new BackpackDefinition("medium_backpack", "Medium Backpack", 7, 7, 35.0D),
            "large_backpack", new BackpackDefinition("large_backpack", "Large Backpack", 8, 8, 50.0D));

    private static final Map<String, VestDefinition> VESTS = Map.of(
            "no_vest", new VestDefinition("no_vest", "No Vest", 0, 0, 0.0D, Set.of()),
            "scout_chest_rig", new VestDefinition("scout_chest_rig", "Scout Chest Rig", 3, 2, 8.0D, Set.of()),
            "rangefinder_tactical_vest", new VestDefinition("rangefinder_tactical_vest", "Rangefinder Tactical Vest", 4, 2, 12.0D, Set.of()),
            "operator_load_bearing_vest", new VestDefinition("operator_load_bearing_vest", "Operator Load-Bearing Vest", 4, 3, 16.0D, Set.of()),
            "specter_combat_rig", new VestDefinition("specter_combat_rig", "Specter Combat Rig", 5, 3, 20.0D, Set.of()),
            "arsenal_elite_vest", new VestDefinition("arsenal_elite_vest", "Arsenal Elite Vest", 5, 4, 24.0D, Set.of()),
            "basic_vest", new VestDefinition(
            "basic_vest",
            "Basic Vest",
            4,
            3,
            12.0D,
            Set.of(ItemCategory.AMMO, ItemCategory.MAGAZINES, ItemCategory.MEDICAL, ItemCategory.FOOD)));

    private static final Map<String, SafeContainerDefinition> SAFE_CONTAINERS = Map.of(
            "no_safe_container", new SafeContainerDefinition("no_safe_container", "No Safe Container", 0, 0, 0.0D),
            "pioneer_lockbox", new SafeContainerDefinition("pioneer_lockbox", "Pioneer Lockbox", 2, 2, 8.0D),
            "blacksite_secure_case", new SafeContainerDefinition("blacksite_secure_case", "Blacksite Secure Case", 3, 2, 10.0D),
            "omega_safe_container", new SafeContainerDefinition("omega_safe_container", "Omega Safe Container", 3, 3, 12.0D),
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
        if (key.contains("atlas_raid_pack_mk2")) {
            return BACKPACKS.get("atlas_raid_pack_mk2");
        }
        if (key.contains("atlas_raid_pack")) {
            return BACKPACKS.get("atlas_raid_pack");
        }
        if (key.contains("mule_tactical_pack")) {
            return BACKPACKS.get("mule_tactical_pack");
        }
        if (key.contains("fieldrunner_pack")) {
            return BACKPACKS.get("fieldrunner_pack");
        }
        if (key.contains("sparrow_sling_pack")) {
            return BACKPACKS.get("sparrow_sling_pack");
        }
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
        if (key.contains("arsenal_elite_vest")) {
            return VESTS.get("arsenal_elite_vest");
        }
        if (key.contains("specter_combat_rig")) {
            return VESTS.get("specter_combat_rig");
        }
        if (key.contains("operator_load_bearing_vest")) {
            return VESTS.get("operator_load_bearing_vest");
        }
        if (key.contains("rangefinder_tactical_vest")) {
            return VESTS.get("rangefinder_tactical_vest");
        }
        if (key.contains("scout_chest_rig")) {
            return VESTS.get("scout_chest_rig");
        }
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
        if (key.contains("omega_safe_container")) {
            return SAFE_CONTAINERS.get("omega_safe_container");
        }
        if (key.contains("blacksite_secure_case")) {
            return SAFE_CONTAINERS.get("blacksite_secure_case");
        }
        if (key.contains("pioneer_lockbox")) {
            return SAFE_CONTAINERS.get("pioneer_lockbox");
        }
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
