package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.network.ArmorMitigationSyncPayload;

public final class ClientArmorMitigationState {
    private static int armorPercent;
    private static int helmetPercent;
    private static int combinedPercent;

    private ClientArmorMitigationState() {
    }

    public static void handleSync(ArmorMitigationSyncPayload payload) {
        armorPercent = payload.armorPercent();
        helmetPercent = payload.helmetPercent();
        combinedPercent = payload.combinedPercent();
    }

    public static int armorPercent() {
        return armorPercent;
    }

    public static int helmetPercent() {
        return helmetPercent;
    }

    public static int combinedPercent() {
        return combinedPercent;
    }

    public static void clear() {
        armorPercent = 0;
        helmetPercent = 0;
        combinedPercent = 0;
    }
}
