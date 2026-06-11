package com.chaseschwartz.extractcraft.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum RaiderRole {
    SCAV("scav", 0, false, false),
    GUARD("guard", 18, true, false),
    PATROL("patrol", 24, true, false),
    SNIPER("sniper", 14, true, true),
    PMC("pmc", 0, false, false);

    private final String id;
    private final int leashRadius;
    private final boolean leashed;
    private final boolean stationary;

    RaiderRole(String id, int leashRadius, boolean leashed, boolean stationary) {
        this.id = id;
        this.leashRadius = leashRadius;
        this.leashed = leashed;
        this.stationary = stationary;
    }

    public String id() {
        return id;
    }

    public int leashRadius() {
        return leashRadius;
    }

    public boolean isLeashed() {
        return leashed;
    }

    public boolean isStationary() {
        return stationary;
    }

    public static RaiderRole fallback() {
        return SCAV;
    }

    public static Optional<RaiderRole> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(role -> role.id.equals(normalized) || role.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static RaiderRole fromSavedName(String value) {
        return parse(value).orElse(fallback());
    }

    public static String validRoles() {
        return Arrays.stream(values())
                .map(RaiderRole::id)
                .collect(Collectors.joining(", "));
    }
}
