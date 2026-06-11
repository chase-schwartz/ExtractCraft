package com.chaseschwartz.extractcraft.raid;

public enum BleedStatus {
    NONE,
    LIGHT,
    HEAVY;

    public boolean active() {
        return this != NONE;
    }

    public String label() {
        return switch (this) {
            case LIGHT -> "Light Bleed";
            case HEAVY -> "Heavy Bleed";
            case NONE -> "";
        };
    }
}
