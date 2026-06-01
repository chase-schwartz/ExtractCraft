package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRaidInventoryPayload() implements CustomPacketPayload {
    public static final OpenRaidInventoryPayload INSTANCE = new OpenRaidInventoryPayload();
    public static final CustomPacketPayload.Type<OpenRaidInventoryPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "open_raid_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRaidInventoryPayload> STREAM_CODEC = CustomPacketPayload.codec(OpenRaidInventoryPayload::write, OpenRaidInventoryPayload::new);

    private OpenRaidInventoryPayload(RegistryFriendlyByteBuf buffer) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
