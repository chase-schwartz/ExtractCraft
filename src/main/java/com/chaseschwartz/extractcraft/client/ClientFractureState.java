package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.network.FractureStateSyncPayload;

public final class ClientFractureState {
    private static boolean fractured;

    private ClientFractureState() {
    }

    public static void handleSync(FractureStateSyncPayload payload) {
        fractured = payload.fractured();
    }

    public static boolean fractured() {
        return fractured;
    }

    public static void clear() {
        fractured = false;
    }
}
