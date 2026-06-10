package com.chaseschwartz.extractcraft.network;

import java.util.ArrayList;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuickUseStatePayload(String selectedItemId, List<Option> options) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuickUseStatePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "quick_use_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuickUseStatePayload> STREAM_CODEC = CustomPacketPayload.codec(QuickUseStatePayload::write, QuickUseStatePayload::new);

    private QuickUseStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUtf(128), readOptions(buffer));
    }

    public QuickUseStatePayload {
        selectedItemId = selectedItemId == null ? "" : selectedItemId;
        options = options == null ? List.of() : List.copyOf(options);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(selectedItemId, 128);
        buffer.writeVarInt(options.size());
        for (Option option : options) {
            buffer.writeUtf(option.itemId(), 128);
            buffer.writeUtf(option.displayName(), 96);
            buffer.writeVarInt(option.count());
        }
    }

    private static List<Option> readOptions(RegistryFriendlyByteBuf buffer) {
        int size = Math.max(0, Math.min(32, buffer.readVarInt()));
        List<Option> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            options.add(new Option(buffer.readUtf(128), buffer.readUtf(96), buffer.readVarInt()));
        }
        return options;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Option(String itemId, String displayName, int count) {
        public Option {
            itemId = itemId == null ? "" : itemId;
            displayName = displayName == null ? itemId : displayName;
            count = Math.max(0, count);
        }
    }
}
