package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public record RaidLootContainer(BlockPos pos, Block containerBlock, List<RaidLootItem> loot) {
    public RaidLootContainer {
        loot = List.copyOf(loot);
    }
}
