package com.chaseschwartz.extractcraft.client;

import java.util.List;
import java.util.Optional;

import com.chaseschwartz.extractcraft.network.QuickUseStatePayload;

public final class ClientQuickUseState {
    private static String selectedItemId = "";
    private static List<QuickUseStatePayload.Option> options = List.of();

    private ClientQuickUseState() {
    }

    public static void handleSync(QuickUseStatePayload payload) {
        selectedItemId = payload.selectedItemId() == null ? "" : payload.selectedItemId();
        options = payload.options();
    }

    public static void clear() {
        selectedItemId = "";
        options = List.of();
    }

    public static Optional<String> selectedItemId() {
        return selectedItemId.isBlank() ? Optional.empty() : Optional.of(selectedItemId);
    }

    public static Optional<QuickUseStatePayload.Option> selectedOption() {
        if (selectedItemId.isBlank()) {
            return Optional.empty();
        }
        return options.stream().filter(option -> selectedItemId.equals(option.itemId())).findFirst();
    }

    public static List<QuickUseStatePayload.Option> options() {
        return options;
    }

    public static boolean hasOptions() {
        return !options.isEmpty();
    }
}
