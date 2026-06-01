package com.chaseschwartz.extractcraft.raid.containers;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ActiveLootContainerMenu extends AbstractContainerMenu {
    private static final int COLUMNS = 9;
    private final Container container;
    private final BlockPos containerPos;
    private final int rows;
    private final DataSlot usedCapacity;
    private final DataSlot maxCapacity;
    private final DataSlot usedWeightTenths;
    private final DataSlot maxWeightTenths;
    private final DataSlot totalValue;

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(data.readVarInt()), data.readBlockPos(), null);
    }

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, Container container, BlockPos containerPos, ServerPlayer serverPlayer) {
        super(ExtractCraft.ACTIVE_LOOT_CONTAINER_MENU.get(), containerId);
        this.container = container;
        this.containerPos = containerPos;
        this.rows = Math.max(1, (int) Math.ceil(container.getContainerSize() / (double) COLUMNS));

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            int x = 8 + (slot % COLUMNS) * 18;
            int y = 24 + (slot / COLUMNS) * 18;
            addSlot(new ReadOnlyContainerSlot(container, slot, x, y));
        }

        this.usedCapacity = addDataSlot(statSlot(serverPlayer, Stat.USED_CAPACITY));
        this.maxCapacity = addDataSlot(statSlot(serverPlayer, Stat.MAX_CAPACITY));
        this.usedWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.USED_WEIGHT_TENTHS));
        this.maxWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.MAX_WEIGHT_TENTHS));
        this.totalValue = addDataSlot(statSlot(serverPlayer, Stat.TOTAL_VALUE));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < container.getContainerSize() && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            if (player instanceof ServerPlayer serverPlayer) {
                transferSlotToBackpack(serverPlayer, slotId);
            }
            setCarried(ItemStack.EMPTY);
            broadcastChanges();
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Use click or shift-click to transfer loot into your raid backpack."));
        }
        setCarried(ItemStack.EMPTY);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player instanceof ServerPlayer serverPlayer && index >= 0 && index < container.getContainerSize()) {
            return transferSlotToBackpack(serverPlayer, index);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.canInteractWithBlock(containerPos, 8.0D)) {
            return false;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(containerPos);
        return blockEntity instanceof Container;
    }

    public int rows() {
        return rows;
    }

    public int usedCapacity() {
        return usedCapacity.get();
    }

    public int maxCapacity() {
        return maxCapacity.get();
    }

    public double usedWeight() {
        return usedWeightTenths.get() / 10.0D;
    }

    public double maxWeight() {
        return maxWeightTenths.get() / 10.0D;
    }

    public int totalValue() {
        return totalValue.get();
    }

    private ItemStack transferSlotToBackpack(ServerPlayer player, int slotId) {
        ItemStack stack = container.getItem(slotId);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        RaidInventory.AddResult result = RaidInventoryManager.addStackToBackpack(player, copy);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
            return ItemStack.EMPTY;
        }

        container.setItem(slotId, ItemStack.EMPTY);
        container.setChanged();
        player.sendSystemMessage(Component.literal("Moved " + copy.getCount() + "x " + copy.getHoverName().getString() + " to raid backpack."));
        return copy;
    }

    private static DataSlot statSlot(ServerPlayer player, Stat stat) {
        if (player == null) {
            return DataSlot.standalone();
        }

        return new DataSlot() {
            @Override
            public int get() {
                RaidInventory inventory = RaidInventoryManager.get(player);
                return switch (stat) {
                    case USED_CAPACITY -> inventory.backpack().usedCapacity();
                    case MAX_CAPACITY -> inventory.backpack().capacity();
                    case USED_WEIGHT_TENTHS -> (int) Math.round(inventory.backpack().usedWeight() * 10.0D);
                    case MAX_WEIGHT_TENTHS -> (int) Math.round(inventory.backpack().maxWeight() * 10.0D);
                    case TOTAL_VALUE -> inventory.backpack().totalValue();
                };
            }

            @Override
            public void set(int value) {
            }
        };
    }

    private enum Stat {
        USED_CAPACITY,
        MAX_CAPACITY,
        USED_WEIGHT_TENTHS,
        MAX_WEIGHT_TENTHS,
        TOTAL_VALUE
    }
}
