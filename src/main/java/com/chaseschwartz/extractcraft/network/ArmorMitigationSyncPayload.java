package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArmorMitigationSyncPayload(int armorPercent, int helmetPercent, int combinedPercent, int pulseSequence) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArmorMitigationSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "armor_mitigation_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorMitigationSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(ArmorMitigationSyncPayload::write, ArmorMitigationSyncPayload::new);

    private ArmorMitigationSyncPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public ArmorMitigationSyncPayload(int armorPercent, int helmetPercent, int combinedPercent) {
        this(armorPercent, helmetPercent, combinedPercent, 0);
    }

    public ArmorMitigationSyncPayload {
        armorPercent = clampPercent(armorPercent);
        helmetPercent = clampPercent(helmetPercent);
        combinedPercent = clampPercent(combinedPercent);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(armorPercent);
        buffer.writeVarInt(helmetPercent);
        buffer.writeVarInt(combinedPercent);
        buffer.writeVarInt(pulseSequence);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
