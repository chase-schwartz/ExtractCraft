package com.chaseschwartz.extractcraft.client;

import java.util.List;

import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryMenu;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryMenu.ItemSnapshot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryMenu.StorageSnapshot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class RaidInventoryScreen extends AbstractContainerScreen<RaidInventoryMenu> {
    private static final int PANEL_COLOR = 0xF0101116;
    private static final int SECTION_COLOR = 0xFF1B2029;
    private static final int BORDER_COLOR = 0xFF49D8E8;
    private static final int TEXT = 0xFFDFFBFF;
    private static final int MUTED_TEXT = 0xFF9AA6B2;

    public RaidInventoryScreen(RaidInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 336;
        this.imageHeight = 232;
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

        section(guiGraphics, x + 8, y + 22, 320, 104);
        section(guiGraphics, x + 8, y + 132, 155, 92);
        section(guiGraphics, x + 173, y + 132, 155, 92);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        RaidInventoryMenu.RaidInventorySnapshot snapshot = this.menu.snapshot();
        guiGraphics.drawString(this.font, "Raid Inventory", 8, 7, TEXT, false);
        guiGraphics.drawString(this.font, String.format("Total %.1f weight | %d credits", snapshot.totalWeight(), snapshot.totalValue()), 176, 7, MUTED_TEXT, false);

        renderStorage(guiGraphics, "Backpack", snapshot.backpack(), 14, 28, 308, 6);
        renderStorage(guiGraphics, "Vest", snapshot.vest(), 14, 138, 143, 3);
        renderStorage(guiGraphics, "Safe Box", snapshot.safeBox(), 179, 138, 143, 3);
    }

    private void renderStorage(GuiGraphics guiGraphics, String label, StorageSnapshot storage, int x, int y, int width, int maxRows) {
        guiGraphics.drawString(this.font, label + " - " + storage.name(), x, y, TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("%d/%d slots | %.1f/%.1f weight | %d credits", storage.usedCapacity(), storage.capacity(), storage.usedWeight(), storage.maxWeight(), storage.totalValue()),
                x, y + 11, MUTED_TEXT, false);

        List<ItemSnapshot> items = storage.items();
        if (items.isEmpty()) {
            guiGraphics.drawString(this.font, "Empty", x, y + 30, MUTED_TEXT, false);
            return;
        }

        int rowY = y + 28;
        for (int i = 0; i < Math.min(maxRows, items.size()); i++) {
            ItemSnapshot item = items.get(i);
            renderItemRow(guiGraphics, item, x, rowY + i * 18, width);
        }
        if (items.size() > maxRows) {
            guiGraphics.drawString(this.font, "+" + (items.size() - maxRows) + " more", x, rowY + maxRows * 18 + 2, MUTED_TEXT, false);
        }
    }

    private void renderItemRow(GuiGraphics guiGraphics, ItemSnapshot item, int x, int y, int width) {
        ItemStack stack = displayStack(item);
        guiGraphics.renderItem(stack, x, y);
        guiGraphics.renderItemDecorations(this.font, stack, x, y);

        String name = trim(item.displayName(), width > 200 ? 26 : 15);
        guiGraphics.drawString(this.font, name, x + 20, y, TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("%s | %d slots | %.1f wt | %d cr", item.category(), item.slotCost(), item.weight(), item.value()),
                x + 20, y + 9, MUTED_TEXT, false);
    }

    private static ItemStack displayStack(ItemSnapshot item) {
        return ItemStackVariantFactory.create(item.lookupKey(), item.count())
                .orElseGet(() -> {
                    try {
                        ResourceLocation itemId = ResourceLocation.parse(item.itemId());
                        if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                            return new ItemStack(BuiltInRegistries.ITEM.get(itemId), item.count());
                        }
                    } catch (Exception ignored) {
                    }
                    return ItemStack.EMPTY;
                });
    }

    private static void section(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, SECTION_COLOR);
        guiGraphics.fill(x, y, x + width, y + 1, 0x6649D8E8);
    }

    private static String trim(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
