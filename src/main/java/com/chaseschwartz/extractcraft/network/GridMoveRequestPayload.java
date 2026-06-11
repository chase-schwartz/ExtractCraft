package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GridMoveRequestPayload(int transactionId, int menuId, int operation, int sourceSlotId, int sourceIndex, int targetSlotId, int targetCell) implements CustomPacketPayload {
    public static final int ACTIVE_CONTAINER_TO_RAID_CELL = 1;
    public static final int ACTIVE_RAID_TO_RAID_CELL = 2;
    public static final int ACTIVE_RAID_TO_CONTAINER = 3;
    public static final int ACTIVE_RAID_DROP = 4;
    public static final int ACTIVE_RAID_SPLIT = 5;
    public static final int ACTIVE_CONTAINER_SPLIT = 6;
    public static final int ACTIVE_CARRIED_TO_RAID_CELL = 7;
    public static final int ACTIVE_CARRIED_TO_CONTAINER = 8;
    public static final int ACTIVE_RAID_REPAIR = 9;
    public static final int BASE_STASH_TO_BASE_CELL = 10;
    public static final int BASE_BASE_TO_BASE_CELL = 11;
    public static final int BASE_BASE_TO_STASH = 12;
    public static final int BASE_BASE_DROP = 13;
    public static final int BASE_STASH_DROP = 14;
    public static final int BASE_STASH_QUICK_TO_BASE = 15;
    public static final int BASE_STASH_TO_STASH_CELL = 16;
    public static final int BASE_STASH_SPLIT = 17;
    public static final int BASE_BASE_SPLIT = 18;
    public static final int BASE_CARRIED_TO_STASH_CELL = 19;
    public static final int BASE_CARRIED_TO_BASE_CELL = 20;
    public static final int ACTIVE_RAID_USE = 21;

    public static final CustomPacketPayload.Type<GridMoveRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "grid_move_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GridMoveRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(GridMoveRequestPayload::write, GridMoveRequestPayload::new);

    private GridMoveRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(transactionId);
        buffer.writeVarInt(menuId);
        buffer.writeVarInt(operation);
        buffer.writeVarInt(sourceSlotId);
        buffer.writeVarInt(sourceIndex);
        buffer.writeVarInt(targetSlotId);
        buffer.writeVarInt(targetCell);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
