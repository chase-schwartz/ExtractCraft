package com.chaseschwartz.extractcraft.raid.markers;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.world.level.block.Block;

public enum RaidMarkerType {
    PLAYER_SPAWN("PLAYER_SPAWN", ExtractCraft.PLAYER_SPAWN_MARKER),
    EXTRACTION("EXTRACTION", ExtractCraft.EXTRACTION_MARKER),
    LOOT("LOOT", ExtractCraft.LOOT_MARKER),
    RARE_LOOT("RARE_LOOT", ExtractCraft.RARE_LOOT_MARKER),
    MOB_SPAWN("MOB_SPAWN", ExtractCraft.MOB_SPAWN_MARKER);

    private final String serializedName;
    private final Supplier<? extends Block> block;

    RaidMarkerType(String serializedName, Supplier<? extends Block> block) {
        this.serializedName = serializedName;
        this.block = block;
    }

    public String serializedName() {
        return serializedName;
    }

    public Block block() {
        return block.get();
    }

    public static Optional<RaidMarkerType> byBlock(Block block) {
        return Arrays.stream(values())
                .filter(type -> type.block() == block)
                .findFirst();
    }

    public static Optional<RaidMarkerType> bySerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst();
    }
}
