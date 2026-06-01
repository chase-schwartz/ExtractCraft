package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidStateSyncPayload(boolean inRaid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RaidStateSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "raid_state_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidStateSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(RaidStateSyncPayload::write, RaidStateSyncPayload::new);

    private RaidStateSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(inRaid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
