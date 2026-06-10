package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetQuickUseSelectionPayload(String itemId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetQuickUseSelectionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "set_quick_use_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetQuickUseSelectionPayload> STREAM_CODEC = CustomPacketPayload.codec(SetQuickUseSelectionPayload::write, SetQuickUseSelectionPayload::new);

    private SetQuickUseSelectionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(128));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(itemId == null ? "" : itemId, 128);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
