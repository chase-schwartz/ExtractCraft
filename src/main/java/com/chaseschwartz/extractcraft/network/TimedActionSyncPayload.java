package com.chaseschwartz.extractcraft.network;

import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimedActionSyncPayload(UUID actionId, String actionType, String label, int durationTicks, int elapsedTicks, int status, String message) implements CustomPacketPayload {
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_CANCELED = 1;
    public static final int STATUS_COMPLETE = 2;

    public static final CustomPacketPayload.Type<TimedActionSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "timed_action_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimedActionSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(TimedActionSyncPayload::write, TimedActionSyncPayload::new);

    private TimedActionSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(96), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(128));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(actionId);
        buffer.writeUtf(actionType, 64);
        buffer.writeUtf(label, 96);
        buffer.writeVarInt(durationTicks);
        buffer.writeVarInt(elapsedTicks);
        buffer.writeVarInt(status);
        buffer.writeUtf(message, 128);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
