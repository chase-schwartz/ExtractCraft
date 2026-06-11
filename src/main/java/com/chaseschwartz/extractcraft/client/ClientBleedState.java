package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.network.BleedStateSyncPayload;
import com.chaseschwartz.extractcraft.raid.BleedStatus;

public final class ClientBleedState {
    private static BleedStatus status = BleedStatus.NONE;
    private static int lastDamagePulse;
    private static long lastDamagePulseMillis;

    private ClientBleedState() {
    }

    public static void handleSync(BleedStateSyncPayload payload) {
        try {
            status = BleedStatus.valueOf(payload.status());
        } catch (RuntimeException ignored) {
            status = BleedStatus.NONE;
        }
        if (payload.damagePulse() > 0 && payload.damagePulse() != lastDamagePulse) {
            lastDamagePulse = payload.damagePulse();
            lastDamagePulseMillis = net.minecraft.Util.getMillis();
        }
    }

    public static BleedStatus status() {
        return status;
    }

    public static long lastDamagePulseMillis() {
        return lastDamagePulseMillis;
    }

    public static void clear() {
        status = BleedStatus.NONE;
        lastDamagePulse = 0;
        lastDamagePulseMillis = 0L;
    }
}
