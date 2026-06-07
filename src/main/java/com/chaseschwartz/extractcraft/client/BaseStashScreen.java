package com.chaseschwartz.extractcraft.client;

import java.util.HashSet;
import java.util.Set;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.durability.PaidRepairService;
import com.chaseschwartz.extractcraft.durability.PaidRepairService.RepairEstimate;
import com.chaseschwartz.extractcraft.network.BulkBaseInventoryActionPayload;
import com.chaseschwartz.extractcraft.network.GridMoveRequestPayload;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashMenu;
import com.chaseschwartz.extractcraft.raid.inventory.GridDisplayMetadata;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfile;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class BaseStashScreen extends AbstractContainerScreen<BaseStashMenu> {
    private static final int PANEL_COLOR = 0xF0101116;
    private static final int SECTION_COLOR = 0xFF1B2029;
    private static final int SLOT_COLOR = 0xFF40444D;
    private static final int BORDER_COLOR = 0xFF49D8E8;
    private static final int HOVER_BORDER = 0xFF9CF6FF;
    private static final int TEXT = 0xFFDFFBFF;
    private static final int MUTED_TEXT = 0xFF9AA6B2;
    private static final int BACKPACK_MAX_COLUMNS = 8;
    private static final int BACKPACK_MAX_ROWS = 8;
    private static final int BACKPACK_GRID_X = 104;
    private static final int BACKPACK_GRID_Y = 62;
    private static final int VEST_GRID_X = 104;
    private static final int VEST_GRID_Y = 256;
    private static final int SAFE_GRID_X = 196;
    private static final int SAFE_GRID_Y = 256;
    private static final int EQUIPMENT_X = 8;
    private static final int EQUIPMENT_Y = 24;
    private static final int STORAGE_X = 98;
    private static final int STASH_X = 276;
    private static final int STASH_Y = 24;
    private static final int STASH_SLOT_X = 286;
    private static final int STASH_SLOT_Y = 82;
    private static final int STASH_PANEL_PADDING = 10;
    private static final int CONTEXT_MENU_WIDTH = 74;
    private static final int CONTEXT_MENU_ROW_HEIGHT = 17;
    private static final int SPLIT_DIALOG_WIDTH = 150;
    private static final int SPLIT_DIALOG_HEIGHT = 106;
    private static final int REPAIR_DIALOG_WIDTH = 190;
    private static final int REPAIR_DIALOG_HEIGHT = 152;
    private static final int CLIENT_CONTEXT_ACTION_SPLIT = 3;
    private static final int MULTI_BUTTON_Y_OFFSET = 20;
    private static final int MULTI_BUTTON_HEIGHT = 16;
    private DragSource dragSource = DragSource.NONE;
    private RaidEquipmentSlot draggedBaseSlot;
    private int draggedSourceIndex = -1;
    private int draggedStashActualIndex = -1;
    private int draggedStashGridX = -1;
    private int draggedStashGridY = -1;
    private int draggedStashFootprintWidth = 1;
    private int draggedStashFootprintHeight = 1;
    private ItemStack draggedStack = ItemStack.EMPTY;
    private final Set<Integer> draggedSourceMenuSlots = new HashSet<>();
    private final Set<Integer> pendingSourceMenuSlots = new HashSet<>();
    private int pendingSourceTicks;
    private boolean pendingSourceObservedPresent;
    private int nextTransactionId = 1;
    private int pendingTransactionId = -1;
    private String gridMoveStatus = "";
    private ContextMenu contextMenu = null;
    private SplitDialog splitDialog = null;
    private RepairDialog repairDialog = null;
    private String splitInput = "";
    private boolean splitInputFocused = false;
    private boolean splitSliderDragging = false;
    private boolean multiSelectMode = false;
    private final Set<SelectionKey> selectedItems = new HashSet<>();
    private double dragStartX;
    private double dragStartY;
    private String lastPreviewLogKey = "";

    public BaseStashScreen(BaseStashMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = Math.max(500, STASH_SLOT_X + menu.stashColumns() * 18 + STASH_PANEL_PADDING + 8);
        this.imageHeight = Math.max(350, STASH_SLOT_Y + menu.stashRows() * 18 + 44);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderFootprintOverlays(guiGraphics);
        renderDragPreview(guiGraphics, mouseX, mouseY);
        renderFootprintHover(guiGraphics, mouseX, mouseY);
        if (!draggedStack.isEmpty()) {
            renderHeldStack(guiGraphics, mouseX, mouseY);
        }
        renderMultiSelectOverlays(guiGraphics);
        renderContextMenu(guiGraphics, mouseX, mouseY);
        renderSplitDialog(guiGraphics, mouseX, mouseY);
        renderRepairDialog(guiGraphics, mouseX, mouseY);
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
        section(guiGraphics, x + STORAGE_X, y + 24, 164, 210, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.BACKPACK);
        section(guiGraphics, x + STORAGE_X, y + 238, 82, 82, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.VEST);
        section(guiGraphics, x + STORAGE_X + 90, y + 238, 82, 82, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SAFE_BOX);
        section(guiGraphics, x + STASH_X, y + STASH_Y, this.imageWidth - STASH_X - 8, this.imageHeight - STASH_Y - 8, dragging && isStashPanel(mouseX, mouseY));
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (isBaseSlot(slot) || isStashSlot(slot)) {
            if (isBaseSlot(slot) && !isGridDisplaySlot(slot)) {
                drawEquipmentSlotBackground(guiGraphics, slot.x, slot.y);
            } else {
                drawVanillaSlotBackground(guiGraphics, slot.x, slot.y);
            }
            if (isDragSourceSlot(slot)) {
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
        if ((isBaseSlot(slot) || isStashSlot(slot)) && slot.hasItem() && !isGridShadowSlot(slot) && !isGridAnchorFootprintSlot(slot)) {
            CustomDurabilityBarRenderer.render(guiGraphics, slot.getItem(), slot.x, slot.y, 18, 18);
        }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (splitDialog != null) {
            return;
        }
        if (repairDialog != null) {
            return;
        }
        if (contextMenu != null) {
            return;
        }
        if ((isBaseSlot(slot) && isGridDisplaySlot(slot)) || isStashSlot(slot)) {
            return;
        }
        super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (splitDialog != null) {
            return;
        }
        if (repairDialog != null) {
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
        guiGraphics.drawString(this.font, "Base Inventory", 8, 8, TEXT, false);
        String emeralds = formatEmeralds(menu.credits());
        int emeraldTextX = this.imageWidth - 8 - this.font.width(emeralds);
        guiGraphics.drawString(this.font, emeralds, emeraldTextX, 8, TEXT, false);
        guiGraphics.renderItem(new ItemStack(Items.EMERALD), emeraldTextX - 19, 4);
        guiGraphics.drawString(this.font, "Persistent Stash", STASH_X + 8, STASH_Y + 7, TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("Level %d | %d/%d slots",
                        menu.stashLevel(),
                        menu.stashUsedCapacity(),
                        menu.stashMaxCapacity()),
                STASH_X + 8,
                STASH_Y + 19,
                MUTED_TEXT,
                false);
        guiGraphics.drawString(this.font,
                String.format("Value: %d cr | Sort: %s", menu.totalValue(), menu.sortModeName()),
                STASH_X + 8,
                STASH_Y + 31,
                MUTED_TEXT,
                false);

        guiGraphics.drawString(this.font, "Equipment", 14, 29, TEXT, false);
        guiGraphics.drawString(this.font, "Helmet", 42, 42, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Armor", 42, 72, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Pack", 42, 102, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Vest", 42, 132, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Safe", 42, 162, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Primary", 42, 200, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Second", 42, 230, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Backpack", 104, 29, TEXT, false);
        guiGraphics.drawString(this.font, "Vest", 104, 243, TEXT, false);
        guiGraphics.drawString(this.font, "Safe Box", 196, 243, TEXT, false);

        int sortY = this.imageHeight - 18;
        guiGraphics.drawString(this.font, "[Name]", STASH_X + 8, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Value]", STASH_X + 50, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Wt]", STASH_X + 96, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Cat]", STASH_X + 134, sortY, MUTED_TEXT, false);
        renderMultiSelectButtons(guiGraphics, mouseX, mouseY);
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

        if (repairDialog != null) {
            if (button == 0 && handleRepairDialogClick(mouseX, mouseY)) {
                return true;
            }
            repairDialog = null;
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

        if (button == 0 && handleMultiSelectClick(mouseX, mouseY)) {
            return true;
        }

        if (button == 0 && !this.menu.getCarried().isEmpty()) {
            return tryPlaceCarriedStack(mouseX, mouseY);
        }

        if (button == 0 && multiSelectMode) {
            Slot slot = slotAt(mouseX, mouseY);
            if (toggleSelection(slot)) {
                return true;
            }
            return true;
        }

        if (button == 1) {
            Slot slot = slotAt(mouseX, mouseY);
            if (slot != null && slot.hasItem()) {
                if (isBaseSlot(slot)) {
                    RaidEquipmentSlot source = this.menu.baseSlotForMenuSlot(slot.index);
                    int sourceIndex = this.menu.baseItemIndexForMenuSlot(slot.index);
                    if (sourceIndex >= 0) {
                        if (hasShiftDown()) {
                            sendSplit(false, source, sourceIndex, halfSplitAmount(slot.getItem()));
                        } else {
                            contextMenu = new ContextMenu((int) mouseX, (int) mouseY, false, source, sourceIndex, canSplit(slot.getItem()), canRepair(slot.getItem()));
                        }
                    }
                    return true;
                }
                if (isStashSlot(slot)) {
                    int sourceIndex = stashOwnerDisplayIndex(slot);
                    if (hasShiftDown()) {
                        sendSplit(true, RaidEquipmentSlot.BACKPACK, sourceIndex, halfSplitAmount(slot.getItem()));
                    } else {
                        contextMenu = new ContextMenu((int) mouseX, (int) mouseY, true, RaidEquipmentSlot.BACKPACK, sourceIndex, canSplit(slot.getItem()), canRepair(slot.getItem()));
                    }
                    return true;
                }
            }
        }

        if (button == 0 && handleSortClick(mouseX, mouseY)) {
            return true;
        }

        Slot shiftSlot = slotAt(mouseX, mouseY);
        if (button == 0 && hasShiftDown() && shiftSlot != null && shiftSlot.hasItem()) {
            if (isStashSlot(shiftSlot)) {
                int sourceIndex = this.menu.stashDisplayIndexForMenuSlot(shiftSlot.index);
                sendQuickStashToBase(sourceIndex);
                markPendingSlots(sourceMenuSlots(DragSource.STASH, sourceIndex, null));
                return true;
            }
            if (isBaseSlot(shiftSlot)) {
                RaidEquipmentSlot source = this.menu.baseSlotForMenuSlot(shiftSlot.index);
                int sourceIndex = this.menu.baseItemIndexForMenuSlot(shiftSlot.index);
                if (sourceIndex >= 0) {
                    sendBaseToStash(source, sourceIndex);
                    markPendingSlots(sourceMenuSlots(DragSource.BASE, sourceIndex, source));
                }
                return true;
            }
        }

        if (button == 0 && dragSource != DragSource.NONE) {
            if (tryPlaceHeldStack(mouseX, mouseY)) {
                clearDrag();
            }
            return true;
        }

        Slot slot = slotAt(mouseX, mouseY);
        if (button == 0 && slot != null && slot.hasItem()) {
            if (isBaseSlot(slot)) {
                RaidEquipmentSlot source = this.menu.baseSlotForMenuSlot(slot.index);
                int sourceIndex = this.menu.baseItemIndexForMenuSlot(slot.index);
                if (sourceIndex < 0) {
                    return true;
                }
                startDrag(DragSource.BASE, sourceIndex, source, slot.getItem(), slot, mouseX, mouseY);
                GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(draggedStack);
                com.chaseschwartz.extractcraft.ExtractCraft.LOGGER.info("Base grid click pickup: section={}, menuSlot={}, clickedCell=({},{}), ownerIndex={}, key={}, ownerFootprint=({},{} {}x{} rotated={}), heldFootprint=({}x{}), mouse=({},{}), cell={}",
                        source,
                        slot.index,
                        source == null ? -1 : cellX(source, targetCellAt(source, (int) mouseX, (int) mouseY)),
                        source == null ? -1 : cellY(source, targetCellAt(source, (int) mouseX, (int) mouseY)),
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
                        targetCellAt(source, (int) mouseX, (int) mouseY));
                return true;
            }
            if (isStashSlot(slot)) {
                int sourceIndex = stashOwnerDisplayIndex(slot);
                startDrag(DragSource.STASH, sourceIndex, null, slot.getItem(), slot, mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (splitDialog != null) {
            splitSliderDragging = false;
            return true;
        }
        if (repairDialog != null) {
            return true;
        }
        if (button == 0 && dragSource != DragSource.NONE) {
            if (isClickRelease(mouseX, mouseY)) {
                return true;
            }
            tryPlaceHeldStack(mouseX, mouseY);
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
        if (repairDialog != null) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return splitDialog != null || repairDialog != null || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (repairDialog != null) {
            if (keyCode == 256) {
                repairDialog = null;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmRepairDialog();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean tryPlaceHeldStack(double mouseX, double mouseY) {
        RaidEquipmentSlot baseTarget = targetAt((int) mouseX, (int) mouseY);
        if (dragSource == DragSource.STASH && baseTarget != null) {
            int targetCell = placementCellAt(baseTarget, (int) mouseX, (int) mouseY, footprintFor(draggedStack));
            logDropTarget("stash-to-base", mouseX, mouseY, baseTarget, targetCell);
            if (isGridTarget(baseTarget) && targetCell < 0) {
                gridMoveStatus = "Target cell is blocked.";
                return false;
            }
            sendStashToBase(draggedSourceIndex, baseTarget, targetCell);
            markPendingSource();
            return true;
        }
        if (dragSource == DragSource.BASE && isStashPanel((int) mouseX, (int) mouseY)) {
            int targetCell = stashPlacementCellAt((int) mouseX, (int) mouseY, footprintFor(draggedStack));
            if (targetCell < 0) {
                gridMoveStatus = "Stash target is blocked.";
                return false;
            }
            sendBaseToStash(draggedBaseSlot, draggedSourceIndex, targetCell);
            markPendingSource();
            return true;
        }
        if (dragSource == DragSource.STASH && isStashPanel((int) mouseX, (int) mouseY)) {
            int targetCell = stashPlacementCellAt((int) mouseX, (int) mouseY, footprintFor(draggedStack));
            if (targetCell < 0) {
                gridMoveStatus = "Stash target is blocked.";
                return false;
            }
            sendStashToStash(draggedSourceIndex, targetCell);
            markPendingSource();
            return true;
        }
        if (dragSource == DragSource.BASE && baseTarget != null && baseTarget != draggedBaseSlot) {
            int targetCell = placementCellAt(baseTarget, (int) mouseX, (int) mouseY, footprintFor(draggedStack));
            logDropTarget("base-to-base", mouseX, mouseY, baseTarget, targetCell);
            if (isGridTarget(baseTarget) && targetCell < 0) {
                gridMoveStatus = "Target cell is blocked.";
                return false;
            }
            sendBaseToBase(draggedBaseSlot, draggedSourceIndex, baseTarget, targetCell);
            markPendingSource();
            return true;
        }
        if (dragSource == DragSource.BASE && baseTarget != null && baseTarget == draggedBaseSlot) {
            int targetCell = placementCellAt(baseTarget, (int) mouseX, (int) mouseY, footprintFor(draggedStack));
            if (targetCell >= 0) {
                logDropTarget("base-reposition", mouseX, mouseY, baseTarget, targetCell);
                sendBaseToBase(draggedBaseSlot, draggedSourceIndex, baseTarget, targetCell);
                markPendingSource();
            }
            return true;
        }
        if (isOutsideScreen(mouseX, mouseY)) {
            if (dragSource == DragSource.BASE) {
                sendDropBase(draggedBaseSlot, draggedSourceIndex);
                markPendingSource();
                return true;
            }
            if (dragSource == DragSource.STASH) {
                sendDropStash(draggedSourceIndex);
                markPendingSource();
                return true;
            }
        }
        return false;
    }

    private boolean handleSortClick(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        int sortY = this.imageHeight - 18;
        if (localY < sortY || localY > sortY + 10 || localX < STASH_X + 8 || localX > STASH_X + 176) {
            return false;
        }
        if (localX < STASH_X + 48) {
            sendSort(0);
        } else if (localX < STASH_X + 94) {
            sendSort(1);
        } else if (localX < STASH_X + 132) {
            sendSort(2);
        } else {
            sendSort(3);
        }
        return true;
    }

    private void startDrag(DragSource source, int sourceIndex, RaidEquipmentSlot baseSlot, ItemStack stack, Slot clickedSlot, double mouseX, double mouseY) {
        this.dragSource = source;
        this.draggedSourceIndex = sourceIndex;
        this.draggedStashActualIndex = source == DragSource.STASH ? this.menu.stashActualIndexForDisplayIndex(sourceIndex) : -1;
        this.draggedStashGridX = -1;
        this.draggedStashGridY = -1;
        this.draggedStashFootprintWidth = 1;
        this.draggedStashFootprintHeight = 1;
        if (source == DragSource.STASH) {
            Slot owner = ownerSlotForManagedGrid(clickedSlot);
            GridDisplayMetadata.Metadata metadata = owner == null ? GridDisplayMetadata.Metadata.EMPTY : GridDisplayMetadata.read(owner.getItem());
            if (metadata.present()) {
                this.draggedStashGridX = metadata.gridX();
                this.draggedStashGridY = metadata.gridY();
                this.draggedStashFootprintWidth = metadata.footprintWidth();
                this.draggedStashFootprintHeight = metadata.footprintHeight();
                this.draggedStack = GridDisplayMetadata.stamp(stack.copy(), metadata, true);
            } else {
                this.draggedStack = stack.copy();
            }
        } else {
            this.draggedStack = withResolvedGridMetadata(stack.copy(), sourceIndex, baseSlot, clickedSlot);
        }
        this.draggedBaseSlot = baseSlot;
        this.draggedSourceMenuSlots.clear();
        this.draggedSourceMenuSlots.addAll(sourceMenuSlots(source, sourceIndex, baseSlot));
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
    }

    private void clearDrag() {
        this.dragSource = DragSource.NONE;
        this.draggedSourceIndex = -1;
        this.draggedStashActualIndex = -1;
        this.draggedStashGridX = -1;
        this.draggedStashGridY = -1;
        this.draggedStashFootprintWidth = 1;
        this.draggedStashFootprintHeight = 1;
        this.draggedBaseSlot = null;
        this.draggedStack = ItemStack.EMPTY;
        this.draggedSourceMenuSlots.clear();
        this.lastPreviewLogKey = "";
    }

    private void markPendingSource() {
        markPendingSlots(this.draggedSourceMenuSlots);
    }

    private void markPendingSlots(Set<Integer> menuSlots) {
        this.pendingSourceMenuSlots.clear();
        this.pendingSourceMenuSlots.addAll(menuSlots);
        this.pendingSourceTicks = 20;
        this.pendingSourceObservedPresent = true;
    }

    private boolean isClickRelease(double mouseX, double mouseY) {
        return Math.abs(mouseX - dragStartX) <= 3.0D && Math.abs(mouseY - dragStartY) <= 3.0D;
    }

    private void sendBaseToStash(RaidEquipmentSlot source, int sourceIndex) {
        sendBaseToStash(source, sourceIndex, -1);
    }

    private void sendBaseToStash(RaidEquipmentSlot source, int sourceIndex, int targetCell) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            sendGridMove(GridMoveRequestPayload.BASE_BASE_TO_STASH, source, sourceIndex, null, targetCell);
        }
    }

    private void sendStashToStash(int stashDisplayIndex, int targetCell) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            sendGridMove(GridMoveRequestPayload.BASE_STASH_TO_STASH_CELL, null, stashDisplayIndex, null, targetCell);
        }
    }

    private void sendStashToBase(int stashDisplayIndex, RaidEquipmentSlot target) {
        sendStashToBase(stashDisplayIndex, target, -1);
    }

    private void sendStashToBase(int stashDisplayIndex, RaidEquipmentSlot target, int targetCell) {
        if (this.minecraft != null && this.minecraft.gameMode != null && target != null) {
            if (targetCell >= 0) {
                sendGridMove(GridMoveRequestPayload.BASE_STASH_TO_BASE_CELL, null, stashDisplayIndex, target, targetCell);
                return;
            }
            int buttonId = targetCell >= 0
                    ? BaseStashMenu.stashToBaseCellButtonId(stashDisplayIndex, target, targetCell)
                    : BaseStashMenu.stashToBaseButtonId(stashDisplayIndex, target);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void sendBaseToBase(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        sendBaseToBase(source, sourceIndex, target, -1);
    }

    private void sendBaseToBase(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int targetCell) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null && target != null) {
            if (targetCell >= 0) {
                sendGridMove(GridMoveRequestPayload.BASE_BASE_TO_BASE_CELL, source, sourceIndex, target, targetCell);
                return;
            }
            int buttonId = targetCell >= 0
                    ? BaseStashMenu.baseToBaseCellButtonId(source, sourceIndex, target, targetCell)
                    : BaseStashMenu.baseToBaseButtonId(source, sourceIndex, target);
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void sendGridMove(int operation, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int targetCell) {
        int transactionId = nextTransactionId++;
        pendingTransactionId = transactionId;
        gridMoveStatus = "";
        ExtractCraft.LOGGER.info("Base grid move request sent: tx={}, menu={}, op={}, source={}#{}, target={}, cell={}",
                transactionId,
                this.menu.containerId,
                operation,
                source,
                sourceIndex,
                target,
                targetCell);
        PacketDistributor.sendToServer(new GridMoveRequestPayload(transactionId, this.menu.containerId, operation, slotId(source), sourceIndex, slotId(target), targetCell));
    }

    private void sendSplit(boolean stashSource, RaidEquipmentSlot source, int sourceIndex, int amount) {
        if (amount <= 0 || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        int operation = stashSource ? GridMoveRequestPayload.BASE_STASH_SPLIT : GridMoveRequestPayload.BASE_BASE_SPLIT;
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
            sendGridMove(GridMoveRequestPayload.BASE_CARRIED_TO_BASE_CELL, null, -1, target, targetCell);
            return true;
        }
        if (isStashGrid((int) mouseX, (int) mouseY)) {
            int targetCell = stashPlacementCellAt((int) mouseX, (int) mouseY, footprintFor(carried));
            if (targetCell < 0) {
                gridMoveStatus = "Stash target is blocked.";
                return true;
            }
            sendGridMove(GridMoveRequestPayload.BASE_CARRIED_TO_STASH_CELL, null, -1, null, targetCell);
            return true;
        }
        return true;
    }

    private void sendDropBase(RaidEquipmentSlot source, int sourceIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            sendGridMove(GridMoveRequestPayload.BASE_BASE_DROP, source, sourceIndex, null, -1);
        }
    }

    private void sendDropStash(int stashDisplayIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            sendGridMove(GridMoveRequestPayload.BASE_STASH_DROP, null, stashDisplayIndex, null, -1);
        }
    }

    private void sendQuickStashToBase(int stashDisplayIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            sendGridMove(GridMoveRequestPayload.BASE_STASH_QUICK_TO_BASE, null, stashDisplayIndex, null, -1);
        }
    }

    public void handleGridMoveResult(int transactionId, boolean success, String message) {
        if (pendingTransactionId != transactionId) {
            ExtractCraft.LOGGER.info("Base grid move result ignored: tx={}, pendingTx={}, success={}, message={}", transactionId, pendingTransactionId, success, message);
            return;
        }
        ExtractCraft.LOGGER.info("Base grid move result applied: tx={}, success={}, message={}, pendingSlots={}", transactionId, success, message, pendingSourceMenuSlots);
        pendingTransactionId = -1;
        gridMoveStatus = success ? "" : message;
        if (!success) {
            pendingSourceMenuSlots.clear();
            pendingSourceTicks = 0;
            pendingSourceObservedPresent = false;
        }
    }

    private void sendSort(int sortId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.sortButtonId(sortId));
        }
    }

    private void sendContextAction(int action, ContextMenu menu) {
        sendContextAction(action, menu.stashSource(), menu.source(), menu.sourceIndex());
    }

    private void sendContextAction(int action, boolean stashSource, RaidEquipmentSlot source, int sourceIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.contextActionButtonId(action, stashSource, source, sourceIndex));
        }
    }

    private void sendBulkAction(int action) {
        if (selectedItems.isEmpty() || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        int[] stashIndexes = selectedItems.stream()
                .filter(SelectionKey::stashSource)
                .mapToInt(SelectionKey::sourceIndex)
                .toArray();
        SelectionKey[] baseKeys = selectedItems.stream()
                .filter(key -> !key.stashSource())
                .toArray(SelectionKey[]::new);
        int[] baseSlotIds = new int[baseKeys.length];
        int[] baseIndexes = new int[baseKeys.length];
        for (int i = 0; i < baseKeys.length; i++) {
            baseSlotIds[i] = slotId(baseKeys[i].source());
            baseIndexes[i] = baseKeys[i].sourceIndex();
        }
        int transactionId = nextTransactionId++;
        pendingTransactionId = transactionId;
        gridMoveStatus = "";
        PacketDistributor.sendToServer(new BulkBaseInventoryActionPayload(transactionId, this.menu.containerId, action, stashIndexes, baseSlotIds, baseIndexes));
        selectedItems.clear();
    }

    private boolean handleMultiSelectClick(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        int y = multiButtonY();
        if (inside(localX, localY, 8, y, 82, MULTI_BUTTON_HEIGHT)) {
            multiSelectMode = !multiSelectMode;
            selectedItems.clear();
            contextMenu = null;
            splitDialog = null;
            repairDialog = null;
            return true;
        }
        if (!multiSelectMode) {
            return false;
        }
        if (inside(localX, localY, 96, y, 48, MULTI_BUTTON_HEIGHT)) {
            sendBulkAction(BulkBaseInventoryActionPayload.ACTION_SELL);
            return true;
        }
        if (inside(localX, localY, 148, y, 48, MULTI_BUTTON_HEIGHT)) {
            sendBulkAction(BulkBaseInventoryActionPayload.ACTION_DROP);
            return true;
        }
        if (inside(localX, localY, 200, y, 48, MULTI_BUTTON_HEIGHT)) {
            sendBulkAction(BulkBaseInventoryActionPayload.ACTION_TRASH);
            return true;
        }
        if (inside(localX, localY, 252, y, 48, MULTI_BUTTON_HEIGHT)) {
            selectedItems.clear();
            return true;
        }
        return false;
    }

    private void renderMultiSelectButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = multiButtonY();
        renderButton(guiGraphics, 8, y, 82, MULTI_BUTTON_HEIGHT, multiSelectMode ? "Selecting" : "Multi-Select", multiSelectMode, localHover(mouseX, mouseY, 8, y, 82, MULTI_BUTTON_HEIGHT));
        if (!multiSelectMode) {
            return;
        }
        boolean hasSelection = !selectedItems.isEmpty();
        renderButton(guiGraphics, 96, y, 48, MULTI_BUTTON_HEIGHT, "Sell", hasSelection, localHover(mouseX, mouseY, 96, y, 48, MULTI_BUTTON_HEIGHT));
        renderButton(guiGraphics, 148, y, 48, MULTI_BUTTON_HEIGHT, "Drop", hasSelection, localHover(mouseX, mouseY, 148, y, 48, MULTI_BUTTON_HEIGHT));
        renderButton(guiGraphics, 200, y, 48, MULTI_BUTTON_HEIGHT, "Trash", hasSelection, localHover(mouseX, mouseY, 200, y, 48, MULTI_BUTTON_HEIGHT));
        renderButton(guiGraphics, 252, y, 48, MULTI_BUTTON_HEIGHT, "Clear", hasSelection, localHover(mouseX, mouseY, 252, y, 48, MULTI_BUTTON_HEIGHT));
        guiGraphics.drawString(this.font, selectedItems.size() + " selected", 306, y + 4, MUTED_TEXT, false);
    }

    private void renderButton(GuiGraphics guiGraphics, int x, int y, int width, int height, String label, boolean enabled, boolean hovered) {
        int fill = enabled ? (hovered ? 0xFF2B5360 : 0xFF243640) : 0xFF1B2026;
        int border = enabled ? (hovered ? HOVER_BORDER : BORDER_COLOR) : 0x5549D8E8;
        guiGraphics.fill(x, y, x + width, y + height, fill);
        border(guiGraphics, x, y, width, height, border);
        int color = enabled ? TEXT : 0xFF68727C;
        guiGraphics.drawString(this.font, label, x + Math.max(4, (width - this.font.width(label)) / 2), y + 5, color, false);
    }

    private boolean localHover(int mouseX, int mouseY, int x, int y, int width, int height) {
        return inside(mouseX - this.leftPos, mouseY - this.topPos, x, y, width, height);
    }

    private int multiButtonY() {
        return this.imageHeight - MULTI_BUTTON_Y_OFFSET;
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
        int action = contextMenuActionForOption(option);
        if (action == CLIENT_CONTEXT_ACTION_SPLIT) {
            openSplitDialog(contextMenu);
            contextMenu = null;
            return true;
        }
        if (action == BaseStashMenu.CONTEXT_ACTION_REPAIR) {
            openRepairDialog(contextMenu);
            contextMenu = null;
            return true;
        }
        if (action == BaseStashMenu.CONTEXT_ACTION_SELL
                || action == BaseStashMenu.CONTEXT_ACTION_DROP
                || action == BaseStashMenu.CONTEXT_ACTION_TRASH) {
            sendContextAction(action, contextMenu);
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
        guiGraphics.fill(x, y, x + CONTEXT_MENU_WIDTH, y + contextMenuHeight(), 0xF0181B22);
        border(guiGraphics, x, y, CONTEXT_MENU_WIDTH, contextMenuHeight(), BORDER_COLOR);
        int row = 0;
        if (contextMenu.canSplit()) {
            renderContextRow(guiGraphics, x, y, row, "Split", hovered == row, TEXT, 0x553A5E66);
            row++;
        }
        if (contextMenu.canRepair()) {
            renderContextRow(guiGraphics, x, y, row, "Repair", hovered == row, 0xFFB8F5C8, 0x55305A3A);
            row++;
        }
        renderContextRow(guiGraphics, x, y, row, "Sell", hovered == row, TEXT, 0x553A5E66);
        row++;
        renderContextRow(guiGraphics, x, y, row, "Drop", hovered == row, TEXT, 0x553A5E66);
        row++;
        renderContextRow(guiGraphics, x, y, row, "Trash", hovered == row, 0xFFFFA0A0, 0x554A2228);
        guiGraphics.pose().popPose();
    }

    private void renderContextRow(GuiGraphics guiGraphics, int menuX, int menuY, int row, String label, boolean hovered, int textColor, int hoverColor) {
        int rowX = menuX + 2;
        int rowY = menuY + 2 + row * CONTEXT_MENU_ROW_HEIGHT;
        if (hovered) {
            guiGraphics.fill(rowX, rowY, rowX + CONTEXT_MENU_WIDTH - 4, rowY + CONTEXT_MENU_ROW_HEIGHT, hoverColor);
        }
        guiGraphics.drawString(this.font, label, rowX + 5, rowY + 5, textColor, false);
    }

    private int contextMenuOptionAt(double mouseX, double mouseY) {
        if (contextMenu == null) {
            return -1;
        }
        int x = contextMenuX() + 2;
        int y = contextMenuY() + 2;
        if (!inside((int) mouseX, (int) mouseY, x, y, CONTEXT_MENU_WIDTH - 4, CONTEXT_MENU_ROW_HEIGHT * contextMenuRows())) {
            return -1;
        }
        int option = ((int) mouseY - y) / CONTEXT_MENU_ROW_HEIGHT;
        return option >= 0 && option < contextMenuRows() ? option : -1;
    }

    private int contextMenuRows() {
        if (contextMenu == null) {
            return 0;
        }
        return 3 + (contextMenu.canSplit() ? 1 : 0) + (contextMenu.canRepair() ? 1 : 0);
    }

    private int contextMenuHeight() {
        return CONTEXT_MENU_ROW_HEIGHT * contextMenuRows() + 4;
    }

    private int contextMenuActionForOption(int option) {
        if (contextMenu == null || option < 0) {
            return -1;
        }
        int row = 0;
        if (contextMenu.canSplit()) {
            if (option == row) {
                return CLIENT_CONTEXT_ACTION_SPLIT;
            }
            row++;
        }
        if (contextMenu.canRepair()) {
            if (option == row) {
                return BaseStashMenu.CONTEXT_ACTION_REPAIR;
            }
            row++;
        }
        if (option == row) {
            return BaseStashMenu.CONTEXT_ACTION_SELL;
        }
        row++;
        if (option == row) {
            return BaseStashMenu.CONTEXT_ACTION_DROP;
        }
        row++;
        return option == row ? BaseStashMenu.CONTEXT_ACTION_TRASH : -1;
    }

    private int contextMenuX() {
        return Math.min(contextMenu.x(), this.leftPos + this.imageWidth - CONTEXT_MENU_WIDTH - 4);
    }

    private int contextMenuY() {
        return Math.min(contextMenu.y(), this.topPos + this.imageHeight - contextMenuHeight() - 4);
    }

    private void openSplitDialog(ContextMenu menu) {
        Slot slot = sourceOwnerSlot(menu);
        if (slot == null || !canSplit(slot.getItem())) {
            return;
        }
        int max = slot.getItem().getCount() - 1;
        int amount = Math.max(1, slot.getItem().getCount() / 2);
        splitDialog = new SplitDialog(menu.stashSource(), menu.source(), menu.sourceIndex(), slot.getItem().copy(), amount, max);
        splitInput = Integer.toString(amount);
    }

    private void openRepairDialog(ContextMenu menu) {
        Slot slot = sourceOwnerSlot(menu);
        if (slot == null || slot.getItem().isEmpty()) {
            return;
        }
        RepairEstimate estimate = PaidRepairService.estimate(slot.getItem()).orElse(null);
        if (estimate == null || !estimate.available() || this.menu.credits() < estimate.cost()) {
            return;
        }
        repairDialog = new RepairDialog(menu.stashSource(), menu.source(), menu.sourceIndex(), slot.getItem().copy(), estimate);
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

    private boolean handleRepairDialogClick(double mouseX, double mouseY) {
        int x = repairDialogX();
        int y = repairDialogY();
        if (!inside((int) mouseX, (int) mouseY, x, y, REPAIR_DIALOG_WIDTH, REPAIR_DIALOG_HEIGHT)) {
            repairDialog = null;
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, x + 16, repairButtonY(), 58, 16)) {
            confirmRepairDialog();
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, x + REPAIR_DIALOG_WIDTH - 74, repairButtonY(), 58, 16)) {
            repairDialog = null;
            return true;
        }
        return true;
    }

    private void renderRepairDialog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (repairDialog == null) {
            return;
        }
        int x = repairDialogX();
        int y = repairDialogY();
        RepairEstimate estimate = repairDialog.estimate();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, 0.0D, 1000.0D);
        guiGraphics.fill(x, y, x + REPAIR_DIALOG_WIDTH, y + REPAIR_DIALOG_HEIGHT, 0xF0181B22);
        border(guiGraphics, x, y, REPAIR_DIALOG_WIDTH, REPAIR_DIALOG_HEIGHT, BORDER_COLOR);
        guiGraphics.renderItem(repairDialog.stack(), x + 10, y + 10);
        guiGraphics.drawString(this.font, "Repair Item", x + 32, y + 10, TEXT, false);
        guiGraphics.drawString(this.font, trimToWidth(repairDialog.stack().getHoverName().getString(), REPAIR_DIALOG_WIDTH - 44), x + 32, y + 22, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Durability: " + estimate.currentDurability() + "/" + estimate.currentMaxDurability(), x + 10, y + 40, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "After: " + estimate.predictedCurrentDurability() + "/" + estimate.predictedCurrentMax(), x + 10, y + 52, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Pristine Max: " + estimate.pristineMaxDurability(), x + 10, y + 64, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Repairs: " + estimate.repairCount(), x + 10, y + 76, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "Cost: " + estimate.cost() + " cr", x + 10, y + 88, 0xFFB8F5C8, false);
        guiGraphics.drawString(this.font, "Warning: max condition", x + 10, y + 102, 0xFFFFD080, false);
        guiGraphics.drawString(this.font, "drops after repair.", x + 10, y + 114, 0xFFFFD080, false);
        renderDialogButton(guiGraphics, x + 16, repairButtonY(), "Confirm", inside(mouseX, mouseY, x + 16, repairButtonY(), 58, 16));
        renderDialogButton(guiGraphics, x + REPAIR_DIALOG_WIDTH - 74, repairButtonY(), "Cancel", inside(mouseX, mouseY, x + REPAIR_DIALOG_WIDTH - 74, repairButtonY(), 58, 16));
        guiGraphics.pose().popPose();
    }

    private String trimToWidth(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, width - this.font.width(ellipsis));
        String trimmed = this.font.plainSubstrByWidth(text, limit);
        return trimmed + ellipsis;
    }

    private int repairDialogX() {
        return this.leftPos + this.imageWidth / 2 - REPAIR_DIALOG_WIDTH / 2;
    }

    private int repairDialogY() {
        return this.topPos + this.imageHeight / 2 - REPAIR_DIALOG_HEIGHT / 2;
    }

    private int repairButtonY() {
        return repairDialogY() + REPAIR_DIALOG_HEIGHT - 24;
    }

    private void confirmRepairDialog() {
        if (repairDialog == null) {
            return;
        }
        sendContextAction(BaseStashMenu.CONTEXT_ACTION_REPAIR, repairDialog.stashSource(), repairDialog.source(), repairDialog.sourceIndex());
        repairDialog = null;
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
        splitDialog = new SplitDialog(splitDialog.stashSource(), splitDialog.source(), splitDialog.sourceIndex(), splitDialog.stack(), clamped, splitDialog.maxAmount());
        splitInput = Integer.toString(clamped);
    }

    private void confirmSplitDialog() {
        if (splitDialog == null) {
            return;
        }
        int amount = parseSplitInput();
        sendSplit(splitDialog.stashSource(), splitDialog.source(), splitDialog.sourceIndex(), amount);
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

    private Slot sourceOwnerSlot(ContextMenu menu) {
        if (menu == null) {
            return null;
        }
        if (menu.stashSource()) {
            int menuSlot = this.menu.stashMenuSlotStart() + menu.sourceIndex();
            return menuSlot >= 0 && menuSlot < this.menu.slots.size() ? ownerSlotForManagedGrid(this.menu.slots.get(menuSlot)) : null;
        }
        int menuSlot = this.menu.baseMenuSlotForItemIndex(menu.source(), menu.sourceIndex());
        return menuSlot >= 0 && menuSlot < this.menu.slots.size() ? ownerSlotForManagedGrid(this.menu.slots.get(menuSlot)) : null;
    }

    private static boolean canSplit(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > 1 && stack.getMaxStackSize() > 1;
    }

    private boolean canRepair(ItemStack stack) {
        RepairEstimate estimate = PaidRepairService.estimate(stack).orElse(null);
        return estimate != null && estimate.available() && this.menu.credits() >= estimate.cost();
    }

    private static int halfSplitAmount(ItemStack stack) {
        return canSplit(stack) ? Math.max(1, stack.getCount() / 2) : 0;
    }

    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            int size = isBaseSlot(slot) || isStashSlot(slot) ? 18 : 16;
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
        if (repairDialog != null) {
            return null;
        }
        if (contextMenu != null || dragSource != DragSource.NONE || !draggedStack.isEmpty()) {
            return null;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null || !slot.hasItem() || isDragSourceSlot(slot)) {
            return null;
        }
        return isManagedGridSlot(slot) ? ownerSlotForManagedGrid(slot) : slot;
    }

    private int targetCellAt(RaidEquipmentSlot target, int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return switch (target) {
            case BACKPACK -> gridCell(localX, localY, BACKPACK_GRID_X, BACKPACK_GRID_Y, menu.backpackGridWidth(), menu.backpackGridHeight());
            case VEST -> gridCell(localX, localY, VEST_GRID_X, VEST_GRID_Y, menu.vestGridWidth(), menu.vestGridHeight());
            case SAFE_BOX -> gridCell(localX, localY, SAFE_GRID_X, SAFE_GRID_Y, menu.safeGridWidth(), menu.safeGridHeight());
            case HELMET, ARMOR, EQUIPPED_BACKPACK, EQUIPPED_VEST, EQUIPPED_SAFE_CONTAINER, PRIMARY_WEAPON, SECONDARY_WEAPON -> -1;
        };
    }

    private int placementCellAt(RaidEquipmentSlot target, int mouseX, int mouseY, Footprint footprint) {
        Placement placement = placementAt(target, mouseX, mouseY, footprint);
        if (!placement.inGrid()) {
            return -1;
        }
        if (!isGridTarget(target)) {
            return -1;
        }
        return placement.valid() ? placement.y() * placement.layout().columns() + placement.x() : -1;
    }

    private int stashPlacementCellAt(int mouseX, int mouseY, Footprint footprint) {
        Placement placement = placementAt(stashLayout(), mouseX, mouseY, footprint);
        if (!placement.inGrid()) {
            return -1;
        }
        return placement.valid() && previewFitsStash(placement.x(), placement.y(), footprint)
                ? placement.y() * placement.layout().columns() + placement.x()
                : -1;
    }

    private Placement placementAt(RaidEquipmentSlot target, int mouseX, int mouseY, Footprint footprint) {
        if (target == null || !isGridTarget(target)) {
            return Placement.invalid(layoutFor(RaidEquipmentSlot.BACKPACK), 0, 0, false);
        }
        return placementAt(layoutFor(target), mouseX, mouseY, footprint);
    }

    private Placement placementAt(GridLayout layout, int mouseX, int mouseY, Footprint footprint) {
        int localX = mouseX - this.leftPos - layout.x();
        int localY = mouseY - this.topPos - layout.y();
        boolean inGrid = localX >= 0 && localY >= 0 && localX < layout.columns() * 18 && localY < layout.rows() * 18;
        double visualTopLeftX = localX - (footprint.width() * 18) / 2.0D;
        double visualTopLeftY = localY - (footprint.height() * 18) / 2.0D;
        int topLeftX = (int) Math.round(visualTopLeftX / 18);
        int topLeftY = (int) Math.round(visualTopLeftY / 18);
        boolean valid = inGrid && topLeftX >= 0 && topLeftY >= 0
                && topLeftX + footprint.width() <= layout.columns()
                && topLeftY + footprint.height() <= layout.rows();
        return new Placement(layout, topLeftX, topLeftY, inGrid, valid);
    }

    private static int gridCell(int localX, int localY, int gridX, int gridY, int columns, int rows) {
        if (!inside(localX, localY, gridX, gridY, columns * 18, rows * 18)) {
            return -1;
        }
        int cellX = (localX - gridX) / 18;
        int cellY = (localY - gridY) / 18;
        return cellY * columns + cellX;
    }

    private boolean isBaseSlot(Slot slot) {
        return this.menu.baseSlotForMenuSlot(slot.index) != null;
    }

    private boolean isStashSlot(Slot slot) {
        return this.menu.isStashMenuSlot(slot.index);
    }

    private int stashSlotActualIndex(Slot slot) {
        if (slot == null || !isStashSlot(slot)) {
            return -1;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        if (metadata.present()) {
            return metadata.sourceIndex();
        }
        return this.menu.stashActualIndexForDisplayIndex(this.menu.stashDisplayIndexForMenuSlot(slot.index));
    }

    private int stashOwnerDisplayIndex(Slot slot) {
        int actualIndex = stashSlotActualIndex(slot);
        if (actualIndex < 0) {
            return this.menu.stashDisplayIndexForMenuSlot(slot.index);
        }
        for (Slot candidate : this.menu.slots) {
            if (!isStashSlot(candidate) || !candidate.hasItem()) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(candidate.getItem());
            if (metadata.present() && metadata.sourceIndex() == actualIndex && metadata.anchor()) {
                return this.menu.stashDisplayIndexForMenuSlot(candidate.index);
            }
        }
        return this.menu.stashDisplayIndexForMenuSlot(slot.index);
    }

    private boolean isWeaponSlot(Slot slot) {
        return slot.index == BaseStashMenu.PRIMARY_WEAPON_START || slot.index == BaseStashMenu.SECONDARY_WEAPON_START;
    }

    private boolean isGridDisplaySlot(Slot slot) {
        return isGridTarget(this.menu.baseSlotForMenuSlot(slot.index));
    }

    private boolean isManagedGridSlot(Slot slot) {
        return (isBaseSlot(slot) && isGridDisplaySlot(slot)) || isStashSlot(slot);
    }

    private boolean isSelectableManagedSlot(Slot slot) {
        if (slot == null) {
            return false;
        }
        if (isStashSlot(slot)) {
            return true;
        }
        if (!isBaseSlot(slot)) {
            return false;
        }
        RaidEquipmentSlot source = this.menu.baseSlotForMenuSlot(slot.index);
        return isGridTarget(source);
    }

    private int managedItemIndexForSlot(Slot slot) {
        if (isStashSlot(slot)) {
            return stashSlotActualIndex(slot);
        }
        return this.menu.baseItemIndexForMenuSlot(slot.index);
    }

    private boolean sameManagedGrid(Slot a, Slot b) {
        if (a == null || b == null) {
            return false;
        }
        if (isStashSlot(a) || isStashSlot(b)) {
            return isStashSlot(a) && isStashSlot(b);
        }
        return isBaseSlot(a)
                && isBaseSlot(b)
                && this.menu.baseSlotForMenuSlot(a.index) == this.menu.baseSlotForMenuSlot(b.index);
    }

    private Slot ownerSlotForManagedGrid(Slot clickedSlot) {
        if (clickedSlot == null || !clickedSlot.hasItem() || !isManagedGridSlot(clickedSlot)) {
            return clickedSlot;
        }

        GridDisplayMetadata.Metadata clickedMetadata = GridDisplayMetadata.read(clickedSlot.getItem());
        int ownerIndex = managedItemIndexForSlot(clickedSlot);
        for (Slot slot : this.menu.slots) {
            if (!slot.hasItem() || !isManagedGridSlot(slot) || !sameManagedGrid(clickedSlot, slot)) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || !metadata.anchor()) {
                continue;
            }
            if (ownerIndex >= 0 && metadata.sourceIndex() == ownerIndex) {
                return slot;
            }
            if (clickedMetadata.present()
                    && metadata.gridX() == clickedMetadata.gridX()
                    && metadata.gridY() == clickedMetadata.gridY()
                    && metadata.footprintWidth() == clickedMetadata.footprintWidth()
                    && metadata.footprintHeight() == clickedMetadata.footprintHeight()) {
                return slot;
            }
        }
        return clickedSlot;
    }

    private boolean isDragSourceSlot(Slot slot) {
        if (pendingSourceMenuSlots.contains(slot.index)) {
            return true;
        }
        if (draggedSourceMenuSlots.contains(slot.index)) {
            return true;
        }
        if (dragSource == DragSource.BASE && draggedBaseSlot != null) {
            if (!isBaseSlot(slot) || this.menu.baseSlotForMenuSlot(slot.index) != draggedBaseSlot) {
                return false;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            return metadata.present()
                    ? metadata.sourceIndex() == draggedSourceIndex
                    : this.menu.baseItemIndexForMenuSlot(slot.index) == draggedSourceIndex;
        }
        if (dragSource == DragSource.STASH) {
            return isStashSlot(slot) && isDraggedStashSlot(slot);
        }
        return false;
    }

    private boolean isDraggedStashSlot(Slot slot) {
        if (slot == null || !isStashSlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        if (!metadata.present()) {
            return stashSlotActualIndex(slot) == draggedStashActualIndex;
        }
        if (metadata.sourceIndex() == draggedStashActualIndex) {
            return true;
        }
        return draggedStashGridX >= 0
                && metadata.gridX() == draggedStashGridX
                && metadata.gridY() == draggedStashGridY
                && metadata.footprintWidth() == draggedStashFootprintWidth
                && metadata.footprintHeight() == draggedStashFootprintHeight;
    }

    private boolean isGridShadowSlot(Slot slot) {
        if (!slot.hasItem() || !isManagedGridSlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && !metadata.anchor();
    }

    private boolean isGridAnchorFootprintSlot(Slot slot) {
        if (!slot.hasItem() || !isManagedGridSlot(slot)) {
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
        com.chaseschwartz.extractcraft.ExtractCraft.LOGGER.info("Base stash grid drop target: action={}, raw=({},{}), gui=({},{}), local=({},{}), source={}#{}, sourceKey={}, target={}, cell={}, cellXY=({},{}), insideBackpack={}, insideVest={}, insideSafe={}, insideStash={}",
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
                isStashPanel((int) mouseX, (int) mouseY));
    }

    private boolean insideBackpackGrid(int localX, int localY) {
        return inside(localX, localY, BACKPACK_GRID_X, BACKPACK_GRID_Y, menu.backpackGridWidth() * 18, menu.backpackGridHeight() * 18);
    }

    private boolean insideVestGrid(int localX, int localY) {
        return inside(localX, localY, VEST_GRID_X, VEST_GRID_Y, menu.vestGridWidth() * 18, menu.vestGridHeight() * 18);
    }

    private boolean insideSafeGrid(int localX, int localY) {
        return inside(localX, localY, SAFE_GRID_X, SAFE_GRID_Y, menu.safeGridWidth() * 18, menu.safeGridHeight() * 18);
    }

    private int cellX(RaidEquipmentSlot target, int cell) {
        return cell % switch (target) {
            case VEST -> Math.max(1, menu.vestGridWidth());
            case SAFE_BOX -> Math.max(1, menu.safeGridWidth());
            default -> Math.max(1, menu.backpackGridWidth());
        };
    }

    private int cellY(RaidEquipmentSlot target, int cell) {
        return cell / switch (target) {
            case VEST -> Math.max(1, menu.vestGridWidth());
            case SAFE_BOX -> Math.max(1, menu.safeGridWidth());
            default -> Math.max(1, menu.backpackGridWidth());
        };
    }

    private static int slotId(RaidEquipmentSlot slot) {
        if (slot == null) {
            return -1;
        }
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

    private boolean isStashPanel(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, STASH_X, STASH_Y, this.imageWidth - STASH_X - 8, this.imageHeight - STASH_Y - 8);
    }

    private boolean isStashGrid(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, STASH_SLOT_X, STASH_SLOT_Y, menu.stashColumns() * 18, menu.stashRows() * 18);
    }

    private boolean isOutsideScreen(double mouseX, double mouseY) {
        return !inside((int) mouseX, (int) mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    private static boolean isGridTarget(RaidEquipmentSlot target) {
        return target == RaidEquipmentSlot.BACKPACK || target == RaidEquipmentSlot.VEST || target == RaidEquipmentSlot.SAFE_BOX;
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

    private void drawFootprint(GuiGraphics guiGraphics, Slot slot) {
        if (!slot.hasItem()) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        if (!metadata.present() || !metadata.anchor() || metadata.footprintWidth() <= 1 && metadata.footprintHeight() <= 1) {
            return;
        }
        int width = metadata.footprintWidth() * 18 - 2;
        int height = metadata.footprintHeight() * 18 - 2;
        guiGraphics.fill(slot.x, slot.y, slot.x + width, slot.y + height, 0x2210151D);
        border(guiGraphics, slot.x - 1, slot.y - 1, width + 2, height + 2, 0x8849D8E8);
    }

    private void renderFootprintItem(GuiGraphics guiGraphics, Slot slot) {
        if (!slot.hasItem() || isDragSourceSlot(slot)) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        if (!metadata.present() || !metadata.anchor() || metadata.footprintWidth() <= 1 && metadata.footprintHeight() <= 1) {
            return;
        }
        int width = metadata.footprintWidth() * 18;
        int height = metadata.footprintHeight() * 18;
        renderItemCentered(guiGraphics, slot.getItem(), slot.x, slot.y, width, height);
        CustomDurabilityBarRenderer.render(guiGraphics, slot.getItem(), slot.x, slot.y, width, height);
    }

    private void renderFootprintOverlays(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        for (Slot slot : this.menu.slots) {
            if (isManagedGridSlot(slot)) {
                drawFootprint(guiGraphics, slot);
            }
        }
        for (Slot slot : this.menu.slots) {
            if (isManagedGridSlot(slot)) {
                renderFootprintItem(guiGraphics, slot);
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderMultiSelectOverlays(GuiGraphics guiGraphics) {
        if (!multiSelectMode) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 700.0F);
        Set<SelectionKey> rendered = new HashSet<>();
        for (Slot slot : this.menu.slots) {
            if (!isSelectableManagedSlot(slot) || !slot.hasItem()) {
                continue;
            }
            Slot owner = ownerSlotForManagedGrid(slot);
            SelectionKey key = selectionKeyForSlot(owner);
            if (key == null || !rendered.add(key)) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(owner.getItem());
            int width = metadata.present() ? metadata.footprintWidth() * 18 : 18;
            int height = metadata.present() ? metadata.footprintHeight() * 18 : 18;
            boolean selected = selectedItems.contains(key);
            int fill = selected ? 0x5549D8E8 : 0x33202730;
            int border = selected ? HOVER_BORDER : 0x8849D8E8;
            guiGraphics.fill(owner.x, owner.y, owner.x + width - 2, owner.y + height - 2, fill);
            border(guiGraphics, owner.x - 1, owner.y - 1, width, height, border);
            int checkboxX = owner.x + 2;
            int checkboxY = owner.y + 2;
            int checkboxSize = 7;
            guiGraphics.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, selected ? 0xFF49D8E8 : 0xDD202632);
            border(guiGraphics, checkboxX, checkboxY, checkboxSize, checkboxSize, selected ? 0xFFFFFFFF : 0xAA9AA6B2);
            if (selected) {
                guiGraphics.fill(checkboxX + 1, checkboxY + 4, checkboxX + 3, checkboxY + 6, 0xFF061016);
                guiGraphics.fill(checkboxX + 3, checkboxY + 5, checkboxX + 5, checkboxY + 6, 0xFF061016);
                guiGraphics.fill(checkboxX + 5, checkboxY + 2, checkboxX + 6, checkboxY + 5, 0xFF061016);
            }
        }
        guiGraphics.pose().popPose();
    }

    private boolean toggleSelection(Slot slot) {
        if (!isSelectableManagedSlot(slot) || !slot.hasItem()) {
            return false;
        }
        Slot owner = ownerSlotForManagedGrid(slot);
        SelectionKey key = selectionKeyForSlot(owner);
        if (key == null) {
            return false;
        }
        if (!selectedItems.add(key)) {
            selectedItems.remove(key);
        }
        return true;
    }

    private SelectionKey selectionKeyForSlot(Slot slot) {
        if (slot == null || !slot.hasItem() || !isSelectableManagedSlot(slot)) {
            return null;
        }
        if (isStashSlot(slot)) {
            int sourceIndex = stashOwnerDisplayIndex(slot);
            return sourceIndex >= 0 ? new SelectionKey(true, null, sourceIndex) : null;
        }
        RaidEquipmentSlot source = this.menu.baseSlotForMenuSlot(slot.index);
        int sourceIndex = this.menu.baseItemIndexForMenuSlot(slot.index);
        return source != null && isGridTarget(source) && sourceIndex >= 0 ? new SelectionKey(false, source, sourceIndex) : null;
    }

    private void renderFootprintHover(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (splitDialog != null) {
            return;
        }
        if (repairDialog != null) {
            return;
        }
        if (contextMenu != null) {
            return;
        }
        if (dragSource != DragSource.NONE) {
            return;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null || !slot.hasItem()) {
            if (slot != null && isManagedGridSlot(slot)) {
                renderSingleSlotHover(guiGraphics, slot);
            }
            return;
        }
        if (!isManagedGridSlot(slot)) {
            return;
        }
        Slot owner = ownerSlotForManagedGrid(slot);
        renderOwnerHover(guiGraphics, owner == null ? slot : owner);
    }

    private void renderSingleSlotHover(GuiGraphics guiGraphics, Slot slot) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x3349D8E8);
        border(guiGraphics, slot.x - 1, slot.y - 1, 18, 18, 0xAA9CF6FF);
        guiGraphics.pose().popPose();
    }

    private Slot ownerSlotForStash(Slot clickedSlot, int ownerIndex) {
        if (ownerIndex < 0) {
            return clickedSlot;
        }
        GridDisplayMetadata.Metadata clickedMetadata = clickedSlot == null ? GridDisplayMetadata.Metadata.EMPTY : GridDisplayMetadata.read(clickedSlot.getItem());
        for (Slot slot : this.menu.slots) {
            if (!slot.hasItem() || !isStashSlot(slot)) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (metadata.present() && metadata.sourceIndex() == ownerIndex && metadata.anchor()) {
                return slot;
            }
            if (clickedMetadata.present()
                    && metadata.present()
                    && metadata.anchor()
                    && metadata.gridX() == clickedMetadata.gridX()
                    && metadata.gridY() == clickedMetadata.gridY()
                    && metadata.footprintWidth() == clickedMetadata.footprintWidth()
                    && metadata.footprintHeight() == clickedMetadata.footprintHeight()) {
                return slot;
            }
        }
        return clickedSlot;
    }

    private void renderOwnerHover(GuiGraphics guiGraphics, Slot ownerSlot) {
        if (ownerSlot == null || !ownerSlot.hasItem()) {
            return;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(ownerSlot.getItem());
        int width = metadata.present() ? metadata.footprintWidth() * 18 : 18;
        int height = metadata.present() ? metadata.footprintHeight() * 18 : 18;
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
        Footprint footprint = footprintFor(draggedStack);
        if (isStashGrid(mouseX, mouseY)) {
            renderManagedGridDragPreview(guiGraphics, stashLayout(), true, null, mouseX, mouseY, footprint);
            return;
        }
        if (target == null || !isGridTarget(target)) {
            return;
        }
        renderManagedGridDragPreview(guiGraphics, layoutFor(target), false, target, mouseX, mouseY, footprint);
    }

    private void renderManagedGridDragPreview(GuiGraphics guiGraphics, GridLayout layout, boolean stash, RaidEquipmentSlot target, int mouseX, int mouseY, Footprint footprint) {
        Placement placement = placementAt(layout, mouseX, mouseY, footprint);
        if (!placement.inGrid()) {
            return;
        }
        int localX = placement.layout().x() + placement.x() * 18;
        int localY = placement.layout().y() + placement.y() * 18;
        boolean fits = placement.valid()
                && (stash ? previewFitsStash(placement.x(), placement.y(), footprint) : previewFits(target, placement.x(), placement.y(), footprint));
        int width = footprint.width() * 18;
        int height = footprint.height() * 18;
        int color = fits ? 0xAA62F3E8 : 0xAAFF5D5D;
        logPreview(stash ? "STASH" : String.valueOf(target), placement.x(), placement.y(), footprint, fits);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
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
            if (!isBaseSlot(slot) || !isGridDisplaySlot(slot) || !slot.hasItem()) {
                continue;
            }
            if (this.menu.baseSlotForMenuSlot(slot.index) != target) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || !metadata.anchor()) {
                continue;
            }
            if (dragSource == DragSource.BASE && draggedBaseSlot == target && metadata.sourceIndex() == draggedSourceIndex) {
                continue;
            }
            if (overlaps(x, y, footprint.width(), footprint.height(), metadata.gridX(), metadata.gridY(), metadata.footprintWidth(), metadata.footprintHeight())) {
                return false;
            }
        }
        return true;
    }

    private boolean previewFitsStash(int x, int y, Footprint footprint) {
        if (x < 0 || y < 0 || x + footprint.width() > menu.stashColumns() || y + footprint.height() > menu.stashRows()) {
            return false;
        }
        int draggedActualIndex = dragSource == DragSource.STASH ? draggedStashActualIndex : -1;
        for (Slot slot : this.menu.slots) {
            if (!isStashSlot(slot) || !slot.hasItem()) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || !metadata.anchor()) {
                continue;
            }
            if (draggedActualIndex >= 0 && metadata.sourceIndex() == draggedActualIndex) {
                continue;
            }
            if (overlaps(x, y, footprint.width(), footprint.height(), metadata.gridX(), metadata.gridY(), metadata.footprintWidth(), metadata.footprintHeight())) {
                return false;
            }
        }
        return true;
    }

    private void logPreview(String target, int cellX, int cellY, Footprint footprint, boolean fits) {
        String key = target + ":" + cellX + ":" + cellY + ":" + footprint.width() + "x" + footprint.height() + ":" + fits;
        if (key.equals(lastPreviewLogKey)) {
            return;
        }
        lastPreviewLogKey = key;
        com.chaseschwartz.extractcraft.ExtractCraft.LOGGER.info("Base grid preview: target={}, targetCell=({},{}), heldFootprint={}x{}, fits={}, source={}#{}",
                target,
                cellX,
                cellY,
                footprint.width(),
                footprint.height(),
                fits,
                dragSource,
                draggedSourceIndex);
    }

    private Set<Integer> sourceMenuSlots(DragSource source, int sourceIndex, RaidEquipmentSlot baseSlot) {
        Set<Integer> slots = new HashSet<>();
        if (source == DragSource.STASH) {
            int actualIndex = this.menu.stashActualIndexForDisplayIndex(sourceIndex);
            for (Slot slot : this.menu.slots) {
                if (!isStashSlot(slot)) {
                    continue;
                }
                GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
                if ((metadata.present() && metadata.sourceIndex() == actualIndex)
                        || (!metadata.present() && stashSlotActualIndex(slot) == actualIndex)) {
                    slots.add(slot.index);
                }
            }
            return slots;
        }
        if (source == DragSource.BASE && baseSlot != null) {
            for (Slot slot : this.menu.slots) {
                if (isBaseSlot(slot)
                        && this.menu.baseSlotForMenuSlot(slot.index) == baseSlot
                        && this.menu.baseItemIndexForMenuSlot(slot.index) == sourceIndex) {
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

    private static void renderItemCentered(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int width, int height) {
        float scale = Math.min(3.0F, Math.max(1.0F, (Math.min(width, height) - 2) / 16.0F));
        double iconX = x + (width - 16.0D * scale) / 2.0D;
        double iconY = y + (height - 16.0D * scale) / 2.0D;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX, iconY, 0.0D);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private static String formatEmeralds(int amount) {
        if (amount >= 1_000_000) {
            double millions = amount / 1_000_000.0D;
            String formatted = millions >= 10.0D
                    ? String.format(java.util.Locale.ROOT, "%.1fm", millions)
                    : String.format(java.util.Locale.ROOT, "%.2fm", millions);
            return formatted.replace(".00m", ".0m").replaceAll("0m$", "m");
        }
        return String.format(java.util.Locale.US, "%,d", amount);
    }

    private void renderHeldStack(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Footprint footprint = footprintFor(draggedStack);
        int width = Math.max(18, footprint.width() * 18);
        int height = Math.max(18, footprint.height() * 18);
        int x = mouseX - width / 2;
        int y = mouseY - height / 2;
        border(guiGraphics, x - 1, y - 1, width, height, 0xAA62F3E8);
        renderItemCentered(guiGraphics, draggedStack, x, y, width, height);
        CustomDurabilityBarRenderer.render(guiGraphics, draggedStack, x, y, width, height);
    }

    private ItemStack withResolvedGridMetadata(ItemStack stack, int sourceIndex, RaidEquipmentSlot baseSlot, Slot clickedSlot) {
        if (GridDisplayMetadata.read(stack).present() || sourceIndex < 0 || baseSlot == null) {
            return stack;
        }
        for (Slot slot : this.menu.slots) {
            if (!slot.hasItem() || !isBaseSlot(slot) || this.menu.baseSlotForMenuSlot(slot.index) != baseSlot) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (metadata.present() && metadata.sourceIndex() == sourceIndex) {
                com.chaseschwartz.extractcraft.ExtractCraft.LOGGER.info("Base grid pickup metadata recovered: clickedMenuSlot={}, ownerIndex={}, footprint=({},{} {}x{} rotated={})",
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

    private GridLayout stashLayout() {
        return new GridLayout(STASH_SLOT_X, STASH_SLOT_Y, menu.stashColumns(), menu.stashRows());
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static void border(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private enum DragSource {
        NONE,
        BASE,
        STASH
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

    private record ContextMenu(int x, int y, boolean stashSource, RaidEquipmentSlot source, int sourceIndex, boolean canSplit, boolean canRepair) {
    }

    private record SplitDialog(boolean stashSource, RaidEquipmentSlot source, int sourceIndex, ItemStack stack, int amount, int maxAmount) {
    }

    private record RepairDialog(boolean stashSource, RaidEquipmentSlot source, int sourceIndex, ItemStack stack, RepairEstimate estimate) {
    }

    private record SelectionKey(boolean stashSource, RaidEquipmentSlot source, int sourceIndex) {
    }
}
