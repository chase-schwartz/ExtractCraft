package com.chaseschwartz.extractcraft.itemvalues;

public enum ItemCategory {
    JUNK,
    SCRAP_METAL,
    ELECTRONICS,
    TOOLS,
    VALUABLES,
    ACCESS,
    QUEST,
    FOOD,
    MEDICAL,
    GUNS,
    AMMO,
    MAGAZINES,
    ATTACHMENTS,
    WEAPON_PARTS,
    ARMOR,
    ARMOR_PARTS;

    public static ItemCategory fromJson(String value) {
        if (value == null || value.isBlank()) {
            return JUNK;
        }

        return ItemCategory.valueOf(value.trim().toUpperCase());
    }
}
