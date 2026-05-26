package com.chaseschwartz.extractcraft.raid.map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public record RaidStructurePlacement(ResourceLocation templateId, BlockPos origin, Rotation rotation, Mirror mirror, boolean includeEntities) {
}
