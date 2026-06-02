package com.chaseschwartz.extractcraft.network;

import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PickupManagedDropPayload(UUID entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PickupManagedDropPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "pickup_managed_drop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PickupManagedDropPayload> STREAM_CODEC = CustomPacketPayload.codec(PickupManagedDropPayload::write, PickupManagedDropPayload::new);

    private PickupManagedDropPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(entityId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
