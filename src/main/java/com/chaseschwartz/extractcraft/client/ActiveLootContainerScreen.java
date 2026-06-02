package com.chaseschwartz.extractcraft.client;

import java.util.HashSet;
import java.util.Set;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.network.GridMoveRequestPayload;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.GridDisplayMetadata;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfile;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ActiveLootContainerScreen extends AbstractContainerScreen<ActiveLootContainerMenu> {
    private static final int CONTAINER_PANEL_X = 214;
    private static final int CONTAINER_PANEL_Y = 28;
    private static final int CONTAINER_PANEL_WIDTH = 130;
    private static final int WEAPON_BACKGROUND_SIZE = 20;
    private static final int WEAPON_INNER_SIZE = 18;
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
    private String gridMoveStatus = "";
    private double dragStartX;
    private double dragStartY;
    private boolean loggedLayout;
    private String lastPreviewLogKey = "";

    public ActiveLootContainerScreen(ActiveLootContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 352;
        this.imageHeight = Math.max(318, 102 + menu.containerRows() * 18);
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
        tickPendingSource();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        border(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);

        boolean dragging = dragSource != DragSource.NONE;
        section(guiGraphics, x + 8, y + 24, 128, 184, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.BACKPACK);
        section(guiGraphics, x + 8, y + 214, 82, 96, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.VEST);
        section(guiGraphics, x + 98, y + 214, 82, 96, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SAFE_BOX);
        weaponSection(guiGraphics, x + 144, y + 28, weaponPanelWidth(), 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.PRIMARY_WEAPON);
        weaponSection(guiGraphics, x + 144, y + 92, weaponPanelWidth(), 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SECONDARY_WEAPON);
        if (this.menu.hasWorldContainer()) {
            section(guiGraphics, x + CONTAINER_PANEL_X, y + CONTAINER_PANEL_Y, CONTAINER_PANEL_WIDTH, this.imageHeight - 40, false);
        }
        logLayoutOnce();
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (isRaidInventorySlot(slot) || isContainerSlot(slot)) {
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
        if ((isRaidInventorySlot(slot) && !isWeaponSlot(slot)) || isContainerSlot(slot)) {
            return;
        }
        super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "Raid Loot", 8, 8, TEXT, false);
        guiGraphics.drawString(this.font, String.format("Total: %.1f/%.1f wt | %d cr", totalUsedWeight(), totalMaxWeight(), menu.totalValue()), 168, 8, MUTED_TEXT, false);
        if (this.menu.hasWorldContainer()) {
            guiGraphics.drawString(this.font, "Container", CONTAINER_PANEL_X + 8, CONTAINER_PANEL_Y + 7, TEXT, false);
        }

        guiGraphics.drawString(this.font, "Backpack", 14, 29, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.backpackUsedCapacity(), menu.backpackMaxCapacity()), 14, 42, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, String.format("%.1f/%.1f wt", menu.backpackUsedWeight(), menu.backpackMaxWeight()), 14, 53, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, this.menu.hasWorldContainer() ? "Primary" : "Primary Weapon", 150, 34, TEXT, false);
        guiGraphics.drawString(this.font, this.menu.hasWorldContainer() ? "Secondary" : "Secondary Weapon", 150, 98, TEXT, false);
        drawWeaponName(guiGraphics, ActiveLootContainerMenu.PRIMARY_WEAPON_START, 170, 53, this.menu.hasWorldContainer() ? 5 : 24);
        drawWeaponName(guiGraphics, ActiveLootContainerMenu.SECONDARY_WEAPON_START, 170, 115, this.menu.hasWorldContainer() ? 5 : 24);

        guiGraphics.drawString(this.font, "Vest", 14, 219, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.vestUsedCapacity(), menu.vestMaxCapacity()), 14, 232, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Safe Box", 104, 219, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.safeBoxUsedCapacity(), menu.safeBoxMaxCapacity()), 104, 232, MUTED_TEXT, false);

        int hintY = Math.min(this.imageHeight - 28, 64 + this.menu.containerRows() * 18);
        if (this.menu.hasWorldContainer()) {
            guiGraphics.drawString(this.font, "Click: Backpack", CONTAINER_PANEL_X + 8, hintY, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Drag: choose", CONTAINER_PANEL_X + 8, hintY + 10, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Drag left: return", CONTAINER_PANEL_X + 8, hintY + 20, MUTED_TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dragSource != DragSource.NONE) {
            if (tryPlaceHeldStack(mouseX, mouseY)) {
                clearDrag();
            }
            return true;
        }

        Slot slot = slotAt(mouseX, mouseY);
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
        if (button == 0 && dragSource != DragSource.NONE) {
            if (isClickRelease(mouseX, mouseY)) {
                return true;
            }

            RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
            if (target != null) {
                int targetCell = placementCellAt(target, (int) mouseX, (int) mouseY, footprintFor(draggedStack));
                logDropTarget("release", mouseX, mouseY, target, targetCell);
                if (dragSource == DragSource.CONTAINER) {
                    sendTransfer(draggedSourceIndex, target, targetCell);
                } else if (dragSource == DragSource.RAID_INVENTORY) {
                    sendMove(draggedRaidSlot, draggedSourceIndex, target, targetCell);
                }
                markPendingSource();
            } else if (dragSource == DragSource.RAID_INVENTORY && isContainerPanel((int) mouseX, (int) mouseY)) {
                sendReturn(draggedRaidSlot, draggedSourceIndex);
                markPendingSource();
            }
            clearDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
            if (dragSource == DragSource.CONTAINER) {
                sendTransfer(draggedSourceIndex, target, targetCell);
                markPendingSource();
                return true;
            }
            if (dragSource == DragSource.RAID_INVENTORY) {
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

    private void sendGridMove(int operation, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target, int targetCell) {
        int transactionId = nextTransactionId++;
        pendingTransactionId = transactionId;
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

    public void handleGridMoveResult(int transactionId, boolean success, String message) {
        if (pendingTransactionId != transactionId) {
            ExtractCraft.LOGGER.info("Grid move result ignored: tx={}, pendingTx={}, success={}, message={}", transactionId, pendingTransactionId, success, message);
            return;
        }
        ExtractCraft.LOGGER.info("Grid move result applied: tx={}, success={}, message={}, pendingSlots={}", transactionId, success, message, pendingSourceMenuSlots);
        pendingTransactionId = -1;
        gridMoveStatus = success ? "" : message;
        if (!success) {
            pendingSourceMenuSlots.clear();
            pendingSourceTicks = 0;
            pendingSourceObservedPresent = false;
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

    private int targetCellAt(RaidEquipmentSlot target, int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return switch (target) {
            case BACKPACK -> gridCell(localX, localY, 12, 66, 6, 6);
            case VEST -> gridCell(localX, localY, 12, 248, 4, 3);
            case SAFE_BOX -> gridCell(localX, localY, 104, 248, 3, 3);
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
        if (!slot.hasItem() || isWeaponSlot(slot) || !isRaidInventorySlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && !metadata.anchor();
    }

    private boolean isGridAnchorFootprintSlot(Slot slot) {
        if (!slot.hasItem() || isWeaponSlot(slot) || !isRaidInventorySlot(slot)) {
            return false;
        }
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
        return metadata.present() && metadata.anchor() && (metadata.footprintWidth() > 1 || metadata.footprintHeight() > 1);
    }

    private RaidEquipmentSlot targetAt(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (inside(localX, localY, 144, 28, weaponPanelWidth(), 54)) {
            return RaidEquipmentSlot.PRIMARY_WEAPON;
        }
        if (inside(localX, localY, 144, 92, weaponPanelWidth(), 54)) {
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

    private static boolean insideBackpackGrid(int localX, int localY) {
        return inside(localX, localY, 12, 66, 6 * SLOT_STEP, 6 * SLOT_STEP);
    }

    private static boolean insideVestGrid(int localX, int localY) {
        return inside(localX, localY, 12, 248, 4 * SLOT_STEP, 3 * SLOT_STEP);
    }

    private static boolean insideSafeGrid(int localX, int localY) {
        return inside(localX, localY, 104, 248, 3 * SLOT_STEP, 3 * SLOT_STEP);
    }

    private static int cellX(RaidEquipmentSlot target, int cell) {
        return cell % switch (target) {
            case VEST -> 4;
            case SAFE_BOX -> 3;
            default -> 6;
        };
    }

    private static int cellY(RaidEquipmentSlot target, int cell) {
        return cell / switch (target) {
            case VEST -> 4;
            case SAFE_BOX -> 3;
            default -> 6;
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

    private boolean isContainerPanel(int mouseX, int mouseY) {
        if (!this.menu.hasWorldContainer()) {
            return false;
        }
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, CONTAINER_PANEL_X, CONTAINER_PANEL_Y, CONTAINER_PANEL_WIDTH, this.imageHeight - 40);
    }

    private int weaponPanelWidth() {
        return this.menu.hasWorldContainer() ? 62 : 192;
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

    private static void weaponSection(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean highlighted) {
        guiGraphics.fill(x, y, x + width, y + height, SECTION_COLOR);
        border(guiGraphics, x, y, width, height, highlighted ? HOVER_BORDER : 0x8849D8E8);
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
        int width = metadata.footprintWidth() * SLOT_STEP - 2;
        int height = metadata.footprintHeight() * SLOT_STEP - 2;
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

        int width = metadata.footprintWidth() * SLOT_STEP;
        int height = metadata.footprintHeight() * SLOT_STEP;
        renderItemCentered(guiGraphics, slot.getItem(), slot.x, slot.y, width, height);
    }

    private void renderFootprintOverlays(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        for (Slot slot : this.menu.slots) {
            if (isRaidInventorySlot(slot) && !isWeaponSlot(slot)) {
                drawFootprint(guiGraphics, slot);
            }
        }
        for (Slot slot : this.menu.slots) {
            if (isRaidInventorySlot(slot) && !isWeaponSlot(slot)) {
                renderFootprintItem(guiGraphics, slot);
            }
        }
        guiGraphics.pose().popPose();
    }

    private void renderFootprintHover(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
        if (isRaidInventorySlot(slot)) {
            Slot owner = ownerSlotFor(slot, this.menu.raidSlotForMenuSlot(slot.index), this.menu.raidItemIndexForMenuSlot(slot.index));
            renderOwnerHover(guiGraphics, owner == null ? slot : owner);
            return;
        }
        if (isContainerSlot(slot)) {
            renderOwnerHover(guiGraphics, slot);
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
            if (this.menu.raidSlotForMenuSlot(slot.index) != target) {
                continue;
            }
            GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(slot.getItem());
            if (!metadata.present() || !metadata.anchor()) {
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

    private static void renderItemCentered(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int width, int height) {
        float scale = Math.min(3.0F, Math.max(1.0F, (Math.min(width, height) - 2) / 16.0F));
        double centerX = x + width / 2.0D;
        double centerY = y + height / 2.0D;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0.0D);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(stack, -8, -8);
        guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, -8, -8);
        guiGraphics.pose().popPose();
    }

    private void renderHeldStack(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Footprint footprint = footprintFor(draggedStack);
        int width = Math.max(SLOT_STEP, footprint.width() * SLOT_STEP);
        int height = Math.max(SLOT_STEP, footprint.height() * SLOT_STEP);
        int x = mouseX - width / 2;
        int y = mouseY - height / 2;
        border(guiGraphics, x - 1, y - 1, width, height, 0xAA62F3E8);
        renderItemCentered(guiGraphics, draggedStack, x, y, width, height);
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

    private static GridLayout layoutFor(RaidEquipmentSlot slot) {
        return switch (slot) {
            case VEST -> new GridLayout(12, 248, 4, 3);
            case SAFE_BOX -> new GridLayout(104, 248, 3, 3);
            default -> new GridLayout(12, 66, 6, 6);
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
        logSection("Primary", ActiveLootContainerMenu.PRIMARY_WEAPON_START, 1, 1, 1, 144, 28);
        logSection("Secondary", ActiveLootContainerMenu.SECONDARY_WEAPON_START, 1, 1, 1, 144, 92);
        logSection("Backpack", ActiveLootContainerMenu.BACKPACK_START, ActiveLootContainerMenu.BACKPACK_DISPLAY_SLOTS, 6, 6, 8, 24);
        logSection("Vest", ActiveLootContainerMenu.VEST_START, ActiveLootContainerMenu.VEST_DISPLAY_SLOTS, 4, 3, 8, 214);
        logSection("Safe Box", ActiveLootContainerMenu.SAFE_BOX_START, ActiveLootContainerMenu.SAFE_BOX_DISPLAY_SLOTS, 3, 3, 98, 214);
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
        boolean weaponSection = start == ActiveLootContainerMenu.PRIMARY_WEAPON_START || start == ActiveLootContainerMenu.SECONDARY_WEAPON_START;
        int backgroundSize = weaponSection ? WEAPON_BACKGROUND_SIZE : VANILLA_BACKGROUND_SIZE;
        int innerSize = weaponSection ? WEAPON_INNER_SIZE : VANILLA_INNER_SIZE;
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
                slotRect(start, weaponSection),
                slotRect(last, weaponSection));
    }

    private String slotRect(int slotIndex, boolean weaponSlot) {
        Slot slot = this.menu.slots.get(slotIndex);
        int screenX = this.leftPos + slot.x;
        int screenY = this.topPos + slot.y;
        int bgX = weaponSlot ? screenX - 2 : screenX - 1;
        int bgY = weaponSlot ? screenY - 2 : screenY - 1;
        int bgSize = weaponSlot ? WEAPON_BACKGROUND_SIZE : VANILLA_BACKGROUND_SIZE;
        return slotIndex
                + " local=(" + slot.x + "," + slot.y + ")"
                + " screen=(" + screenX + "," + screenY + ")"
                + " bg=(" + bgX + "," + bgY + "," + bgSize + "x" + bgSize + ")"
                + " hover=(" + screenX + "," + screenY + "," + VANILLA_HOVER_SIZE + "x" + VANILLA_HOVER_SIZE + ")"
                + " item=(" + screenX + "," + screenY + "," + VANILLA_ITEM_SIZE + "x" + VANILLA_ITEM_SIZE + ")";
    }

    private void drawWeaponName(GuiGraphics guiGraphics, int menuSlotIndex, int x, int y, int maxLength) {
        if (menuSlotIndex < 0 || menuSlotIndex >= this.menu.slots.size()) {
            return;
        }
        ItemStack stack = this.menu.slots.get(menuSlotIndex).getItem();
        if (!stack.isEmpty()) {
            guiGraphics.drawString(this.font, trim(stack.getHoverName().getString(), maxLength), x, y, MUTED_TEXT, false);
        }
    }

    private static String trim(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
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
}
