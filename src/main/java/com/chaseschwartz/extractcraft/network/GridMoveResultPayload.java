package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GridMoveResultPayload(int transactionId, boolean success, String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GridMoveResultPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "grid_move_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GridMoveResultPayload> STREAM_CODEC = CustomPacketPayload.codec(GridMoveResultPayload::write, GridMoveResultPayload::new);

    private GridMoveResultPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(512));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(transactionId);
        buffer.writeBoolean(success);
        buffer.writeUtf(message, 512);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
