package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UseQuickUseSelectionPayload() implements CustomPacketPayload {
    public static final UseQuickUseSelectionPayload INSTANCE = new UseQuickUseSelectionPayload();
    public static final CustomPacketPayload.Type<UseQuickUseSelectionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "use_quick_use_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UseQuickUseSelectionPayload> STREAM_CODEC = CustomPacketPayload.codec(UseQuickUseSelectionPayload::write, UseQuickUseSelectionPayload::new);

    private UseQuickUseSelectionPayload(RegistryFriendlyByteBuf buffer) {
        this();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
