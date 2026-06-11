package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BleedStateSyncPayload(String status, int damagePulse) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BleedStateSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "bleed_state_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BleedStateSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(BleedStateSyncPayload::write, BleedStateSyncPayload::new);

    private BleedStateSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(16), buffer.readVarInt());
    }

    public BleedStateSyncPayload(String status) {
        this(status, 0);
    }

    public BleedStateSyncPayload {
        status = status == null ? "NONE" : status;
        damagePulse = Math.max(0, damagePulse);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(status, 16);
        buffer.writeVarInt(damagePulse);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
