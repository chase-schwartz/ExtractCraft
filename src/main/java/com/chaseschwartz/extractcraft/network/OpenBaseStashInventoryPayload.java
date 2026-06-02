package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBaseStashInventoryPayload() implements CustomPacketPayload {
    public static final OpenBaseStashInventoryPayload INSTANCE = new OpenBaseStashInventoryPayload();
    public static final CustomPacketPayload.Type<OpenBaseStashInventoryPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "open_base_stash_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBaseStashInventoryPayload> STREAM_CODEC = CustomPacketPayload.codec(OpenBaseStashInventoryPayload::write, OpenBaseStashInventoryPayload::new);

    private OpenBaseStashInventoryPayload(RegistryFriendlyByteBuf buffer) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
