package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectRaidWeaponPayload(int selection) implements CustomPacketPayload {
    public static final int SELECT_PRIMARY = 0;
    public static final int SELECT_SECONDARY = 1;
    public static final int CYCLE_FORWARD = 2;
    public static final int CYCLE_BACKWARD = 3;

    public static final CustomPacketPayload.Type<SelectRaidWeaponPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "select_raid_weapon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectRaidWeaponPayload> STREAM_CODEC = CustomPacketPayload.codec(SelectRaidWeaponPayload::write, SelectRaidWeaponPayload::new);

    private SelectRaidWeaponPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(selection);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
