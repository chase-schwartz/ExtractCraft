package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ActiveLootContainerScreen extends AbstractContainerScreen<ActiveLootContainerMenu> {
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
    private double dragStartX;
    private double dragStartY;

    public ActiveLootContainerScreen(ActiveLootContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 352;
        this.imageHeight = Math.max(318, 102 + menu.containerRows() * 18);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (!draggedStack.isEmpty()) {
            guiGraphics.renderItem(draggedStack, mouseX - 8, mouseY - 8);
            guiGraphics.renderItemDecorations(this.font, draggedStack, mouseX - 8, mouseY - 8);
        }
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
        weaponSection(guiGraphics, x + 144, y + 28, this.menu.hasWorldContainer() ? 64 : 192, 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.PRIMARY_WEAPON);
        weaponSection(guiGraphics, x + 144, y + 92, this.menu.hasWorldContainer() ? 64 : 192, 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SECONDARY_WEAPON);
        if (this.menu.hasWorldContainer()) {
            section(guiGraphics, x + 216, y + 24, 128, this.imageHeight - 32, false);
        }

        drawSlotGrid(guiGraphics, x + 11, y + 65, 6, 6);
        drawSlotGrid(guiGraphics, x + 11, y + 247, 4, 3);
        drawSlotGrid(guiGraphics, x + 103, y + 247, 3, 3);
        drawLargeSlot(guiGraphics, x + 149, y + 47);
        drawLargeSlot(guiGraphics, x + 149, y + 109);
        if (this.menu.hasWorldContainer()) {
            drawSlotGrid(guiGraphics, x + 225, y + 57, ActiveLootContainerMenu.CONTAINER_COLUMNS, this.menu.containerRows());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "Raid Loot", 8, 8, TEXT, false);
        guiGraphics.drawString(this.font, String.format("Total: %.1f/%.1f wt | %d cr", totalUsedWeight(), totalMaxWeight(), menu.totalValue()), 168, 8, MUTED_TEXT, false);
        if (this.menu.hasWorldContainer()) {
            guiGraphics.drawString(this.font, "Container", 222, 18, TEXT, false);
        }

        guiGraphics.drawString(this.font, "Backpack", 14, 29, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.backpackUsedCapacity(), menu.backpackMaxCapacity()), 14, 42, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, String.format("%.1f/%.1f wt", menu.backpackUsedWeight(), menu.backpackMaxWeight()), 14, 53, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Primary Weapon", 150, 34, TEXT, false);
        guiGraphics.drawString(this.font, "Secondary Weapon", 150, 98, TEXT, false);
        drawWeaponName(guiGraphics, ActiveLootContainerMenu.PRIMARY_WEAPON_START, 170, 53, this.menu.hasWorldContainer() ? 6 : 24);
        drawWeaponName(guiGraphics, ActiveLootContainerMenu.SECONDARY_WEAPON_START, 170, 115, this.menu.hasWorldContainer() ? 6 : 24);

        guiGraphics.drawString(this.font, "Vest", 14, 219, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.vestUsedCapacity(), menu.vestMaxCapacity()), 14, 232, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Safe Box", 104, 219, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d slots", menu.safeBoxUsedCapacity(), menu.safeBoxMaxCapacity()), 104, 232, MUTED_TEXT, false);

        int hintY = Math.min(this.imageHeight - 28, 64 + this.menu.containerRows() * 18);
        if (this.menu.hasWorldContainer()) {
            guiGraphics.drawString(this.font, "Click: Backpack", 222, hintY, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Drag: choose", 222, hintY + 10, MUTED_TEXT, false);
            guiGraphics.drawString(this.font, "Drag left: return", 222, hintY + 20, MUTED_TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot slot = slotAt(mouseX, mouseY);
        if (button == 0 && slot != null && isContainerSlot(slot) && slot.hasItem()) {
            int containerSlot = slot.index - this.menu.containerMenuSlotStart();
            if (hasShiftDown()) {
                sendTransfer(containerSlot, RaidEquipmentSlot.BACKPACK);
                return true;
            }

            startDrag(DragSource.CONTAINER, containerSlot, null, slot.getItem(), mouseX, mouseY);
            return true;
        }
        if (button == 0 && slot != null && isRaidInventorySlot(slot) && slot.hasItem()) {
            RaidEquipmentSlot sourceSlot = this.menu.raidSlotForMenuSlot(slot.index);
            int sourceIndex = this.menu.raidItemIndexForMenuSlot(slot.index);
            startDrag(DragSource.RAID_INVENTORY, sourceIndex, sourceSlot, slot.getItem(), mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragSource != DragSource.NONE) {
            RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
            if (dragSource == DragSource.CONTAINER && target == null && isClickRelease(mouseX, mouseY)) {
                sendTransfer(draggedSourceIndex, RaidEquipmentSlot.BACKPACK);
            } else if (target != null) {
                if (dragSource == DragSource.CONTAINER) {
                    sendTransfer(draggedSourceIndex, target);
                } else if (dragSource == DragSource.RAID_INVENTORY && draggedRaidSlot != target) {
                    sendMove(draggedRaidSlot, draggedSourceIndex, target);
                }
            } else if (dragSource == DragSource.RAID_INVENTORY && isContainerPanel((int) mouseX, (int) mouseY)) {
                sendReturn(draggedRaidSlot, draggedSourceIndex);
            }
            clearDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void startDrag(DragSource source, int sourceIndex, RaidEquipmentSlot raidSlot, ItemStack stack, double mouseX, double mouseY) {
        this.dragSource = source;
        this.draggedSourceIndex = sourceIndex;
        this.draggedRaidSlot = raidSlot;
        this.draggedStack = stack.copy();
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
    }

    private void clearDrag() {
        this.dragSource = DragSource.NONE;
        this.draggedSourceIndex = -1;
        this.draggedRaidSlot = null;
        this.draggedStack = ItemStack.EMPTY;
        this.dragStartX = 0.0D;
        this.dragStartY = 0.0D;
    }

    private boolean isClickRelease(double mouseX, double mouseY) {
        return Math.abs(mouseX - dragStartX) <= 3.0D && Math.abs(mouseY - dragStartY) <= 3.0D;
    }

    private void sendTransfer(int containerSlot, RaidEquipmentSlot target) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ActiveLootContainerMenu.buttonId(target, containerSlot));
        }
    }

    private void sendMove(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null && target != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ActiveLootContainerMenu.moveButtonId(source, sourceIndex, target));
        }
    }

    private void sendReturn(RaidEquipmentSlot source, int sourceIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ActiveLootContainerMenu.returnButtonId(source, sourceIndex));
        }
    }

    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (slot.isActive() && this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private boolean isContainerSlot(Slot slot) {
        return slot.index >= this.menu.containerMenuSlotStart()
                && slot.index < this.menu.containerMenuSlotStart() + this.menu.containerSlotCount();
    }

    private boolean isRaidInventorySlot(Slot slot) {
        return this.menu.raidSlotForMenuSlot(slot.index) != null;
    }

    private RaidEquipmentSlot targetAt(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (inside(localX, localY, 144, 28, this.menu.hasWorldContainer() ? 64 : 192, 54)) {
            return RaidEquipmentSlot.PRIMARY_WEAPON;
        }
        if (inside(localX, localY, 144, 92, this.menu.hasWorldContainer() ? 64 : 192, 54)) {
            return RaidEquipmentSlot.SECONDARY_WEAPON;
        }
        if (inside(localX, localY, 8, 24, 128, 184)) {
            return RaidEquipmentSlot.BACKPACK;
        }
        if (inside(localX, localY, 8, 214, 82, 96)) {
            return RaidEquipmentSlot.VEST;
        }
        if (inside(localX, localY, 98, 214, 82, 96)) {
            return RaidEquipmentSlot.SAFE_BOX;
        }
        return null;
    }

    private boolean isContainerPanel(int mouseX, int mouseY) {
        if (!this.menu.hasWorldContainer()) {
            return false;
        }
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, 216, 24, 128, this.imageHeight - 32);
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

    private static void drawSlotGrid(GuiGraphics guiGraphics, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int slotX = x - 1 + column * 18;
                int slotY = y - 1 + row * 18;
                guiGraphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF1C2028);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, SLOT_COLOR);
            }
        }
    }

    private static void section(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean highlighted) {
        guiGraphics.fill(x, y, x + width, y + height, SECTION_COLOR);
        border(guiGraphics, x, y, width, height, highlighted ? HOVER_BORDER : 0x6649D8E8);
    }

    private static void weaponSection(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean highlighted) {
        guiGraphics.fill(x, y, x + width, y + height, SECTION_COLOR);
        border(guiGraphics, x, y, width, height, highlighted ? HOVER_BORDER : 0x8849D8E8);
    }

    private static void drawLargeSlot(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF1C2028);
        guiGraphics.fill(x, y, x + 18, y + 18, SLOT_COLOR);
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
}
