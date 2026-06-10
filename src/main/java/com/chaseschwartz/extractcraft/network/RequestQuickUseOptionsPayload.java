package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestQuickUseOptionsPayload() implements CustomPacketPayload {
    public static final RequestQuickUseOptionsPayload INSTANCE = new RequestQuickUseOptionsPayload();
    public static final CustomPacketPayload.Type<RequestQuickUseOptionsPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "request_quick_use_options"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestQuickUseOptionsPayload> STREAM_CODEC = CustomPacketPayload.codec(RequestQuickUseOptionsPayload::write, RequestQuickUseOptionsPayload::new);

    private RequestQuickUseOptionsPayload(RegistryFriendlyByteBuf buffer) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
