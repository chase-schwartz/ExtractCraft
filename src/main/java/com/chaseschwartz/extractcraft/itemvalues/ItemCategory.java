package com.chaseschwartz.extractcraft.itemvalues;

public enum ItemCategory {
    JUNK,
    SCRAP_METAL,
    ELECTRONICS,
    OPTICS,
    TOOLS,
    INDUSTRIAL,
    VALUABLES,
    ACCESS,
    INTEL,
    CONTRABAND,
    TROPHY,
    POWER,
    SECURITY,
    SURVIVAL,
    QUEST,
    FOOD,
    MEDICAL,
    MEDICAL_TECH,
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
