package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ActiveLootContainerScreen extends AbstractContainerScreen<ActiveLootContainerMenu> {
    private static final int SLOT_COLOR = 0xFF40444D;
    private static final int PANEL_COLOR = 0xE0101116;
    private static final int BORDER_COLOR = 0xFF49D8E8;

    public ActiveLootContainerScreen(ActiveLootContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 72 + menu.rows() * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        guiGraphics.fill(x, y, x + this.imageWidth, y + 1, BORDER_COLOR);
        guiGraphics.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(x, y, x + 1, y + this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, BORDER_COLOR);

        for (int row = 0; row < this.menu.rows(); row++) {
            for (int column = 0; column < 9; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + 23 + row * 18;
                guiGraphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF1C2028);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, SLOT_COLOR);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xDFFBFFFF, false);
        int statusY = 28 + this.menu.rows() * 18;
        String capacity = "Backpack: " + this.menu.usedCapacity() + "/" + this.menu.maxCapacity() + " slots";
        String weight = String.format("Weight: %.1f/%.1f", this.menu.usedWeight(), this.menu.maxWeight());
        String value = "Value: " + this.menu.totalValue();
        guiGraphics.drawString(this.font, capacity, 8, statusY, 0xFFC9F7FF, false);
        guiGraphics.drawString(this.font, weight, 8, statusY + 11, 0xFFC9F7FF, false);
        guiGraphics.drawString(this.font, value, 8, statusY + 22, 0xFFC9F7FF, false);
        guiGraphics.drawString(this.font, "Click/shift-click loot to move it into RaidInventory.", 8, statusY + 36, 0xFF9AA6B2, false);
    }
}
