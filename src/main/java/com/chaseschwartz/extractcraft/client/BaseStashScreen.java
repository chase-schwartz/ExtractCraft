package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.raid.inventory.BaseStashMenu;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BaseStashScreen extends AbstractContainerScreen<BaseStashMenu> {
    private static final int PANEL_COLOR = 0xF0101116;
    private static final int SECTION_COLOR = 0xFF1B2029;
    private static final int SLOT_COLOR = 0xFF40444D;
    private static final int BORDER_COLOR = 0xFF49D8E8;
    private static final int HOVER_BORDER = 0xFF9CF6FF;
    private static final int TEXT = 0xFFDFFBFF;
    private static final int MUTED_TEXT = 0xFF9AA6B2;
    private static final int STASH_X = 226;
    private static final int STASH_Y = 24;
    private static final int STASH_SLOT_X = 236;
    private static final int STASH_SLOT_Y = 82;
    private DragSource dragSource = DragSource.NONE;
    private RaidEquipmentSlot draggedBaseSlot;
    private int draggedSourceIndex = -1;
    private ItemStack draggedStack = ItemStack.EMPTY;
    private double dragStartX;
    private double dragStartY;

    public BaseStashScreen(BaseStashMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = Math.max(446, STASH_SLOT_X + menu.stashColumns() * 18 + 14);
        this.imageHeight = Math.max(318, STASH_SLOT_Y + menu.stashRows() * 18 + 38);
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
        section(guiGraphics, x + 8, y + 24, 128, 178, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.BACKPACK);
        section(guiGraphics, x + 8, y + 208, 82, 82, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.VEST);
        section(guiGraphics, x + 98, y + 208, 82, 82, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SAFE_BOX);
        section(guiGraphics, x + 144, y + 28, 72, 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.PRIMARY_WEAPON);
        section(guiGraphics, x + 144, y + 90, 72, 54, dragging && targetAt(mouseX, mouseY) == RaidEquipmentSlot.SECONDARY_WEAPON);
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
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, "Base Inventory", 8, 8, TEXT, false);
        guiGraphics.drawString(this.font, "Persistent Stash", STASH_X + 8, STASH_Y + 7, TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("Level %d | %d/%d slots | %d cr",
                        menu.stashLevel(),
                        menu.stashUsedCapacity(),
                        menu.stashMaxCapacity(),
                        menu.credits()),
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
        guiGraphics.drawString(this.font, "Primary", 150, 34, TEXT, false);
        guiGraphics.drawString(this.font, "Secondary", 150, 96, TEXT, false);
        guiGraphics.drawString(this.font, "Vest", 14, 213, TEXT, false);
        guiGraphics.drawString(this.font, "Safe Box", 104, 213, TEXT, false);

        int sortY = this.imageHeight - 18;
        guiGraphics.drawString(this.font, "[Name]", STASH_X + 8, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Value]", STASH_X + 50, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Weight]", STASH_X + 96, sortY, MUTED_TEXT, false);
        guiGraphics.drawString(this.font, "[Category]", STASH_X + 150, sortY, MUTED_TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleSortClick(mouseX, mouseY)) {
            return true;
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
                startDrag(DragSource.BASE, sourceIndex, source, slot.getItem(), mouseX, mouseY);
                return true;
            }
            if (isStashSlot(slot)) {
                int sourceIndex = this.menu.stashDisplayIndexForMenuSlot(slot.index);
                startDrag(DragSource.STASH, sourceIndex, null, slot.getItem(), mouseX, mouseY);
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
            sendStashToBase(draggedSourceIndex, baseTarget);
            return true;
        }
        if (dragSource == DragSource.BASE && isStashPanel((int) mouseX, (int) mouseY)) {
            sendBaseToStash(draggedBaseSlot, draggedSourceIndex);
            return true;
        }
        if (dragSource == DragSource.BASE && baseTarget != null && baseTarget != draggedBaseSlot) {
            sendBaseToBase(draggedBaseSlot, draggedSourceIndex, baseTarget);
            return true;
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

    private void startDrag(DragSource source, int sourceIndex, RaidEquipmentSlot baseSlot, ItemStack stack, double mouseX, double mouseY) {
        this.dragSource = source;
        this.draggedSourceIndex = sourceIndex;
        this.draggedBaseSlot = baseSlot;
        this.draggedStack = stack.copy();
        this.dragStartX = mouseX;
        this.dragStartY = mouseY;
    }

    private void clearDrag() {
        this.dragSource = DragSource.NONE;
        this.draggedSourceIndex = -1;
        this.draggedBaseSlot = null;
        this.draggedStack = ItemStack.EMPTY;
    }

    private boolean isClickRelease(double mouseX, double mouseY) {
        return Math.abs(mouseX - dragStartX) <= 3.0D && Math.abs(mouseY - dragStartY) <= 3.0D;
    }

    private void sendBaseToStash(RaidEquipmentSlot source, int sourceIndex) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.baseToStashButtonId(source, sourceIndex));
        }
    }

    private void sendStashToBase(int stashDisplayIndex, RaidEquipmentSlot target) {
        if (this.minecraft != null && this.minecraft.gameMode != null && target != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.stashToBaseButtonId(stashDisplayIndex, target));
        }
    }

    private void sendBaseToBase(RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        if (this.minecraft != null && this.minecraft.gameMode != null && source != null && target != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.baseToBaseButtonId(source, sourceIndex, target));
        }
    }

    private void sendSort(int sortId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BaseStashMenu.sortButtonId(sortId));
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
        if (dragSource == DragSource.BASE && draggedBaseSlot != null) {
            return slot.index == sourceMenuSlotIndex(draggedBaseSlot, draggedSourceIndex);
        }
        if (dragSource == DragSource.STASH) {
            return slot.index == this.menu.stashMenuSlotStart() + draggedSourceIndex;
        }
        return false;
    }

    private static int sourceMenuSlotIndex(RaidEquipmentSlot slot, int sourceIndex) {
        return switch (slot) {
            case PRIMARY_WEAPON -> BaseStashMenu.PRIMARY_WEAPON_START;
            case SECONDARY_WEAPON -> BaseStashMenu.SECONDARY_WEAPON_START;
            case BACKPACK -> BaseStashMenu.BACKPACK_START + sourceIndex;
            case VEST -> BaseStashMenu.VEST_START + sourceIndex;
            case SAFE_BOX -> BaseStashMenu.SAFE_BOX_START + sourceIndex;
        };
    }

    private RaidEquipmentSlot targetAt(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (inside(localX, localY, 144, 28, 72, 54)) {
            return RaidEquipmentSlot.PRIMARY_WEAPON;
        }
        if (inside(localX, localY, 144, 90, 72, 54)) {
            return RaidEquipmentSlot.SECONDARY_WEAPON;
        }
        if (inside(localX, localY, 8, 24, 128, 178)) {
            return RaidEquipmentSlot.BACKPACK;
        }
        if (inside(localX, localY, 8, 208, 82, 82)) {
            return RaidEquipmentSlot.VEST;
        }
        if (inside(localX, localY, 98, 208, 82, 82)) {
            return RaidEquipmentSlot.SAFE_BOX;
        }
        return null;
    }

    private boolean isStashPanel(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return inside(localX, localY, STASH_X, STASH_Y, this.imageWidth - STASH_X - 8, this.imageHeight - STASH_Y - 8);
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
}
