package com.chaseschwartz.extractcraft.itemidentity;

import java.util.Locale;
import java.util.Map;

public final class TaczDisplayNameResolver {
    private static final Map<String, String> KNOWN_NAMES = Map.ofEntries(
            Map.entry("tacz:ammo#tacz:9mm", "9mm Ammo"),
            Map.entry("tacz:ammo#tacz:57x28", "57x28 Ammo"),
            Map.entry("tacz:attachment#tacz:extended_mag_2", "Extended Mag II"),
            Map.entry("tacz:attachment#tacz:laser_peq6", "Laser PEQ-6"),
            Map.entry("tacz:modern_kinetic_gun#tacz:ak47", "AK-47"),
            Map.entry("tacz:modern_kinetic_gun#tacz:glock_17", "Glock 17"),
            Map.entry("tacz:modern_kinetic_gun#tacz:m249", "M249"),
            Map.entry("tacz:modern_kinetic_gun#tacz:m95", "M95"),
            Map.entry("tacz:modern_kinetic_gun#tacz:rpg7", "RPG-7"));

    private TaczDisplayNameResolver() {
    }

    public static String displayName(String normalizedKey, String fallbackName) {
        String known = KNOWN_NAMES.get(normalizedKey);
        if (known != null) {
            return known;
        }
        if (!isTaczVariantKey(normalizedKey)) {
            return fallbackName;
        }

        String variant = normalizedKey.substring(normalizedKey.indexOf('#') + 1);
        String path = variant.contains(":") ? variant.substring(variant.indexOf(':') + 1) : variant;
        String suffix = "";
        if (normalizedKey.startsWith("tacz:ammo#")) {
            suffix = " Ammo";
        }
        return titleCaseVariant(path) + suffix;
    }

    public static boolean isTaczVariantKey(String normalizedKey) {
        return normalizedKey.startsWith("tacz:") && normalizedKey.contains("#");
    }

    private static String titleCaseVariant(String raw) {
        String[] words = raw.replace('-', '_').split("_+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(formatWord(word));
        }
        return builder.isEmpty() ? raw : builder.toString();
    }

    private static String formatWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.matches("[a-z]*\\d+[a-z\\d]*") || lower.length() <= 3 && lower.matches("[a-z]+")) {
            return lower.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
