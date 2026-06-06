package com.chaseschwartz.extractcraft.itemvalues;

public enum ItemCategory {
    JUNK,
    SCRAP_METAL,
    ELECTRONICS,
    OPTICS,
    TOOLS,
    INDUSTRIAL,
    INDUSTRIAL_TOOLS,
    VALUABLES,
    CIVILIAN_VALUABLE,
    LUXURY_COLLECTIBLE,
    PAWN_COLLECTIBLE,
    ACCESS,
    OFFICE_ADMIN,
    INTEL,
    CONTRABAND,
    TROPHY,
    JACKPOT,
    WEIRD_LORE,
    POWER,
    SECURITY,
    SURVIVAL,
    SURVIVAL_UTILITY,
    QUEST,
    FOOD,
    FOOD_COMFORT,
    MEDICAL,
    MEDICAL_TECH,
    HOME_MEDICAL,
    FIELD_MEDICAL,
    LAB_BIO,
    RARE_MEDICAL,
    GUNS,
    AMMO,
    MAGAZINES,
    ATTACHMENTS,
    WEAPON_PARTS,
    ARMOR,
    ARMOR_PARTS,
    ARMOR_MATERIALS;

    public static ItemCategory fromJson(String value) {
        if (value == null || value.isBlank()) {
            return JUNK;
        }

        return ItemCategory.valueOf(value.trim().toUpperCase());
    }
}
