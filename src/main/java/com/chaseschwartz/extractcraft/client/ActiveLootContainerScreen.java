package com.chaseschwartz.extractcraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.durability.InRaidRepairService;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentityResolver;
import com.chaseschwartz.extractcraft.network.GridMoveRequestPayload;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.containers.LootRevealTiming;
import com.chaseschwartz.extractcraft.raid.inventory.GridDisplayMetadata;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfile;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.itemvalues.ItemRarity;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.itemvalues.RarityPresentation;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ActiveLootContainerScreen extends AbstractContainerScreen<ActiveLootContainerMenu> {
    private static final int CONTAINER_PANEL_X = 292;
    private static final int CONTAINER_PANEL_Y = 28;
    private static final int CONTAINER_PANEL_WIDTH = 130;
    private static final int EQUIPMENT_X = 8;
    private static final int EQUIPMENT_Y = 24;
    private static final int BACKPACK_GRID_X = 104;
    private static final int BACKPACK_GRID_Y = 62;
    private static final int VEST_GRID_X = 104;
    private static final int VEST_GRID_Y = 256;
    private static final int SAFE_GRID_X = 206;
    private static final int SAFE_GRID_Y = 256;
    private static final int VANILLA_BACKGROUND_SIZE = 18;
    private static final int VANILLA_INNER_SIZE = 16;
    private static final int VANILLA_HOVER_SIZE = 16;
    private static final int VANILLA_ITEM_SIZE = 16;
    private static final int SLOT_STEP = 18;
    private static final int PANEL_COLOR = 0xF0101116;
    private static final int SECTION_COLOR = 0xFF1B2029;
    private static final int SLOT_COLOR = 0xFF40444D;
    private static final int BORDER_COLOR = 0xFF49D8E8;
    private static final int HOVER_BORDER = 0xFF9CF6FF;
    private static final int TEXT = 0xFFDFFBFF;
    private static final int MUTED_TEXT = 0xFF9AA6B2;
    private static final int CONTEXT_MENU_WIDTH = 74;
    private static final int CONTEXT_MENU_ROW_HEIGHT = 17;
    private static final int SPLIT_DIALOG_WIDTH = 150;
    private static final int SPLIT_DIALOG_HEIGHT = 106;
    private static final Map<String, Set<String>> REVEALED_CONTAINER_CACHE = new HashMap<>();
    private DragSource dragSource = DragSource.NONE;
    private int draggedSourceIndex = -1;
    private RaidEquipmentSlot draggedRaidSlot = null;
    private ItemStack draggedStack = ItemStack.EMPTY;
    private final Set<Integer> draggedSourceMenuSlots = new HashSet<>();
    private final Set<Integer> pendingSourceMenuSlots = new HashSet<>();
    private int pendingSourceTicks;
    private boolean pendingSourceObservedPresent;
    private int nextTransactionId = 1;
    private int pendingTransactionId = -1;
    private int pendingOperation = -1;
    private String gridMoveStatus = "";
    private double dragStartX;
    private double dragStartY;
    private boolean loggedLayout;
    private String lastPreviewLogKey = "";
    private ContextMenu contextMenu = null;
    private SplitDialog splitDialog = null;
    private String splitInput = "";
    private boolean splitInputFocused = false;
    private boolean splitSliderDragging = false;
    private final Map<Integer, RevealState> revealStates = new HashMap<>();
    private boolean revealInitialized;

    public ActiveLootContainerScreen(ActiveLootContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 430;
        this.imageHeight = Math.max(350, 102 + menu.containerRows() * 18);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncRevealState();
        updateRevealState();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderFootprintOverlays(guiGraphics);
        renderRevealOverlays(guiGraphics);
        renderDragPreview(guiGraphics, mouseX, mouseY);
        renderFootprintHover(guiGraphics, mouseX, mouseY);
        if (!draggedStack.isEmpty()) {
            renderHeldStack(guiGraphics, mouseX, mouseY);
        }
        renderContextMenu(guiGraphics, mouseX, mouseY);
        renderSplitDialog(guiGraphics, mouseX, mouseY);
        renderCustomTooltip(guiGraphics, mouseX, mouseY);
        tickPendingSource();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        border(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);

        boolean dragging = dragSource != DragSource.NONE;
        section(guiGraphics, x + EQUIPMENT_X, y + EQUIPMENT_Y, 82, 268, false);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 34, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.HELMET);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 64, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.ARMOR);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 94, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.EQUIPPED_BACKPACK);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 124, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.EQUIPPED_VEST);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 154, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 192, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.PRIMARY_WEAPON);
        equipmentRow(guiGraphics, x + EQUIPMENT_X, y + 222, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SECONDARY_WEAPON);
        section(guiGraphics, x + 98, y + 24, 164, 210, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.BACKPACK);
        section(guiGraphics, x + 98, y + 238, 100, 100, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.VEST);
        section(guiGraphics, x + 202, y + 238, 82, 100, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SAFE_BOX);
        if (this.menu.hasWorldContainer()) {
            section(guiGraphics, x + CONTAINER_PANEL_X, y + CONTAINER_PANEL_Y, CONTAINER_PANEL_WIDTH, this.imageHeight - 40, false);
        }
        logLayoutOnce();
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (isRaidInventorySlot(slot) || isContainerSlot(slot)) {
            if (isRaidInventorySlot(slot) && isGridDisplaySlot(slot) && !isVisibleGridSlot(slot)) {
                return;
            }
            if (isRaidInventorySlot(slot) && !isGridDisplaySlot(slot)) {
                drawEquipmentSlotBackground(guiGraphics, slot.x, slot.y);
            } else {
                drawVanillaSlotBackground(guiGraphics, slot.x, slot.y);
            }
            if (isContainerSlot(slot) && isContainerSlotRevealed(slot)) {
                drawRevealTint(guiGraphics, slot, revealStateForSlot(slot));
            }
            if (isDragSourceSlot(slot)) {
                return;
            }
            if (isContainerSlotHidden(slot)) {
                return;
            }
            if (isGridShadowSlot(slot)) {
                return;
            }
            if (isGridAnchorFootprintSlot(slot)) {
                return;
            }
        }
        super.renderSlot(guiGraphics, slot);
        if ((isRaidInventorySlot(slot) || isContainerSlot(slot)) && slot.hasItem() && !isContainerSlotHidden(slot) && !isGridShadowSlot(slot) && !isGridAnchorFootprintSlot(slot)) {
            CustomDurabilityBarRenderer.render(guiGraphics, slot.getItem(), slot.x, slot.y, SLOT_STEP, SLOT_STEP);
        }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (splitDialog != null) {
            return;
        }
        if (contextMenu != null) {
            return;
        }
        if ((isRaidInventorySlot(slot) && !isWeaponSlot(slot)) || isContainerSlot(slot)) {
            return;
        }
        super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (splitDialog != null) {
            return;
        }
        if (contextMenu != null) {
            return;
        }
        if (customTooltipSlot(x, y) != null) {
            return;
        }
        super.renderTooltip(guiGraphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "Raid Loot", 8, 8, TEXT, false);
        guiGraphics.drawString(this.font, String.format("Total: %.1f/%.1f wt | %d cr", totalUsedWeight(), totalMaxWeight(), menu.totalValue()), 168, 8, MUTED_TEXT, false);
        if (this.menu.hasWorldContainer()) {
            guiGraphics.drawString(this.font, "Container", CONTAINER_PANEL_X + 8, CONTAINER_PANEL_Y + 7, TEXT, false);
        }

        guiGraphics.drawString(this.font, "Equipment", 14, 29, TEXT, false);
        guiGraphics.drawString(this.font, "Helmet", 42, 42, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Armor", 42, 72, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Pack", 42, 102, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Vest", 42, 132, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Safe", 42, 162, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Primary", 42, 200, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Second", 42, 230, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Backpack", BACKPACK_GRID_X, 29, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.backpackUsedCapacity(), menu.backpackMaxCapacity()), BACKPACK_GRID_X, 42, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, String.format("%.1f/%.1f wt", menu.backpackUsedWeight(), menu.backpackMaxWeight()), BACKPACK_GRID_X, 53, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Vest", VEST_GRID_X, 243, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d", menu.vestUsedCapacity(), menu.vestMaxCapacity()), VEST_GRID_X + 44, 243, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Safe Box", SAFE_GRID_X, 243, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d", menu.safeBoxUsedCapacity(), menu.safeBoxMaxCapacity()), SAFE_GRID_X + 54, 243, MUTED_TEXT, false);

        int hintY = Math.min(this.imageHeight - 28, 64 + this.menu.containerRows() * 18);
        if (this.menu.hasWorldContainer()) {
            guiGraphics.drawString(this.font, "Click: Backpack", CONTAINER_PANEL_X + 8, hintY, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Drag: choose", CONTAINER_PANEL_X + 8, hintY + 10, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Drag left: return", CONTAINER_PANEL_X + 8, hintY + 20, MUTED_TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (splitDialog != null) {
            if (button == 0 && handleSplitDialogClick(mouseX, mouseY)) {
                return true;
            }
            splitDialog = null;
            splitInput = "";
            splitInputFocused = false;
            splitSliderDragging = false;
            return true;
        }

        if (contextMenu != null) {
            if (button == 0 && handleContextMenuClick(mouseX, mouseY)) {
                return true;
            }
            contextMenu = null;
            if (button == 0) {
                return true;
            }
        }

        if (button == 0 && !this.menu.getCarried().isEmpty()) {
            return tryPlaceCarriedStack(mouseX, mouseY);
        }

        if (button == 1) {
            Slot slot = slotAt(mouseX, mouseY);
            if (slot != null && slot.hasItem() && isContainerSlot(slot)) {
                slot = containerOwnerSlotForCell(slot);
                if (slot != null && slot.hasItem() && hiddenRevealAtSlot(slot) == null) {
                    int containerSlot = slot.index - this.menu.containerMenuSlotStart();
                    if (hasShiftDown()) {
                        sendSplit(true, null, containerSlot, halfSplitAmount(slot.getItem()));
                    } else if (canSplit(slot.getItem())) {
                        openSplitDialog(new SplitDialog(true, null, containerSlot, slot.getItem().copy(), halfSplitAmount(slot.getItem()), slot.getItem().getCount() - 1));
                    }
                    return true;
                }
            }
            if (slot != null && slot.hasItem() && isRaidInventorySlot(slot)) {
                RaidEquipmentSlot source = this.menu.raidSlotForMenuSlot(slot.index);
                int sourceIndex = this.menu.raidItemIndexForMenuSlot(slot.index);
                if (sourceIndex >= 0) {
                    if (hasShiftDown()) {
                        sendSplit(false, source, sourceIndex, halfSplitAmount(slot.getItem()));
                    } else {
                        contextMenu = new ContextMenu((int) mouseX, (int) mouseY, source, sourceIndex, canSplit(slot.getItem()),
                                !this.menu.persistentBaseMode() && canInRaidRepair(slot.getItem()));
                    }
                    return true;
                }
            }
        }

        if (button == 0 && dragSource != DragSource.NONE) {
            if (tryPlaceHeldStack(mouseX, mouseY)) {
                clearDrag();
            }
            return true;
        }

        Slot slot = slotAt(mouseX, mouseY);
        if (slot != null && isContainerSlot(slot) && hiddenRevealAtSlot(slot) != null) {
            return true;
        }
        slot = containerOwnerSlotForCell(slot);
        if (button == 0 && slot != null && isContainerSlot(slot) && slot.hasItem()) {
            int containerSlot = slot.index - this.menu.containerMenuSlotStart();
            if (hasShiftDown()) {
                sendTransfer(containerSlot, RaidEquipmentSlot.BACKPACK);
                return true;
            }

            startDrag(DragSource.CONTAINER, containerSlot, null, slot.getItem(), slot, mouseX, mouseY);
            return true;
        }
        if (button == 0 && slot != null && isRaidInventorySlot(slot) && slot.hasItem()) {
            RaidEquipmentSlot sourceSlot = this.menu.raidSlotForMenuSlot(slot.index);
            int sourceIndex = this.menu.raidItemIndexForMenuSlot(slot.index);
            if (sourceIndex < 0) {
                return true;
            }
            startDrag(DragSource.RAID_INVENTORY, sourceIndex, sourceSlot, slot.getItem(), slot, mouseX, mouseY);
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(draggedStack);
            ExtractCraft.LOGGER.info("Raid grid click pickup: section={}, menuSlot={}, clickedCell=({},{}), ownerIndex={}, key={}, ownerFootprint=({},{} {}x{} rotated={}), heldFootprint=({}x{}), mouse=({},{}), cell={}",
                    sourceSlot,
                    slot.index,
                    sourceSlot == null ? -1 : cellX(sourceSlot, targetCellAt(sourceSlot, (int) mouseX, (int) mouseY)),
                    sourceSlot == null ? -1 : cellY(sourceSlot, targetCellAt(sourceSlot, (int) mouseX, (int) mouseY)),
                    sourceIndex,
                    slot.getItem().getHoverName().getString(),
                    metadata.gridX(),
                    metadata.gridY(),
                    metadata.footprintWidth(),
                    metadata.footprintHeight(),
                    metadata.rotated(),
                    footprintFor(draggedStack).width(),
                    footprintFor(draggedStack).height(),
                    (int) mouseX,
                    (int) mouseY,
                    targetCellAt(sourceSlot, (int) mouseX, (int) mouseY));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (splitDialog != null) {
            splitSliderDragging = false;
            return true;
        }
        if (button == 0 && dragSource != DragSource.NONE) {
            if (isClickRelease(mouseX, mouseY)) {
                return true;
            }

            RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
            if (target != null) {
                int targetCell = placementCellAt(target, (int) mouseX, (int) mouseY, footprintFor(draggedStack));
                logDropTarget("release", mouseX, mouseY, target, targetCell);
                if (isGridTarget(target) && targetCell < 0) {
                    gridMoveStatus = "Target cell is blocked.";
                    clearDrag();
                    return true;
                }
                if (dragSource == DragSource.CONTAINER) {
                    sendTransfer(draggedSourceIndex, target, targetCell);
                } else if (dragSource == DragSource.RAID_INVENTORY) {
                    if (isRepairKitEquipmentDrop(draggedStack, target)) {
                        sendRepair(draggedRaidSlot, draggedSourceIndex, target);
                    } else {
                        sendMove(draggedRaidSlot, draggedSourceIndex, target, targetCell);
                    }
                }
                markPendingSource();
            } else if (dragSource == DragSource.RAID_INVENTORY && isContainerPanel((int) mouseX, (int) mouseY)) {
                sendReturn(draggedRaidSlot, draggedSourceIndex);
                markPendingSource();
            } else if (dragSource == DragSource.RAID_INVENTORY && isOutsideScreen(mouseX, mouseY)) {
                sendDrop(draggedRaidSlot, draggedSourceIndex);
                markPendingSource();
            }
            clearDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (splitDialog != null) {
            if (splitSliderDragging) {
                updateSplitAmountFromSlider(mouseX);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return splitDialog != null || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (splitDialog != null) {
            if (splitInputFocused && Character.isDigit(codePoint)) {
                splitInput = (splitInput + codePoint).replaceFirst("^0+(?!$)", "");
                setSplitAmount(parseSplitInput());
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (splitDialog != null) {
            if (keyCode == 256) {
                splitDialog = null;
                splitInput = "";
                splitInputFocused = false;
                splitSliderDragging = false;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmSplitDialog();
                return true;
            }
            if (keyCode == 259 && splitInputFocused && !splitInput.isEmpty()) {
                splitInput = splitInput.substring(0, splitInput.length() - 1);
                if (!splitInput.isEmpty()) {
                    setSplitAmount(parseSplitInput());
                }
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void startDrag(DragSource source, int sourceIndex, RaidEquipmentSlot raidSlot, ItemStack stack, Slot clickedSlot, double mouseX, double mouseY) {
        this.dragSource = source;
        this.draggedSourceIndex = sourceIndex;
        this.draggedRaidSlot = raidSlot;
        this.draggedStack = withResolvedGridMetadata(stack.copy(), sourceIndex, raidSlot, clickedSlot);
        this.draggedSourceMenuSlots.clear();
        this.draggedSourceMenuSlots.addAll(sourceMenuSlots(source, sourceIndex, raidSlot));
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
    }

    private void clearDrag() {
        this.dragSource = DragSource.NONE;
        this.draggedSourceIndex = -1;
        this.draggedRaidSlot = null;
        this.draggedStack = ItemStack.EMPTY;
        this.draggedSourceMenuSlots.clear();
        this.dragStartX = 0.0D;
        this.dragStartY = 0.0D;
        this.lastPreviewLogKey = "";
    }

    private void markPendingSource() {
        this.pendingSourceMenuSlots.clear();
        this.pendingSourceMenuSlots.addAll(this.draggedSourceMenuSlots);
        this.pendingSourceTicks = 20;
        this.pendingSourceObservedPresent = true;
    }

    private boolean isClickRelease(double mouseX, double mouseY) {
        return Math.abs(mouseX - dragStartX) <= 3.0D && Math.abs(mouseY - dragStartY) <= 3.0D;
    }

    private boolean tryPlaceHeldStack(double mouseX, double mouseY) {
        RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
        if (target != null) {
            int targetCell = placementCellAt(target, (int) mouseX, (int) mouseY, footprintFor(draggedStack));
            logDropTarget("click-place", mouseX, mouseY, target, targetCell);
            if (isGridTarget(target) && targetCell < 0) {
                gridMoveStatus = "Target cell is blocked.";
                return false;
            }
            if (dragSource == DragSource.CONTAINER) {
                sendTransfer(draggedSourceIndex, target, targetCell);
                markPendingSource();
                return true;
            }
            if (dragSource == DragSource.RAID_INVENTORY) {
                if (isRepairKitEquipmentDrop(draggedStack, target)) {
                    sendRepair(draggedRaidSlot, draggedSourceIndex, target);
                    markPendingSource();
                    return true;
                }
                sendMove(draggedRaidSlot, draggedSourceIndex, target, targetCell);
                markPendingSource();
                return true;
            }
            return false;
        }

        if (dragSource == DragSource.RAID_INVENTORY && isContainerPanel((int) mouseX, (int) mouseY)) {
            sendReturn(draggedRaidSlot, draggedSourceIndex);
            markPendingSource();
            return true;
        }

        if (dragSource == DragSource.CONTAINER && isContainerPanel((int) mouseX, (int) mouseY)) {
            clearDrag();
            return false;
        }

        return false;
    }

    private void sendTransfer(int containerSlot, RaidEquipmentSlot target) {
        sendTransfer(containerSlot, target, -1);
    }

    private void sendTransfer(int containerSlot, RaidEquipmentSlot target, int targetCell) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            if (targetCell >= 0) {
                sendGridMove(GridMoveRequestPayload.ACTIVE_CONTAINER_TO_RAID_CELL, null, containerSlot, target, targetCell);
                return;
            }
            int buttonId = targetCell >= 0
                    ? ActiveLootContainerMenu.cellButtonId(target, containerSlot, targetCell)
                    : ActiveLootContainerMenu.buttonId(target, containerSlot);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void sendMove(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        sendMove(source, sourceIndex, target, -1);
    }

    private void sendMove(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int targetCell) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null && target != null) {
            if (targetCell >= 0) {
                sendGridMove(GridMoveRequestPayload.ACTIVE_RAID_TO_RAID_CELL, source, sourceIndex, target, targetCell);
                return;
            }
            int buttonId = targetCell >= 0
                    ? ActiveLootContainerMenu.moveCellButtonId(source, sourceIndex, target, targetCell)
                    : ActiveLootContainerMenu.moveButtonId(source, sourceIndex, target);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void sendReturn(RaidEquipmentSlot source, int sourceIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            sendGridMove(GridMoveRequestPayload.ACTIVE_RAID_TO_CONTAINER, source, sourceIndex, null, -1);
        }
    }

    private void sendDrop(RaidEquipmentSlot source, int sourceIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            sendGridMove(GridMoveRequestPayload.ACTIVE_RAID_DROP, source, sourceIndex, null, -1);
        }
    }

    private void sendRepair(RaidEquipmentSlot source, int sourceIndex) {
        sendRepair(source, sourceIndex, null);
    }

    private void sendRepair(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            sendGridMove(GridMoveRequestPayload.ACTIVE_RAID_REPAIR, source, sourceIndex, target, -1);
        }
    }

    private void sendGridMove(int operation, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int targetCell) {
        int transactionId = nextTransactionId++;
        pendingTransactionId = transactionId;
        pendingOperation = operation;
        gridMoveStatus = "";
        ExtractCraft.LOGGER.info("Grid move request sent: tx={}, menu={}, op={}, source={}#{}, target={}, cell={}",
                transactionId,
                this.menu.containerId,
                operation,
                source,
                sourceIndex,
                target,
                targetCell);
        PacketDistributor.sendToServer(new GridMoveRequestPayload(transactionId, this.menu.containerId, operation, slotId(source), sourceIndex, slotId(target), targetCell));
    }

    private void sendSplit(boolean containerSource, RaidEquipmentSlot source, int sourceIndex, int amount) {
        if (amount <= 0) {
            return;
        }
        int operation = containerSource ? GridMoveRequestPayload.ACTIVE_CONTAINER_SPLIT : GridMoveRequestPayload.ACTIVE_RAID_SPLIT;
        sendGridMove(operation, source, sourceIndex, null, amount);
    }

    private boolean tryPlaceCarriedStack(double mouseX, double mouseY) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
        if (target != null && isGridTarget(target)) {
            int targetCell = placementCellAt(target, (int) mouseX, (int) mouseY, footprintFor(carried));
            if (targetCell < 0) {
                gridMoveStatus = "Target cell is blocked.";
                return true;
            }
            sendGridMove(GridMoveRequestPayload.ACTIVE_CARRIED_TO_RAID_CELL, null, -1, target, targetCell);
            return true;
        }
        if (this.menu.hasWorldContainer() && isContainerPanel((int) mouseX, (int) mouseY)) {
            sendGridMove(GridMoveRequestPayload.ACTIVE_CARRIED_TO_CONTAINER, null, -1, null, -1);
            return true;
        }
        return true;
    }

    public void handleGridMoveResult(int transactionId, boolean success, String message) {
        if (pendingTransactionId != transactionId) {
            ExtractCraft.LOGGER.info("Grid move result ignored: tx={}, pendingTx={}, success={}, message={}", transactionId, pendingTransactionId, success, message);
            return;
        }
        ExtractCraft.LOGGER.info("Grid move result applied: tx={}, success={}, message={}, pendingSlots={}", transactionId, success, message, pendingSourceMenuSlots);
        int completedOperation = pendingOperation;
        pendingTransactionId = -1;
        pendingOperation = -1;
        gridMoveStatus = success ? "" : message;
        if (!success) {
            pendingSourceMenuSlots.clear();
            pendingSourceTicks = 0;
            pendingSourceObservedPresent = false;
            return;
        }
        if (completedOperation == GridMoveRequestPayload.ACTIVE_RAID_REPAIR && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        }
    }

    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            int size = isRaidInventorySlot(slot) || isContainerSlot(slot) ? SLOT_STEP : 16;
            if (slot.isActive() && this.isHovering(slot.x, slot.y, size, size, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private void renderCustomTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Slot slot = customTooltipSlot(mouseX, mouseY);
        if (slot == null || !slot.hasItem()) {
            return;
        }
        guiGraphics.renderComponentTooltip(this.font, ExtractCraftTooltipBuilder.build(slot.getItem()), mouseX, mouseY, slot.getItem());
    }

    private Slot customTooltipSlot(int mouseX, int mouseY) {
        if (splitDialog != null) {
            return null;
        }
        if (contextMenu != null || dragSource != DragSource.NONE || !draggedStack.isEmpty()) {
            return null;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot != null && isContainerSlot(slot)) {
            if (hiddenRevealAtSlot(slot) != null) {
                return null;
            }
            slot = containerOwnerSlotForCell(slot);
        }
        if (slot == null || !slot.hasItem() || isDragSourceSlot(slot)) {
            return null;
        }
        if (isRaidInventorySlot(slot) && !isWeaponSlot(slot)) {
            return ownerSlotFor(slot, this.menu.raidSlotForMenuSlot(slot.index), this.menu.raidItemIndexForMenuSlot(slot.index));
        }
        return slot;
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY) {
        if (contextMenu == null) {
            return false;
        }
        int x = contextMenuX();
        int y = contextMenuY();
        if (!inside((int) mouseX, (int) mouseY, x, y, CONTEXT_MENU_WIDTH, contextMenuHeight())) {
            return false;
        }
        int option = contextMenuOptionAt(mouseX, mouseY);
        ContextAction action = contextMenuActionForOption(option);
        if (action == ContextAction.SPLIT) {
            openSplitDialog(new SplitDialog(false, contextMenu.source(), contextMenu.sourceIndex(), sourceOwnerStack(contextMenu), halfSplitAmount(sourceOwnerStack(contextMenu)), sourceOwnerStack(contextMenu).getCount() - 1));
            contextMenu = null;
            return true;
        }
        if (action == ContextAction.REPAIR) {
            this.pendingSourceMenuSlots.clear();
            this.pendingSourceMenuSlots.addAll(sourceMenuSlots(DragSource.RAID_INVENTORY, contextMenu.sourceIndex(), contextMenu.source()));
            this.pendingSourceTicks = 20;
            this.pendingSourceObservedPresent = true;
            sendRepair(contextMenu.source(), contextMenu.sourceIndex());
            contextMenu = null;
            return true;
        }
        if (action == ContextAction.DROP) {
            this.pendingSourceMenuSlots.clear();
            this.pendingSourceMenuSlots.addAll(sourceMenuSlots(DragSource.RAID_INVENTORY, contextMenu.sourceIndex(), contextMenu.source()));
            this.pendingSourceTicks = 20;
            this.pendingSourceObservedPresent = true;
            sendDrop(contextMenu.source(), contextMenu.sourceIndex());
            contextMenu = null;
            return true;
        }
        return false;
    }

    private void renderContextMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (contextMenu == null) {
            return;
        }
        int x = contextMenuX();
        int y = contextMenuY();
        int hovered = contextMenuOptionAt(mouseX, mouseY);
        int rows = contextMenuRows();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, 0.0D, 500.0D);
        guiGraphics.fill(x, y, x + CONTEXT_MENU_WIDTH, y + CONTEXT_MENU_ROW_HEIGHT * rows + 4, 0xF0181B22);
        border(guiGraphics, x, y, CONTEXT_MENU_WIDTH, CONTEXT_MENU_ROW_HEIGHT * rows + 4, BORDER_COLOR);
        int row = 0;
        if (contextMenu.canSplit()) {
            renderContextRow(guiGraphics, x, y, row, "Split", hovered == row, TEXT);
            row++;
        }
        if (contextMenu.canRepair()) {
            renderContextRow(guiGraphics, x, y, row, "Repair", hovered == row, 0xFFB8F5C8);
            row++;
        }
        renderContextRow(guiGraphics, x, y, row, "Drop", hovered == row, TEXT);
        guiGraphics.pose().popPose();
    }

    private void renderContextRow(GuiGraphics guiGraphics, int menuX, int menuY, int row, String label, boolean hovered, int textColor) {
        int rowX = menuX + 2;
        int rowY = menuY + 2 + row * CONTEXT_MENU_ROW_HEIGHT;
        if (hovered) {
            guiGraphics.fill(rowX, rowY, rowX + CONTEXT_MENU_WIDTH - 4, rowY + CONTEXT_MENU_ROW_HEIGHT, 0x553A5E66);
        }
        guiGraphics.drawString(this.font, label, rowX + 5, rowY + 5, textColor, false);
    }

    private int contextMenuOptionAt(double mouseX, double mouseY) {
        if (contextMenu == null) {
            return -1;
        }
        int x = contextMenuX() + 2;
        int y = contextMenuY() + 2;
        int rows = contextMenuRows();
        if (!inside((int) mouseX, (int) mouseY, x, y, CONTEXT_MENU_WIDTH - 4, CONTEXT_MENU_ROW_HEIGHT * rows)) {
            return -1;
        }
        int option = ((int) mouseY - y) / CONTEXT_MENU_ROW_HEIGHT;
        return option >= 0 && option < rows ? option : -1;
    }

    private int contextMenuRows() {
        if (contextMenu == null) {
            return 0;
        }
        return 1 + (contextMenu.canSplit() ? 1 : 0) + (contextMenu.canRepair() ? 1 : 0);
    }

    private ContextAction contextMenuActionForOption(int option) {
        if (contextMenu == null || option < 0) {
            return ContextAction.NONE;
        }
        int row = 0;
        if (contextMenu.canSplit()) {
            if (option == row) {
                return ContextAction.SPLIT;
            }
            row++;
        }
        if (contextMenu.canRepair()) {
            if (option == row) {
                return ContextAction.REPAIR;
            }
            row++;
        }
        return option == row ? ContextAction.DROP : ContextAction.NONE;
    }

    private int contextMenuHeight() {
        int rows = contextMenuRows();
        return CONTEXT_MENU_ROW_HEIGHT * rows + 4;
    }

    private int contextMenuX() {
        return Math.min(contextMenu.x(), this.leftPos + this.imageWidth - CONTEXT_MENU_WIDTH - 4);
    }

    private int contextMenuY() {
        int rows = contextMenuRows();
        return Math.min(contextMenu.y(), this.topPos + this.imageHeight - CONTEXT_MENU_ROW_HEIGHT * rows - 8);
    }

    private void openSplitDialog(SplitDialog dialog) {
        if (dialog == null || !canSplit(dialog.stack())) {
            return;
        }
        int amount = Math.max(1, Math.min(dialog.maxAmount(), dialog.amount()));
        splitDialog = new SplitDialog(dialog.containerSource(), dialog.source(), dialog.sourceIndex(), dialog.stack(), amount, dialog.maxAmount());
        splitInput = Integer.toString(amount);
    }

    private boolean handleSplitDialogClick(double mouseX, double mouseY) {
        int x = splitDialogX();
        int y = splitDialogY();
        if (!inside((int) mouseX, (int) mouseY, x, y, SPLIT_DIALOG_WIDTH, SPLIT_DIALOG_HEIGHT)) {
            splitDialog = null;
            splitInput = "";
            splitInputFocused = false;
            splitSliderDragging = false;
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, splitSliderX(), splitSliderY() - 4, splitSliderWidth(), 11)) {
            splitInputFocused = false;
            splitSliderDragging = true;
            updateSplitAmountFromSlider(mouseX);
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, splitInputX(), splitInputY(), splitInputWidth(), splitInputHeight())) {
            splitInputFocused = true;
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, x + 10, splitButtonY(), 58, 16)) {
            confirmSplitDialog();
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, x + 82, splitButtonY(), 58, 16)) {
            splitDialog = null;
            splitInput = "";
            splitInputFocused = false;
            splitSliderDragging = false;
            return true;
        }
        splitInputFocused = false;
        return true;
    }

    private void renderSplitDialog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (splitDialog == null) {
            return;
        }
        int x = splitDialogX();
        int y = splitDialogY();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, 0.0D, 1000.0D);
        guiGraphics.fill(x, y, x + SPLIT_DIALOG_WIDTH, y + SPLIT_DIALOG_HEIGHT, 0xF0181B22);
        border(guiGraphics, x, y, SPLIT_DIALOG_WIDTH, SPLIT_DIALOG_HEIGHT, BORDER_COLOR);
        guiGraphics.renderItem(splitDialog.stack(), x + 10, y + 10);
        guiGraphics.drawString(this.font, "Split Stack", x + 32, y + 10, TEXT, false);
        guiGraphics.drawString(this.font, splitDialog.stack().getHoverName().getString(), x + 32, y + 22, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Count: " + splitDialog.stack().getCount(), x + 10, y + 36, MUTED_TEXT, false);
        int barX = splitSliderX();
        int barY = splitSliderY();
        int barW = splitSliderWidth();
        guiGraphics.fill(barX, barY, barX + barW, barY + 3, 0xFF4A5564);
        int knob = barX + (int) Math.round((splitDialog.amount() - 1) / (double) Math.max(1, splitDialog.maxAmount() - 1) * barW);
        guiGraphics.fill(knob - 2, barY - 3, knob + 2, barY + 6, 0xFF49D8E8);
        guiGraphics.drawString(this.font, "Amount", x + 10, y + 63, MUTED_TEXT, false);
        int inputX = splitInputX();
        int inputY = splitInputY();
        guiGraphics.fill(inputX, inputY, inputX + splitInputWidth(), inputY + splitInputHeight(), splitInputFocused ? 0xFF243640 : 0xFF151A21);
        border(guiGraphics, inputX, inputY, splitInputWidth(), splitInputHeight(), splitInputFocused ? HOVER_BORDER : BORDER_COLOR);
        guiGraphics.drawString(this.font, splitInput.isEmpty() ? "_" : splitInput, inputX + 4, inputY + 3, TEXT, false);
        renderDialogButton(guiGraphics, x + 10, splitButtonY(), "Confirm", inside(mouseX, mouseY, x + 10, splitButtonY(), 58, 16));
        renderDialogButton(guiGraphics, x + 82, splitButtonY(), "Cancel", inside(mouseX, mouseY, x + 82, splitButtonY(), 58, 16));
        guiGraphics.pose().popPose();
    }

    private void renderDialogButton(GuiGraphics guiGraphics, int x, int y, String label, boolean hovered) {
        guiGraphics.fill(x, y, x + 58, y + 16, hovered ? 0x663A5E66 : 0x333A5E66);
        border(guiGraphics, x, y, 58, 16, hovered ? HOVER_BORDER : BORDER_COLOR);
        guiGraphics.drawString(this.font, label, x + 6, y + 4, TEXT, false);
    }

    private int splitDialogX() {
        return this.leftPos + this.imageWidth / 2 - SPLIT_DIALOG_WIDTH / 2;
    }

    private int splitDialogY() {
        return this.topPos + this.imageHeight / 2 - SPLIT_DIALOG_HEIGHT / 2;
    }

    private int splitSliderX() {
        return splitDialogX() + 12;
    }

    private int splitSliderY() {
        return splitDialogY() + 50;
    }

    private int splitSliderWidth() {
        return SPLIT_DIALOG_WIDTH - 24;
    }

    private int splitInputX() {
        return splitDialogX() + 60;
    }

    private int splitInputY() {
        return splitDialogY() + 61;
    }

    private int splitInputWidth() {
        return 34;
    }

    private int splitInputHeight() {
        return 12;
    }

    private int splitButtonY() {
        return splitDialogY() + 82;
    }

    private void updateSplitAmountFromSlider(double mouseX) {
        if (splitDialog == null) {
            return;
        }
        int rel = Math.max(0, Math.min(splitSliderWidth(), (int) mouseX - splitSliderX()));
        int amount = 1 + (int) Math.round(rel / (double) Math.max(1, splitSliderWidth()) * (splitDialog.maxAmount() - 1));
        setSplitAmount(amount);
    }

    private void setSplitAmount(int amount) {
        if (splitDialog == null) {
            return;
        }
        int clamped = Math.max(1, Math.min(splitDialog.maxAmount(), amount));
        splitDialog = new SplitDialog(splitDialog.containerSource(), splitDialog.source(), splitDialog.sourceIndex(), splitDialog.stack(), clamped, splitDialog.maxAmount());
        splitInput = Integer.toString(clamped);
    }

    private void confirmSplitDialog() {
        if (splitDialog == null) {
            return;
        }
        sendSplit(splitDialog.containerSource(), splitDialog.source(), splitDialog.sourceIndex(), parseSplitInput());
        splitDialog = null;
        splitInput = "";
        splitInputFocused = false;
        splitSliderDragging = false;
    }

    private int parseSplitInput() {
        if (splitDialog == null) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(splitDialog.maxAmount(), Integer.parseInt(splitInput)));
        } catch (NumberFormatException ignored) {
            return splitDialog.amount();
        }
    }

    private ItemStack sourceOwnerStack(ContextMenu menu) {
        if (menu == null) {
            return ItemStack.EMPTY;
        }
        int menuSlot = this.menu.raidMenuSlotForItemIndex(menu.source(), menu.sourceIndex());
        return menuSlot >= 0 && menuSlot < this.menu.slots.size() ? this.menu.slots.get(menuSlot).getItem() : ItemStack.EMPTY;
    }

    private static boolean canSplit(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 1 && stack.getMaxStackSize() > 1;
    }

    private static boolean canInRaidRepair(ItemStack stack) {
        return InRaidRepairService.isInRaidRepairKit(stack);
    }

    private static boolean isRepairKitEquipmentDrop(ItemStack stack, RaidEquipmentSlot target) {
        return InRaidRepairService.isInRaidRepairKit(stack) && isRepairEquipmentDropTarget(target);
    }

    private static boolean isRepairEquipmentDropTarget(RaidEquipmentSlot target) {
        return target == RaidEquipmentSlot.HELMET
                || target == RaidEquipmentSlot.ARMOR
                || target == RaidEquipmentSlot.EQUIPPED_BACKPACK
                || target == RaidEquipmentSlot.EQUIPPED_VEST
                || target == RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER;
    }

    private static int halfSplitAmount(ItemStack stack) {
        return canSplit(stack) ? Math.max(1, stack.getCount() / 2) : 0;
    }

    private int targetCellAt(RaidEquipmentSlot target, int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return switch (target) {
            case BACKPACK -> gridCell(localX, localY, BACKPACK_GRID_X, BACKPACK_GRID_Y, menu.backpackGridWidth(), menu.backpackGridHeight());
            case VEST -> gridCell(localX, localY, VEST_GRID_X, VEST_GRID_Y, menu.vestGridWidth(), menu.vestGridHeight());
            case SAFE_BOX -> gridCell(localX, localY, SAFE_GRID_X, SAFE_GRID_Y, menu.safeGridWidth(), menu.safeGridHeight());
            default -> -1;
        };
    }

    private int placementCellAt(RaidEquipmentSlot target, int mouseX, int mouseY, Footprint footprint) {
        Placement placement = placementAt(target, mouseX, mouseY, footprint);
        if (!placement.inGrid()) {
            return -1;
        }
        if (target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return -1;
        }
        return placement.valid() ? placement.y() * placement.layout().columns() + placement.x() : -1;
    }

    private Placement placementAt(RaidEquipmentSlot target, int mouseX, int mouseY, Footprint footprint) {
        if (target == null || target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return Placement.invalid(layoutFor(RaidEquipmentSlot.BACKPACK), 0, 0, false);
        }
        GridLayout layout = layoutFor(target);
        int localX = mouseX - this.leftPos - layout.x();
        int localY = mouseY - this.topPos - layout.y();
        boolean inGrid = localX >= 0 && localY >= 0 && localX < layout.columns() * SLOT_STEP && localY < layout.rows() * SLOT_STEP;
        double visualTopLeftX = localX - (footprint.width() * SLOT_STEP) / 2.0D;
        double visualTopLeftY = localY - (footprint.height() * SLOT_STEP) / 2.0D;
        int topLeftX = (int) Math.round(visualTopLeftX / SLOT_STEP);
        int topLeftY = (int) Math.round(visualTopLeftY / SLOT_STEP);
        boolean valid = inGrid && topLeftX >= 0 && topLeftY >= 0
                && topLeftX + footprint.width() <= layout.columns()
                && topLeftY + footprint.height() <= layout.rows();
        return new Placement(layout, topLeftX, topLeftY, inGrid, valid);
    }

    private static int gridCell(int localX, int localY, int gridX, int gridY, int columns, int rows) {
        if (!inside(localX, localY, gridX, gridY, columns * SLOT_STEP, rows * SLOT_STEP)) {
            return -1;
        }
        int cellX = (localX - gridX) / SLOT_STEP;
        int cellY = (localY - gridY) / SLOT_STEP;
        return cellY * columns + cellX;
    }

    private boolean isContainerSlot(Slot slot) {
        return slot.index >= this.menu.containerMenuSlotStart()
                && slot.index < this.menu.containerMenuSlotStart() + this.menu.containerSlotCount();
    }

    private boolean isRaidInventorySlot(Slot slot) {
        return this.menu.raidSlotForMenuSlot(slot.index) != null;
    }

    private boolean isVisibleGridSlot(Slot slot) {
        RaidEquipmentSlot section = this.menu.raidSlotForMenuSlot(slot.index);
        if (!isGridTarget(section)) {
            return true;
        }
        int offset = switch (section) {
            case BACKPACK -> slot.index - ActiveLootContainerMenu.BACKPACK_START;
            case VEST -> slot.index - ActiveLootContainerMenu.VEST_START;
            case SAFE_BOX -> slot.index - ActiveLootContainerMenu.SAFE_BOX_START;
            default -> -1;
        };
        GridLayout layout = layoutFor(section);
        if (layout.columns() <= 0 || layout.rows() <= 0 || offset < 0) {
            return false;
        }
        int x = offset % Math.max(1, layout.columns());
        int y = offset / Math.max(1, layout.columns());
        return x < layout.columns() && y < layout.rows();
    }

    private boolean isGridDisplaySlot(Slot slot) {
        return isGridTarget(this.menu.raidSlotForMenuSlot(slot.index));
    }

    private boolean isWeaponSlot(Slot slot) {
        return slot.index == ActiveLootContainerMenu.PRIMARY_WEAPON_START
                || slot.index == ActiveLootContainerMenu.SECONDARY_WEAPON_START;
    }

    private boolean isDragSourceSlot(Slot slot) {
        if (pendingSourceMenuSlots.contains(slot.index)) {
            return true;
        }
        if (draggedSourceMenuSlots.contains(slot.index)) {
            return true;
        }
        if (dragSource == DragSource.CONTAINER) {
            return slot.index == this.menu.containerMenuSlotStart() + draggedSourceIndex;
        }
        if (dragSource == DragSource.RAID_INVENTORY && draggedRaidSlot != null) {
            return isRaidInventorySlot(slot)
                    && this.menu.raidSlotForMenuSlot(slot.index) == draggedRaidSlot
                    && this.menu.raidItemIndexForMenuSlot(slot.index) == draggedSourceIndex;
        }
        return false;
    }

    private boolean isGridShadowSlot(Slot slot) {
        if (!slot.hasItem() || isWeaponSlot(slot) || !isFootprintOverlaySlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && !metadata.anchor();
    }

    private boolean isGridAnchorFootprintSlot(Slot slot) {
        if (!slot.hasItem() || isWeaponSlot(slot) || !isFootprintOverlaySlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && metadata.anchor() && (metadata.footprintWidth() > 1 || metadata.footprintHeight() > 1);
    }

    private RaidEquipmentSlot targetAt(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (inside(localX, localY, EQUIPMENT_X, 34, 82, 26)) {
            return RaidEquipmentSlot.HELMET;
        }
        if (inside(localX, localY, EQUIPMENT_X, 64, 82, 26)) {
            return RaidEquipmentSlot.ARMOR;
        }
        if (inside(localX, localY, EQUIPMENT_X, 94, 82, 26)) {
            return RaidEquipmentSlot.EQUIPPED_BACKPACK;
        }
        if (inside(localX, localY, EQUIPMENT_X, 124, 82, 26)) {
            return RaidEquipmentSlot.EQUIPPED_VEST;
        }
        if (inside(localX, localY, EQUIPMENT_X, 154, 82, 26)) {
            return RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER;
        }
        if (inside(localX, localY, EQUIPMENT_X, 192, 82, 26)) {
            return RaidEquipmentSlot.PRIMARY_WEAPON;
        }
        if (inside(localX, localY, EQUIPMENT_X, 222, 82, 26)) {
            return RaidEquipmentSlot.SECONDARY_WEAPON;
        }
        if (insideBackpackGrid(localX, localY)) {
            return RaidEquipmentSlot.BACKPACK;
        }
        if (insideVestGrid(localX, localY)) {
            return RaidEquipmentSlot.VEST;
        }
        if (insideSafeGrid(localX, localY)) {
            return RaidEquipmentSlot.SAFE_BOX;
        }
        return null;
    }

    private void logDropTarget(String action, double mouseX, double mouseY, RaidEquipmentSlot target, int targetCell) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        ExtractCraft.LOGGER.info("Raid grid drop target: action={}, raw=({},{}), gui=({},{}), local=({},{}), source={}#{}, sourceKey={}, target={}, cell={}, cellXY=({},{}), insideBackpack={}, insideVest={}, insideSafe={}, insideContainer={}",
                action,
                (int) mouseX,
                (int) mouseY,
                this.leftPos,
                this.topPos,
                localX,
                localY,
                dragSource,
                draggedSourceIndex,
                draggedStack.isEmpty() ? "empty" : draggedStack.getHoverName().getString(),
                target,
                targetCell,
                targetCell >= 0 ? cellX(target, targetCell) : -1,
                targetCell >= 0 ? cellY(target, targetCell) : -1,
                insideBackpackGrid(localX, localY),
                insideVestGrid(localX, localY),
                insideSafeGrid(localX, localY),
                isContainerPanel((int) mouseX, (int) mouseY));
    }

    private boolean insideBackpackGrid(int localX, int localY) {
        return inside(localX, localY, BACKPACK_GRID_X, BACKPACK_GRID_Y, menu.backpackGridWidth() * SLOT_STEP, menu.backpackGridHeight() * SLOT_STEP);
    }

    private boolean insideVestGrid(int localX, int localY) {
        return inside(localX, localY, VEST_GRID_X, VEST_GRID_Y, menu.vestGridWidth() * SLOT_STEP, menu.vestGridHeight() * SLOT_STEP);
    }

    private boolean insideSafeGrid(int localX, int localY) {
        return inside(localX, localY, SAFE_GRID_X, SAFE_GRID_Y, menu.safeGridWidth() * SLOT_STEP, menu.safeGridHeight() * SLOT_STEP);
    }

    private int cellX(RaidEquipmentSlot target, int cell) {
        return cell % Math.max(1, layoutFor(target).columns());
    }

    private int cellY(RaidEquipmentSlot target, int cell) {
        return cell / Math.max(1, layoutFor(target).columns());
    }

    private static int slotId(RaidEquipmentSlot slot) {
        if (slot == null) {
            return -1;
        }
        return switch (slot) {
            case PRIMARY_WEAPON -> 0;
            case SECONDARY_WEAPON -> 1;
            case BACKPACK -> 2;
            case VEST -> 3;
            case SAFE_BOX -> 4;
            case HELMET -> 5;
            case ARMOR -> 6;
            case EQUIPPED_BACKPACK -> 7;
            case EQUIPPED_VEST -> 8;
            case EQUIPPED_SAFE_CONTAINER -> 9;
            default -> -1;
        };
    }

    private boolean isContainerPanel(int mouseX, int mouseY) {
        if (!this.menu.hasWorldContainer()) {
            return false;
        }
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, CONTAINER_PANEL_X, CONTAINER_PANEL_Y, CONTAINER_PANEL_WIDTH, this.imageHeight - 40);
    }

    private boolean isOutsideScreen(double mouseX, double mouseY) {
        return !inside((int) mouseX, (int) mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    private static boolean isGridTarget(RaidEquipmentSlot target) {
        return target == RaidEquipmentSlot.BACKPACK || target == RaidEquipmentSlot.VEST || target == RaidEquipmentSlot.SAFE_BOX;
    }

    private double totalUsedWeight() {
        return menu.backpackUsedWeight() + menu.vestUsedWeight() + menu.safeBoxUsedWeight();
    }

    private double totalMaxWeight() {
        return menu.backpackMaxWeight() + menu.vestMaxWeight() + menu.safeBoxMaxWeight();
    }

    private static boolean inside(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && x < rectX + width && y >= rectY && y < rectY + height;
    }

    private static void section(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean highlighted) {
        guiGraphics.fill(x, y, x + width, y + height, SECTION_COLOR);
        border(guiGraphics, x, y, width, height, highlighted ? HOVER_BORDER : 0x6649D8E8);
    }

    private static void equipmentRow(GuiGraphics guiGraphics, int x, int y, boolean highlighted) {
        if (highlighted) {
            guiGraphics.fill(x + 4, y + 4, x + 78, y + 28, 0x2239BFD0);
            border(guiGraphics, x + 4, y + 4, 74, 24, HOVER_BORDER);
        }
    }

    private static void drawEquipmentSlotBackground(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF202632);
        guiGraphics.fill(x, y, x + 16, y + 16, SLOT_COLOR);
        border(guiGraphics, x - 1, y - 1, 18, 18, 0x332A323A);
    }

    private static void drawVanillaSlotBackground(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF1C2028);
        guiGraphics.fill(x, y, x + 16, y + 16, SLOT_COLOR);
    }

    private void syncRevealState() {
        if (!this.menu.hasWorldContainer()) {
            revealStates.clear();
            revealInitialized = false;
            return;
        }

        long now = System.currentTimeMillis();
        List<Slot> anchors = containerAnchorSlots();
        Set<Integer> activeSlots = anchors.stream().map(slot -> slot.index).collect(java.util.stream.Collectors.toSet());
        revealStates.entrySet().removeIf(entry -> !activeSlots.contains(entry.getKey()) || !entry.getValue().signature().equals(revealSignature(this.menu.slots.get(entry.getKey()))));
        Set<String> cachedRevealed = cachedRevealedSignatures();

        if (!revealInitialized) {
            long cursor = now;
            for (Slot slot : anchors) {
                if (revealStates.containsKey(slot.index)) {
                    continue;
                }
                ItemRarity rarity = rarityFor(slot.getItem());
                String signature = revealSignature(slot);
                if (cachedRevealed.contains(signature)) {
                    revealStates.put(slot.index, RevealState.revealed(signature, rarity, now));
                } else {
                    cursor += revealDelayMs(rarity);
                    revealStates.put(slot.index, RevealState.hidden(signature, rarity, cursor));
                }
            }
            revealInitialized = true;
            return;
        }

        for (Slot slot : anchors) {
            revealStates.computeIfAbsent(slot.index, ignored -> {
                String signature = revealSignature(slot);
                if (cachedRevealed.contains(signature)) {
                    return RevealState.revealed(signature, rarityFor(slot.getItem()), now);
                }
                return RevealState.revealed(signature, rarityFor(slot.getItem()), now);
            });
        }
    }

    private List<Slot> containerAnchorSlots() {
        List<Slot> anchors = new ArrayList<>();
        for (Slot slot : this.menu.slots) {
            if (!isContainerSlot(slot) || !slot.hasItem()) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || metadata.anchor()) {
                anchors.add(slot);
            }
        }
        anchors.sort(Comparator.comparingInt(slot -> slot.index));
        return anchors;
    }

    private void updateRevealState() {
        long now = System.currentTimeMillis();
        RevealState nextHidden = null;
        for (RevealState state : revealStates.values()) {
            if (!state.revealed() && now >= state.revealAtMs()) {
                state.reveal();
                cacheRevealed(state.signature());
                playRevealSound(state.rarity());
            }
            if (!state.revealed() && (nextHidden == null || state.revealAtMs() < nextHidden.revealAtMs())) {
                nextHidden = state;
            }
        }
        for (RevealState state : revealStates.values()) {
            state.setActive(state == nextHidden);
        }
        if (nextHidden != null && now - nextHidden.lastSearchSoundAtMs() > 360L) {
            nextHidden.markSearchSound(now);
            playSearchSound();
        }
    }

    private void renderRevealOverlays(GuiGraphics guiGraphics) {
        long now = System.currentTimeMillis();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 40.0F);
        for (Map.Entry<Integer, RevealState> entry : revealStates.entrySet()) {
            if (entry.getValue().revealed()) {
                continue;
            }
            Slot slot = this.menu.slots.get(entry.getKey());
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            int width = (metadata.present() ? metadata.footprintWidth() : 1) * SLOT_STEP;
            int height = (metadata.present() ? metadata.footprintHeight() : 1) * SLOT_STEP;
            int x = slot.x;
            int y = slot.y;
            guiGraphics.fill(x, y, x + width - 2, y + height - 2, 0xAA10141B);
            border(guiGraphics, x - 1, y - 1, width, height, 0x775E6B78);
            if (!entry.getValue().active()) {
                continue;
            }
            drawSearchSpinner(guiGraphics, x, y, width, height, now);
        }
        guiGraphics.pose().popPose();
    }

    private static void drawSearchSpinner(GuiGraphics guiGraphics, int x, int y, int width, int height, long now) {
        int interiorWidth = Math.max(1, width - 2);
        int interiorHeight = Math.max(1, height - 2);
        int centerX = x + interiorWidth / 2;
        int centerY = y + interiorHeight / 2;
        int radius = Math.max(5, Math.min(interiorWidth, interiorHeight) / 2 - 2);
        double angle = (now % 1_000L) / 1_000.0D * Math.PI * 2.0D - Math.PI / 2.0D;
        int endX = centerX + (int) Math.round(Math.cos(angle) * radius);
        int endY = centerY + (int) Math.round(Math.sin(angle) * radius);
        drawLine(guiGraphics, centerX, centerY, endX, endY, 0xCC7EEAF2);
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xAA7EEAF2);
    }

    private static void drawLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            guiGraphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * error;
            if (e2 > -dy) {
                error -= dy;
                x += sx;
            }
            if (e2 < dx) {
                error += dx;
                y += sy;
            }
        }
    }

    private void drawRevealTint(GuiGraphics guiGraphics, Slot slot, RevealState state) {
        if (state == null || !state.revealed()) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        int width = (metadata.present() ? metadata.footprintWidth() : 1) * SLOT_STEP - 2;
        int height = (metadata.present() ? metadata.footprintHeight() : 1) * SLOT_STEP - 2;
        guiGraphics.fill(slot.x, slot.y, slot.x + width, slot.y + height, rarityTint(state.rarity()));
    }

    private RevealState revealStateForSlot(Slot slot) {
        return slot == null ? null : revealStates.get(slot.index);
    }

    private boolean isContainerSlotRevealed(Slot slot) {
        RevealState state = revealStateForSlot(slot);
        return state != null && state.revealed();
    }

    private boolean isContainerSlotHidden(Slot slot) {
        RevealState state = revealStateForSlot(slot);
        return state != null && !state.revealed();
    }

    private RevealState hiddenRevealAtSlot(Slot slot) {
        if (slot == null || !isContainerSlot(slot)) {
            return null;
        }
        int containerSlot = slot.index - this.menu.containerMenuSlotStart();
        int cellX = containerSlot % ActiveLootContainerMenu.CONTAINER_COLUMNS;
        int cellY = containerSlot / ActiveLootContainerMenu.CONTAINER_COLUMNS;
        for (Map.Entry<Integer, RevealState> entry : revealStates.entrySet()) {
            RevealState state = entry.getValue();
            if (state.revealed()) {
                continue;
            }
            Slot anchor = this.menu.slots.get(entry.getKey());
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(anchor.getItem());
            int anchorSlot = anchor.index - this.menu.containerMenuSlotStart();
            int anchorX = metadata.present() ? metadata.gridX() : anchorSlot % ActiveLootContainerMenu.CONTAINER_COLUMNS;
            int anchorY = metadata.present() ? metadata.gridY() : anchorSlot / ActiveLootContainerMenu.CONTAINER_COLUMNS;
            int width = metadata.present() ? metadata.footprintWidth() : 1;
            int height = metadata.present() ? metadata.footprintHeight() : 1;
            if (cellX >= anchorX && cellX < anchorX + width && cellY >= anchorY && cellY < anchorY + height) {
                return state;
            }
        }
        return null;
    }

    private static String revealSignature(Slot slot) {
        if (slot == null || !slot.hasItem()) {
            return "empty";
        }
        ItemStack stack = slot.getItem();
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(stack);
        return ItemIdentityResolver.resolve(stack).normalizedKey()
                + "|" + stack.getCount()
                + "|" + metadata.gridX()
                + "," + metadata.gridY()
                + "|" + metadata.footprintWidth()
                + "x" + metadata.footprintHeight();
    }

    private static ItemRarity rarityFor(ItemStack stack) {
        return ItemValueRegistry.get(stack).map(ItemValueEntry::rarity).orElse(ItemRarity.COMMON);
    }

    private Set<String> cachedRevealedSignatures() {
        String key = revealCacheKey();
        if (key.isBlank()) {
            return Set.of();
        }
        return REVEALED_CONTAINER_CACHE.computeIfAbsent(key, ignored -> new HashSet<>());
    }

    private void cacheRevealed(String signature) {
        String key = revealCacheKey();
        if (!key.isBlank()) {
            REVEALED_CONTAINER_CACHE.computeIfAbsent(key, ignored -> new HashSet<>()).add(signature);
        }
    }

    private String revealCacheKey() {
        if (this.minecraft == null || this.minecraft.level == null || !this.menu.hasWorldContainer()) {
            return "";
        }
        return this.minecraft.level.dimension().location() + "|" + this.menu.containerPos() + "|" + this.menu.containerSlotCount();
    }

    private static long revealDelayMs(ItemRarity rarity) {
        return LootRevealTiming.delayMs(rarity);
    }

    private static int rarityTint(ItemRarity rarity) {
        return RarityPresentation.slotTint(rarity);
    }

    private void playSearchSound() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.08F, 1.7F);
    }

    private void playRevealSound(ItemRarity rarity) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        float pitch = switch (rarity) {
            case COMMON, BLUE -> 1.15F;
            case UNCOMMON -> 1.3F;
            case RARE, PURPLE -> 1.55F;
            case EPIC, GOLD -> 1.85F;
            case RED, LEGENDARY -> 2.0F;
            default -> 1.2F;
        };
        float volume = switch (rarity) {
            case RED, LEGENDARY -> 0.45F;
            case EPIC, GOLD -> 0.35F;
            case RARE, PURPLE -> 0.28F;
            default -> 0.2F;
        };
        this.minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, volume, pitch);
    }

    private void drawFootprint(GuiGraphics guiGraphics, Slot slot) {
        if (!slot.hasItem()) {
            return;
        }
        if (isContainerSlot(slot) && isContainerSlotHidden(slot)) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        if (!metadata.present() || !metadata.anchor() || metadata.footprintWidth() <= 1 && metadata.footprintHeight() <= 1) {
            return;
        }
        int width = metadata.footprintWidth() * SLOT_STEP - 2;
        int height = metadata.footprintHeight() * SLOT_STEP - 2;
        RevealState revealState = isContainerSlot(slot) ? revealStateForSlot(slot) : null;
        guiGraphics.fill(slot.x, slot.y, slot.x + width, slot.y + height, revealState == null ? 0x2210151D : rarityTint(revealState.rarity()));
        border(guiGraphics, slot.x - 1, slot.y - 1, width + 2, height + 2, 0x8849D8E8);
    }

    private void renderFootprintItem(GuiGraphics guiGraphics, Slot slot) {
        if (!slot.hasItem() || isDragSourceSlot(slot)) {
            return;
        }
        if (isContainerSlot(slot) && isContainerSlotHidden(slot)) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        if (!metadata.present() || !metadata.anchor() || metadata.footprintWidth() <= 1 && metadata.footprintHeight() <= 1) {
            return;
        }

        int width = metadata.footprintWidth() * SLOT_STEP;
        int height = metadata.footprintHeight() * SLOT_STEP;
        if (isContainerSlot(slot)) {
            renderItemCenteredInFootprintInterior(guiGraphics, slot.getItem(), slot.x, slot.y, width, height);
        } else {
            ManagedGridItemRenderer.renderItemCentered(guiGraphics, slot.getItem(), slot.x, slot.y, width, height);
        }
        CustomDurabilityBarRenderer.render(guiGraphics, slot.getItem(), slot.x, slot.y, width, height);
    }

    private void renderFootprintOverlays(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        for (Slot slot : this.menu.slots) {
            if (isFootprintOverlaySlot(slot)) {
                drawFootprint(guiGraphics, slot);
            }
        }
        for (Slot slot : this.menu.slots) {
            if (isFootprintOverlaySlot(slot)) {
                renderFootprintItem(guiGraphics, slot);
            }
        }
        guiGraphics.pose().popPose();
    }

    private boolean isFootprintOverlaySlot(Slot slot) {
        return (isRaidInventorySlot(slot) && isGridDisplaySlot(slot)) || isContainerSlot(slot);
    }

    private void renderFootprintHover(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (splitDialog != null) {
            return;
        }
        if (contextMenu != null) {
            return;
        }
        if (dragSource != DragSource.NONE) {
            return;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot != null && isContainerSlot(slot)) {
            if (hiddenRevealAtSlot(slot) != null) {
                return;
            }
            Slot owner = containerOwnerSlotForCell(slot);
            if (owner != null) {
                renderOwnerHover(guiGraphics, owner);
            }
            return;
        }
        if (slot == null || !slot.hasItem()) {
            return;
        }
        if (isWeaponSlot(slot)) {
            return;
        }
        if (isRaidInventorySlot(slot)) {
            Slot owner = ownerSlotFor(slot, this.menu.raidSlotForMenuSlot(slot.index), this.menu.raidItemIndexForMenuSlot(slot.index));
            renderOwnerHover(guiGraphics, owner == null ? slot : owner);
            return;
        }
    }

    private Slot ownerSlotFor(Slot clickedSlot, RaidEquipmentSlot section, int ownerIndex) {
        if (section == null || ownerIndex < 0) {
            return clickedSlot;
        }
        for (Slot slot : this.menu.slots) {
            if (!slot.hasItem() || !isRaidInventorySlot(slot) || this.menu.raidSlotForMenuSlot(slot.index) != section) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (metadata.present() && metadata.sourceIndex() == ownerIndex && metadata.anchor()) {
                return slot;
            }
        }
        return clickedSlot;
    }

    private Slot containerOwnerSlotForCell(Slot clickedSlot) {
        if (clickedSlot == null || !isContainerSlot(clickedSlot)) {
            return clickedSlot;
        }
        if (clickedSlot.hasItem()) {
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(clickedSlot.getItem());
            if (!metadata.present() || metadata.anchor()) {
                return clickedSlot;
            }
        }

        int containerSlot = clickedSlot.index - this.menu.containerMenuSlotStart();
        int cellX = containerSlot % ActiveLootContainerMenu.CONTAINER_COLUMNS;
        int cellY = containerSlot / ActiveLootContainerMenu.CONTAINER_COLUMNS;
        for (Slot slot : this.menu.slots) {
            if (!isContainerSlot(slot) || !slot.hasItem()) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || !metadata.anchor()) {
                continue;
            }
            boolean inside = cellX >= metadata.gridX()
                    && cellY >= metadata.gridY()
                    && cellX < metadata.gridX() + metadata.footprintWidth()
                    && cellY < metadata.gridY() + metadata.footprintHeight();
            if (inside && hiddenRevealAtSlot(slot) == null) {
                return slot;
            }
        }
        return clickedSlot.hasItem() ? clickedSlot : null;
    }

    private void renderOwnerHover(GuiGraphics guiGraphics, Slot ownerSlot) {
        if (ownerSlot == null || !ownerSlot.hasItem()) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(ownerSlot.getItem());
        int width = metadata.present() ? metadata.footprintWidth() * SLOT_STEP : SLOT_STEP;
        int height = metadata.present() ? metadata.footprintHeight() * SLOT_STEP : SLOT_STEP;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        guiGraphics.fill(ownerSlot.x, ownerSlot.y, ownerSlot.x + width - 2, ownerSlot.y + height - 2, 0x3349D8E8);
        border(guiGraphics, ownerSlot.x - 1, ownerSlot.y - 1, width, height, 0xCC9CF6FF);
        guiGraphics.pose().popPose();
    }

    private void renderDragPreview(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (dragSource == DragSource.NONE || draggedStack.isEmpty()) {
            return;
        }
        RaidEquipmentSlot target = targetAt(mouseX, mouseY);
        if (target == null || target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return;
        }
        Footprint footprint = footprintFor(draggedStack);
        Placement placement = placementAt(target, mouseX, mouseY, footprint);
        if (!placement.inGrid()) {
            return;
        }
        int localX = placement.layout().x() + placement.x() * SLOT_STEP;
        int localY = placement.layout().y() + placement.y() * SLOT_STEP;
        boolean fits = placement.valid() && previewFits(target, placement.x(), placement.y(), footprint);
        int color = fits ? 0xAA62F3E8 : 0xAAFF5D5D;
        logPreview(target, placement.x(), placement.y(), footprint, fits);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        int width = footprint.width() * SLOT_STEP;
        int height = footprint.height() * SLOT_STEP;
        guiGraphics.fill(localX, localY, localX + width - 2, localY + height - 2, fits ? 0x3349D8E8 : 0x33FF5D5D);
        border(guiGraphics, localX - 1, localY - 1, width, height, color);
        guiGraphics.pose().popPose();
    }

    private boolean previewFits(RaidEquipmentSlot target, int x, int y, Footprint footprint) {
        GridLayout layout = layoutFor(target);
        if (x < 0 || y < 0 || x + footprint.width() > layout.columns() || y + footprint.height() > layout.rows()) {
            return false;
        }
        for (Slot slot : this.menu.slots) {
            if (!isRaidInventorySlot(slot) || isWeaponSlot(slot) || !slot.hasItem()) {
                continue;
            }
            if (draggedSourceMenuSlots.contains(slot.index)) {
                continue;
            }
            if (this.menu.raidSlotForMenuSlot(slot.index) != target) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || !metadata.anchor()) {
                continue;
            }
            if (dragSource == DragSource.RAID_INVENTORY && draggedRaidSlot == target && draggedFootprintMatches(metadata)) {
                continue;
            }
            if (dragSource == DragSource.RAID_INVENTORY && draggedRaidSlot == target && metadata.sourceIndex() == draggedSourceIndex) {
                continue;
            }
            if (overlaps(x, y, footprint.width(), footprint.height(), metadata.gridX(), metadata.gridY(), metadata.footprintWidth(), metadata.footprintHeight())) {
                return false;
            }
        }
        return true;
    }

    private void logPreview(RaidEquipmentSlot target, int cellX, int cellY, Footprint footprint, boolean fits) {
        String key = target + ":" + cellX + ":" + cellY + ":" + footprint.width() + "x" + footprint.height() + ":" + fits;
        if (key.equals(lastPreviewLogKey)) {
            return;
        }
        lastPreviewLogKey = key;
        ExtractCraft.LOGGER.info("Raid grid preview: target={}, targetCell=({},{}), heldFootprint={}x{}, fits={}, source={}#{}",
                target,
                cellX,
                cellY,
                footprint.width(),
                footprint.height(),
                fits,
                dragSource,
                draggedSourceIndex);
    }

    private boolean draggedFootprintMatches(GridDisplayMetadata.Metadata metadata) {
        GridDisplayMetadata.Metadata dragged = GridDisplayMetadata.read(draggedStack);
        return dragged.present()
                && metadata.gridX() == dragged.gridX()
                && metadata.gridY() == dragged.gridY()
                && metadata.footprintWidth() == dragged.footprintWidth()
                && metadata.footprintHeight() == dragged.footprintHeight()
                && metadata.rotated() == dragged.rotated();
    }

    private Set<Integer> sourceMenuSlots(DragSource source, int sourceIndex, RaidEquipmentSlot raidSlot) {
        Set<Integer> slots = new HashSet<>();
        if (source == DragSource.CONTAINER) {
            slots.add(this.menu.containerMenuSlotStart() + sourceIndex);
            return slots;
        }
        if (source == DragSource.RAID_INVENTORY && raidSlot != null) {
            for (Slot slot : this.menu.slots) {
                if (isRaidInventorySlot(slot)
                        && this.menu.raidSlotForMenuSlot(slot.index) == raidSlot
                        && this.menu.raidItemIndexForMenuSlot(slot.index) == sourceIndex) {
                    slots.add(slot.index);
                }
            }
        }
        return slots;
    }

    private void tickPendingSource() {
        if (pendingSourceMenuSlots.isEmpty()) {
            return;
        }
        pendingSourceTicks--;
        boolean sourceStillVisible = pendingSourceMenuSlots.stream().anyMatch(this::slotStillHasItem);
        if (sourceStillVisible) {
            pendingSourceObservedPresent = true;
        }
        if (pendingSourceTicks <= 0 || pendingSourceObservedPresent && !sourceStillVisible) {
            pendingSourceMenuSlots.clear();
            pendingSourceTicks = 0;
            pendingSourceObservedPresent = false;
        }
    }

    private boolean slotStillHasItem(int menuSlot) {
        return menuSlot >= 0 && menuSlot < this.menu.slots.size() && this.menu.slots.get(menuSlot).hasItem();
    }

    private static void renderItemCenteredInFootprintInterior(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int width, int height) {
        float scale = Math.min(3.0F, Math.max(1.0F, (Math.min(width, height) - 2) / 16.0F));
        int interiorWidth = Math.max(VANILLA_ITEM_SIZE, width - 2);
        int interiorHeight = Math.max(VANILLA_ITEM_SIZE, height - 2);
        double iconX = x + (interiorWidth - 16.0D * scale) / 2.0D;
        double iconY = y + (interiorHeight - 16.0D * scale) / 2.0D;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX, iconY, 0.0D);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private void renderHeldStack(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Footprint footprint = footprintFor(draggedStack);
        int width = Math.max(SLOT_STEP, footprint.width() * SLOT_STEP);
        int height = Math.max(SLOT_STEP, footprint.height() * SLOT_STEP);
        int x = mouseX - width / 2;
        int y = mouseY - height / 2;
        border(guiGraphics, x - 1, y - 1, width, height, 0xAA62F3E8);
        ManagedGridItemRenderer.renderItemCentered(guiGraphics, draggedStack, x, y, width, height);
        CustomDurabilityBarRenderer.render(guiGraphics, draggedStack, x, y, width, height);
    }

    private ItemStack withResolvedGridMetadata(ItemStack stack, int sourceIndex, RaidEquipmentSlot raidSlot, Slot clickedSlot) {
        if (GridDisplayMetadata.read(stack).present() || sourceIndex < 0 || raidSlot == null) {
            return stack;
        }
        for (Slot slot : this.menu.slots) {
            if (!slot.hasItem() || !isRaidInventorySlot(slot) || this.menu.raidSlotForMenuSlot(slot.index) != raidSlot) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (metadata.present() && metadata.sourceIndex() == sourceIndex) {
                ExtractCraft.LOGGER.info("Raid grid pickup metadata recovered: clickedMenuSlot={}, ownerIndex={}, footprint=({},{} {}x{} rotated={})",
                        clickedSlot == null ? -1 : clickedSlot.index,
                        sourceIndex,
                        metadata.gridX(),
                        metadata.gridY(),
                        metadata.footprintWidth(),
                        metadata.footprintHeight(),
                        metadata.rotated());
                return GridDisplayMetadata.stamp(stack, metadata, metadata.anchor());
            }
        }
        return stack;
    }

    private static Footprint footprintFor(ItemStack stack) {
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(stack);
        if (metadata.present()) {
            return new Footprint(metadata.footprintWidth(), metadata.footprintHeight());
        }
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(stack).orElse(null);
        if (profile == null) {
            return new Footprint(1, 1);
        }
        int width = profile.gridWidth().orElseGet(() -> fallbackGridWidth(profile.category()));
        int height = profile.gridHeight().orElseGet(() -> fallbackGridHeight(profile.category()));
        return new Footprint(width, height);
    }

    private static int fallbackGridWidth(com.chaseschwartz.extractcraft.itemvalues.ItemCategory category) {
        return switch (category) {
            case GUNS -> 2;
            case ARMOR -> 3;
            case TOOLS, WEAPON_PARTS -> 2;
            case MEDICAL -> 2;
            case MAGAZINES, ATTACHMENTS -> 1;
            default -> 1;
        };
    }

    private static int fallbackGridHeight(com.chaseschwartz.extractcraft.itemvalues.ItemCategory category) {
        return switch (category) {
            case GUNS -> 5;
            case ARMOR -> 3;
            case TOOLS, WEAPON_PARTS -> 2;
            case MEDICAL -> 2;
            case MAGAZINES -> 2;
            default -> 1;
        };
    }

    private GridLayout layoutFor(RaidEquipmentSlot slot) {
        return switch (slot) {
            case VEST -> new GridLayout(VEST_GRID_X, VEST_GRID_Y, menu.vestGridWidth(), menu.vestGridHeight());
            case SAFE_BOX -> new GridLayout(SAFE_GRID_X, SAFE_GRID_Y, menu.safeGridWidth(), menu.safeGridHeight());
            default -> new GridLayout(BACKPACK_GRID_X, BACKPACK_GRID_Y, menu.backpackGridWidth(), menu.backpackGridHeight());
        };
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void logLayoutOnce() {
        if (loggedLayout) {
            return;
        }
        loggedLayout = true;
        ExtractCraft.LOGGER.info("ActiveLootContainerScreen layout: mode={}, leftPos={}, topPos={}, backpack={}, vest={}, safeBox={}, container={}, primary={}, secondary={}",
                this.menu.hasWorldContainer() ? "container" : "inventory_only",
                this.leftPos,
                this.topPos,
                firstLastSlot(ActiveLootContainerMenu.BACKPACK_START, ActiveLootContainerMenu.BACKPACK_DISPLAY_SLOTS),
                firstLastSlot(ActiveLootContainerMenu.VEST_START, ActiveLootContainerMenu.VEST_DISPLAY_SLOTS),
                firstLastSlot(ActiveLootContainerMenu.SAFE_BOX_START, ActiveLootContainerMenu.SAFE_BOX_DISPLAY_SLOTS),
                firstLastSlot(this.menu.containerMenuSlotStart(), this.menu.containerSlotCount()),
                slotInfo(ActiveLootContainerMenu.PRIMARY_WEAPON_START),
                slotInfo(ActiveLootContainerMenu.SECONDARY_WEAPON_START));
        logSection("Helmet", ActiveLootContainerMenu.HELMET_START, 1, 1, 1, EQUIPMENT_X, 34);
        logSection("Armor", ActiveLootContainerMenu.ARMOR_START, 1, 1, 1, EQUIPMENT_X, 64);
        logSection("Pack", ActiveLootContainerMenu.EQUIPPED_BACKPACK_START, 1, 1, 1, EQUIPMENT_X, 94);
        logSection("Vest Equip", ActiveLootContainerMenu.EQUIPPED_VEST_START, 1, 1, 1, EQUIPMENT_X, 124);
        logSection("Safe Equip", ActiveLootContainerMenu.EQUIPPED_SAFE_CONTAINER_START, 1, 1, 1, EQUIPMENT_X, 154);
        logSection("Primary", ActiveLootContainerMenu.PRIMARY_WEAPON_START, 1, 1, 1, EQUIPMENT_X, 192);
        logSection("Secondary", ActiveLootContainerMenu.SECONDARY_WEAPON_START, 1, 1, 1, EQUIPMENT_X, 222);
        logSection("Backpack", ActiveLootContainerMenu.BACKPACK_START, ActiveLootContainerMenu.BACKPACK_DISPLAY_SLOTS, menu.backpackGridWidth(), menu.backpackGridHeight(), 98, 24);
        logSection("Vest", ActiveLootContainerMenu.VEST_START, ActiveLootContainerMenu.VEST_DISPLAY_SLOTS, menu.vestGridWidth(), menu.vestGridHeight(), 98, 238);
        logSection("Safe Box", ActiveLootContainerMenu.SAFE_BOX_START, ActiveLootContainerMenu.SAFE_BOX_DISPLAY_SLOTS, menu.safeGridWidth(), menu.safeGridHeight(), 202, 238);
        if (this.menu.hasWorldContainer()) {
            int rows = Math.max(1, (int) Math.ceil(this.menu.containerSlotCount() / (double) ActiveLootContainerMenu.CONTAINER_COLUMNS));
            logSection("Container", this.menu.containerMenuSlotStart(), this.menu.containerSlotCount(), ActiveLootContainerMenu.CONTAINER_COLUMNS, rows, CONTAINER_PANEL_X, CONTAINER_PANEL_Y);
        }
    }

    private String firstLastSlot(int start, int count) {
        if (count <= 0 || start < 0 || start >= this.menu.slots.size()) {
            return "none";
        }
        int last = Math.min(this.menu.slots.size() - 1, start + count - 1);
        return slotInfo(start) + " -> " + slotInfo(last);
    }

    private String slotInfo(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.menu.slots.size()) {
            return "none";
        }
        Slot slot = this.menu.slots.get(slotIndex);
        return slotIndex + "@local(" + slot.x + "," + slot.y + ") screen(" + (this.leftPos + slot.x) + "," + (this.topPos + slot.y) + ")";
    }

    private void logSection(String name, int start, int count, int columns, int rows, int visualOriginX, int visualOriginY) {
        if (count <= 0 || start < 0 || start >= this.menu.slots.size()) {
            ExtractCraft.LOGGER.info("Slot diagnostic {}: count=0/no slots", name);
            return;
        }

        int last = Math.min(this.menu.slots.size() - 1, start + count - 1);
        Slot firstSlot = this.menu.slots.get(start);
        Slot lastSlot = this.menu.slots.get(last);
        boolean equipmentSection = start >= ActiveLootContainerMenu.HELMET_START && start <= ActiveLootContainerMenu.SECONDARY_WEAPON_START;
        int backgroundSize = VANILLA_BACKGROUND_SIZE;
        int innerSize = VANILLA_INNER_SIZE;
        ExtractCraft.LOGGER.info("Slot diagnostic {}: visualOrigin=({},{}), menuOrigin=({},{}), bgSize={}, innerSize={}, hoverSize={}, itemSize={}, columns={}, rows={}, step=({},{}), first={}, last={}",
                name,
                visualOriginX,
                visualOriginY,
                firstSlot.x,
                firstSlot.y,
                backgroundSize,
                innerSize,
                VANILLA_HOVER_SIZE,
                VANILLA_ITEM_SIZE,
                columns,
                rows,
                SLOT_STEP,
                SLOT_STEP,
                slotRect(start, equipmentSection),
                slotRect(last, equipmentSection));
    }

    private String slotRect(int slotIndex, boolean equipmentSlot) {
        Slot slot = this.menu.slots.get(slotIndex);
        int screenX = this.leftPos + slot.x;
        int screenY = this.topPos + slot.y;
        int bgX = screenX - 1;
        int bgY = screenY - 1;
        int bgSize = VANILLA_BACKGROUND_SIZE;
        return slotIndex
                + " local=(" + slot.x + "," + slot.y + ")"
                + " screen=(" + screenX + "," + screenY + ")"
                + " bg=(" + bgX + "," + bgY + "," + bgSize + "x" + bgSize + ")"
                + " hover=(" + screenX + "," + screenY + "," + VANILLA_HOVER_SIZE + "x" + VANILLA_HOVER_SIZE + ")"
                + " item=(" + screenX + "," + screenY + "," + VANILLA_ITEM_SIZE + "x" + VANILLA_ITEM_SIZE + ")";
    }

    private static void border(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private enum DragSource {
        NONE,
        CONTAINER,
        RAID_INVENTORY
    }

    private record Footprint(int width, int height) {
    }

    private record GridLayout(int x, int y, int columns, int rows) {
    }

    private record Placement(GridLayout layout, int x, int y, boolean inGrid, boolean valid) {
        private static Placement invalid(GridLayout layout, int x, int y, boolean inGrid) {
            return new Placement(layout, x, y, inGrid, false);
        }
    }

    private enum ContextAction {
        NONE,
        SPLIT,
        REPAIR,
        DROP
    }

    private record ContextMenu(int x, int y, RaidEquipmentSlot source, int sourceIndex, boolean canSplit, boolean canRepair) {
    }

    private record SplitDialog(boolean containerSource, RaidEquipmentSlot source, int sourceIndex, ItemStack stack, int amount, int maxAmount) {
    }

    private static final class RevealState {
        private final String signature;
        private final ItemRarity rarity;
        private final long revealAtMs;
        private boolean revealed;
        private boolean active;
        private long lastSearchSoundAtMs;

        private RevealState(String signature, ItemRarity rarity, long revealAtMs, boolean revealed) {
            this.signature = signature;
            this.rarity = rarity;
            this.revealAtMs = revealAtMs;
            this.revealed = revealed;
        }

        private static RevealState hidden(String signature, ItemRarity rarity, long revealAtMs) {
            return new RevealState(signature, rarity, revealAtMs, false);
        }

        private static RevealState revealed(String signature, ItemRarity rarity, long now) {
            return new RevealState(signature, rarity, now, true);
        }

        private String signature() {
            return signature;
        }

        private ItemRarity rarity() {
            return rarity;
        }

        private long revealAtMs() {
            return revealAtMs;
        }

        private boolean revealed() {
            return revealed;
        }

        private boolean active() {
            return active;
        }

        private long lastSearchSoundAtMs() {
            return lastSearchSoundAtMs;
        }

        private void reveal() {
            this.revealed = true;
            this.active = false;
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        private void markSearchSound(long now) {
            this.lastSearchSoundAtMs = now;
        }
    }
}
