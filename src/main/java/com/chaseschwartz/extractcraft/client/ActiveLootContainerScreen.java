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
    private int draggedContainerSlot = -1;
    private ItemStack draggedStack = ItemStack.EMPTY;

    public ActiveLootContainerScreen(ActiveLootContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 352;
        this.imageHeight = Math.max(236, 68 + menu.containerRows() * 18);
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

        boolean dragging = draggedContainerSlot >= 0;
        section(guiGraphics, x + 8, y + 24, 128, 124, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.BACKPACK);
        section(guiGraphics, x + 8, y + 154, 80, 74, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.VEST);
        section(guiGraphics, x + 98, y + 154, 80, 74, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SAFE_BOX);
        section(guiGraphics, x + 216, y + 24, 128, this.imageHeight - 32, false);

        drawSlotGrid(guiGraphics, x + 11, y + 37, 6, 6);
        drawSlotGrid(guiGraphics, x + 11, y + 161, 4, 3);
        drawSlotGrid(guiGraphics, x + 101, y + 161, 3, 3);
        drawSlotGrid(guiGraphics, x + 225, y + 37, ActiveLootContainerMenu.CONTAINER_COLUMNS, this.menu.containerRows());
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "Raid Loot", 8, 8, TEXT, false);
        guiGraphics.drawString(this.font, "Container", 222, 8, TEXT, false);

        guiGraphics.drawString(this.font, "Backpack", 14, 29, TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("%d/%d slots %.1f/%.1f wt %d cr", menu.backpackUsedCapacity(), menu.backpackMaxCapacity(), menu.backpackUsedWeight(), menu.backpackMaxWeight(), menu.backpackValue()),
                14, 137, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Vest", 14, 159, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d", menu.vestUsedCapacity(), menu.vestMaxCapacity()), 14, 214, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Safe Box", 104, 159, TEXT, false);
        guiGraphics.drawString(this.font, String.format("%d/%d", menu.safeBoxUsedCapacity(), menu.safeBoxMaxCapacity()), 104, 214, MUTED_TEXT, false);

        guiGraphics.drawString(this.font, "Click: backpack. Drag: choose target.", 216, this.imageHeight - 18, MUTED_TEXT, false);
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

            draggedContainerSlot = containerSlot;
            draggedStack = slot.getItem().copy();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedContainerSlot >= 0) {
            RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
            sendTransfer(draggedContainerSlot, target == null ? RaidEquipmentSlot.BACKPACK : target);
            draggedContainerSlot = -1;
            draggedStack = ItemStack.EMPTY;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void sendTransfer(int containerSlot, RaidEquipmentSlot target) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ActiveLootContainerMenu.buttonId(target, containerSlot));
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

    private RaidEquipmentSlot targetAt(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (inside(localX, localY, 8, 24, 128, 124)) {
            return RaidEquipmentSlot.BACKPACK;
        }
        if (inside(localX, localY, 8, 154, 80, 74)) {
            return RaidEquipmentSlot.VEST;
        }
        if (inside(localX, localY, 98, 154, 80, 74)) {
            return RaidEquipmentSlot.SAFE_BOX;
        }
        return null;
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

    private static void border(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
