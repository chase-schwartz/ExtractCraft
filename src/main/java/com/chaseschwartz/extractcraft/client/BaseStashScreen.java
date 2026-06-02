package com.chaseschwartz.extractcraft.client;

import java.util.HashSet;
import java.util.Set;

import com.chaseschwartz.extractcraft.ExtractCraft;
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
    private static final int BACKPACK_COLUMNS = 8;
    private static final int BACKPACK_ROWS = 8;
    private static final int BACKPACK_GRID_X = 12;
    private static final int BACKPACK_GRID_Y = 62;
    private static final int VEST_GRID_X = 12;
    private static final int VEST_GRID_Y = 256;
    private static final int SAFE_GRID_X = 104;
    private static final int SAFE_GRID_Y = 256;
    private static final int STASH_X = 264;
    private static final int STASH_Y = 24;
    private static final int STASH_SLOT_X = 274;
    private static final int STASH_SLOT_Y = 82;
    private static final int CONTEXT_MENU_WIDTH = 74;
    private static final int CONTEXT_MENU_ROW_HEIGHT = 17;
    private static final int CONTEXT_MENU_ROWS = 3;
    private static final int CONTEXT_MENU_HEIGHT = CONTEXT_MENU_ROW_HEIGHT * CONTEXT_MENU_ROWS + 4;
    private DragSource dragSource = DragSource.NONE;
    private RaidEquipmentSlot draggedBaseSlot;
    private int draggedSourceIndex = -1;
    private ItemStack draggedStack = ItemStack.EMPTY;
    private final Set<Integer> draggedSourceMenuSlots = new HashSet<>();
    private final Set<Integer> pendingSourceMenuSlots = new HashSet<>();
    private int pendingSourceTicks;
    private boolean pendingSourceObservedPresent;
    private int nextTransactionId = 1;
    private int pendingTransactionId = -1;
    private String gridMoveStatus = "";
    private ContextMenu contextMenu = null;
    private double dragStartX;
    private double dragStartY;
    private String lastPreviewLogKey = "";

    public BaseStashScreen(BaseStashMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = Math.max(486, STASH_SLOT_X + menu.stashColumns() * 18 + 14);
        this.imageHeight = Math.max(350, STASH_SLOT_Y + menu.stashRows() * 18 + 38);
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
        renderContextMenu(guiGraphics, mouseX, mouseY);
        tickPendingSource();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        border(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);

        boolean dragging = dragSource != DragSource.NONE;
        section(guiGraphics, x + 8, y + 24, 164, 210, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.BACKPACK);
        section(guiGraphics, x + 8, y + 238, 82, 82, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.VEST);
        section(guiGraphics, x + 98, y + 238, 82, 82, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SAFE_BOX);
        section(guiGraphics, x + 188, y + 28, 72, 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.PRIMARY_WEAPON);
        section(guiGraphics, x + 188, y + 90, 72, 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SECONDARY_WEAPON);
        section(guiGraphics, x + STASH_X, y + STASH_Y, this.imageWidth - STASH_X - 8, this.imageHeight - STASH_Y - 8, dragging && isStashPanel(mouseX, mouseY));
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (isBaseSlot(slot) || isStashSlot(slot)) {
            if (isWeaponSlot(slot)) {
                drawWeaponSlotBackground(guiGraphics, slot.x - 1, slot.y - 1);
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
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (contextMenu != null) {
            return;
        }
        if ((isBaseSlot(slot) && !isWeaponSlot(slot)) || isStashSlot(slot)) {
            return;
        }
        super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (contextMenu != null) {
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

        guiGraphics.drawString(this.font, "Backpack", 14, 29, TEXT, false);
        guiGraphics.drawString(this.font, "Primary", 194, 34, TEXT, false);
        guiGraphics.drawString(this.font, "Secondary", 194, 96, TEXT, false);
        guiGraphics.drawString(this.font, "Vest", 14, 243, TEXT, false);
        guiGraphics.drawString(this.font, "Safe Box", 104, 243, TEXT, false);

        int sortY = this.imageHeight - 18;
        guiGraphics.drawString(this.font, "[Name]", STASH_X + 8, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Value]", STASH_X + 50, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Weight]", STASH_X + 96, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Category]", STASH_X + 150, sortY, MUTED_TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu != null) {
            if (button == 0 && handleContextMenuClick(mouseX, mouseY)) {
                return true;
            }
            contextMenu = null;
            if (button == 0) {
                return true;
            }
        }

        if (button == 1) {
            Slot slot = slotAt(mouseX, mouseY);
            if (slot != null && slot.hasItem()) {
                if (isBaseSlot(slot)) {
                    RaidEquipmentSlot source = this.menu.baseSlotForMenuSlot(slot.index);
                    int sourceIndex = this.menu.baseItemIndexForMenuSlot(slot.index);
                    if (sourceIndex >= 0) {
                        contextMenu = new ContextMenu((int) mouseX, (int) mouseY, false, source, sourceIndex);
                    }
                    return true;
                }
                if (isStashSlot(slot)) {
                    contextMenu = new ContextMenu((int) mouseX, (int) mouseY, true, RaidEquipmentSlot.BACKPACK, this.menu.stashDisplayIndexForMenuSlot(slot.index));
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
                int sourceIndex = this.menu.stashDisplayIndexForMenuSlot(slot.index);
                startDrag(DragSource.STASH, sourceIndex, null, slot.getItem(), slot, mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
            sendBaseToStash(draggedBaseSlot, draggedSourceIndex);
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
        if (localY < sortY || localY > sortY + 10 || localX < STASH_X + 8 || localX > STASH_X + 210) {
            return false;
        }
        if (localX < STASH_X + 48) {
            sendSort(0);
        } else if (localX < STASH_X + 94) {
            sendSort(1);
        } else if (localX < STASH_X + 148) {
            sendSort(2);
        } else {
            sendSort(3);
        }
        return true;
    }

    private void startDrag(DragSource source, int sourceIndex, RaidEquipmentSlot baseSlot, ItemStack stack, Slot clickedSlot, double mouseX, double mouseY) {
        this.dragSource = source;
        this.draggedSourceIndex = sourceIndex;
        this.draggedBaseSlot = baseSlot;
        this.draggedStack = withResolvedGridMetadata(stack.copy(), sourceIndex, baseSlot, clickedSlot);
        this.draggedSourceMenuSlots.clear();
        this.draggedSourceMenuSlots.addAll(sourceMenuSlots(source, sourceIndex, baseSlot));
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
    }

    private void clearDrag() {
        this.dragSource = DragSource.NONE;
        this.draggedSourceIndex = -1;
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
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            sendGridMove(GridMoveRequestPayload.BASE_BASE_TO_STASH, source, sourceIndex, null, -1);
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
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.contextActionButtonId(action, menu.stashSource(), menu.source(), menu.sourceIndex()));
        }
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY) {
        if (contextMenu == null) {
            return false;
        }
        int x = contextMenuX();
        int y = contextMenuY();
        if (!inside((int) mouseX, (int) mouseY, x, y, CONTEXT_MENU_WIDTH, CONTEXT_MENU_HEIGHT)) {
            return false;
        }
        int option = contextMenuOptionAt(mouseX, mouseY);
        if (option >= 0 && option <= 2) {
            sendContextAction(option, contextMenu);
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
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0D, 0.0D, 500.0D);
        guiGraphics.fill(x, y, x + CONTEXT_MENU_WIDTH, y + CONTEXT_MENU_HEIGHT, 0xF0181B22);
        border(guiGraphics, x, y, CONTEXT_MENU_WIDTH, CONTEXT_MENU_HEIGHT, BORDER_COLOR);
        renderContextRow(guiGraphics, x, y, 0, "Sell", hovered == 0, TEXT, 0x553A5E66);
        renderContextRow(guiGraphics, x, y, 1, "Drop", hovered == 1, TEXT, 0x553A5E66);
        renderContextRow(guiGraphics, x, y, 2, "Trash", hovered == 2, 0xFFFFA0A0, 0x554A2228);
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
        if (!inside((int) mouseX, (int) mouseY, x, y, CONTEXT_MENU_WIDTH - 4, CONTEXT_MENU_ROW_HEIGHT * CONTEXT_MENU_ROWS)) {
            return -1;
        }
        int option = ((int) mouseY - y) / CONTEXT_MENU_ROW_HEIGHT;
        return option >= 0 && option < CONTEXT_MENU_ROWS ? option : -1;
    }

    private int contextMenuX() {
        return Math.min(contextMenu.x(), this.leftPos + this.imageWidth - CONTEXT_MENU_WIDTH - 4);
    }

    private int contextMenuY() {
        return Math.min(contextMenu.y(), this.topPos + this.imageHeight - CONTEXT_MENU_HEIGHT - 4);
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

    private int targetCellAt(RaidEquipmentSlot target, int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return switch (target) {
            case BACKPACK -> gridCell(localX, localY, BACKPACK_GRID_X, BACKPACK_GRID_Y, BACKPACK_COLUMNS, BACKPACK_ROWS);
            case VEST -> gridCell(localX, localY, VEST_GRID_X, VEST_GRID_Y, 4, 3);
            case SAFE_BOX -> gridCell(localX, localY, SAFE_GRID_X, SAFE_GRID_Y, 3, 3);
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> -1;
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

    private boolean isWeaponSlot(Slot slot) {
        return slot.index == BaseStashMenu.PRIMARY_WEAPON_START || slot.index == BaseStashMenu.SECONDARY_WEAPON_START;
    }

    private boolean isDragSourceSlot(Slot slot) {
        if (pendingSourceMenuSlots.contains(slot.index)) {
            return true;
        }
        if (draggedSourceMenuSlots.contains(slot.index)) {
            return true;
        }
        if (dragSource == DragSource.BASE && draggedBaseSlot != null) {
            return isBaseSlot(slot)
                    && this.menu.baseSlotForMenuSlot(slot.index) == draggedBaseSlot
                    && this.menu.baseItemIndexForMenuSlot(slot.index) == draggedSourceIndex;
        }
        if (dragSource == DragSource.STASH) {
            return slot.index == this.menu.stashMenuSlotStart() + draggedSourceIndex;
        }
        return false;
    }

    private boolean isGridShadowSlot(Slot slot) {
        if (!slot.hasItem() || isWeaponSlot(slot) || !isBaseSlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && !metadata.anchor();
    }

    private boolean isGridAnchorFootprintSlot(Slot slot) {
        if (!slot.hasItem() || isWeaponSlot(slot) || !isBaseSlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && metadata.anchor() && (metadata.footprintWidth() > 1 || metadata.footprintHeight() > 1);
    }

    private RaidEquipmentSlot targetAt(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (inside(localX, localY, 188, 28, 72, 54)) {
            return RaidEquipmentSlot.PRIMARY_WEAPON;
        }
        if (inside(localX, localY, 188, 90, 72, 54)) {
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

    private static boolean insideBackpackGrid(int localX, int localY) {
        return inside(localX, localY, BACKPACK_GRID_X, BACKPACK_GRID_Y, BACKPACK_COLUMNS * 18, BACKPACK_ROWS * 18);
    }

    private static boolean insideVestGrid(int localX, int localY) {
        return inside(localX, localY, VEST_GRID_X, VEST_GRID_Y, 4 * 18, 3 * 18);
    }

    private static boolean insideSafeGrid(int localX, int localY) {
        return inside(localX, localY, SAFE_GRID_X, SAFE_GRID_Y, 3 * 18, 3 * 18);
    }

    private static int cellX(RaidEquipmentSlot target, int cell) {
        return cell % switch (target) {
            case VEST -> 4;
            case SAFE_BOX -> 3;
            default -> BACKPACK_COLUMNS;
        };
    }

    private static int cellY(RaidEquipmentSlot target, int cell) {
        return cell / switch (target) {
            case VEST -> 4;
            case SAFE_BOX -> 3;
            default -> BACKPACK_COLUMNS;
        };
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
        };
    }

    private boolean isStashPanel(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, STASH_X, STASH_Y, this.imageWidth - STASH_X - 8, this.imageHeight - STASH_Y - 8);
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

    private static void drawWeaponSlotBackground(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF1C2028);
        guiGraphics.fill(x, y, x + 18, y + 18, SLOT_COLOR);
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
        renderItemCentered(guiGraphics, slot.getItem(), slot.x, slot.y, metadata.footprintWidth() * 18, metadata.footprintHeight() * 18);
    }

    private void renderFootprintOverlays(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        for (Slot slot : this.menu.slots) {
            if (isBaseSlot(slot) && !isWeaponSlot(slot)) {
                drawFootprint(guiGraphics, slot);
            }
        }
        for (Slot slot : this.menu.slots) {
            if (isBaseSlot(slot) && !isWeaponSlot(slot)) {
                renderFootprintItem(guiGraphics, slot);
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderFootprintHover(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (contextMenu != null) {
            return;
        }
        if (dragSource != DragSource.NONE) {
            return;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot == null || !slot.hasItem()) {
            return;
        }
        if (isWeaponSlot(slot)) {
            return;
        }
        if (isBaseSlot(slot)) {
            Slot owner = ownerSlotFor(slot, this.menu.baseSlotForMenuSlot(slot.index), this.menu.baseItemIndexForMenuSlot(slot.index));
            renderOwnerHover(guiGraphics, owner == null ? slot : owner);
            return;
        }
        if (isStashSlot(slot)) {
            renderOwnerHover(guiGraphics, slot);
        }
    }

    private Slot ownerSlotFor(Slot clickedSlot, RaidEquipmentSlot section, int ownerIndex) {
        if (section == null || ownerIndex < 0) {
            return clickedSlot;
        }
        for (Slot slot : this.menu.slots) {
            if (!slot.hasItem() || !isBaseSlot(slot) || this.menu.baseSlotForMenuSlot(slot.index) != section) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (metadata.present() && metadata.sourceIndex() == ownerIndex && metadata.anchor()) {
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
        if (target == null || target == RaidEquipmentSlot.PRIMARY_WEAPON || target == RaidEquipmentSlot.SECONDARY_WEAPON) {
            return;
        }
        Footprint footprint = footprintFor(draggedStack);
        Placement placement = placementAt(target, mouseX, mouseY, footprint);
        if (!placement.inGrid()) {
            return;
        }
        int localX = placement.layout().x() + placement.x() * 18;
        int localY = placement.layout().y() + placement.y() * 18;
        boolean fits = placement.valid() && previewFits(target, placement.x(), placement.y(), footprint);
        int width = footprint.width() * 18;
        int height = footprint.height() * 18;
        int color = fits ? 0xAA62F3E8 : 0xAAFF5D5D;
        logPreview(target, placement.x(), placement.y(), footprint, fits);

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
            if (!isBaseSlot(slot) || isWeaponSlot(slot) || !slot.hasItem()) {
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

    private void logPreview(RaidEquipmentSlot target, int cellX, int cellY, Footprint footprint, boolean fits) {
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
            slots.add(this.menu.stashMenuSlotStart() + sourceIndex);
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

    private static GridLayout layoutFor(RaidEquipmentSlot slot) {
        return switch (slot) {
            case VEST -> new GridLayout(VEST_GRID_X, VEST_GRID_Y, 4, 3);
            case SAFE_BOX -> new GridLayout(SAFE_GRID_X, SAFE_GRID_Y, 3, 3);
            default -> new GridLayout(BACKPACK_GRID_X, BACKPACK_GRID_Y, BACKPACK_COLUMNS, BACKPACK_ROWS);
        };
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

    private record ContextMenu(int x, int y, boolean stashSource, RaidEquipmentSlot source, int sourceIndex) {
    }
}
