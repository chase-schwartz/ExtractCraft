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
    public static final int PRIMARY_WEAPON_START = 0;
    public static final int SECONDARY_WEAPON_START = 1;
    public static final int BACKPACK_START = 2;
    public static final int BACKPACK_DISPLAY_SLOTS = 36;
    public static final int VEST_START = BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
    public static final int VEST_DISPLAY_SLOTS = 12;
    public static final int SAFE_BOX_START = VEST_START + VEST_DISPLAY_SLOTS;
    public static final int SAFE_BOX_DISPLAY_SLOTS = 9;
    private static final int WEAPON_DISPLAY_SLOTS = 2;
    private static final int RAID_DISPLAY_SLOTS = SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
    private static final int BUTTON_FACTOR = 1000;
    private static final int MOVE_BUTTON_OFFSET = 100_000;
    private static final int RETURN_BUTTON_OFFSET = 200_000;
    private static final int MOVE_SOURCE_FACTOR = 10_000;
    private static final int MOVE_INDEX_FACTOR = 10;

    private final Container container;
    private final SimpleContainer raidDisplay;
    private final BlockPos containerPos;
    private final int containerRows;
    private final int containerSlotCount;
    private final boolean hasWorldContainer;
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
        this(containerId, playerInventory, readClientData(data));
    }

    private ActiveLootContainerMenu(int containerId, Inventory playerInventory, ClientData data) {
        this(containerId, playerInventory, new SimpleContainer(data.containerSlotCount()), data.containerPos(), null, data.hasWorldContainer());
    }

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, Container container, BlockPos containerPos, ServerPlayer serverPlayer) {
        this(containerId, playerInventory, container, containerPos, serverPlayer, true);
    }

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, ServerPlayer serverPlayer) {
        this(containerId, playerInventory, new SimpleContainer(0), serverPlayer.blockPosition(), serverPlayer, false);
    }

    private ActiveLootContainerMenu(int containerId, Inventory playerInventory, Container container, BlockPos containerPos, ServerPlayer serverPlayer, boolean hasWorldContainer) {
        super(ExtractCraft.ACTIVE_LOOT_CONTAINER_MENU.get(), containerId);
        this.container = container;
        this.containerPos = containerPos;
        this.serverPlayer = serverPlayer;
        this.hasWorldContainer = hasWorldContainer;
        this.containerSlotCount = container.getContainerSize();
        this.containerRows = hasWorldContainer ? Math.max(1, (int) Math.ceil(containerSlotCount / (double) CONTAINER_COLUMNS)) : 0;
        this.raidDisplay = new SimpleContainer(RAID_DISPLAY_SLOTS);

        addRaidDisplaySlots();
        addContainerSlots();
        logConstruction();

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

    private static ClientData readClientData(RegistryFriendlyByteBuf buffer) {
        boolean hasWorldContainer = buffer.readBoolean();
        int containerSlotCount = hasWorldContainer ? buffer.readVarInt() : 0;
        BlockPos containerPos = buffer.readBlockPos();
        return new ClientData(hasWorldContainer, containerSlotCount, containerPos);
    }

    private void logConstruction() {
        ExtractCraft.LOGGER.info("ActiveLootContainerMenu constructed: mode={}, hasContainer={}, backingContainerSlots={}, weaponSlots={}, backpackVisualSlots={}, vestVisualSlots={}, safeBoxVisualSlots={}, raidDisplaySlots={}, raidDisplayBackingSize={}, totalMenuSlots={}, primary={}, secondary={}, backpackFirstLast={}, vestFirstLast={}, safeBoxFirstLast={}, containerFirstLast={}",
                hasWorldContainer ? "container" : "inventory_only",
                hasWorldContainer,
                containerSlotCount,
                WEAPON_DISPLAY_SLOTS,
                BACKPACK_DISPLAY_SLOTS,
                VEST_DISPLAY_SLOTS,
                SAFE_BOX_DISPLAY_SLOTS,
                RAID_DISPLAY_SLOTS,
                raidDisplay.getContainerSize(),
                this.slots.size(),
                slotCoordinate(PRIMARY_WEAPON_START),
                slotCoordinate(SECONDARY_WEAPON_START),
                firstLastSlot(BACKPACK_START, BACKPACK_DISPLAY_SLOTS),
                firstLastSlot(VEST_START, VEST_DISPLAY_SLOTS),
                firstLastSlot(SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS),
                firstLastSlot(containerMenuSlotStart(), containerSlotCount));
    }

    private String firstLastSlot(int start, int count) {
        if (count <= 0 || start < 0 || start >= this.slots.size()) {
            return "none";
        }
        int last = Math.min(this.slots.size() - 1, start + count - 1);
        return slotCoordinate(start) + " -> " + slotCoordinate(last);
    }

    private String slotCoordinate(int slotIndex) {
        net.minecraft.world.inventory.Slot slot = this.slots.get(slotIndex);
        return slotIndex + "@(" + slot.x + "," + slot.y + ")";
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isWorldContainerSlot(slotId) && clickType == ClickType.QUICK_MOVE) {
            if (player instanceof ServerPlayer serverPlayer) {
                transferContainerSlot(serverPlayer, containerSlotForMenuSlot(slotId), RaidEquipmentSlot.BACKPACK);
            }
            setCarried(ItemStack.EMPTY);
            broadcastChanges();
            return;
        }

        if (isWorldContainerSlot(slotId) && clickType == ClickType.PICKUP) {
            setCarried(ItemStack.EMPTY);
            broadcastChanges();
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Drag loot to a section, or shift-click to quick-move to Backpack."));
        }
        setCarried(ItemStack.EMPTY);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        if (id >= RETURN_BUTTON_OFFSET) {
            int payload = id - RETURN_BUTTON_OFFSET;
            int sourceId = Math.floorDiv(payload, MOVE_SOURCE_FACTOR);
            int sourceIndex = Math.floorMod(payload, MOVE_SOURCE_FACTOR);
            returnStoredItemToContainer(serverPlayer, slotFromId(sourceId), sourceIndex);
        } else if (id >= MOVE_BUTTON_OFFSET) {
            int payload = id - MOVE_BUTTON_OFFSET;
            int sourceId = Math.floorDiv(payload, MOVE_SOURCE_FACTOR);
            int remainder = Math.floorMod(payload, MOVE_SOURCE_FACTOR);
            int sourceIndex = Math.floorDiv(remainder, MOVE_INDEX_FACTOR);
            int targetId = Math.floorMod(remainder, MOVE_INDEX_FACTOR);
            moveStoredItem(serverPlayer, slotFromId(sourceId), sourceIndex, slotFromId(targetId));
        } else {
            int targetId = Math.floorDiv(id, BUTTON_FACTOR);
            int containerSlot = Math.floorMod(id, BUTTON_FACTOR);
            transferContainerSlot(serverPlayer, containerSlot, slotFromId(targetId));
        }
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
        if (!hasWorldContainer) {
            return true;
        }
        if (!player.canInteractWithBlock(containerPos, 8.0D)) {
            return false;
        }

        BlockEntity blockEntity = player.level().getBlockEntity(containerPos);
        return blockEntity instanceof Container;
    }

    public static int buttonId(RaidEquipmentSlot target, int containerSlot) {
        return slotId(target) * BUTTON_FACTOR + containerSlot;
    }

    public static int moveButtonId(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        return MOVE_BUTTON_OFFSET + slotId(source) * MOVE_SOURCE_FACTOR + sourceIndex * MOVE_INDEX_FACTOR + slotId(target);
    }

    public static int returnButtonId(RaidEquipmentSlot source, int sourceIndex) {
        return RETURN_BUTTON_OFFSET + slotId(source) * MOVE_SOURCE_FACTOR + sourceIndex;
    }

    public int containerRows() {
        return containerRows;
    }

    public int containerSlotCount() {
        return containerSlotCount;
    }

    public boolean hasWorldContainer() {
        return hasWorldContainer;
    }

    public int containerMenuSlotStart() {
        return RAID_DISPLAY_SLOTS;
    }

    public RaidEquipmentSlot raidSlotForMenuSlot(int menuSlot) {
        if (menuSlot == PRIMARY_WEAPON_START) {
            return RaidEquipmentSlot.PRIMARY_WEAPON;
        }
        if (menuSlot == SECONDARY_WEAPON_START) {
            return RaidEquipmentSlot.SECONDARY_WEAPON;
        }
        if (menuSlot >= BACKPACK_START && menuSlot < BACKPACK_START + BACKPACK_DISPLAY_SLOTS) {
            return RaidEquipmentSlot.BACKPACK;
        }
        if (menuSlot >= VEST_START && menuSlot < VEST_START + VEST_DISPLAY_SLOTS) {
            return RaidEquipmentSlot.VEST;
        }
        if (menuSlot >= SAFE_BOX_START && menuSlot < SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS) {
            return RaidEquipmentSlot.SAFE_BOX;
        }
        return null;
    }

    public int raidItemIndexForMenuSlot(int menuSlot) {
        RaidEquipmentSlot slot = raidSlotForMenuSlot(menuSlot);
        if (slot == RaidEquipmentSlot.PRIMARY_WEAPON || slot == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return 0;
        }
        if (slot == RaidEquipmentSlot.BACKPACK) {
            return menuSlot - BACKPACK_START;
        }
        if (slot == RaidEquipmentSlot.VEST) {
            return menuSlot - VEST_START;
        }
        if (slot == RaidEquipmentSlot.SAFE_BOX) {
            return menuSlot - SAFE_BOX_START;
        }
        return -1;
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

    private void moveStoredItem(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        if (source == null || target == null || source == target) {
            return;
        }

        RaidInventory.AddResult result = RaidInventoryManager.moveBetween(player, source, sourceIndex, target);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
            return;
        }

        rebuildRaidDisplay();
        player.sendSystemMessage(Component.literal(result.message()));
    }

    private void returnStoredItemToContainer(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        if (!hasWorldContainer) {
            player.sendSystemMessage(Component.literal("No container is open."));
            return;
        }
        if (source == null) {
            return;
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        RaidInventoryItem item = itemAt(inventory, source, sourceIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return;
        }

        ItemStack stack = displayStack(item);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Unable to rebuild item stack for " + item.lookupKey() + "."));
            return;
        }
        if (!canFitInContainer(stack)) {
            player.sendSystemMessage(Component.literal("Container does not have room for that stack."));
            return;
        }

        RaidInventoryItem removed = removeAt(inventory, source, sourceIndex);
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return;
        }

        insertIntoContainer(stack);
        container.setChanged();
        rebuildRaidDisplay();
        player.sendSystemMessage(Component.literal("Returned " + stack.getCount() + "x " + stack.getHoverName().getString() + " to container."));
    }

    private void addRaidDisplaySlots() {
        addSlot(new ReadOnlyContainerSlot(raidDisplay, PRIMARY_WEAPON_START, 150, 48));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, SECONDARY_WEAPON_START, 150, 110));
        addDisplayGrid(BACKPACK_START, BACKPACK_DISPLAY_SLOTS, 6, 12, 66);
        addDisplayGrid(VEST_START, VEST_DISPLAY_SLOTS, 4, 12, 248);
        addDisplayGrid(SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, 3, 104, 248);
    }

    private void addDisplayGrid(int start, int count, int columns, int x, int y) {
        for (int i = 0; i < count; i++) {
            addSlot(new ReadOnlyContainerSlot(raidDisplay, start + i, x + (i % columns) * 18, y + (i / columns) * 18));
        }
    }

    private void addContainerSlots() {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            int x = 226 + (slot % CONTAINER_COLUMNS) * 18;
            int y = 58 + (slot / CONTAINER_COLUMNS) * 18;
            addSlot(new ReadOnlyContainerSlot(container, slot, x, y));
        }
    }

    private void rebuildRaidDisplay() {
        if (serverPlayer == null) {
            return;
        }

        raidDisplay.clearContent();
        RaidInventory inventory = RaidInventoryManager.get(serverPlayer);
        if (inventory.primaryWeapon() != null) {
            raidDisplay.setItem(PRIMARY_WEAPON_START, displayStack(inventory.primaryWeapon()));
        }
        if (inventory.secondaryWeapon() != null) {
            raidDisplay.setItem(SECONDARY_WEAPON_START, displayStack(inventory.secondaryWeapon()));
        }
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
        ItemStack stored = item.toItemStack();
        if (!stored.isEmpty()) {
            return stored;
        }
        return ItemStackVariantFactory.create(item.lookupKey(), item.count())
                .orElseGet(() -> {
                    if (BuiltInRegistries.ITEM.containsKey(item.itemId())) {
                        return new ItemStack(BuiltInRegistries.ITEM.get(item.itemId()), item.count());
                    }
                    return ItemStack.EMPTY;
                });
    }

    private boolean canFitInContainer(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) {
                int transferable = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                remaining.shrink(transferable);
                if (remaining.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void insertIntoContainer(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) {
                container.setItem(slot, remaining.copy());
                remaining.setCount(0);
            } else if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) {
                int transferable = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(transferable);
                remaining.shrink(transferable);
                container.setItem(slot, current);
            }
        }
    }

    private static RaidInventoryItem itemAt(RaidInventory inventory, RaidEquipmentSlot source, int sourceIndex) {
        return inventory.itemAt(source, sourceIndex);
    }

    private static RaidInventoryItem removeAt(RaidInventory inventory, RaidEquipmentSlot source, int sourceIndex) {
        return inventory.removeAt(source, sourceIndex);
    }

    private static String targetName(RaidEquipmentSlot target) {
        return switch (target) {
            case PRIMARY_WEAPON -> "primary weapon";
            case SECONDARY_WEAPON -> "secondary weapon";
            case BACKPACK -> "backpack";
            case VEST -> "vest";
            case SAFE_BOX -> "safe box";
        };
    }

    private static int slotId(RaidEquipmentSlot slot) {
        return switch (slot) {
            case PRIMARY_WEAPON -> 0;
            case SECONDARY_WEAPON -> 1;
            case BACKPACK -> 2;
            case VEST -> 3;
            case SAFE_BOX -> 4;
        };
    }

    private static RaidEquipmentSlot slotFromId(int id) {
        return switch (id) {
            case 0 -> RaidEquipmentSlot.PRIMARY_WEAPON;
            case 1 -> RaidEquipmentSlot.SECONDARY_WEAPON;
            case 3 -> RaidEquipmentSlot.VEST;
            case 4 -> RaidEquipmentSlot.SAFE_BOX;
            default -> RaidEquipmentSlot.BACKPACK;
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

    private record ClientData(boolean hasWorldContainer, int containerSlotCount, BlockPos containerPos) {
    }
}
