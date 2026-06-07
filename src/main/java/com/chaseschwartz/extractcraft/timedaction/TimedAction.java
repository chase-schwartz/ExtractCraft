package com.chaseschwartz.extractcraft.timedaction;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

public record TimedAction(
        UUID playerId,
        UUID actionId,
        TimedActionType type,
        long startTick,
        int durationTicks,
        Optional<String> sourceReference,
        Optional<String> targetReference,
        boolean cancelOnDamage,
        boolean cancelOnMove,
        boolean cancelOnContainerClose,
        boolean allowOutsideRaid,
        String label) {

    public int elapsedTicks(long currentTick) {
        return Math.max(0, (int) Math.min(Integer.MAX_VALUE, currentTick - startTick));
    }

    public boolean completeAt(long currentTick) {
        return elapsedTicks(currentTick) >= durationTicks;
    }

    public ResourceLocation sourceItemIdOrNull() {
        return sourceReference.flatMap(TimedAction::parseResourceLocation).orElse(null);
    }

    public ResourceLocation targetItemIdOrNull() {
        return targetReference.flatMap(TimedAction::parseResourceLocation).orElse(null);
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        try {
            return Optional.of(ResourceLocation.parse(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
