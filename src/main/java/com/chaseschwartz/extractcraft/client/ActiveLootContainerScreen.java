package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
    private double dragStartX;
    private double dragStartY;
    private boolean loggedLayout;

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
        }
        super.renderSlot(guiGraphics, slot);
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
            if (isClickRelease(mouseX, mouseY)) {
                return true;
            }

            RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
            if (target != null) {
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

    private boolean tryPlaceHeldStack(double mouseX, double mouseY) {
        RaidEquipmentSlot target = targetAt((int) mouseX, (int) mouseY);
        if (target != null) {
            if (dragSource == DragSource.CONTAINER) {
                sendTransfer(draggedSourceIndex, target);
                return true;
            }
            if (dragSource == DragSource.RAID_INVENTORY && draggedRaidSlot != target) {
                sendMove(draggedRaidSlot, draggedSourceIndex, target);
                return true;
            }
            return false;
        }

        if (dragSource == DragSource.RAID_INVENTORY && isContainerPanel((int) mouseX, (int) mouseY)) {
            sendReturn(draggedRaidSlot, draggedSourceIndex);
            return true;
        }

        if (dragSource == DragSource.CONTAINER && isContainerPanel((int) mouseX, (int) mouseY)) {
            clearDrag();
            return false;
        }

        return false;
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

    private boolean isWeaponSlot(Slot slot) {
        return slot.index == ActiveLootContainerMenu.PRIMARY_WEAPON_START
                || slot.index == ActiveLootContainerMenu.SECONDARY_WEAPON_START;
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
}
