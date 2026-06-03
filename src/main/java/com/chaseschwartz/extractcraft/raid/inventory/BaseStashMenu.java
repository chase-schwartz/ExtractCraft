package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.Comparator;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class BaseStashMenu extends AbstractContainerMenu {
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
    public static final int VEST_DISPLAY_SLOTS = 12;
    public static final int SAFE_BOX_START = VEST_START + VEST_DISPLAY_SLOTS;
    public static final int SAFE_BOX_DISPLAY_SLOTS = 9;
    public static final int BASE_DISPLAY_SLOTS = SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
    public static final int STASH_COLUMNS_MIN = 10;
    private static final int STASH_SLOT_X = 286;
    private static final int STASH_SLOT_Y = 82;
    private static final int MOVE_BASE_TO_STASH_OFFSET = 100_000;
    private static final int MOVE_STASH_TO_BASE_OFFSET = 200_000;
    private static final int MOVE_BASE_TO_BASE_OFFSET = 300_000;
    private static final int MOVE_STASH_TO_BASE_CELL_OFFSET = 400_000;
    private static final int MOVE_BASE_TO_BASE_CELL_OFFSET = 1_000_000;
    private static final int SORT_OFFSET = 2_000_000;
    private static final int CONTEXT_ACTION_OFFSET = 3_000_000;
    private static final int SOURCE_FACTOR = 10_000;
    private static final int INDEX_FACTOR = 10;
    private static final int CONTEXT_ACTION_FACTOR = 1_000_000;
    private static final int CONTEXT_KIND_FACTOR = 100_000;
    private static final int CONTEXT_SOURCE_FACTOR = 10_000;
    private static final int CELL_SOURCE_FACTOR = 100_000;
    private static final int CELL_INDEX_FACTOR = 1_000;
    private static final int CELL_TARGET_FACTOR = 100;

    private final SimpleContainer baseDisplay;
    private final SimpleContainer stashDisplay;
    private final int[] displayedBaseIndexes;
    private final ServerPlayer serverPlayer;
    private final PlayerStashService.PlayerStashData stashData;
    private final int stashCapacity;
    private final int[] displayedStashIndexes;
    private final int backpackGridWidth;
    private final int backpackGridHeight;
    private final int vestGridWidth;
    private final int vestGridHeight;
    private final int safeGridWidth;
    private final int safeGridHeight;
    private SortMode sortMode = SortMode.MANUAL;
    private final DataSlot stashUsedCapacity;
    private final DataSlot stashMaxCapacity;
    private final DataSlot stashLevel;
    private final DataSlot credits;
    private final DataSlot totalValue;
    private boolean reopenAfterChange;

    public BaseStashMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, null, null, data.readVarInt(), data.readVarInt(), data.readVarInt(),
                data.readVarInt(), data.readVarInt(), data.readVarInt(), data.readVarInt(), data.readVarInt(), data.readVarInt());
    }

    public BaseStashMenu(int containerId, Inventory playerInventory, ServerPlayer player, PlayerStashService.PlayerStashData stashData) {
        this(containerId, playerInventory, player, stashData, stashData.stash().capacity(), stashData.stashLevel(), stashData.credits(),
                stashData.baseInventory().backpack().gridWidth(), stashData.baseInventory().backpack().gridHeight(),
                stashData.baseInventory().vest().gridWidth(), stashData.baseInventory().vest().gridHeight(),
                stashData.baseInventory().safeBox().gridWidth(), stashData.baseInventory().safeBox().gridHeight());
    }

    private BaseStashMenu(int containerId, Inventory playerInventory, ServerPlayer player, PlayerStashService.PlayerStashData stashData, int stashCapacity, int initialStashLevel, int initialCredits,
            int backpackGridWidth, int backpackGridHeight, int vestGridWidth, int vestGridHeight, int safeGridWidth, int safeGridHeight) {
        super(ExtractCraft.BASE_STASH_MENU.get(), containerId);
        this.serverPlayer = player;
        this.stashData = stashData;
        this.stashCapacity = Math.max(1, stashCapacity);
        this.backpackGridWidth = Math.max(0, Math.min(8, backpackGridWidth));
        this.backpackGridHeight = Math.max(0, Math.min(8, backpackGridHeight));
        this.vestGridWidth = Math.max(0, Math.min(4, vestGridWidth));
        this.vestGridHeight = Math.max(0, Math.min(3, vestGridHeight));
        this.safeGridWidth = Math.max(0, Math.min(3, safeGridWidth));
        this.safeGridHeight = Math.max(0, Math.min(3, safeGridHeight));
        this.displayedStashIndexes = new int[this.stashCapacity];
        this.displayedBaseIndexes = new int[BASE_DISPLAY_SLOTS];
        java.util.Arrays.fill(this.displayedStashIndexes, -1);
        java.util.Arrays.fill(this.displayedBaseIndexes, -1);
        this.baseDisplay = new SimpleContainer(BASE_DISPLAY_SLOTS);
        this.stashDisplay = new SimpleContainer(this.stashCapacity);

        addBaseDisplaySlots();
        addStashSlots();

        this.stashUsedCapacity = addDataSlot(stashStat(player, initialStashLevel, initialCredits, StashStat.USED_CAPACITY));
        this.stashMaxCapacity = addDataSlot(stashStat(player, initialStashLevel, initialCredits, StashStat.MAX_CAPACITY));
        this.stashLevel = addDataSlot(stashStat(player, initialStashLevel, initialCredits, StashStat.LEVEL));
        this.credits = addDataSlot(stashStat(player, initialStashLevel, initialCredits, StashStat.CREDITS));
        this.totalValue = addDataSlot(stashStat(player, initialStashLevel, initialCredits, StashStat.TOTAL_VALUE));

        rebuildDisplays();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Drag items between Base Inventory and Stash."));
        }
        setCarried(ItemStack.EMPTY);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        reopenAfterChange = false;
        boolean changed;
        if (id >= CONTEXT_ACTION_OFFSET) {
            int payload = id - CONTEXT_ACTION_OFFSET;
            int action = Math.floorDiv(payload, CONTEXT_ACTION_FACTOR);
            int remainder = Math.floorMod(payload, CONTEXT_ACTION_FACTOR);
            int kind = Math.floorDiv(remainder, CONTEXT_KIND_FACTOR);
            remainder = Math.floorMod(remainder, CONTEXT_KIND_FACTOR);
            int sourceId = Math.floorDiv(remainder, CONTEXT_SOURCE_FACTOR);
            int sourceIndex = Math.floorMod(remainder, CONTEXT_SOURCE_FACTOR);
            changed = handleContextAction(serverPlayer, action, kind == 1, slotFromId(sourceId), sourceIndex);
        } else if (id >= SORT_OFFSET) {
            sortStashOnce(serverPlayer, SortMode.fromId(id - SORT_OFFSET));
            rebuildDisplays();
            broadcastChanges();
            return true;
        } else if (id >= MOVE_BASE_TO_BASE_CELL_OFFSET) {
            int payload = id - MOVE_BASE_TO_BASE_CELL_OFFSET;
            int sourceId = Math.floorDiv(payload, CELL_SOURCE_FACTOR);
            int remainder = Math.floorMod(payload, CELL_SOURCE_FACTOR);
            int sourceIndex = Math.floorDiv(remainder, CELL_INDEX_FACTOR);
            remainder = Math.floorMod(remainder, CELL_INDEX_FACTOR);
            int targetId = Math.floorDiv(remainder, CELL_TARGET_FACTOR);
            int cell = Math.floorMod(remainder, CELL_TARGET_FACTOR);
            changed = moveBaseToBase(serverPlayer, slotFromId(sourceId), sourceIndex, slotFromId(targetId), cell);
        } else if (id >= MOVE_STASH_TO_BASE_CELL_OFFSET) {
            int payload = id - MOVE_STASH_TO_BASE_CELL_OFFSET;
            int stashDisplayIndex = Math.floorDiv(payload, CELL_INDEX_FACTOR);
            int remainder = Math.floorMod(payload, CELL_INDEX_FACTOR);
            int targetId = Math.floorDiv(remainder, CELL_TARGET_FACTOR);
            int cell = Math.floorMod(remainder, CELL_TARGET_FACTOR);
            changed = moveStashToBase(serverPlayer, stashDisplayIndex, slotFromId(targetId), cell);
        } else if (id >= MOVE_BASE_TO_BASE_OFFSET) {
            int payload = id - MOVE_BASE_TO_BASE_OFFSET;
            int sourceId = Math.floorDiv(payload, SOURCE_FACTOR);
            int remainder = Math.floorMod(payload, SOURCE_FACTOR);
            int sourceIndex = Math.floorDiv(remainder, INDEX_FACTOR);
            int targetId = Math.floorMod(remainder, INDEX_FACTOR);
            changed = moveBaseToBase(serverPlayer, slotFromId(sourceId), sourceIndex, slotFromId(targetId));
        } else if (id >= MOVE_STASH_TO_BASE_OFFSET) {
            int payload = id - MOVE_STASH_TO_BASE_OFFSET;
            int stashDisplayIndex = Math.floorDiv(payload, INDEX_FACTOR);
            int targetId = Math.floorMod(payload, INDEX_FACTOR);
            changed = moveStashToBase(serverPlayer, stashDisplayIndex, slotFromId(targetId));
        } else if (id >= MOVE_BASE_TO_STASH_OFFSET) {
            int payload = id - MOVE_BASE_TO_STASH_OFFSET;
            int sourceId = Math.floorDiv(payload, SOURCE_FACTOR);
            int sourceIndex = Math.floorMod(payload, SOURCE_FACTOR);
            changed = moveBaseToStash(serverPlayer, slotFromId(sourceId), sourceIndex);
        } else {
            changed = false;
        }

        if (changed) {
            PlayerStashService.save(serverPlayer, stashData);
            rebuildDisplays();
        }
        setCarried(ItemStack.EMPTY);
        broadcastChanges();
        reopenIfRequested(serverPlayer, changed);
        return true;
    }

    public GridMoveResult handleGridMoveRequest(ServerPlayer player, int operation, int sourceSlotId, int sourceIndex, int targetSlotId, int targetCell) {
        reopenAfterChange = false;
        boolean changed = switch (operation) {
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_STASH_TO_BASE_CELL ->
                    moveStashToBase(player, sourceIndex, slotFromId(targetSlotId), targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_BASE_TO_BASE_CELL ->
                    moveBaseToBase(player, slotFromId(sourceSlotId), sourceIndex, slotFromId(targetSlotId), targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_BASE_TO_STASH ->
                    moveBaseToStash(player, slotFromId(sourceSlotId), sourceIndex, targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_BASE_DROP ->
                    dropBaseItem(player, slotFromId(sourceSlotId), sourceIndex);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_STASH_DROP ->
                    dropStashItem(player, sourceIndex);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_STASH_QUICK_TO_BASE ->
                    quickMoveStashToBase(player, sourceIndex);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_STASH_TO_STASH_CELL ->
                    moveStashToStash(player, sourceIndex, targetCell);
            default -> false;
        };
        if (changed) {
            PlayerStashService.save(player, stashData);
            rebuildDisplays();
        }
        setCarried(ItemStack.EMPTY);
        broadcastChanges();
        reopenIfRequested(player, changed);
        return changed ? GridMoveResult.success("Move committed.") : GridMoveResult.failure("Move rejected.");
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public static int baseToStashButtonId(RaidEquipmentSlot source, int sourceIndex) {
        return MOVE_BASE_TO_STASH_OFFSET + slotId(source) * SOURCE_FACTOR + sourceIndex;
    }

    public static int stashToBaseButtonId(int stashDisplayIndex, RaidEquipmentSlot target) {
        return MOVE_STASH_TO_BASE_OFFSET + stashDisplayIndex * INDEX_FACTOR + slotId(target);
    }

    public static int stashToBaseCellButtonId(int stashDisplayIndex, RaidEquipmentSlot target, int cell) {
        return MOVE_STASH_TO_BASE_CELL_OFFSET + stashDisplayIndex * CELL_INDEX_FACTOR + slotId(target) * CELL_TARGET_FACTOR + cell;
    }

    public static int baseToBaseButtonId(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        return MOVE_BASE_TO_BASE_OFFSET + slotId(source) * SOURCE_FACTOR + sourceIndex * INDEX_FACTOR + slotId(target);
    }

    public static int baseToBaseCellButtonId(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int cell) {
        return MOVE_BASE_TO_BASE_CELL_OFFSET + slotId(source) * CELL_SOURCE_FACTOR + sourceIndex * CELL_INDEX_FACTOR + slotId(target) * CELL_TARGET_FACTOR + cell;
    }

    public static int sortButtonId(int sortId) {
        return SORT_OFFSET + sortId;
    }

    public static int contextActionButtonId(int action, boolean stashSource, RaidEquipmentSlot source, int sourceIndex) {
        return CONTEXT_ACTION_OFFSET + action * CONTEXT_ACTION_FACTOR + (stashSource ? 1 : 0) * CONTEXT_KIND_FACTOR + slotId(source) * CONTEXT_SOURCE_FACTOR + sourceIndex;
    }

    public RaidEquipmentSlot baseSlotForMenuSlot(int menuSlot) {
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

    public int baseItemIndexForMenuSlot(int menuSlot) {
        RaidEquipmentSlot slot = baseSlotForMenuSlot(menuSlot);
        if (slot == RaidEquipmentSlot.PRIMARY_WEAPON || slot == RaidEquipmentSlot.SECONDARY_WEAPON || RaidInventory.isEquipmentSlot(slot)) {
            return 0;
        }
        if (menuSlot >= 0 && menuSlot < this.slots.size()) {
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(this.slots.get(menuSlot).getItem());
            if (metadata.present()) {
                return metadata.sourceIndex();
            }
        }
        if (menuSlot >= 0 && menuSlot < displayedBaseIndexes.length) {
            return displayedBaseIndexes[menuSlot];
        }
        return -1;
    }

    public int baseMenuSlotForItemIndex(RaidEquipmentSlot slot, int sourceIndex) {
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
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> 0;
        };
        int end = switch (slot) {
            case BACKPACK -> BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
            case VEST -> VEST_START + VEST_DISPLAY_SLOTS;
            case SAFE_BOX -> SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> 0;
        };
        for (int index = start; index < end; index++) {
            GridDisplayMetadata.Metadata metadata = index >= 0 && index < this.slots.size()
                    ? GridDisplayMetadata.read(this.slots.get(index).getItem())
                    : GridDisplayMetadata.Metadata.EMPTY;
            if (metadata.present() && metadata.sourceIndex() == sourceIndex && metadata.anchor()) {
                return index;
            }
            if (displayedBaseIndexes[index] == sourceIndex) {
                return index;
            }
        }
        return -1;
    }

    public boolean isStashMenuSlot(int menuSlot) {
        return menuSlot >= stashMenuSlotStart() && menuSlot < stashMenuSlotStart() + stashCapacity;
    }

    public int stashDisplayIndexForMenuSlot(int menuSlot) {
        return menuSlot - stashMenuSlotStart();
    }

    public int stashActualIndexForDisplayIndex(int stashDisplayIndex) {
        return stashSourceIndex(stashDisplayIndex);
    }

    public int stashMenuSlotStart() {
        return BASE_DISPLAY_SLOTS;
    }

    public int stashCapacity() {
        return stashCapacity;
    }

    public int stashColumns() {
        return stashColumns(stashCapacity);
    }

    public int stashRows() {
        return (int) Math.ceil(stashCapacity / (double) stashColumns());
    }

    public int stashUsedCapacity() {
        return stashUsedCapacity.get();
    }

    public int stashMaxCapacity() {
        return stashMaxCapacity.get();
    }

    public int stashLevel() {
        return stashLevel.get();
    }

    public int credits() {
        return credits.get();
    }

    public int totalValue() {
        return totalValue.get();
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

    public String sortModeName() {
        return sortMode.label;
    }

    private void sortStashOnce(ServerPlayer player, SortMode mode) {
        stashData.stash().sortItems(mode.itemComparator);
        if (!stashData.stash().repackFirstFit(true)) {
            player.sendSystemMessage(Component.literal("Could not repack stash after sorting."));
            return;
        }
        sortMode = SortMode.MANUAL;
        PlayerStashService.save(player, stashData);
        player.sendSystemMessage(Component.literal("Sorted stash by " + mode.label + ". Manual placement restored."));
    }

    private boolean moveBaseToStash(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        return moveBaseToStash(player, source, sourceIndex, -1);
    }

    private boolean moveBaseToStash(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, int targetCell) {
        RaidInventoryItem item = stashData.baseInventory().itemAt(source, sourceIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }
        int targetX = targetCell >= 0 ? stashCellX(targetCell) : -1;
        int targetY = targetCell >= 0 ? stashCellY(targetCell) : -1;
        if (targetCell >= 0) {
            int moved = stashData.stash().copy().addPartialAt(PlayerStashService.copyItem(item).withoutPlacement(), targetX, targetY, false, -1, true);
            if (moved < item.count()) {
                player.sendSystemMessage(Component.literal("Stash target is blocked."));
                return false;
            }
        } else if (stashData.stash().countAddable(item, -1) < item.count()) {
            player.sendSystemMessage(Component.literal("Stash does not have enough capacity."));
            return false;
        }

        RaidInventoryItem removed = stashData.baseInventory().removeCountAt(source, sourceIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }
        int moved = targetCell >= 0
                ? stashData.stash().addPartialAt(removed.withoutPlacement(), targetX, targetY, false, -1, true)
                : stashData.stash().addPartial(removed);
        if (moved < removed.count()) {
            restoreRemovedItem(false, source, removed);
            player.sendSystemMessage(Component.literal("Stash target is blocked."));
            return false;
        }
        player.sendSystemMessage(Component.literal("Moved " + removed.displayName() + " to stash."));
        reopenIfEquipmentMove(source, null);
        return true;
    }

    private boolean moveStashToStash(ServerPlayer player, int stashDisplayIndex, int targetCell) {
        int stashIndex = stashSourceIndex(stashDisplayIndex);
        RaidInventoryItem item = stashData.stash().itemAt(stashIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }
        if (targetCell < 0) {
            player.sendSystemMessage(Component.literal("No stash target cell selected."));
            return false;
        }

        int targetX = stashCellX(targetCell);
        int targetY = stashCellY(targetCell);
        RaidStorageContainer copy = stashData.stash().copy();
        RaidInventoryItem copyRemoved = copy.removeCountAt(stashIndex, item.count());
        if (copyRemoved == null || copy.addPartialAt(copyRemoved.withoutPlacement(), targetX, targetY, false, -1, true) < item.count()) {
            player.sendSystemMessage(Component.literal("Stash target is blocked."));
            return false;
        }

        RaidInventoryItem removed = stashData.stash().removeCountAt(stashIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }

        int moved = stashData.stash().addPartialAt(removed.withoutPlacement(), targetX, targetY, false, -1, true);
        if (moved >= removed.count()) {
            player.sendSystemMessage(Component.literal("Moved " + removed.displayName() + " in stash."));
            return true;
        }

        stashData.stash().addPartialAt(removed.withoutPlacement(), removed.gridX(), removed.gridY(), removed.rotated(), -1, true);
        player.sendSystemMessage(Component.literal("Stash target is blocked."));
        return false;
    }

    private boolean handleContextAction(ServerPlayer player, int action, boolean stashSource, RaidEquipmentSlot source, int sourceIndex) {
        RaidInventoryItem item = stashSource ? stashData.stash().itemAt(stashSourceIndex(sourceIndex)) : stashData.baseInventory().itemAt(source, sourceIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }

        int actualIndex = stashSource ? stashSourceIndex(sourceIndex) : sourceIndex;
        RaidInventoryItem removed = stashSource
                ? stashData.stash().removeCountAt(actualIndex, item.count())
                : stashData.baseInventory().removeCountAt(source, actualIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }

        if (action == 0) {
            int value = removed.totalValue();
            stashData.setCredits(stashData.credits() + value);
            player.sendSystemMessage(Component.literal("Sold " + removed.displayName() + " for " + value + " Emeralds."));
        } else if (action == 1) {
            ItemStack stack = removed.toItemStack();
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.literal("Could not rebuild item stack for drop."));
                restoreRemovedItem(stashSource, source, removed);
                return false;
            }
            ManagedDropService.spawnManagedDrop(player, stack);
            player.sendSystemMessage(Component.literal("Dropped " + removed.displayName() + "."));
        } else if (action == 2) {
            player.sendSystemMessage(Component.literal("Trashed " + removed.displayName() + "."));
        } else {
            restoreRemovedItem(stashSource, source, removed);
            return false;
        }
        if (!stashSource) {
            reopenIfEquipmentMove(source, null);
        }
        return true;
    }

    private boolean dropBaseItem(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        RaidInventoryItem item = stashData.baseInventory().itemAt(source, sourceIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }
        RaidInventoryItem removed = stashData.baseInventory().removeCountAt(source, sourceIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }
        boolean dropped = spawnManagedDropOrRestore(player, false, source, removed);
        if (dropped) {
            reopenIfEquipmentMove(source, null);
        }
        return dropped;
    }

    private boolean dropStashItem(ServerPlayer player, int stashDisplayIndex) {
        int stashIndex = stashSourceIndex(stashDisplayIndex);
        RaidInventoryItem item = stashData.stash().itemAt(stashIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }
        RaidInventoryItem removed = stashData.stash().removeCountAt(stashIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }
        return spawnManagedDropOrRestore(player, true, RaidEquipmentSlot.BACKPACK, removed);
    }

    private boolean spawnManagedDropOrRestore(ServerPlayer player, boolean stashSource, RaidEquipmentSlot source, RaidInventoryItem removed) {
        ItemStack stack = removed.toItemStack();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Could not rebuild item stack for drop."));
            restoreRemovedItem(stashSource, source, removed);
            return false;
        }
        ManagedDropService.spawnManagedDrop(player, stack);
        player.sendSystemMessage(Component.literal("Dropped " + removed.displayName() + "."));
        return true;
    }

    private void restoreRemovedItem(boolean stashSource, RaidEquipmentSlot source, RaidInventoryItem removed) {
        if (stashSource) {
            stashData.stash().addPartial(removed);
            return;
        }
        if (source == RaidEquipmentSlot.PRIMARY_WEAPON || source == RaidEquipmentSlot.SECONDARY_WEAPON) {
            stashData.baseInventory().setWeaponSlot(source, removed);
        } else if (RaidInventory.isEquipmentSlot(source)) {
            stashData.baseInventory().setEquipmentSlot(source, removed);
        } else {
            RaidStorageContainer target = switch (source) {
                case BACKPACK -> stashData.baseInventory().backpack();
                case VEST -> stashData.baseInventory().vest();
                case SAFE_BOX -> stashData.baseInventory().safeBox();
                case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalStateException("handled above");
            };
            target.addPartial(removed);
        }
    }

    private boolean moveStashToBase(ServerPlayer player, int stashDisplayIndex, RaidEquipmentSlot target) {
        return moveStashToBase(player, stashDisplayIndex, target, -1);
    }

    private boolean quickMoveStashToBase(ServerPlayer player, int stashDisplayIndex) {
        int stashIndex = stashSourceIndex(stashDisplayIndex);
        RaidInventoryItem item = stashData.stash().itemAt(stashIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }

        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            player.sendSystemMessage(Component.literal(item.lookupKey() + " has no carry profile."));
            return false;
        }

        RaidInventory.AddResult lastResult = null;
        for (RaidEquipmentSlot target : List.of(RaidEquipmentSlot.BACKPACK, RaidEquipmentSlot.VEST, RaidEquipmentSlot.SAFE_BOX)) {
            RaidInventory candidate = PlayerStashService.copyInventory(stashData.baseInventory());
            RaidInventoryItem copy = PlayerStashService.copyItem(item).withoutPlacement();
            RaidInventory.AddResult result = quickAddToBaseStorage(candidate, copy, profile, target);
            lastResult = result;
            RaidStorageContainer targetStorage = storage(candidate, target);
            ExtractCraft.LOGGER.info("BaseStash shift-click first-fit attempt: key={}, footprint={}x{}, target={}, grid={}x{}, moved={}/{}, success={}, message={}",
                    item.lookupKey(),
                    copy.gridWidth(),
                    copy.gridHeight(),
                    target,
                    targetStorage.gridWidth(),
                    targetStorage.gridHeight(),
                    result.movedCount(),
                    item.count(),
                    result.success(),
                    result.message());
            ExtractCraft.LOGGER.info("BaseStash shift-click diagnostics: source=STASH key={} displayName='{}' count={} rotated={} target={} existingItems={} usedCapacity={}/{} usedWeight={}/{} occupied={} firstFit={} firstFitFailure={} legacyCountAddable={}",
                    item.lookupKey(),
                    item.displayName(),
                    item.count(),
                    item.rotated(),
                    target,
                    targetStorage.itemCount(),
                    targetStorage.usedCapacity(),
                    targetStorage.capacity(),
                    targetStorage.usedWeight(),
                    targetStorage.maxWeight(),
                    targetStorage.occupiedCellsDescription(),
                    targetStorage.findFirstFit(copy).map(placement -> "(" + placement.x() + "," + placement.y() + ") rotated=" + placement.rotated()).orElse("none"),
                    targetStorage.firstFitFailureDescription(copy),
                    targetStorage.countAddable(copy, -1));
            if (result.movedCount() >= item.count()) {
                int beforeBackpackCount = stashData.baseInventory().backpack().itemCount();
                stashData.stash().removeCountAt(stashIndex, item.count());
                PlayerStashService.replaceInventoryContents(stashData.baseInventory(), candidate);
                ExtractCraft.LOGGER.info("BaseStash shift-click success: player={}, target={}, targetBackpack={} '{}', targetGrid={}x{}, key={}, count={}, footprint={}x{}, backpackItemsBefore={}, backpackItemsAfter={}, stashItemsAfter={}",
                        player.getUUID(),
                        target,
                        stashData.baseInventory().loadout().backpack().id(),
                        stashData.baseInventory().loadout().backpack().name(),
                        stashData.baseInventory().backpack().gridWidth(),
                        stashData.baseInventory().backpack().gridHeight(),
                        item.lookupKey(),
                        item.count(),
                        item.gridWidth(),
                        item.gridHeight(),
                        beforeBackpackCount,
                        stashData.baseInventory().backpack().itemCount(),
                        stashData.stash().itemCount());
                player.sendSystemMessage(Component.literal(result.message()));
                return true;
            }
        }

        player.sendSystemMessage(Component.literal(lastResult == null
                ? "No room in Backpack, Vest, or Safe Box."
                : "No room in Backpack, Vest, or Safe Box. " + lastResult.message()));
        return false;
    }

    private RaidInventory.AddResult quickAddToBaseStorage(RaidInventory candidate, RaidInventoryItem item, ItemCarryProfile profile, RaidEquipmentSlot target) {
        RaidStorageContainer targetStorage = storage(candidate, target);
        if (target == RaidEquipmentSlot.VEST && profile.category() == ItemCategory.GUNS) {
            return new RaidInventory.AddResult(false, target, "Guns must be carried in Backpack or equipped.", 0);
        }
        if (target == RaidEquipmentSlot.SAFE_BOX && (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR)) {
            return new RaidInventory.AddResult(false, target, "Item is not allowed in safe box.", 0);
        }

        int moved = targetStorage.addPartialGridFirstFit(item, true);
        if (moved > 0) {
            return new RaidInventory.AddResult(true, target, "Moved " + moved + "x " + item.displayName() + " to " + targetStorage.name() + ".", moved);
        }
        return new RaidInventory.AddResult(false, target, targetStorage.name() + " has no Grid v2 first-fit space for " + item.displayName() + ".", 0);
    }

    private boolean moveStashToBase(ServerPlayer player, int stashDisplayIndex, RaidEquipmentSlot target, int cell) {
        int stashIndex = stashSourceIndex(stashDisplayIndex);
        RaidInventoryItem item = stashData.stash().itemAt(stashIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }

        ExtractCraft.LOGGER.info("BaseStash exact move received: source=STASH displayIndex={} actualIndex={} key={} oldGrid=({},{}), target={} targetCell={} targetXY=({},{}), exactCell={}, fallbackFirstFit={}",
                stashDisplayIndex,
                stashIndex,
                item.lookupKey(),
                item.gridX(),
                item.gridY(),
                target,
                cell,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                cell >= 0 && isGridSlot(target),
                !(cell >= 0 && isGridSlot(target)));

        RaidInventory candidate = PlayerStashService.copyInventory(stashData.baseInventory());
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            player.sendSystemMessage(Component.literal(item.lookupKey() + " has no carry profile."));
            return false;
        }

        RaidInventoryItem copy = PlayerStashService.copyItem(item);
        RaidInventory.AddResult result;
        if (RaidInventory.isEquipmentSlot(target)) {
            return moveStashToEquipment(player, stashIndex, item, target);
        }
        if (cell >= 0 && isGridSlot(target)) {
            result = baseAddToStorageAt(candidate, copy, profile, target, cellX(target, cell), cellY(target, cell), false);
        } else {
            result = baseAddToTarget(candidate, copy, profile, target);
        }
        if (result.movedCount() < item.count()) {
            player.sendSystemMessage(Component.literal(result.success() ? "Base target does not have enough capacity." : result.message()));
            return false;
        }

        stashData.stash().removeCountAt(stashIndex, item.count());
        PlayerStashService.replaceInventoryContents(stashData.baseInventory(), candidate);
        RaidInventoryItem placed = cell >= 0 && isGridSlot(target)
                ? storage(stashData.baseInventory(), target).itemAtCell(cellX(target, cell), cellY(target, cell))
                : null;
        ExtractCraft.LOGGER.info("BaseStash exact move committed: source=STASH key={}, target={} targetXY=({},{}), placedKey={}, placedGrid=({},{}), savedToPersistentBaseInventory=true, repacked=false",
                item.lookupKey(),
                target,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                placed == null ? "unknown" : placed.lookupKey(),
                placed == null ? -1 : placed.gridX(),
                placed == null ? -1 : placed.gridY());
        player.sendSystemMessage(Component.literal(result.message()));
        reopenIfEquipmentMove(null, target);
        return true;
    }

    private boolean moveStashToEquipment(ServerPlayer player, int stashIndex, RaidInventoryItem item, RaidEquipmentSlot target) {
        RaidInventoryItem incoming = PlayerStashService.copyItem(item).withoutPlacement();
        if (!RaidInventory.canEquipItem(incoming, target)) {
            player.sendSystemMessage(Component.literal(incoming.displayName() + " cannot be equipped there."));
            return false;
        }

        RaidInventory candidate = PlayerStashService.copyInventory(stashData.baseInventory());
        RaidInventoryItem previous = candidate.itemAt(target, 0);
        if (previous != null && stashData.stash().countAddable(previous.withoutPlacement(), stashIndex) < previous.count()) {
            player.sendSystemMessage(Component.literal("Stash does not have room for currently equipped " + previous.displayName() + "."));
            return false;
        }

        RaidInventory.AddResult result = candidate.setEquipmentSlot(target, incoming);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
            return false;
        }

        RaidInventoryItem removed = stashData.stash().removeCountAt(stashIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }
        if (previous != null) {
            stashData.stash().addPartial(previous.withoutPlacement());
        }
        PlayerStashService.replaceInventoryContents(stashData.baseInventory(), candidate);
        player.sendSystemMessage(Component.literal(result.message()));
        reopenIfEquipmentMove(null, target);
        return true;
    }

    private boolean moveBaseToBase(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        return moveBaseToBase(player, source, sourceIndex, target, -1);
    }

    private boolean moveBaseToBase(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int cell) {
        RaidInventory candidate = PlayerStashService.copyInventory(stashData.baseInventory());
        RaidInventoryItem item = candidate.itemAt(source, sourceIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }

        ExtractCraft.LOGGER.info("BaseStash exact move received: source={}#{} key={} oldGrid=({},{}), target={} targetCell={} targetXY=({},{}), exactCell={}, fallbackFirstFit={}",
                source,
                sourceIndex,
                item.lookupKey(),
                item.gridX(),
                item.gridY(),
                target,
                cell,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                cell >= 0 && isGridSlot(target),
                !(cell >= 0 && isGridSlot(target)));

        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            player.sendSystemMessage(Component.literal(item.lookupKey() + " has no carry profile."));
            return false;
        }

        RaidInventory.AddResult result = baseMove(candidate, source, sourceIndex, target, profile, cell);
        if (result.movedCount() < item.count()) {
            player.sendSystemMessage(Component.literal(result.success() ? "Base target does not have enough capacity." : result.message()));
            return false;
        }

        PlayerStashService.replaceInventoryContents(stashData.baseInventory(), candidate);
        RaidInventoryItem placed = cell >= 0 && isGridSlot(target)
                ? storage(stashData.baseInventory(), target).itemAtCell(cellX(target, cell), cellY(target, cell))
                : null;
        ExtractCraft.LOGGER.info("BaseStash exact move committed: source={}#{} key={}, target={} targetXY=({},{}), placedKey={}, placedGrid=({},{}), savedToPersistentBaseInventory=true, repacked=false",
                source,
                sourceIndex,
                item.lookupKey(),
                target,
                cell >= 0 && isGridSlot(target) ? cellX(target, cell) : -1,
                cell >= 0 && isGridSlot(target) ? cellY(target, cell) : -1,
                placed == null ? "unknown" : placed.lookupKey(),
                placed == null ? -1 : placed.gridX(),
                placed == null ? -1 : placed.gridY());
        player.sendSystemMessage(Component.literal(result.message()));
        reopenIfEquipmentMove(source, target);
        return true;
    }

    private void reopenIfEquipmentMove(RaidEquipmentSlot source, RaidEquipmentSlot target) {
        if (RaidInventory.isEquipmentSlot(source) || RaidInventory.isEquipmentSlot(target)) {
            reopenAfterChange = true;
        }
    }

    private void reopenIfRequested(ServerPlayer player, boolean changed) {
        if (changed && reopenAfterChange) {
            BaseStashScreenOpener.open(player);
        }
        reopenAfterChange = false;
    }

    private RaidInventory.AddResult baseMove(RaidInventory candidate, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, ItemCarryProfile profile, int cell) {
        RaidInventoryItem item = candidate.itemAt(source, sourceIndex);
        if (item == null) {
            return new RaidInventory.AddResult(false, target, "Source item is no longer available.", 0);
        }

        RaidInventoryItem removed = candidate.removeCountAt(source, sourceIndex, item.count());
        if (removed == null) {
            return new RaidInventory.AddResult(false, target, "Source item is no longer available.", 0);
        }

        RaidInventory.AddResult result = cell >= 0 && isGridSlot(target)
                ? baseAddToStorageAt(candidate, removed, profile, target, cellX(target, cell), cellY(target, cell), false)
                : baseAddToTarget(candidate, removed, profile, target);
        if (result.movedCount() >= removed.count()) {
            return result;
        }

        restoreToBaseCandidate(candidate, source, removed);
        return result;
    }

    private RaidInventory.AddResult baseAddToTarget(RaidInventory candidate, RaidInventoryItem item, ItemCarryProfile profile, RaidEquipmentSlot target) {
        if (RaidInventory.isEquipmentSlot(target)) {
            return candidate.equipItem(item, target);
        }
        if (target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return candidate.addToWeaponSlot(item, target);
        }
        if (target == RaidEquipmentSlot.VEST && profile.category() == ItemCategory.GUNS) {
            return new RaidInventory.AddResult(false, target, "Guns must be carried in Backpack or equipped.", 0);
        }
        if (target == RaidEquipmentSlot.SAFE_BOX && (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR)) {
            return new RaidInventory.AddResult(false, target, "Item is not allowed in safe box.", 0);
        }

        RaidStorageContainer targetStorage = storage(candidate, target);
        int moved = targetStorage.addPartialGridFirstFit(item, true);
        if (moved > 0) {
            return new RaidInventory.AddResult(true, target, "Moved " + moved + "x " + item.displayName() + " to " + targetStorage.name() + ".", moved);
        }
        return new RaidInventory.AddResult(false, target, targetStorage.name() + " has no room.", 0);
    }

    private RaidInventory.AddResult baseAddToStorageAt(RaidInventory candidate, RaidInventoryItem item, ItemCarryProfile profile, RaidEquipmentSlot target, int x, int y, boolean rotated) {
        if (RaidInventory.isEquipmentSlot(target)) {
            return candidate.equipItem(item, target);
        }
        if (target == RaidEquipmentSlot.VEST && profile.category() == ItemCategory.GUNS) {
            return new RaidInventory.AddResult(false, target, "Guns must be carried in Backpack or equipped.", 0);
        }
        if (target == RaidEquipmentSlot.SAFE_BOX && (!profile.allowInSafeBox() || profile.category() == ItemCategory.GUNS || profile.category() == ItemCategory.ARMOR)) {
            return new RaidInventory.AddResult(false, target, "Item is not allowed in safe box.", 0);
        }

        RaidStorageContainer targetStorage = storage(candidate, target);
        int moved = targetStorage.addPartialAt(item, x, y, rotated, -1, true);
        if (moved > 0) {
            return new RaidInventory.AddResult(true, target, "Moved " + moved + "x " + item.displayName() + " to " + targetStorage.name() + ".", moved);
        }
        return new RaidInventory.AddResult(false, target, targetStorage.name() + " target cell is blocked.", 0);
    }

    private static void restoreToBaseCandidate(RaidInventory candidate, RaidEquipmentSlot source, RaidInventoryItem removed) {
        if (RaidInventory.isEquipmentSlot(source)) {
            candidate.setEquipmentSlot(source, removed);
            return;
        }
        if (source == RaidEquipmentSlot.PRIMARY_WEAPON || source == RaidEquipmentSlot.SECONDARY_WEAPON) {
            candidate.setWeaponSlot(source, removed);
            return;
        }
        storage(candidate, source).addPartialAt(removed, removed.gridX(), removed.gridY(), removed.rotated(), -1, true);
    }

    private static RaidStorageContainer storage(RaidInventory inventory, RaidEquipmentSlot slot) {
        return switch (slot) {
            case BACKPACK -> inventory.backpack();
            case VEST -> inventory.vest();
            case SAFE_BOX -> inventory.safeBox();
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalArgumentException("Non-storage slots do not have grid storage.");
        };
    }

    private int stashSourceIndex(int stashDisplayIndex) {
        if (stashDisplayIndex < 0 || stashDisplayIndex >= displayedStashIndexes.length) {
            return -1;
        }
        return displayedStashIndexes[stashDisplayIndex];
    }

    private void addBaseDisplaySlots() {
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, HELMET_START, 20, 42));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, ARMOR_START, 20, 72));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, EQUIPPED_BACKPACK_START, 20, 102));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, EQUIPPED_VEST_START, 20, 132));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, EQUIPPED_SAFE_CONTAINER_START, 20, 162));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, PRIMARY_WEAPON_START, 20, 200));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, SECONDARY_WEAPON_START, 20, 230));
        addDisplayGrid(baseDisplay, BACKPACK_START, BACKPACK_DISPLAY_SLOTS, backpackGridWidth, backpackGridHeight, 104, 62);
        addDisplayGrid(baseDisplay, VEST_START, VEST_DISPLAY_SLOTS, vestGridWidth, vestGridHeight, 104, 256);
        addDisplayGrid(baseDisplay, SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, safeGridWidth, safeGridHeight, 196, 256);
    }

    private void addStashSlots() {
        int columns = stashColumns();
        for (int slot = 0; slot < stashCapacity; slot++) {
            int x = STASH_SLOT_X + (slot % columns) * 18;
            int y = STASH_SLOT_Y + (slot / columns) * 18;
            addSlot(new ReadOnlyDisplaySlot(stashDisplay, slot, x, y));
        }
    }

    private void addDisplayGrid(SimpleContainer container, int start, int count, int columns, int rows, int x, int y) {
        int visible = Math.max(0, columns) * Math.max(0, rows);
        for (int i = 0; i < count; i++) {
            if (i >= visible || columns <= 0) {
                addSlot(new ReadOnlyDisplaySlot(container, start + i, -10000, -10000));
            } else {
                addSlot(new ReadOnlyDisplaySlot(container, start + i, x + (i % columns) * 18, y + (i / columns) * 18));
            }
        }
    }

    private void rebuildDisplays() {
        baseDisplay.clearContent();
        stashDisplay.clearContent();
        java.util.Arrays.fill(displayedBaseIndexes, -1);
        for (int i = 0; i < displayedStashIndexes.length; i++) {
            displayedStashIndexes[i] = -1;
        }
        if (stashData == null) {
            return;
        }

        RaidInventory baseInventory = stashData.baseInventory();
        fillEquipmentDisplay(baseInventory);
        if (baseInventory.primaryWeapon() != null) {
            displayedBaseIndexes[PRIMARY_WEAPON_START] = 0;
            baseDisplay.setItem(PRIMARY_WEAPON_START, displayStack(baseInventory.primaryWeapon(), 0));
        }
        if (baseInventory.secondaryWeapon() != null) {
            displayedBaseIndexes[SECONDARY_WEAPON_START] = 0;
            baseDisplay.setItem(SECONDARY_WEAPON_START, displayStack(baseInventory.secondaryWeapon(), 0));
        }
        fillDisplay(baseDisplay, displayedBaseIndexes, BACKPACK_START, BACKPACK_DISPLAY_SLOTS, baseInventory.backpack().gridWidth(), baseInventory.backpack().items());
        fillDisplay(baseDisplay, displayedBaseIndexes, VEST_START, VEST_DISPLAY_SLOTS, baseInventory.vest().gridWidth(), baseInventory.vest().items());
        fillDisplay(baseDisplay, displayedBaseIndexes, SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, baseInventory.safeBox().gridWidth(), baseInventory.safeBox().items());
        ExtractCraft.LOGGER.info("BaseStash display rebuild: baseBackpack={} '{}', storageGrid={}x{}, displayGrid=8x8, storageItems={}, visibleBackpackSlots={}",
                baseInventory.loadout().backpack().id(),
                baseInventory.loadout().backpack().name(),
                baseInventory.backpack().gridWidth(),
                baseInventory.backpack().gridHeight(),
                baseInventory.backpack().itemCount(),
                visibleDisplaySlots(BACKPACK_START, BACKPACK_DISPLAY_SLOTS));

        List<RaidInventoryItem> items = stashData.stash().items();
        fillDisplay(stashDisplay, displayedStashIndexes, 0, stashCapacity, stashData.stash().gridWidth(), items);
        baseDisplay.setChanged();
        stashDisplay.setChanged();
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
        displayedBaseIndexes[menuSlot] = 0;
        baseDisplay.setItem(menuSlot, displayStack(item, 0));
    }

    private int visibleDisplaySlots(int start, int count) {
        int visible = 0;
        for (int index = start; index < start + count && index < baseDisplay.getContainerSize(); index++) {
            if (!baseDisplay.getItem(index).isEmpty()) {
                visible++;
            }
        }
        return visible;
    }

    private void fillDisplay(SimpleContainer container, int[] displayedIndexes, int start, int maxSlots, int columns, List<RaidInventoryItem> items) {
        int sequentialIndex = 0;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            RaidInventoryItem item = items.get(itemIndex);
            int displayIndex = item.isPlaced() ? item.gridY() * Math.max(1, columns) + item.gridX() : sequentialIndex++;
            if (displayIndex < 0 || displayIndex >= maxSlots) {
                continue;
            }
            ExtractCraft.LOGGER.info("BaseStash display sync: start={}, itemIndex={}, key={}, itemGrid=({},{}), footprint={}x{}, displayIndex={}, metadataGrid=({},{}), repacked=false",
                    start,
                    itemIndex,
                    item.lookupKey(),
                    item.gridX(),
                    item.gridY(),
                    footprintWidth(item),
                    footprintHeight(item),
                    displayIndex,
                    item.gridX(),
                    item.gridY());
            placeDisplayFootprint(container, displayedIndexes, start, maxSlots, columns, displayIndex, item, itemIndex);
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

    private static int stashColumns(int capacity) {
        if (capacity >= 220) {
            return 16;
        }
        if (capacity >= 170) {
            return 15;
        }
        return STASH_COLUMNS_MIN;
    }

    private static int slotId(RaidEquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> 0;
            case ARMOR -> 1;
            case EQUIPPED_BACKPACK -> 2;
            case EQUIPPED_VEST -> 3;
            case EQUIPPED_SAFE_CONTAINER -> 4;
            case PRIMARY_WEAPON -> 5;
            case SECONDARY_WEAPON -> 6;
            case BACKPACK -> 7;
            case VEST -> 8;
            case SAFE_BOX -> 9;
        };
    }

    private static RaidEquipmentSlot slotFromId(int id) {
        return switch (id) {
            case 0 -> RaidEquipmentSlot.HELMET;
            case 1 -> RaidEquipmentSlot.ARMOR;
            case 2 -> RaidEquipmentSlot.EQUIPPED_BACKPACK;
            case 3 -> RaidEquipmentSlot.EQUIPPED_VEST;
            case 4 -> RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER;
            case 5 -> RaidEquipmentSlot.PRIMARY_WEAPON;
            case 6 -> RaidEquipmentSlot.SECONDARY_WEAPON;
            case 8 -> RaidEquipmentSlot.VEST;
            case 9 -> RaidEquipmentSlot.SAFE_BOX;
            default -> RaidEquipmentSlot.BACKPACK;
        };
    }

    private static boolean isGridSlot(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.BACKPACK || slot == RaidEquipmentSlot.VEST || slot == RaidEquipmentSlot.SAFE_BOX;
    }

    private int cellX(RaidEquipmentSlot slot, int cell) {
        return cell % columnsFor(slot);
    }

    private int cellY(RaidEquipmentSlot slot, int cell) {
        return cell / columnsFor(slot);
    }

    private int stashCellX(int cell) {
        return cell % Math.max(1, stashData.stash().gridWidth());
    }

    private int stashCellY(int cell) {
        return cell / Math.max(1, stashData.stash().gridWidth());
    }

    private int columnsFor(RaidEquipmentSlot slot) {
        return switch (slot) {
            case VEST -> Math.max(1, vestGridWidth);
            case SAFE_BOX -> Math.max(1, safeGridWidth);
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> 1;
            default -> Math.max(1, backpackGridWidth);
        };
    }

    private static DataSlot stashStat(ServerPlayer player, int initialLevel, int initialCredits, StashStat stat) {
        if (player == null) {
            DataSlot slot = DataSlot.standalone();
            slot.set(switch (stat) {
                case LEVEL -> initialLevel;
                case CREDITS -> initialCredits;
                default -> 0;
            });
            return slot;
        }

        return new DataSlot() {
            @Override
            public int get() {
                PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
                return switch (stat) {
                    case USED_CAPACITY -> data.stash().usedCapacity();
                    case MAX_CAPACITY -> data.stash().capacity();
                    case LEVEL -> data.stashLevel();
                    case CREDITS -> data.credits();
                    case TOTAL_VALUE -> data.stash().totalValue();
                };
            }

            @Override
            public void set(int value) {
            }
        };
    }

    private enum StashStat {
        USED_CAPACITY,
        MAX_CAPACITY,
        LEVEL,
        CREDITS,
        TOTAL_VALUE
    }

    private enum SortMode {
        MANUAL(-1, "Manual", Comparator.comparing(RaidInventoryItem::displayName)),
        NAME(0, "Name", Comparator.comparing(RaidInventoryItem::displayName)),
        VALUE(1, "Value", Comparator.comparingInt(RaidInventoryItem::totalValue).reversed().thenComparing(RaidInventoryItem::displayName)),
        WEIGHT(2, "Weight", Comparator.comparingDouble(RaidInventoryItem::totalWeight).reversed().thenComparing(RaidInventoryItem::displayName)),
        CATEGORY(3, "Category", Comparator.comparing((RaidInventoryItem item) -> item.category()).thenComparing(RaidInventoryItem::displayName));

        private final int id;
        private final String label;
        private final Comparator<RaidInventoryItem> itemComparator;

        SortMode(int id, String label, Comparator<RaidInventoryItem> itemComparator) {
            this.id = id;
            this.label = label;
            this.itemComparator = itemComparator;
        }

        private static SortMode fromId(int id) {
            for (SortMode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return NAME;
        }
    }
}
