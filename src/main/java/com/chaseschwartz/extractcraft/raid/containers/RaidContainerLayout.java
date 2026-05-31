package com.chaseschwartz.extractcraft.raid.containers;

import java.util.List;

import net.minecraft.core.BlockPos;

public record RaidContainerLayout(String mapId, BlockPos layoutOrigin, List<RaidContainerEntry> containers) {
    public RaidContainerLayout {
        containers = List.copyOf(containers);
    }

    public long activeCount() {
        return containers.stream().filter(RaidContainerEntry::activeLootContainer).count();
    }
}
