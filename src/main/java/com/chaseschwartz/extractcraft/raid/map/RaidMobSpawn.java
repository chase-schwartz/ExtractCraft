package com.chaseschwartz.extractcraft.raid.map;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

public record RaidMobSpawn(EntityType<? extends Mob> entityType, double x, double y, double z) {
}
