package com.chaseschwartz.extractcraft.raid.containers;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryItem;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    public static final int CONTAINER_COLUMNS = 6;
    public static final int BACKPACK_START = 0;
    public static final int BACKPACK_DISPLAY_SLOTS = 36;
    public static final int VEST_START = BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
    public static final int VEST_DISPLAY_SLOTS = 12;
    public static final int SAFE_BOX_START = VEST_START + VEST_DISPLAY_SLOTS;
    public static final int SAFE_BOX_DISPLAY_SLOTS = 9;
    private static final int RAID_DISPLAY_SLOTS = BACKPACK_DISPLAY_SLOTS + VEST_DISPLAY_SLOTS + SAFE_BOX_DISPLAY_SLOTS;
    private static final int BUTTON_FACTOR = 1000;

    private final Container container;
    private final SimpleContainer raidDisplay;
    private final BlockPos containerPos;
    private final int containerRows;
    private final int containerSlotCount;
    private final ServerPlayer serverPlayer;
    private final DataSlot backpackUsedCapacity;
    private final DataSlot backpackMaxCapacity;
    private final DataSlot backpackUsedWeightTenths;
    private final DataSlot backpackMaxWeightTenths;
    private final DataSlot backpackValue;
    private final DataSlot vestUsedCapacity;
    private final DataSlot vestMaxCapacity;
    private final DataSlot vestUsedWeightTenths;
    private final DataSlot vestMaxWeightTenths;
    private final DataSlot vestValue;
    private final DataSlot safeBoxUsedCapacity;
    private final DataSlot safeBoxMaxCapacity;
    private final DataSlot safeBoxUsedWeightTenths;
    private final DataSlot safeBoxMaxWeightTenths;
    private final DataSlot safeBoxValue;
    private final DataSlot totalValue;

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, new SimpleContainer(data.readVarInt()), data.readBlockPos(), null);
    }

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, Container container, BlockPos containerPos, ServerPlayer serverPlayer) {
        super(ExtractCraft.ACTIVE_LOOT_CONTAINER_MENU.get(), containerId);
        this.container = container;
        this.containerPos = containerPos;
        this.serverPlayer = serverPlayer;
        this.containerSlotCount = container.getContainerSize();
        this.containerRows = Math.max(1, (int) Math.ceil(containerSlotCount / (double) CONTAINER_COLUMNS));
        this.raidDisplay = new SimpleContainer(RAID_DISPLAY_SLOTS);

        addRaidDisplaySlots();
        addContainerSlots();

        this.backpackUsedCapacity = addDataSlot(statSlot(serverPlayer, Stat.BACKPACK_USED_CAPACITY));
        this.backpackMaxCapacity = addDataSlot(statSlot(serverPlayer, Stat.BACKPACK_MAX_CAPACITY));
        this.backpackUsedWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.BACKPACK_USED_WEIGHT_TENTHS));
        this.backpackMaxWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.BACKPACK_MAX_WEIGHT_TENTHS));
        this.backpackValue = addDataSlot(statSlot(serverPlayer, Stat.BACKPACK_VALUE));
        this.vestUsedCapacity = addDataSlot(statSlot(serverPlayer, Stat.VEST_USED_CAPACITY));
        this.vestMaxCapacity = addDataSlot(statSlot(serverPlayer, Stat.VEST_MAX_CAPACITY));
        this.vestUsedWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.VEST_USED_WEIGHT_TENTHS));
        this.vestMaxWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.VEST_MAX_WEIGHT_TENTHS));
        this.vestValue = addDataSlot(statSlot(serverPlayer, Stat.VEST_VALUE));
        this.safeBoxUsedCapacity = addDataSlot(statSlot(serverPlayer, Stat.SAFE_BOX_USED_CAPACITY));
        this.safeBoxMaxCapacity = addDataSlot(statSlot(serverPlayer, Stat.SAFE_BOX_MAX_CAPACITY));
        this.safeBoxUsedWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.SAFE_BOX_USED_WEIGHT_TENTHS));
        this.safeBoxMaxWeightTenths = addDataSlot(statSlot(serverPlayer, Stat.SAFE_BOX_MAX_WEIGHT_TENTHS));
        this.safeBoxValue = addDataSlot(statSlot(serverPlayer, Stat.SAFE_BOX_VALUE));
        this.totalValue = addDataSlot(statSlot(serverPlayer, Stat.TOTAL_VALUE));

        rebuildRaidDisplay();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isWorldContainerSlot(slotId) && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            if (player instanceof ServerPlayer serverPlayer) {
                transferContainerSlot(serverPlayer, containerSlotForMenuSlot(slotId), RaidEquipmentSlot.BACKPACK);
            }
            setCarried(ItemStack.EMPTY);
            broadcastChanges();
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Drag or click loot into Backpack, Vest, or Safe Box."));
        }
        setCarried(ItemStack.EMPTY);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        int targetId = Math.floorDiv(id, BUTTON_FACTOR);
        int containerSlot = Math.floorMod(id, BUTTON_FACTOR);
        RaidEquipmentSlot target = switch (targetId) {
            case 1 -> RaidEquipmentSlot.VEST;
            case 2 -> RaidEquipmentSlot.SAFE_BOX;
            default -> RaidEquipmentSlot.BACKPACK;
        };
        transferContainerSlot(serverPlayer, containerSlot, target);
        setCarried(ItemStack.EMPTY);
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player instanceof ServerPlayer serverPlayer && isWorldContainerSlot(index)) {
            return transferContainerSlot(serverPlayer, containerSlotForMenuSlot(index), RaidEquipmentSlot.BACKPACK);
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

    public static int buttonId(RaidEquipmentSlot target, int containerSlot) {
        int targetId = switch (target) {
            case BACKPACK -> 0;
            case VEST -> 1;
            case SAFE_BOX -> 2;
        };
        return targetId * BUTTON_FACTOR + containerSlot;
    }

    public int containerRows() {
        return containerRows;
    }

    public int containerSlotCount() {
        return containerSlotCount;
    }

    public int containerMenuSlotStart() {
        return RAID_DISPLAY_SLOTS;
    }

    public int backpackUsedCapacity() {
        return backpackUsedCapacity.get();
    }

    public int backpackMaxCapacity() {
        return backpackMaxCapacity.get();
    }

    public double backpackUsedWeight() {
        return backpackUsedWeightTenths.get() / 10.0D;
    }

    public double backpackMaxWeight() {
        return backpackMaxWeightTenths.get() / 10.0D;
    }

    public int backpackValue() {
        return backpackValue.get();
    }

    public int vestUsedCapacity() {
        return vestUsedCapacity.get();
    }

    public int vestMaxCapacity() {
        return vestMaxCapacity.get();
    }

    public double vestUsedWeight() {
        return vestUsedWeightTenths.get() / 10.0D;
    }

    public double vestMaxWeight() {
        return vestMaxWeightTenths.get() / 10.0D;
    }

    public int vestValue() {
        return vestValue.get();
    }

    public int safeBoxUsedCapacity() {
        return safeBoxUsedCapacity.get();
    }

    public int safeBoxMaxCapacity() {
        return safeBoxMaxCapacity.get();
    }

    public double safeBoxUsedWeight() {
        return safeBoxUsedWeightTenths.get() / 10.0D;
    }

    public double safeBoxMaxWeight() {
        return safeBoxMaxWeightTenths.get() / 10.0D;
    }

    public int safeBoxValue() {
        return safeBoxValue.get();
    }

    public int totalValue() {
        return totalValue.get();
    }

    private boolean isWorldContainerSlot(int menuSlot) {
        return menuSlot >= RAID_DISPLAY_SLOTS && menuSlot < RAID_DISPLAY_SLOTS + containerSlotCount;
    }

    private int containerSlotForMenuSlot(int menuSlot) {
        return menuSlot - RAID_DISPLAY_SLOTS;
    }

    private ItemStack transferContainerSlot(ServerPlayer player, int containerSlot, RaidEquipmentSlot target) {
        if (containerSlot < 0 || containerSlot >= container.getContainerSize()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = container.getItem(containerSlot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        RaidInventory.AddResult result = RaidInventoryManager.addStackTo(player, copy, target);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
            return ItemStack.EMPTY;
        }

        container.setItem(containerSlot, ItemStack.EMPTY);
        container.setChanged();
        rebuildRaidDisplay();
        player.sendSystemMessage(Component.literal("Moved " + copy.getCount() + "x " + copy.getHoverName().getString() + " to " + targetName(target) + "."));
        return copy;
    }

    private void addRaidDisplaySlots() {
        addDisplayGrid(BACKPACK_START, BACKPACK_DISPLAY_SLOTS, 6, 12, 38);
        addDisplayGrid(VEST_START, VEST_DISPLAY_SLOTS, 4, 12, 162);
        addDisplayGrid(SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, 3, 102, 162);
    }

    private void addDisplayGrid(int start, int count, int columns, int x, int y) {
        for (int i = 0; i < count; i++) {
            addSlot(new ReadOnlyContainerSlot(raidDisplay, start + i, x + (i % columns) * 18, y + (i / columns) * 18));
        }
    }

    private void addContainerSlots() {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            int x = 226 + (slot % CONTAINER_COLUMNS) * 18;
            int y = 38 + (slot / CONTAINER_COLUMNS) * 18;
            addSlot(new ReadOnlyContainerSlot(container, slot, x, y));
        }
    }

    private void rebuildRaidDisplay() {
        if (serverPlayer == null) {
            return;
        }

        raidDisplay.clearContent();
        RaidInventory inventory = RaidInventoryManager.get(serverPlayer);
        fillDisplay(BACKPACK_START, BACKPACK_DISPLAY_SLOTS, inventory.backpack().items());
        fillDisplay(VEST_START, VEST_DISPLAY_SLOTS, inventory.vest().items());
        fillDisplay(SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, inventory.safeBox().items());
    }

    private void fillDisplay(int start, int maxSlots, java.util.List<RaidInventoryItem> items) {
        for (int i = 0; i < Math.min(maxSlots, items.size()); i++) {
            raidDisplay.setItem(start + i, displayStack(items.get(i)));
        }
    }

    private static ItemStack displayStack(RaidInventoryItem item) {
        return ItemStackVariantFactory.create(item.lookupKey(), item.count())
                .orElseGet(() -> {
                    if (BuiltInRegistries.ITEM.containsKey(item.itemId())) {
                        return new ItemStack(BuiltInRegistries.ITEM.get(item.itemId()), item.count());
                    }
                    return ItemStack.EMPTY;
                });
    }

    private static String targetName(RaidEquipmentSlot target) {
        return switch (target) {
            case BACKPACK -> "backpack";
            case VEST -> "vest";
            case SAFE_BOX -> "safe box";
        };
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
                    case BACKPACK_USED_CAPACITY -> inventory.backpack().usedCapacity();
                    case BACKPACK_MAX_CAPACITY -> inventory.backpack().capacity();
                    case BACKPACK_USED_WEIGHT_TENTHS -> (int) Math.round(inventory.backpack().usedWeight() * 10.0D);
                    case BACKPACK_MAX_WEIGHT_TENTHS -> (int) Math.round(inventory.backpack().maxWeight() * 10.0D);
                    case BACKPACK_VALUE -> inventory.backpack().totalValue();
                    case VEST_USED_CAPACITY -> inventory.vest().usedCapacity();
                    case VEST_MAX_CAPACITY -> inventory.vest().capacity();
                    case VEST_USED_WEIGHT_TENTHS -> (int) Math.round(inventory.vest().usedWeight() * 10.0D);
                    case VEST_MAX_WEIGHT_TENTHS -> (int) Math.round(inventory.vest().maxWeight() * 10.0D);
                    case VEST_VALUE -> inventory.vest().totalValue();
                    case SAFE_BOX_USED_CAPACITY -> inventory.safeBox().usedCapacity();
                    case SAFE_BOX_MAX_CAPACITY -> inventory.safeBox().capacity();
                    case SAFE_BOX_USED_WEIGHT_TENTHS -> (int) Math.round(inventory.safeBox().usedWeight() * 10.0D);
                    case SAFE_BOX_MAX_WEIGHT_TENTHS -> (int) Math.round(inventory.safeBox().maxWeight() * 10.0D);
                    case SAFE_BOX_VALUE -> inventory.safeBox().totalValue();
                    case TOTAL_VALUE -> inventory.totalValue();
                };
            }

            @Override
            public void set(int value) {
            }
        };
    }

    private enum Stat {
        BACKPACK_USED_CAPACITY,
        BACKPACK_MAX_CAPACITY,
        BACKPACK_USED_WEIGHT_TENTHS,
        BACKPACK_MAX_WEIGHT_TENTHS,
        BACKPACK_VALUE,
        VEST_USED_CAPACITY,
        VEST_MAX_CAPACITY,
        VEST_USED_WEIGHT_TENTHS,
        VEST_MAX_WEIGHT_TENTHS,
        VEST_VALUE,
        SAFE_BOX_USED_CAPACITY,
        SAFE_BOX_MAX_CAPACITY,
        SAFE_BOX_USED_WEIGHT_TENTHS,
        SAFE_BOX_MAX_WEIGHT_TENTHS,
        SAFE_BOX_VALUE,
        TOTAL_VALUE
    }
}
