package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FractureStateSyncPayload(boolean fractured) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FractureStateSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "fracture_state_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FractureStateSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(FractureStateSyncPayload::write, FractureStateSyncPayload::new);

    private FractureStateSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(fractured);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
