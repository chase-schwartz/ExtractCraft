package com.chaseschwartz.extractcraft.timedaction;

import java.util.Locale;

public enum TimedActionType {
    DEBUG_TEST,
    REPAIR_HELMET,
    REPAIR_ARMOR,
    REPAIR_BACKPACK,
    USE_MED,
    USE_BANDAGE,
    USE_SPLINT,
    USE_PAINKILLER,
    USE_FOOD,
    UNKNOWN;

    public static TimedActionType fromNetworkName(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
