package com.chaseschwartz.extractcraft.raid.map;

import java.util.List;

import net.minecraft.core.BlockPos;

public record RaidLootChest(BlockPos pos, List<RaidLootItem> loot) {
    public RaidLootChest {
        loot = List.copyOf(loot);
    }
}
