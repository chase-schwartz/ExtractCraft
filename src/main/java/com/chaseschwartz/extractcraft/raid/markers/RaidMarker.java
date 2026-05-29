package com.chaseschwartz.extractcraft.raid.markers;

import net.minecraft.core.BlockPos;

public record RaidMarker(RaidMarkerType type, BlockPos absolutePos, BlockPos relativePos) {
}
