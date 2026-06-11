package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.network.ArmorMitigationSyncPayload;

public final class ClientArmorMitigationState {
    private static int armorPercent;
    private static int helmetPercent;
    private static int combinedPercent;
    private static int pulseSequence;
    private static long lastPulseMillis;

    private ClientArmorMitigationState() {
    }

    public static void handleSync(ArmorMitigationSyncPayload payload) {
        armorPercent = payload.armorPercent();
        helmetPercent = payload.helmetPercent();
        combinedPercent = payload.combinedPercent();
        if (payload.pulseSequence() > 0 && payload.pulseSequence() != pulseSequence) {
            pulseSequence = payload.pulseSequence();
            lastPulseMillis = net.minecraft.Util.getMillis();
        }
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

    public static long lastPulseMillis() {
        return lastPulseMillis;
    }

    public static void clear() {
        armorPercent = 0;
        helmetPercent = 0;
        combinedPercent = 0;
        pulseSequence = 0;
        lastPulseMillis = 0L;
    }
}
