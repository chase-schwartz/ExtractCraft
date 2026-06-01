package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenVanillaInventoryPayload() implements CustomPacketPayload {
    public static final OpenVanillaInventoryPayload INSTANCE = new OpenVanillaInventoryPayload();
    public static final CustomPacketPayload.Type<OpenVanillaInventoryPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "open_vanilla_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVanillaInventoryPayload> STREAM_CODEC = CustomPacketPayload.codec(OpenVanillaInventoryPayload::write, OpenVanillaInventoryPayload::new);

    private OpenVanillaInventoryPayload(RegistryFriendlyByteBuf buffer) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
