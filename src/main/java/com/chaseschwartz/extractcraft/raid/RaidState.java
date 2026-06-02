package com.chaseschwartz.extractcraft.raid;

import java.util.List;
import java.util.UUID;

import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RaidState(ResourceKey<Level> returnDimension, Vec3 returnPosition, float returnYaw, float returnPitch,
        InventorySnapshot inventorySnapshot, GameType previousGameMode, long expiresAtGameTime, int lastTimerWarningSeconds, List<UUID> raidMobIds,
        RaidMapDefinition raidMap) {
    public RaidState {
        raidMobIds = List.copyOf(raidMobIds);
    }
}
