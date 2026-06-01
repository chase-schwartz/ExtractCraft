package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RaidInventoryMenu extends AbstractContainerMenu {
    private final RaidInventorySnapshot snapshot;

    public RaidInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        super(ExtractCraft.RAID_INVENTORY_MENU.get(), containerId);
        this.snapshot = RaidInventorySnapshot.read(data);
    }

    public RaidInventoryMenu(int containerId, Inventory playerInventory, RaidInventory inventory) {
        super(ExtractCraft.RAID_INVENTORY_MENU.get(), containerId);
        this.snapshot = RaidInventorySnapshot.from(inventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public RaidInventorySnapshot snapshot() {
        return snapshot;
    }

    public record RaidInventorySnapshot(StorageSnapshot backpack, StorageSnapshot vest, StorageSnapshot safeBox, double totalWeight, int totalValue) {
        static RaidInventorySnapshot from(RaidInventory inventory) {
            return new RaidInventorySnapshot(
                    StorageSnapshot.from(inventory.backpack()),
                    StorageSnapshot.from(inventory.vest()),
                    StorageSnapshot.from(inventory.safeBox()),
                    inventory.totalWeight(),
                    inventory.totalValue());
        }

        public void write(RegistryFriendlyByteBuf buffer) {
            backpack.write(buffer);
            vest.write(buffer);
            safeBox.write(buffer);
            buffer.writeDouble(totalWeight);
            buffer.writeVarInt(totalValue);
        }

        static RaidInventorySnapshot read(RegistryFriendlyByteBuf buffer) {
            return new RaidInventorySnapshot(
                    StorageSnapshot.read(buffer),
                    StorageSnapshot.read(buffer),
                    StorageSnapshot.read(buffer),
                    buffer.readDouble(),
                    buffer.readVarInt());
        }
    }

    public record StorageSnapshot(String id, String name, int usedCapacity, int capacity, double usedWeight, double maxWeight, int totalValue, List<ItemSnapshot> items) {
        static StorageSnapshot from(RaidStorageContainer container) {
            List<ItemSnapshot> items = container.items().stream()
                    .map(ItemSnapshot::from)
                    .toList();
            return new StorageSnapshot(container.id(), container.name(), container.usedCapacity(), container.capacity(), container.usedWeight(), container.maxWeight(), container.totalValue(), items);
        }

        void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id);
            buffer.writeUtf(name);
            buffer.writeVarInt(usedCapacity);
            buffer.writeVarInt(capacity);
            buffer.writeDouble(usedWeight);
            buffer.writeDouble(maxWeight);
            buffer.writeVarInt(totalValue);
            buffer.writeVarInt(items.size());
            for (ItemSnapshot item : items) {
                item.write(buffer);
            }
        }

        static StorageSnapshot read(RegistryFriendlyByteBuf buffer) {
            String id = buffer.readUtf();
            String name = buffer.readUtf();
            int usedCapacity = buffer.readVarInt();
            int capacity = buffer.readVarInt();
            double usedWeight = buffer.readDouble();
            double maxWeight = buffer.readDouble();
            int totalValue = buffer.readVarInt();
            int itemCount = buffer.readVarInt();
            List<ItemSnapshot> items = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                items.add(ItemSnapshot.read(buffer));
            }
            return new StorageSnapshot(id, name, usedCapacity, capacity, usedWeight, maxWeight, totalValue, List.copyOf(items));
        }
    }

    public record ItemSnapshot(String itemId, String lookupKey, String displayName, String category, int count, int slotCost, double weight, int value) {
        static ItemSnapshot from(RaidInventoryItem item) {
            return new ItemSnapshot(item.itemId().toString(), item.lookupKey(), item.displayName(), item.category(), item.count(), item.slotCost(), item.weight(), item.value());
        }

        void write(RegistryFriendlyByteBuf buffer) {
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
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readVarInt());
        }
    }
}
