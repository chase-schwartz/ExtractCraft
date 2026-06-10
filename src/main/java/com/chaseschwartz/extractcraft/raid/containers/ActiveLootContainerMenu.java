package com.chaseschwartz.extractcraft.raid.containers;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.durability.InRaidRepairService;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.inventory.GridDisplayMetadata;
import com.chaseschwartz.extractcraft.raid.inventory.GridMoveResult;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfile;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.PlayerStashService;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryItem;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryManager;
import com.chaseschwartz.extractcraft.raid.inventory.RaidStorageContainer;

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
    public static final int HELMET_START = 0;
    public static final int ARMOR_START = 1;
    public static final int EQUIPPED_BACKPACK_START = 2;
    public static final int EQUIPPED_VEST_START = 3;
    public static final int EQUIPPED_SAFE_CONTAINER_START = 4;
    public static final int PRIMARY_WEAPON_START = 5;
    public static final int SECONDARY_WEAPON_START = 6;
    public static final int BACKPACK_START = 7;
    public static final int BACKPACK_DISPLAY_SLOTS = 64;
    public static final int VEST_START = BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
    public static final int VEST_DISPLAY_SLOTS = 20;
    public static final int SAFE_BOX_START = VEST_START + VEST_DISPLAY_SLOTS;
    public static final int SAFE_BOX_DISPLAY_SLOTS = 9;
    private static final int WEAPON_DISPLAY_SLOTS = 2;
    private static final int RAID_DISPLAY_SLOTS = SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
    private static final int BUTTON_FACTOR = 1000;
    private static final int MOVE_BUTTON_OFFSET = 100_000;
    private static final int RETURN_BUTTON_OFFSET = 200_000;
    private static final int CELL_TRANSFER_OFFSET = 300_000;
    private static final int CELL_MOVE_OFFSET = 1_000_000;
    private static final int MOVE_SOURCE_FACTOR = 10_000;
    private static final int MOVE_INDEX_FACTOR = 10;
    private static final int CELL_TARGET_FACTOR = 100_000;
    private static final int CELL_CELL_FACTOR = 1_000;
    private static final int CELL_MOVE_SOURCE_FACTOR = 100_000;
    private static final int CELL_MOVE_INDEX_FACTOR = 1_000;
    private static final int CELL_MOVE_TARGET_FACTOR = 100;

    private final Container container;
    private final SimpleContainer raidDisplay;
    private final int[] displayedRaidIndexes;
    private final BlockPos containerPos;
    private final int containerRows;
    private final int containerSlotCount;
    private final boolean hasWorldContainer;
    private final ServerPlayer serverPlayer;
    private final boolean persistentBaseMode;
    private final PlayerStashService.PlayerStashData baseData;
    private final int backpackGridWidth;
    private final int backpackGridHeight;
    private final int vestGridWidth;
    private final int vestGridHeight;
    private final int safeGridWidth;
    private final int safeGridHeight;
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
        this(containerId, playerInventory, new SimpleContainer(data.containerSlotCount()), data.containerPos(), null, data.hasWorldContainer(),
                data.backpackGridWidth(), data.backpackGridHeight(), data.vestGridWidth(), data.vestGridHeight(), data.safeGridWidth(), data.safeGridHeight());
    }

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, Container container, BlockPos containerPos, ServerPlayer serverPlayer) {
        this(containerId, playerInventory, container, containerPos, serverPlayer, true, -1, -1, -1, -1, -1, -1);
    }

    public ActiveLootContainerMenu(int containerId, Inventory playerInventory, ServerPlayer serverPlayer) {
        this(containerId, playerInventory, new SimpleContainer(0), serverPlayer.blockPosition(), serverPlayer, false, -1, -1, -1, -1, -1, -1);
    }

    private ActiveLootContainerMenu(int containerId, Inventory playerInventory, Container container, BlockPos containerPos, ServerPlayer serverPlayer, boolean hasWorldContainer,
            int syncedBackpackGridWidth, int syncedBackpackGridHeight, int syncedVestGridWidth, int syncedVestGridHeight, int syncedSafeGridWidth, int syncedSafeGridHeight) {
        super(ExtractCraft.ACTIVE_LOOT_CONTAINER_MENU.get(), containerId);
        this.container = container;
        this.containerPos = containerPos;
        this.serverPlayer = serverPlayer;
        this.hasWorldContainer = hasWorldContainer;
        this.persistentBaseMode = serverPlayer != null && RaidManager.getRaidState(serverPlayer).isEmpty();
        this.baseData = persistentBaseMode ? PlayerStashService.load(serverPlayer) : null;
        RaidInventory gridInventory = serverPlayer == null
                ? null
                : (persistentBaseMode ? baseData.baseInventory() : RaidInventoryManager.get(serverPlayer));
        this.backpackGridWidth = clampGrid(syncedBackpackGridWidth >= 0 ? syncedBackpackGridWidth : gridInventory == null ? 0 : gridInventory.backpack().gridWidth(), 8);
        this.backpackGridHeight = clampGrid(syncedBackpackGridHeight >= 0 ? syncedBackpackGridHeight : gridInventory == null ? 0 : gridInventory.backpack().gridHeight(), 8);
        this.vestGridWidth = clampGrid(syncedVestGridWidth >= 0 ? syncedVestGridWidth : gridInventory == null ? 0 : gridInventory.vest().gridWidth(), 5);
        this.vestGridHeight = clampGrid(syncedVestGridHeight >= 0 ? syncedVestGridHeight : gridInventory == null ? 0 : gridInventory.vest().gridHeight(), 4);
        this.safeGridWidth = clampGrid(syncedSafeGridWidth >= 0 ? syncedSafeGridWidth : gridInventory == null ? 0 : gridInventory.safeBox().gridWidth(), 3);
        this.safeGridHeight = clampGrid(syncedSafeGridHeight >= 0 ? syncedSafeGridHeight : gridInventory == null ? 0 : gridInventory.safeBox().gridHeight(), 3);
        this.containerSlotCount = container.getContainerSize();
        this.containerRows = hasWorldContainer ? Math.max(1, (int) Math.ceil(containerSlotCount / (double) CONTAINER_COLUMNS)) : 0;
        this.raidDisplay = new SimpleContainer(RAID_DISPLAY_SLOTS);
        this.displayedRaidIndexes = new int[RAID_DISPLAY_SLOTS];
        java.util.Arrays.fill(this.displayedRaidIndexes, -1);

        addRaidDisplaySlots();
        addContainerSlots();
        logConstruction();

        this.backpackUsedCapacity = addDataSlot(statSlot(serverPlayer, baseData, Stat.BACKPACK_USED_CAPACITY));
        this.backpackMaxCapacity = addDataSlot(statSlot(serverPlayer, baseData, Stat.BACKPACK_MAX_CAPACITY));
        this.backpackUsedWeightTenths = addDataSlot(statSlot(serverPlayer, baseData, Stat.BACKPACK_USED_WEIGHT_TENTHS));
        this.backpackMaxWeightTenths = addDataSlot(statSlot(serverPlayer, baseData, Stat.BACKPACK_MAX_WEIGHT_TENTHS));
        this.backpackValue = addDataSlot(statSlot(serverPlayer, baseData, Stat.BACKPACK_VALUE));
        this.vestUsedCapacity = addDataSlot(statSlot(serverPlayer, baseData, Stat.VEST_USED_CAPACITY));
        this.vestMaxCapacity = addDataSlot(statSlot(serverPlayer, baseData, Stat.VEST_MAX_CAPACITY));
        this.vestUsedWeightTenths = addDataSlot(statSlot(serverPlayer, baseData, Stat.VEST_USED_WEIGHT_TENTHS));
        this.vestMaxWeightTenths = addDataSlot(statSlot(serverPlayer, baseData, Stat.VEST_MAX_WEIGHT_TENTHS));
        this.vestValue = addDataSlot(statSlot(serverPlayer, baseData, Stat.VEST_VALUE));
        this.safeBoxUsedCapacity = addDataSlot(statSlot(serverPlayer, baseData, Stat.SAFE_BOX_USED_CAPACITY));
        this.safeBoxMaxCapacity = addDataSlot(statSlot(serverPlayer, baseData, Stat.SAFE_BOX_MAX_CAPACITY));
        this.safeBoxUsedWeightTenths = addDataSlot(statSlot(serverPlayer, baseData, Stat.SAFE_BOX_USED_WEIGHT_TENTHS));
        this.safeBoxMaxWeightTenths = addDataSlot(statSlot(serverPlayer, baseData, Stat.SAFE_BOX_MAX_WEIGHT_TENTHS));
        this.safeBoxValue = addDataSlot(statSlot(serverPlayer, baseData, Stat.SAFE_BOX_VALUE));
        this.totalValue = addDataSlot(statSlot(serverPlayer, baseData, Stat.TOTAL_VALUE));

        rebuildRaidDisplay();
    }

    private static ClientData readClientData(RegistryFriendlyByteBuf buffer) {
        boolean hasWorldContainer = buffer.readBoolean();
        int containerSlotCount = hasWorldContainer ? buffer.readVarInt() : 0;
        BlockPos containerPos = buffer.readBlockPos();
        int backpackGridWidth = buffer.readVarInt();
        int backpackGridHeight = buffer.readVarInt();
        int vestGridWidth = buffer.readVarInt();
        int vestGridHeight = buffer.readVarInt();
        int safeGridWidth = buffer.readVarInt();
        int safeGridHeight = buffer.readVarInt();
        return new ClientData(hasWorldContainer, containerSlotCount, containerPos, backpackGridWidth, backpackGridHeight, vestGridWidth, vestGridHeight, safeGridWidth, safeGridHeight);
    }

    public static void writeGridDimensions(ServerPlayer player, RegistryFriendlyByteBuf buffer) {
        RaidInventory inventory = RaidManager.getRaidState(player).isEmpty()
                ? PlayerStashService.load(player).baseInventory()
                : RaidInventoryManager.get(player);
        buffer.writeVarInt(clampGrid(inventory.backpack().gridWidth(), 8));
        buffer.writeVarInt(clampGrid(inventory.backpack().gridHeight(), 8));
        buffer.writeVarInt(clampGrid(inventory.vest().gridWidth(), 5));
        buffer.writeVarInt(clampGrid(inventory.vest().gridHeight(), 4));
        buffer.writeVarInt(clampGrid(inventory.safeBox().gridWidth(), 3));
        buffer.writeVarInt(clampGrid(inventory.safeBox().gridHeight(), 3));
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

    private static int clampGrid(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isWorldContainerSlot(slotId) && clickType == ClickType.QUICK_MOVE) {
            if (player instanceof ServerPlayer serverPlayer) {
                transferContainerSlot(serverPlayer, containerSlotForMenuSlot(slotId), RaidEquipmentSlot.BACKPACK);
            }
            broadcastChanges();
            return;
        }

        if (isWorldContainerSlot(slotId) && clickType == ClickType.PICKUP) {
            broadcastChanges();
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Drag loot to a section, or shift-click to quick-move to Backpack."));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        if (id >= CELL_MOVE_OFFSET) {
            int payload = id - CELL_MOVE_OFFSET;
            int sourceId = Math.floorDiv(payload, CELL_MOVE_SOURCE_FACTOR);
            int remainder = Math.floorMod(payload, CELL_MOVE_SOURCE_FACTOR);
            int sourceIndex = Math.floorDiv(remainder, CELL_MOVE_INDEX_FACTOR);
            remainder = Math.floorMod(remainder, CELL_MOVE_INDEX_FACTOR);
            int targetId = Math.floorDiv(remainder, CELL_MOVE_TARGET_FACTOR);
            int cell = Math.floorMod(remainder, CELL_MOVE_TARGET_FACTOR);
            moveStoredItem(serverPlayer, slotFromId(sourceId), sourceIndex, slotFromId(targetId), cell);
        } else if (id >= CELL_TRANSFER_OFFSET) {
            int payload = id - CELL_TRANSFER_OFFSET;
            int targetId = Math.floorDiv(payload, CELL_TARGET_FACTOR);
            int remainder = Math.floorMod(payload, CELL_TARGET_FACTOR);
            int cell = Math.floorDiv(remainder, CELL_CELL_FACTOR);
            int containerSlot = Math.floorMod(remainder, CELL_CELL_FACTOR);
            transferContainerSlot(serverPlayer, containerSlot, slotFromId(targetId), cell);
        } else if (id >= RETURN_BUTTON_OFFSET) {
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
        broadcastChanges();
        return true;
    }

    public GridMoveResult handleGridMoveRequest(ServerPlayer player, int operation, int sourceSlotId, int sourceIndex, int targetSlotId, int targetCell) {
        GridMoveResult result = switch (operation) {
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_CONTAINER_TO_RAID_CELL -> {
                ItemStack moved = transferContainerSlot(player, sourceIndex, slotFromId(targetSlotId), targetCell);
                yield moved.isEmpty()
                        ? GridMoveResult.failure("Move rejected.")
                        : GridMoveResult.success("Moved " + moved.getHoverName().getString() + ".");
            }
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_RAID_TO_RAID_CELL ->
                    moveStoredItem(player, slotFromId(sourceSlotId), sourceIndex, slotFromId(targetSlotId), targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_RAID_TO_CONTAINER ->
                    returnStoredItemToContainer(player, slotFromId(sourceSlotId), sourceIndex);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_RAID_DROP ->
                    dropStoredItem(player, slotFromId(sourceSlotId), sourceIndex);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_RAID_SPLIT ->
                    splitStoredItem(player, slotFromId(sourceSlotId), sourceIndex, targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_CONTAINER_SPLIT ->
                    splitContainerItem(player, sourceIndex, targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_CARRIED_TO_RAID_CELL ->
                    placeCarriedToRaid(player, slotFromId(targetSlotId), targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_CARRIED_TO_CONTAINER ->
                    placeCarriedToContainer(player);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.ACTIVE_RAID_REPAIR ->
                    startRaidRepair(player, slotFromId(sourceSlotId), sourceIndex);
            default -> GridMoveResult.failure("Unsupported raid grid operation " + operation + ".");
        };
        broadcastChanges();
        return result;
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

    public static int cellButtonId(RaidEquipmentSlot target, int containerSlot, int cell) {
        return CELL_TRANSFER_OFFSET + slotId(target) * CELL_TARGET_FACTOR + cell * CELL_CELL_FACTOR + containerSlot;
    }

    public static int moveButtonId(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        return MOVE_BUTTON_OFFSET + slotId(source) * MOVE_SOURCE_FACTOR + sourceIndex * MOVE_INDEX_FACTOR + slotId(target);
    }

    public static int moveCellButtonId(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int cell) {
        return CELL_MOVE_OFFSET + slotId(source) * CELL_MOVE_SOURCE_FACTOR + sourceIndex * CELL_MOVE_INDEX_FACTOR + slotId(target) * CELL_MOVE_TARGET_FACTOR + cell;
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

    public BlockPos containerPos() {
        return containerPos;
    }

    public boolean hasWorldContainer() {
        return hasWorldContainer;
    }

    public boolean persistentBaseMode() {
        return persistentBaseMode;
    }

    public int containerMenuSlotStart() {
        return RAID_DISPLAY_SLOTS;
    }

    public RaidEquipmentSlot raidSlotForMenuSlot(int menuSlot) {
        if (menuSlot == HELMET_START) {
            return RaidEquipmentSlot.HELMET;
        }
        if (menuSlot == ARMOR_START) {
            return RaidEquipmentSlot.ARMOR;
        }
        if (menuSlot == EQUIPPED_BACKPACK_START) {
            return RaidEquipmentSlot.EQUIPPED_BACKPACK;
        }
        if (menuSlot == EQUIPPED_VEST_START) {
            return RaidEquipmentSlot.EQUIPPED_VEST;
        }
        if (menuSlot == EQUIPPED_SAFE_CONTAINER_START) {
            return RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER;
        }
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
        if (menuSlot >= 0 && menuSlot < this.slots.size()) {
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(this.slots.get(menuSlot).getItem());
            if (metadata.present()) {
                return metadata.sourceIndex();
            }
        }
        if (menuSlot >= 0 && menuSlot < displayedRaidIndexes.length) {
            return displayedRaidIndexes[menuSlot];
        }
        return -1;
    }

    public int raidMenuSlotForItemIndex(RaidEquipmentSlot slot, int sourceIndex) {
        if (slot == RaidEquipmentSlot.HELMET) {
            return HELMET_START;
        }
        if (slot == RaidEquipmentSlot.ARMOR) {
            return ARMOR_START;
        }
        if (slot == RaidEquipmentSlot.EQUIPPED_BACKPACK) {
            return EQUIPPED_BACKPACK_START;
        }
        if (slot == RaidEquipmentSlot.EQUIPPED_VEST) {
            return EQUIPPED_VEST_START;
        }
        if (slot == RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER) {
            return EQUIPPED_SAFE_CONTAINER_START;
        }
        if (slot == RaidEquipmentSlot.PRIMARY_WEAPON) {
            return PRIMARY_WEAPON_START;
        }
        if (slot == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return SECONDARY_WEAPON_START;
        }
        int start = switch (slot) {
            case BACKPACK -> BACKPACK_START;
            case VEST -> VEST_START;
            case SAFE_BOX -> SAFE_BOX_START;
            default -> 0;
        };
        int end = switch (slot) {
            case BACKPACK -> BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
            case VEST -> VEST_START + VEST_DISPLAY_SLOTS;
            case SAFE_BOX -> SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
            default -> 0;
        };
        for (int index = start; index < end; index++) {
            GridDisplayMetadata.Metadata metadata = index >= 0 && index < this.slots.size()
                    ? GridDisplayMetadata.read(this.slots.get(index).getItem())
                    : GridDisplayMetadata.Metadata.EMPTY;
            if (metadata.present() && metadata.sourceIndex() == sourceIndex && metadata.anchor()) {
                return index;
            }
            if (displayedRaidIndexes[index] == sourceIndex) {
                return index;
            }
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

    public int backpackGridWidth() {
        return backpackGridWidth;
    }

    public int backpackGridHeight() {
        return backpackGridHeight;
    }

    public int vestGridWidth() {
        return vestGridWidth;
    }

    public int vestGridHeight() {
        return vestGridHeight;
    }

    public int safeGridWidth() {
        return safeGridWidth;
    }

    public int safeGridHeight() {
        return safeGridHeight;
    }

    public int totalValue() {
        return totalValue.get();
    }

    public void refreshRaidDisplay() {
        rebuildRaidDisplay();
        broadcastChanges();
    }

    private boolean isWorldContainerSlot(int menuSlot) {
        return menuSlot >= RAID_DISPLAY_SLOTS && menuSlot < RAID_DISPLAY_SLOTS + containerSlotCount;
    }

    private int containerSlotForMenuSlot(int menuSlot) {
        return menuSlot - RAID_DISPLAY_SLOTS;
    }

    private ItemStack transferContainerSlot(ServerPlayer player, int containerSlot, RaidEquipmentSlot target) {
        return transferContainerSlot(player, containerSlot, target, -1);
    }

    private ItemStack transferContainerSlot(ServerPlayer player, int containerSlot, RaidEquipmentSlot target, int cell) {
        if (containerSlot < 0 || containerSlot >= container.getContainerSize()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = container.getItem(containerSlot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        ExtractCraft.LOGGER.info("Raid grid transfer attempt: player={}, source=CONTAINER#{}, target={}, targetCell={}, targetXY=({},{}), item={}x {}",
                player.getGameProfile().getName(),
                containerSlot,
                target,
                cell,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                copy.getCount(),
                copy.getHoverName().getString());
        if (persistentBaseMode) {
            return transferContainerSlotToBase(player, containerSlot, target, cell, stack, copy);
        }

        RaidInventory.AddResult result = cell >= 0 && isGridSlot(target)
                ? RaidInventoryManager.addStackTo(player, copy, target, cellX(target, cell), cellY(target, cell), false)
                : RaidInventoryManager.addStackTo(player, copy, target);
        if (result.movedCount() <= 0) {
            ExtractCraft.LOGGER.info("Raid loot cursor transfer failed: player={}, source=CONTAINER#{}, target={}, reason={}, movedCount={}",
                    player.getGameProfile().getName(),
                    containerSlot,
                    target,
                    result.message(),
                    result.movedCount());
            player.sendSystemMessage(Component.literal(result.message()));
            return ItemStack.EMPTY;
        }

        stack.shrink(result.movedCount());
        container.setItem(containerSlot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        container.setChanged();
        rebuildRaidDisplay();
        ExtractCraft.LOGGER.info("Raid loot cursor transfer committed: player={}, source=CONTAINER#{}, target={}, movedCount={}, sourceCleared={}, pendingCleared=true",
                player.getGameProfile().getName(),
                containerSlot,
                target,
                result.movedCount(),
                stack.isEmpty());
        player.sendSystemMessage(Component.literal("Moved " + result.movedCount() + "x " + copy.getHoverName().getString() + " to " + targetName(target) + "."));
        return copy;
    }

    private ItemStack transferContainerSlotToBase(ServerPlayer player, int containerSlot, RaidEquipmentSlot target, int cell, ItemStack stack, ItemStack copy) {
        RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, copy).orElse(null);
        if (item == null) {
            String message = BuiltInRegistries.ITEM.getKey(copy.getItem()) + " has no carry profile.";
            player.sendSystemMessage(Component.literal(message));
            return ItemStack.EMPTY;
        }
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            String message = item.lookupKey() + " has no carry profile.";
            player.sendSystemMessage(Component.literal(message));
            return ItemStack.EMPTY;
        }

        RaidInventory candidate = PlayerStashService.copyInventory(baseData.baseInventory());
        int targetX = cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1;
        int targetY = cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1;
        RaidInventory.AddResult result = cell >= 0 && isGridSlot(target)
                ? addToBaseStorageAt(candidate, item, profile, target, targetX, targetY, false)
                : addToBaseTarget(candidate, item, profile, target);
        if (result.movedCount() <= 0) {
            RaidStorageContainer targetStorage = isGridSlot(target) ? storage(candidate, target) : null;
            ExtractCraft.LOGGER.info("Debug loot base transfer failed: player={}, persistentBaseMode={}, activeRaid=false, debugContainer=true, source=CONTAINER#{}, target={}, targetGrid={}x{}, targetCell={}, receivedXY=({},{}), finalXY=({},{}), itemKey={}, footprint={}x{}, reason={}",
                    player.getGameProfile().getName(),
                    persistentBaseMode,
                    containerSlot,
                    target,
                    targetStorage == null ? -1 : targetStorage.gridWidth(),
                    targetStorage == null ? -1 : targetStorage.gridHeight(),
                    cell,
                    targetX,
                    targetY,
                    targetX,
                    targetY,
                    item.lookupKey(),
                    item.gridWidth(),
                    item.gridHeight(),
                    result.message());
            player.sendSystemMessage(Component.literal(result.message()));
            return ItemStack.EMPTY;
        }

        stack.shrink(result.movedCount());
        container.setItem(containerSlot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        container.setChanged();
        PlayerStashService.replaceInventoryContents(baseData.baseInventory(), candidate);
        PlayerStashService.save(player, baseData);
        RaidInventoryItem placed = isGridSlot(target) ? storage(baseData.baseInventory(), target).itemAtCell(targetX, targetY) : null;
        rebuildRaidDisplay();
        ExtractCraft.LOGGER.info("Debug loot base transfer committed: player={}, persistentBaseMode={}, activeRaid=false, debugContainer=true, source=CONTAINER#{}, target={}, targetCell={}, receivedXY=({},{}), finalXY=({},{}), placedKey={}, placedGrid=({},{}), movedCount={}, sourceCleared=true, savedToPersistentBaseInventory=true",
                player.getGameProfile().getName(),
                persistentBaseMode,
                containerSlot,
                target,
                cell,
                targetX,
                targetY,
                targetX,
                targetY,
                placed == null ? "unknown" : placed.lookupKey(),
                placed == null ? -1 : placed.gridX(),
                placed == null ? -1 : placed.gridY(),
                result.movedCount());
        player.sendSystemMessage(Component.literal(result.message()));
        return copy;
    }

    private GridMoveResult moveStoredItem(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        return moveStoredItem(player, source, sourceIndex, target, -1);
    }

    private GridMoveResult moveStoredItem(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int cell) {
        if (source == null || target == null) {
            return GridMoveResult.failure("Invalid source or target.");
        }
        RaidInventory inventory = currentInventory(player);
        RaidInventoryItem sourceItem = inventory.itemAt(source, sourceIndex);
        if (!persistentBaseMode && isInRaidRepairTarget(target)) {
            GridMoveResult repairResult = InRaidRepairService.startFromDrag(player, source, sourceIndex, target);
            if (repairResult.success()) {
                return repairResult;
            }
            if (sourceItem != null && InRaidRepairService.isInRaidRepairKit(sourceItem.toItemStack())) {
                player.sendSystemMessage(Component.literal(repairResult.message()));
                return repairResult;
            }
        }
        ExtractCraft.LOGGER.info("Raid grid move attempt: player={}, source={}#{}, target={}, targetCell={}, targetXY=({},{}), sourceItem={}",
                player.getGameProfile().getName(),
                source,
                sourceIndex,
                target,
                cell,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                sourceItemName(inventory, source, sourceIndex));
        RaidInventory.AddResult result = persistentBaseMode
                ? moveBaseInventoryItem(source, sourceIndex, target, cell)
                : cell >= 0 && isGridSlot(target)
                        ? RaidInventoryManager.moveBetween(player, source, sourceIndex, target, cellX(target, cell), cellY(target, cell), false)
                        : RaidInventoryManager.moveBetween(player, source, sourceIndex, target);
        if (!result.success()) {
            ExtractCraft.LOGGER.info("Raid loot cursor move failed: player={}, source={}#{}, sourceKey={}, sourceGrid=({},{}), sourceFootprint={}x{}, target={}, targetCell={}, targetXY=({},{}), sameSection={}, reason={}, movedCount={}",
                    player.getGameProfile().getName(),
                    source,
                    sourceIndex,
                    sourceItem == null ? "none" : sourceItem.lookupKey(),
                    sourceItem == null ? -1 : sourceItem.gridX(),
                    sourceItem == null ? -1 : sourceItem.gridY(),
                    sourceItem == null ? -1 : sourceItem.gridWidth(),
                    sourceItem == null ? -1 : sourceItem.gridHeight(),
                    target,
                    cell,
                    cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                    cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                    source == target,
                    result.message(),
                    result.movedCount());
            player.sendSystemMessage(Component.literal(result.message()));
            return GridMoveResult.failure(result.message());
        }

        if (persistentBaseMode) {
            PlayerStashService.save(player, baseData);
        }
        rebuildRaidDisplay();
        ExtractCraft.LOGGER.info("Raid loot cursor move committed: player={}, source={}#{}, sourceKey={}, sourceGrid=({},{}), sourceFootprint={}x{}, target={}, targetCell={}, targetXY=({},{}), sameSection={}, movedCount={}, pendingCleared=true",
                player.getGameProfile().getName(),
                source,
                sourceIndex,
                sourceItem == null ? "none" : sourceItem.lookupKey(),
                sourceItem == null ? -1 : sourceItem.gridX(),
                sourceItem == null ? -1 : sourceItem.gridY(),
                sourceItem == null ? -1 : sourceItem.gridWidth(),
                sourceItem == null ? -1 : sourceItem.gridHeight(),
                target,
                cell,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                source == target,
                result.movedCount());
        player.sendSystemMessage(Component.literal(result.message()));
        return GridMoveResult.success(result.message());
    }

    private GridMoveResult startRaidRepair(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        if (persistentBaseMode) {
            String message = "Repair kits can only be used during an active raid.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        return InRaidRepairService.startFromContext(player, source, sourceIndex);
    }

    private RaidInventory.AddResult moveBaseInventoryItem(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int cell) {
        RaidInventory candidate = PlayerStashService.copyInventory(baseData.baseInventory());
        RaidInventoryItem item = candidate.itemAt(source, sourceIndex);
        if (item == null) {
            return new RaidInventory.AddResult(false, target, "Source item is no longer available.", 0);
        }
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            return new RaidInventory.AddResult(false, target, item.lookupKey() + " has no carry profile.", 0);
        }

        if (cell >= 0 && source == target && isGridSlot(source)) {
            RaidStorageContainer storage = storage(candidate, source);
            int moved = storage.moveItemToCell(sourceIndex, cellX(target, cell), cellY(target, cell), false);
            return new RaidInventory.AddResult(moved > 0, target, moved > 0 ? "Moved item in " + target.name().toLowerCase() + "." : "Target cell is blocked.", moved);
        }

        RaidInventoryItem removed = candidate.removeCountAt(source, sourceIndex, item.count());
        if (removed == null) {
            return new RaidInventory.AddResult(false, target, "Source item is no longer available.", 0);
        }

        RaidInventory.AddResult result = cell >= 0 && isGridSlot(target)
                ? addToBaseStorageAt(candidate, removed, profile, target, cellX(target, cell), cellY(target, cell), false)
                : addToBaseTarget(candidate, removed, profile, target);
        if (result.movedCount() < removed.count()) {
            return result;
        }

        PlayerStashService.replaceInventoryContents(baseData.baseInventory(), candidate);
        return result;
    }

    private RaidInventory.AddResult addToBaseTarget(RaidInventory inventory, RaidInventoryItem item, ItemCarryProfile profile, RaidEquipmentSlot target) {
        if (RaidInventory.isEquipmentSlot(target)) {
            return inventory.equipItem(item, target);
        }
        if (target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return inventory.addToWeaponSlot(item, target);
        }
        if (target == RaidEquipmentSlot.VEST && profile.category() == ItemCategory.GUNS) {
            return new RaidInventory.AddResult(false, target, "Guns must be carried in Backpack or equipped.", 0);
        }
        if (target == RaidEquipmentSlot.SAFE_BOX && (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR)) {
            return new RaidInventory.AddResult(false, target, "Item is not allowed in safe box.", 0);
        }

        RaidStorageContainer targetStorage = storage(inventory, target);
        int moved = targetStorage.addPartialGridFirstFit(item, true);
        if (moved > 0) {
            return new RaidInventory.AddResult(true, target, "Moved " + moved + "x " + item.displayName() + " to " + targetStorage.name() + ".", moved);
        }
        return new RaidInventory.AddResult(false, target, targetStorage.name() + " has no room.", 0);
    }

    private RaidInventory.AddResult addToBaseStorageAt(RaidInventory inventory, RaidInventoryItem item, ItemCarryProfile profile, RaidEquipmentSlot target, int x, int y, boolean rotated) {
        if (RaidInventory.isEquipmentSlot(target)) {
            return inventory.equipItem(item, target);
        }
        if (target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return inventory.addToWeaponSlot(item, target);
        }
        if (target == RaidEquipmentSlot.VEST && profile.category() == ItemCategory.GUNS) {
            return new RaidInventory.AddResult(false, target, "Guns must be carried in Backpack or equipped.", 0);
        }
        if (target == RaidEquipmentSlot.SAFE_BOX && (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR)) {
            return new RaidInventory.AddResult(false, target, "Item is not allowed in safe box.", 0);
        }

        RaidStorageContainer targetStorage = storage(inventory, target);
        int moved = targetStorage.addPartialAt(item, x, y, rotated, -1, true);
        if (moved > 0) {
            return new RaidInventory.AddResult(true, target, "Moved " + moved + "x " + item.displayName() + " to " + targetStorage.name() + ".", moved);
        }
        return new RaidInventory.AddResult(false, target, targetStorage.name() + " target cell is blocked.", 0);
    }

    private GridMoveResult returnStoredItemToContainer(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        if (!hasWorldContainer) {
            String message = "No container is open.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        if (source == null) {
            return GridMoveResult.failure("Invalid source.");
        }

        RaidInventory inventory = currentInventory(player);
        RaidInventoryItem item = itemAt(inventory, source, sourceIndex);
        if (item == null) {
            String message = "Source item is no longer available.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }

        ItemStack stack = item.toItemStack();
        if (stack.isEmpty()) {
            String message = "Unable to rebuild item stack for " + item.lookupKey() + ".";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        int fitCount = countFitInContainer(stack);
        if (fitCount <= 0) {
            String message = "Container does not have room for that stack.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }

        RaidInventoryItem removed = inventory.removeCountAt(source, sourceIndex, Math.min(stack.getCount(), fitCount));
        if (removed == null) {
            String message = "Source item is no longer available.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }

        ItemStack removedStack = removed.toItemStack();
        insertIntoContainer(removedStack);
        container.setChanged();
        if (persistentBaseMode) {
            PlayerStashService.save(player, baseData);
        }
        rebuildRaidDisplay();
        String message = "Returned " + removedStack.getCount() + "x " + removedStack.getHoverName().getString() + " to container.";
        player.sendSystemMessage(Component.literal(message));
        return GridMoveResult.success(message);
    }

    private GridMoveResult dropStoredItem(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        if (source == null) {
            return GridMoveResult.failure("Invalid source.");
        }

        RaidInventory inventory = currentInventory(player);
        RaidInventoryItem item = itemAt(inventory, source, sourceIndex);
        if (item == null) {
            String message = "Source item is no longer available.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }

        RaidInventoryItem removed = inventory.removeCountAt(source, sourceIndex, item.count());
        if (removed == null) {
            String message = "Source item is no longer available.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }

        ItemStack stack = removed.toItemStack();
        if (stack.isEmpty()) {
            restoreRemovedItem(inventory, source, removed);
            String message = "Could not rebuild item stack for drop.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }

        com.chaseschwartz.extractcraft.raid.inventory.ManagedDropService.spawnManagedDrop(player, stack);
        if (persistentBaseMode) {
            PlayerStashService.save(player, baseData);
        }
        rebuildRaidDisplay();
        String message = "Dropped " + removed.displayName() + ".";
        player.sendSystemMessage(Component.literal(message));
        return GridMoveResult.success(message);
    }

    private GridMoveResult splitStoredItem(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, int amount) {
        if (source == null) {
            return GridMoveResult.failure("Invalid source.");
        }
        RaidInventory inventory = currentInventory(player);
        RaidInventoryItem item = itemAt(inventory, source, sourceIndex);
        String failure = splitFailure(item, amount);
        if (failure != null) {
            player.sendSystemMessage(Component.literal(failure));
            return GridMoveResult.failure(failure);
        }
        ItemStack preview = item.withCount(amount).toItemStack();
        if (!canAcceptSplit(preview, amount)) {
            String message = "Clear the cursor before splitting this stack.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        RaidInventoryItem removed = inventory.removeCountAt(source, sourceIndex, amount);
        return finishSplit(player, removed, inventory, source);
    }

    private GridMoveResult splitContainerItem(ServerPlayer player, int containerSlot, int amount) {
        if (containerSlot < 0 || containerSlot >= container.getContainerSize()) {
            return GridMoveResult.failure("Invalid container slot.");
        }
        ItemStack source = container.getItem(containerSlot);
        if (source.isEmpty() || source.getCount() <= 1 || source.getMaxStackSize() <= 1) {
            String message = "That item cannot be split.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        if (amount < 1 || amount >= source.getCount()) {
            String message = "Split amount must be between 1 and " + (source.getCount() - 1) + ".";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        ItemStack preview = source.copyWithCount(amount);
        if (!canAcceptSplit(preview, amount)) {
            String message = "Clear the cursor before splitting this stack.";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.failure(message);
        }
        ItemStack removed = source.split(amount);
        container.setChanged();
        acceptSplitStack(removed);
        String message = "Split " + removed.getCount() + "x " + removed.getHoverName().getString() + ".";
        player.sendSystemMessage(Component.literal(message));
        return GridMoveResult.success(message);
    }

    private String splitFailure(RaidInventoryItem item, int amount) {
        if (item == null) {
            return "Source item is no longer available.";
        }
        if (item.count() <= 1 || item.maxStackSize() <= 1) {
            return "That item cannot be split.";
        }
        if (amount < 1 || amount >= item.count()) {
            return "Split amount must be between 1 and " + (item.count() - 1) + ".";
        }
        return null;
    }

    private boolean canAcceptSplit(ItemStack splitStack, int amount) {
        ItemStack carried = getCarried();
        return carried.isEmpty()
                || (!splitStack.isEmpty()
                && ItemStack.isSameItemSameComponents(carried, splitStack)
                && carried.getCount() + amount <= carried.getMaxStackSize());
    }

    private GridMoveResult finishSplit(ServerPlayer player, RaidInventoryItem removed, RaidInventory inventory, RaidEquipmentSlot source) {
        if (removed == null) {
            return GridMoveResult.failure("Source item is no longer available.");
        }
        ItemStack splitStack = removed.toItemStack();
        if (splitStack.isEmpty()) {
            restoreRemovedItem(inventory, source, removed);
            return GridMoveResult.failure("Could not rebuild split stack.");
        }
        acceptSplitStack(splitStack);
        if (persistentBaseMode) {
            PlayerStashService.save(player, baseData);
        }
        rebuildRaidDisplay();
        String message = "Split " + splitStack.getCount() + "x " + splitStack.getHoverName().getString() + ".";
        player.sendSystemMessage(Component.literal(message));
        return GridMoveResult.success(message);
    }

    private void acceptSplitStack(ItemStack splitStack) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            setCarried(splitStack);
            return;
        }
        carried.grow(splitStack.getCount());
        setCarried(carried);
    }

    private GridMoveResult placeCarriedToRaid(ServerPlayer player, RaidEquipmentSlot target, int targetCell) {
        if (target == null || !isGridSlot(target) || targetCell < 0) {
            return GridMoveResult.failure("No valid target cell selected.");
        }
        ItemStack carried = getCarried();
        RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, carried).orElse(null);
        if (item == null) {
            return GridMoveResult.failure("Cursor item has no carry profile.");
        }
        RaidInventory inventory = currentInventory(player);
        RaidStorageContainer storage = storage(inventory, target);
        int moved = storage.addPartialAt(item.withoutPlacement(), cellX(target, targetCell), cellY(target, targetCell), false, -1, true);
        if (moved <= 0) {
            return GridMoveResult.failure("Target cell is blocked.");
        }
        ItemStack remaining = carried.copy();
        remaining.shrink(moved);
        setCarried(remaining);
        if (persistentBaseMode) {
            PlayerStashService.save(player, baseData);
        }
        rebuildRaidDisplay();
        return GridMoveResult.success("Placed split stack.");
    }

    private GridMoveResult placeCarriedToContainer(ServerPlayer player) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return GridMoveResult.failure("Cursor is empty.");
        }
        ItemStack before = carried.copy();
        ItemStack remaining = insertIntoContainerAndReturnRemaining(carried);
        int moved = before.getCount() - remaining.getCount();
        if (moved <= 0) {
            return GridMoveResult.failure("Container does not have room for that stack.");
        }
        setCarried(remaining);
        container.setChanged();
        return GridMoveResult.success("Returned " + moved + "x " + before.getHoverName().getString() + " to container.");
    }

    private void restoreRemovedItem(RaidInventory inventory, RaidEquipmentSlot source, RaidInventoryItem removed) {
        if (source == RaidEquipmentSlot.PRIMARY_WEAPON || source == RaidEquipmentSlot.SECONDARY_WEAPON) {
            inventory.setWeaponSlot(source, removed);
            return;
        }
        RaidStorageContainer storage = switch (source) {
            case BACKPACK -> inventory.backpack();
            case VEST -> inventory.vest();
            case SAFE_BOX -> inventory.safeBox();
            default -> throw new IllegalStateException("handled above");
        };
        storage.addPartialGridFirstFit(removed, true);
    }

    private void addRaidDisplaySlots() {
        addSlot(new ReadOnlyContainerSlot(raidDisplay, HELMET_START, 20, 42));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, ARMOR_START, 20, 72));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, EQUIPPED_BACKPACK_START, 20, 102));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, EQUIPPED_VEST_START, 20, 132));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, EQUIPPED_SAFE_CONTAINER_START, 20, 162));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, PRIMARY_WEAPON_START, 20, 200));
        addSlot(new ReadOnlyContainerSlot(raidDisplay, SECONDARY_WEAPON_START, 20, 230));
        addDisplayGrid(BACKPACK_START, BACKPACK_DISPLAY_SLOTS, Math.max(1, backpackGridWidth), 104, 62);
        addDisplayGrid(VEST_START, VEST_DISPLAY_SLOTS, Math.max(1, vestGridWidth), 104, 256);
        addDisplayGrid(SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, Math.max(1, safeGridWidth), 206, 256);
    }

    private void addDisplayGrid(int start, int count, int columns, int x, int y) {
        for (int i = 0; i < count; i++) {
            addSlot(new ReadOnlyContainerSlot(raidDisplay, start + i, x + (i % columns) * 18, y + (i / columns) * 18));
        }
    }

    private void addContainerSlots() {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            int x = 250 + (slot % CONTAINER_COLUMNS) * 18;
            int y = 58 + (slot / CONTAINER_COLUMNS) * 18;
            addSlot(new ReadOnlyContainerSlot(container, slot, x, y));
        }
    }

    private void rebuildRaidDisplay() {
        if (serverPlayer == null) {
            return;
        }

        raidDisplay.clearContent();
        java.util.Arrays.fill(displayedRaidIndexes, -1);
        RaidInventory inventory = currentInventory(serverPlayer);
        fillEquipmentDisplay(inventory);
        if (inventory.primaryWeapon() != null) {
            displayedRaidIndexes[PRIMARY_WEAPON_START] = 0;
            raidDisplay.setItem(PRIMARY_WEAPON_START, displayStack(inventory.primaryWeapon(), 0));
        }
        if (inventory.secondaryWeapon() != null) {
            displayedRaidIndexes[SECONDARY_WEAPON_START] = 0;
            raidDisplay.setItem(SECONDARY_WEAPON_START, displayStack(inventory.secondaryWeapon(), 0));
        }
        fillDisplay(BACKPACK_START, BACKPACK_DISPLAY_SLOTS, backpackGridWidth, inventory.backpack().items());
        fillDisplay(VEST_START, VEST_DISPLAY_SLOTS, vestGridWidth, inventory.vest().items());
        fillDisplay(SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, safeGridWidth, inventory.safeBox().items());
    }

    private void fillEquipmentDisplay(RaidInventory inventory) {
        setEquipmentDisplay(RaidEquipmentSlot.HELMET, HELMET_START, inventory.equipmentItem(RaidEquipmentSlot.HELMET));
        setEquipmentDisplay(RaidEquipmentSlot.ARMOR, ARMOR_START, inventory.equipmentItem(RaidEquipmentSlot.ARMOR));
        setEquipmentDisplay(RaidEquipmentSlot.EQUIPPED_BACKPACK, EQUIPPED_BACKPACK_START, inventory.equipmentItem(RaidEquipmentSlot.EQUIPPED_BACKPACK));
        setEquipmentDisplay(RaidEquipmentSlot.EQUIPPED_VEST, EQUIPPED_VEST_START, inventory.equipmentItem(RaidEquipmentSlot.EQUIPPED_VEST));
        setEquipmentDisplay(RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER, EQUIPPED_SAFE_CONTAINER_START, inventory.equipmentItem(RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER));
    }

    private void setEquipmentDisplay(RaidEquipmentSlot slot, int menuSlot, RaidInventoryItem item) {
        if (item == null) {
            return;
        }
        displayedRaidIndexes[menuSlot] = 0;
        raidDisplay.setItem(menuSlot, displayStack(item, 0));
    }

    private void fillDisplay(int start, int maxSlots, int columns, java.util.List<RaidInventoryItem> items) {
        int rows = maxSlots / Math.max(1, columns);
        int sequentialIndex = 0;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            RaidInventoryItem item = items.get(itemIndex);
            if (item.isPlaced() && (item.gridX() < 0 || item.gridY() < 0
                    || item.gridX() + footprintWidth(item) > columns
                    || item.gridY() + footprintHeight(item) > rows)) {
                ExtractCraft.LOGGER.info("ActiveLootContainer display skipped item outside visible grid: key={}, grid=({},{}), footprint={}x{}, visibleGrid={}x{}, persistentBaseMode={}",
                        item.lookupKey(),
                        item.gridX(),
                        item.gridY(),
                        footprintWidth(item),
                        footprintHeight(item),
                        columns,
                        rows,
                        persistentBaseMode);
                continue;
            }
            int displayIndex = item.isPlaced() ? item.gridY() * Math.max(1, columns) + item.gridX() : sequentialIndex++;
            if (displayIndex < 0 || displayIndex >= maxSlots) {
                continue;
            }
            placeDisplayFootprint(raidDisplay, displayedRaidIndexes, start, maxSlots, columns, displayIndex, item, itemIndex);
        }
    }

    private static void placeDisplayFootprint(SimpleContainer display, int[] displayedIndexes, int start, int maxSlots, int columns, int anchorIndex, RaidInventoryItem item, int itemIndex) {
        int width = footprintWidth(item);
        int height = footprintHeight(item);
        int anchorX = anchorIndex % Math.max(1, columns);
        int anchorY = anchorIndex / Math.max(1, columns);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int displayIndex = (anchorY + y) * Math.max(1, columns) + anchorX + x;
                if (displayIndex < 0 || displayIndex >= maxSlots || start + displayIndex >= displayedIndexes.length) {
                    continue;
                }
                boolean anchor = x == 0 && y == 0;
                displayedIndexes[start + displayIndex] = itemIndex;
                display.setItem(start + displayIndex, displayStack(item, itemIndex, anchor));
            }
        }
    }

    private static ItemStack displayStack(RaidInventoryItem item) {
        return displayStack(item, -1);
    }

    private static ItemStack displayStack(RaidInventoryItem item, int sourceIndex) {
        return displayStack(item, sourceIndex, true);
    }

    private static ItemStack displayStack(RaidInventoryItem item, int sourceIndex, boolean anchor) {
        ItemStack stored = item.toItemStack();
        if (!stored.isEmpty()) {
            return GridDisplayMetadata.stamp(stored, item, sourceIndex, anchor);
        }
        ItemStack stack = ItemStackVariantFactory.create(item.lookupKey(), item.count())
                .orElseGet(() -> {
                    if (BuiltInRegistries.ITEM.containsKey(item.itemId())) {
                        return new ItemStack(BuiltInRegistries.ITEM.get(item.itemId()), item.count());
                    }
                    return ItemStack.EMPTY;
                });
        return GridDisplayMetadata.stamp(stack, item, sourceIndex, anchor);
    }

    private static int footprintWidth(RaidInventoryItem item) {
        return Math.max(1, item.rotated() ? item.gridHeight() : item.gridWidth());
    }

    private static int footprintHeight(RaidInventoryItem item) {
        return Math.max(1, item.rotated() ? item.gridWidth() : item.gridHeight());
    }

    private int countFitInContainer(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) {
                int free = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                remaining.shrink(free);
                if (remaining.isEmpty()) {
                    return stack.getCount();
                }
                continue;
            }
            if (ItemStack.isSameItemSameComponents(current, remaining) && current.getCount() < current.getMaxStackSize()) {
                int transferable = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                remaining.shrink(transferable);
                if (remaining.isEmpty()) {
                    return stack.getCount();
                }
            }
        }
        return stack.getCount() - remaining.getCount();
    }

    private void insertIntoContainer(ItemStack stack) {
        insertIntoContainerAndReturnRemaining(stack);
    }

    private ItemStack insertIntoContainerAndReturnRemaining(ItemStack stack) {
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
        return remaining;
    }

    private static RaidInventoryItem itemAt(RaidInventory inventory, RaidEquipmentSlot source, int sourceIndex) {
        return inventory.itemAt(source, sourceIndex);
    }

    private static RaidStorageContainer storage(RaidInventory inventory, RaidEquipmentSlot slot) {
        return switch (slot) {
            case BACKPACK -> inventory.backpack();
            case VEST -> inventory.vest();
            case SAFE_BOX -> inventory.safeBox();
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalArgumentException("Non-storage slots do not have grid storage.");
        };
    }

    private static String sourceItemName(RaidInventory inventory, RaidEquipmentSlot source, int sourceIndex) {
        RaidInventoryItem item = inventory.itemAt(source, sourceIndex);
        return item == null ? "none" : item.lookupKey() + " x" + item.count();
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
            default -> "equipment";
        };
    }

    private static boolean isGridSlot(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.BACKPACK || slot == RaidEquipmentSlot.VEST || slot == RaidEquipmentSlot.SAFE_BOX;
    }

    private static boolean isInRaidRepairTarget(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.HELMET || slot == RaidEquipmentSlot.ARMOR || slot == RaidEquipmentSlot.EQUIPPED_BACKPACK;
    }

    private int cellX(RaidEquipmentSlot slot, int cell) {
        return cell % columnsFor(slot);
    }

    private int cellY(RaidEquipmentSlot slot, int cell) {
        return cell / columnsFor(slot);
    }

    private int columnsFor(RaidEquipmentSlot slot) {
        return switch (slot) {
            case VEST -> Math.max(1, vestGridWidth);
            case SAFE_BOX -> Math.max(1, safeGridWidth);
            default -> Math.max(1, backpackGridWidth);
        };
    }

    private static int slotId(RaidEquipmentSlot slot) {
        return switch (slot) {
            case PRIMARY_WEAPON -> 0;
            case SECONDARY_WEAPON -> 1;
            case BACKPACK -> 2;
            case VEST -> 3;
            case SAFE_BOX -> 4;
            case HELMET -> 5;
            case ARMOR -> 6;
            case EQUIPPED_BACKPACK -> 7;
            default -> -1;
        };
    }

    private static RaidEquipmentSlot slotFromId(int id) {
        return switch (id) {
            case 0 -> RaidEquipmentSlot.PRIMARY_WEAPON;
            case 1 -> RaidEquipmentSlot.SECONDARY_WEAPON;
            case 3 -> RaidEquipmentSlot.VEST;
            case 4 -> RaidEquipmentSlot.SAFE_BOX;
            case 5 -> RaidEquipmentSlot.HELMET;
            case 6 -> RaidEquipmentSlot.ARMOR;
            case 7 -> RaidEquipmentSlot.EQUIPPED_BACKPACK;
            default -> RaidEquipmentSlot.BACKPACK;
        };
    }

    private RaidInventory currentInventory(ServerPlayer player) {
        if (persistentBaseMode && baseData != null) {
            return baseData.baseInventory();
        }
        return RaidInventoryManager.get(player);
    }

    private static DataSlot statSlot(ServerPlayer player, PlayerStashService.PlayerStashData baseData, Stat stat) {
        if (player == null) {
            return DataSlot.standalone();
        }

        return new DataSlot() {
            @Override
            public int get() {
                RaidInventory inventory = baseData == null ? RaidInventoryManager.get(player) : baseData.baseInventory();
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

    private record ClientData(boolean hasWorldContainer, int containerSlotCount, BlockPos containerPos,
            int backpackGridWidth, int backpackGridHeight, int vestGridWidth, int vestGridHeight, int safeGridWidth, int safeGridHeight) {
    }
}
