package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PostRaidResultMenu extends AbstractContainerMenu {
    public static final int MOVE_ALL_TO_STASH_BUTTON = 0;
    public static final int KEEP_ON_CHARACTER_BUTTON = 1;

    private final ResultSnapshot snapshot;

    public PostRaidResultMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        super(ExtractCraft.POST_RAID_RESULT_MENU.get(), containerId);
        this.snapshot = ResultSnapshot.read(data);
    }

    public PostRaidResultMenu(int containerId, Inventory playerInventory, ServerPlayer player, RaidResultService.PendingRaidResult pending) {
        super(ExtractCraft.POST_RAID_RESULT_MENU.get(), containerId);
        this.snapshot = ResultSnapshot.from(pending);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        if (id == MOVE_ALL_TO_STASH_BUTTON) {
            if (RaidResultService.movePendingToStash(serverPlayer)) {
                serverPlayer.closeContainer();
            }
            return true;
        }
        if (id == KEEP_ON_CHARACTER_BUTTON) {
            if (RaidResultService.keepPendingOnCharacter(serverPlayer)) {
                serverPlayer.closeContainer();
            }
            return true;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public ResultSnapshot snapshot() {
        return snapshot;
    }

    public record ResultSnapshot(int elapsedSeconds, int totalValue, double totalWeight, int itemCount, int stackCount,
            List<ItemSnapshot> backpack, List<ItemSnapshot> vest, List<ItemSnapshot> safeBox, List<ItemSnapshot> weapons) {
        static ResultSnapshot from(RaidResultService.PendingRaidResult pending) {
            List<RaidInventoryItem> allItems = pending.allItems();
            return new ResultSnapshot(
                    pending.elapsedSeconds(),
                    allItems.stream().mapToInt(RaidInventoryItem::totalValue).sum(),
                    allItems.stream().mapToDouble(RaidInventoryItem::totalWeight).sum(),
                    allItems.stream().mapToInt(RaidInventoryItem::count).sum(),
                    allItems.size(),
                    itemsFrom(pending.backpackItems()),
                    itemsFrom(pending.vestItems()),
                    itemsFrom(pending.safeBoxItems()),
                    weaponItems(pending.primaryWeapon(), pending.secondaryWeapon()));
        }

        private static List<ItemSnapshot> itemsFrom(List<RaidInventoryItem> items) {
            return items.stream().map(item -> ItemSnapshot.from(item, "")).toList();
        }

        private static List<ItemSnapshot> weaponItems(RaidInventoryItem primary, RaidInventoryItem secondary) {
            List<ItemSnapshot> items = new ArrayList<>();
            if (primary != null) {
                items.add(ItemSnapshot.from(primary, "Primary"));
            }
            if (secondary != null) {
                items.add(ItemSnapshot.from(secondary, "Secondary"));
            }
            return List.copyOf(items);
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(elapsedSeconds);
            buffer.writeVarInt(totalValue);
            buffer.writeDouble(totalWeight);
            buffer.writeVarInt(itemCount);
            buffer.writeVarInt(stackCount);
            writeItems(buffer, backpack);
            writeItems(buffer, vest);
            writeItems(buffer, safeBox);
            writeItems(buffer, weapons);
        }

        static ResultSnapshot read(RegistryFriendlyByteBuf buffer) {
            return new ResultSnapshot(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    readItems(buffer),
                    readItems(buffer),
                    readItems(buffer),
                    readItems(buffer));
        }

        private static void writeItems(RegistryFriendlyByteBuf buffer, List<ItemSnapshot> items) {
            buffer.writeVarInt(items.size());
            for (ItemSnapshot item : items) {
                item.write(buffer);
            }
        }

        private static List<ItemSnapshot> readItems(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<ItemSnapshot> items = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                items.add(ItemSnapshot.read(buffer));
            }
            return List.copyOf(items);
        }
    }

    public record ItemSnapshot(String sectionLabel, String itemId, String lookupKey, String displayName, String category, int count, int slotCost, double weight, int value) {
        static ItemSnapshot from(RaidInventoryItem item, String sectionLabel) {
            return new ItemSnapshot(sectionLabel, item.itemId().toString(), item.lookupKey(), item.displayName(), item.category(), item.count(), item.slotCost(), item.totalWeight(), item.totalValue());
        }

        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(sectionLabel);
            buffer.writeUtf(itemId);
            buffer.writeUtf(lookupKey);
            buffer.writeUtf(displayName);
            buffer.writeUtf(category);
            buffer.writeVarInt(count);
            buffer.writeVarInt(slotCost);
            buffer.writeDouble(weight);
            buffer.writeVarInt(value);
        }

        static ItemSnapshot read(RegistryFriendlyByteBuf buffer) {
            return new ItemSnapshot(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readVarInt());
        }
    }
}
