package com.chaseschwartz.extractcraft.network;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BulkBaseInventoryActionPayload(int transactionId, int menuId, int action, int[] stashDisplayIndexes, int[] baseSlotIds, int[] baseIndexes) implements CustomPacketPayload {
    public static final int ACTION_SELL = 0;
    public static final int ACTION_DROP = 1;
    public static final int ACTION_TRASH = 2;

    public static final CustomPacketPayload.Type<BulkBaseInventoryActionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "bulk_base_inventory_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BulkBaseInventoryActionPayload> STREAM_CODEC = CustomPacketPayload.codec(BulkBaseInventoryActionPayload::write, BulkBaseInventoryActionPayload::new);

    private BulkBaseInventoryActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), readArray(buffer), readArray(buffer), readArray(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(transactionId);
        buffer.writeVarInt(menuId);
        buffer.writeVarInt(action);
        writeArray(buffer, stashDisplayIndexes);
        writeArray(buffer, baseSlotIds);
        writeArray(buffer, baseIndexes);
    }

    private static int[] readArray(RegistryFriendlyByteBuf buffer) {
        int length = Math.max(0, Math.min(512, buffer.readVarInt()));
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = buffer.readVarInt();
        }
        return values;
    }

    private static void writeArray(RegistryFriendlyByteBuf buffer, int[] values) {
        int[] safeValues = values == null ? new int[0] : values;
        buffer.writeVarInt(safeValues.length);
        for (int value : safeValues) {
            buffer.writeVarInt(value);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
