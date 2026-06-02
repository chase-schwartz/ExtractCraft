package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;

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
    public static final int PRIMARY_WEAPON_START = 0;
    public static final int SECONDARY_WEAPON_START = 1;
    public static final int BACKPACK_START = 2;
    public static final int BACKPACK_DISPLAY_SLOTS = 36;
    public static final int VEST_START = BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
    public static final int VEST_DISPLAY_SLOTS = 12;
    public static final int SAFE_BOX_START = VEST_START + VEST_DISPLAY_SLOTS;
    public static final int SAFE_BOX_DISPLAY_SLOTS = 9;
    public static final int BASE_DISPLAY_SLOTS = SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
    public static final int STASH_COLUMNS_MIN = 10;
    private static final int STASH_SLOT_X = 236;
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
    private SortMode sortMode = SortMode.NAME;
    private final DataSlot stashUsedCapacity;
    private final DataSlot stashMaxCapacity;
    private final DataSlot stashLevel;
    private final DataSlot credits;
    private final DataSlot totalValue;

    public BaseStashMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, null, null, data.readVarInt(), data.readVarInt(), data.readVarInt());
    }

    public BaseStashMenu(int containerId, Inventory playerInventory, ServerPlayer player, PlayerStashService.PlayerStashData stashData) {
        this(containerId, playerInventory, player, stashData, stashData.stash().capacity(), stashData.stashLevel(), stashData.credits());
    }

    private BaseStashMenu(int containerId, Inventory playerInventory, ServerPlayer player, PlayerStashService.PlayerStashData stashData, int stashCapacity, int initialStashLevel, int initialCredits) {
        super(ExtractCraft.BASE_STASH_MENU.get(), containerId);
        this.serverPlayer = player;
        this.stashData = stashData;
        this.stashCapacity = Math.max(1, stashCapacity);
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
            sortMode = SortMode.fromId(id - SORT_OFFSET);
            rebuildDisplays();
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
        return true;
    }

    public GridMoveResult handleGridMoveRequest(ServerPlayer player, int operation, int sourceSlotId, int sourceIndex, int targetSlotId, int targetCell) {
        boolean changed = switch (operation) {
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_STASH_TO_BASE_CELL ->
                    moveStashToBase(player, sourceIndex, slotFromId(targetSlotId), targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_BASE_TO_BASE_CELL ->
                    moveBaseToBase(player, slotFromId(sourceSlotId), sourceIndex, slotFromId(targetSlotId), targetCell);
            case com.chaseschwartz.extractcraft.network.GridMoveRequestPayload.BASE_BASE_TO_STASH ->
                    moveBaseToStash(player, slotFromId(sourceSlotId), sourceIndex);
            default -> false;
        };
        if (changed) {
            PlayerStashService.save(player, stashData);
            rebuildDisplays();
        }
        setCarried(ItemStack.EMPTY);
        broadcastChanges();
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
        if (slot == RaidEquipmentSlot.PRIMARY_WEAPON || slot == RaidEquipmentSlot.SECONDARY_WEAPON) {
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
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> 0;
        };
        int end = switch (slot) {
            case BACKPACK -> BACKPACK_START + BACKPACK_DISPLAY_SLOTS;
            case VEST -> VEST_START + VEST_DISPLAY_SLOTS;
            case SAFE_BOX -> SAFE_BOX_START + SAFE_BOX_DISPLAY_SLOTS;
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> 0;
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

    public String sortModeName() {
        return sortMode.label;
    }

    private boolean moveBaseToStash(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        RaidInventoryItem item = stashData.baseInventory().itemAt(source, sourceIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }
        if (stashData.stash().countAddable(item, -1) < item.count()) {
            player.sendSystemMessage(Component.literal("Stash does not have enough capacity."));
            return false;
        }

        RaidInventoryItem removed = stashData.baseInventory().removeCountAt(source, sourceIndex, item.count());
        if (removed == null) {
            player.sendSystemMessage(Component.literal("Source item is no longer available."));
            return false;
        }
        stashData.stash().addPartial(removed);
        player.sendSystemMessage(Component.literal("Moved " + removed.displayName() + " to stash."));
        return true;
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
        return true;
    }

    private void restoreRemovedItem(boolean stashSource, RaidEquipmentSlot source, RaidInventoryItem removed) {
        if (stashSource) {
            stashData.stash().addPartial(removed);
            return;
        }
        if (source == RaidEquipmentSlot.PRIMARY_WEAPON || source == RaidEquipmentSlot.SECONDARY_WEAPON) {
            stashData.baseInventory().setWeaponSlot(source, removed);
        } else {
            RaidStorageContainer target = switch (source) {
                case BACKPACK -> stashData.baseInventory().backpack();
                case VEST -> stashData.baseInventory().vest();
                case SAFE_BOX -> stashData.baseInventory().safeBox();
                case PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalStateException("handled above");
            };
            target.addPartial(removed);
        }
    }

    private boolean moveStashToBase(ServerPlayer player, int stashDisplayIndex, RaidEquipmentSlot target) {
        return moveStashToBase(player, stashDisplayIndex, target, -1);
    }

    private boolean moveStashToBase(ServerPlayer player, int stashDisplayIndex, RaidEquipmentSlot target, int cell) {
        int stashIndex = stashSourceIndex(stashDisplayIndex);
        RaidInventoryItem item = stashData.stash().itemAt(stashIndex);
        if (item == null) {
            player.sendSystemMessage(Component.literal("Stash item is no longer available."));
            return false;
        }

        RaidInventory candidate = PlayerStashService.copyInventory(stashData.baseInventory());
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            player.sendSystemMessage(Component.literal(item.lookupKey() + " has no carry profile."));
            return false;
        }

        RaidInventoryItem copy = PlayerStashService.copyItem(item);
        RaidInventory.AddResult result;
        if (cell >= 0 && isGridSlot(target)) {
            result = switch (target) {
                case BACKPACK -> candidate.addToBackpackAt(copy, cellX(target, cell), cellY(target, cell), false);
                case VEST -> candidate.addToVestAt(copy, profile, cellX(target, cell), cellY(target, cell), false);
                case SAFE_BOX -> candidate.addToSafeBoxAt(copy, profile, cellX(target, cell), cellY(target, cell), false);
                case PRIMARY_WEAPON, SECONDARY_WEAPON -> throw new IllegalArgumentException("Weapon slots are not grid targets.");
            };
        } else {
            result = switch (target) {
                case PRIMARY_WEAPON, SECONDARY_WEAPON -> candidate.addToWeaponSlot(copy, target);
                case BACKPACK -> candidate.addToBackpack(copy);
                case VEST -> candidate.addToVest(copy, profile);
                case SAFE_BOX -> candidate.addToSafeBox(copy, profile);
            };
        }
        if (result.movedCount() < item.count()) {
            player.sendSystemMessage(Component.literal(result.success() ? "Base target does not have enough capacity." : result.message()));
            return false;
        }

        stashData.stash().removeCountAt(stashIndex, item.count());
        PlayerStashService.replaceInventoryContents(stashData.baseInventory(), candidate);
        player.sendSystemMessage(Component.literal(result.message()));
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

        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            player.sendSystemMessage(Component.literal(item.lookupKey() + " has no carry profile."));
            return false;
        }

        RaidInventory.AddResult result = cell >= 0 && isGridSlot(target)
                ? candidate.moveToCell(source, sourceIndex, target, profile, cellX(target, cell), cellY(target, cell), false)
                : candidate.move(source, sourceIndex, target, profile);
        if (result.movedCount() < item.count()) {
            player.sendSystemMessage(Component.literal(result.success() ? "Base target does not have enough capacity." : result.message()));
            return false;
        }

        PlayerStashService.replaceInventoryContents(stashData.baseInventory(), candidate);
        player.sendSystemMessage(Component.literal(result.message()));
        return true;
    }

    private int stashSourceIndex(int stashDisplayIndex) {
        if (stashDisplayIndex < 0 || stashDisplayIndex >= displayedStashIndexes.length) {
            return -1;
        }
        return displayedStashIndexes[stashDisplayIndex];
    }

    private void addBaseDisplaySlots() {
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, PRIMARY_WEAPON_START, 150, 50));
        addSlot(new ReadOnlyDisplaySlot(baseDisplay, SECONDARY_WEAPON_START, 150, 112));
        addDisplayGrid(baseDisplay, BACKPACK_START, BACKPACK_DISPLAY_SLOTS, 6, 12, 62);
        addDisplayGrid(baseDisplay, VEST_START, VEST_DISPLAY_SLOTS, 4, 12, 226);
        addDisplayGrid(baseDisplay, SAFE_BOX_START, SAFE_BOX_DISPLAY_SLOTS, 3, 104, 226);
    }

    private void addStashSlots() {
        int columns = stashColumns();
        for (int slot = 0; slot < stashCapacity; slot++) {
            int x = STASH_SLOT_X + (slot % columns) * 18;
            int y = STASH_SLOT_Y + (slot / columns) * 18;
            addSlot(new ReadOnlyDisplaySlot(stashDisplay, slot, x, y));
        }
    }

    private void addDisplayGrid(SimpleContainer container, int start, int count, int columns, int x, int y) {
        for (int i = 0; i < count; i++) {
            addSlot(new ReadOnlyDisplaySlot(container, start + i, x + (i % columns) * 18, y + (i / columns) * 18));
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

        List<IndexedItem> stashItems = new ArrayList<>();
        List<RaidInventoryItem> items = stashData.stash().items();
        for (int index = 0; index < items.size(); index++) {
            stashItems.add(new IndexedItem(index, items.get(index)));
        }
        stashItems.sort(sortMode.comparator);
        for (int displayIndex = 0; displayIndex < Math.min(stashCapacity, stashItems.size()); displayIndex++) {
            IndexedItem indexedItem = stashItems.get(displayIndex);
            displayedStashIndexes[displayIndex] = indexedItem.index();
            stashDisplay.setItem(displayIndex, displayStack(indexedItem.item(), indexedItem.index()));
        }
    }

    private void fillDisplay(SimpleContainer container, int[] displayedIndexes, int start, int maxSlots, int columns, List<RaidInventoryItem> items) {
        int sequentialIndex = 0;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            RaidInventoryItem item = items.get(itemIndex);
            int displayIndex = item.isPlaced() ? item.gridY() * Math.max(1, columns) + item.gridX() : sequentialIndex++;
            if (displayIndex < 0 || displayIndex >= maxSlots) {
                continue;
            }
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

    private static boolean isGridSlot(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.BACKPACK || slot == RaidEquipmentSlot.VEST || slot == RaidEquipmentSlot.SAFE_BOX;
    }

    private static int cellX(RaidEquipmentSlot slot, int cell) {
        return cell % columnsFor(slot);
    }

    private static int cellY(RaidEquipmentSlot slot, int cell) {
        return cell / columnsFor(slot);
    }

    private static int columnsFor(RaidEquipmentSlot slot) {
        return switch (slot) {
            case VEST -> 4;
            case SAFE_BOX -> 3;
            default -> 6;
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
        NAME(0, "Name", Comparator.comparing((IndexedItem item) -> item.item().displayName())),
        VALUE(1, "Value", Comparator.comparingInt((IndexedItem item) -> item.item().totalValue()).reversed().thenComparing(item -> item.item().displayName())),
        WEIGHT(2, "Weight", Comparator.comparingDouble((IndexedItem item) -> item.item().totalWeight()).reversed().thenComparing(item -> item.item().displayName())),
        CATEGORY(3, "Category", Comparator.comparing((IndexedItem item) -> item.item().category()).thenComparing(item -> item.item().displayName()));

        private final int id;
        private final String label;
        private final Comparator<IndexedItem> comparator;

        SortMode(int id, String label, Comparator<IndexedItem> comparator) {
            this.id = id;
            this.label = label;
            this.comparator = comparator;
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

    private record IndexedItem(int index, RaidInventoryItem item) {
    }
}
