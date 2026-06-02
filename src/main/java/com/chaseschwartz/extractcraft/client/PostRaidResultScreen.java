package com.chaseschwartz.extractcraft.client;

import java.util.List;

import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.raid.inventory.BaseStashMenu;
import com.chaseschwartz.extractcraft.raid.inventory.PostRaidResultMenu;
import com.chaseschwartz.extractcraft.raid.inventory.PostRaidResultMenu.ItemSnapshot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class PostRaidResultScreen extends AbstractContainerScreen<PostRaidResultMenu> {
    private static final int PANEL_COLOR = 0xF0101116;
    private static final int SECTION_COLOR = 0xFF1B2029;
    private static final int SLOT_COLOR = 0xFF40444D;
    private static final int BORDER_COLOR = 0xFF49D8E8;
    private static final int TEXT = 0xFFDFFBFF;
    private static final int MUTED_TEXT = 0xFF9AA6B2;
    private static final int WARNING_TEXT = 0xFFFFC857;
    private static final int FAILED_TEXT = 0xFFFF6F61;
    private static final int SECURED_TEXT = 0xFF8DFFF2;
    private static final String STASH_FULL_WARNING = "Stash is full. Choose Keep On Character or free stash space.";
    private static final int BACKPACK_X = 14;
    private static final int BACKPACK_Y = 76;
    private static final int VEST_X = 14;
    private static final int VEST_Y = 222;
    private static final int SAFE_X = 122;
    private static final int SAFE_Y = 222;
    private static final int WEAPON_X = 252;
    private static final int WEAPON_Y = 46;
    private static final int FAILED_BACKPACK_X = 14;
    private static final int FAILED_BACKPACK_Y = 94;
    private static final int FAILED_VEST_X = 14;
    private static final int FAILED_VEST_Y = 244;
    private static final int FAILED_SAFE_X = 122;
    private static final int FAILED_SAFE_Y = 244;
    private static final int FAILED_WEAPON_X = 252;
    private static final int FAILED_WEAPON_Y = 70;
    private static final int BACKPACK_COLUMNS = 6;
    private static final int VEST_COLUMNS = 4;
    private static final int SAFE_BOX_COLUMNS = 3;
    private static final int BACKPACK_ROWS = rows(BaseStashMenu.BACKPACK_DISPLAY_SLOTS, BACKPACK_COLUMNS);
    private static final int VEST_ROWS = rows(BaseStashMenu.VEST_DISPLAY_SLOTS, VEST_COLUMNS);
    private static final int SAFE_BOX_ROWS = rows(BaseStashMenu.SAFE_BOX_DISPLAY_SLOTS, SAFE_BOX_COLUMNS);
    private static final int SLOT_STEP = 18;
    private String feedbackMessage = "";

    public PostRaidResultScreen(PostRaidResultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 386;
        this.imageHeight = 336;
    }

    @Override
    protected void init() {
        super.init();
        int buttonY = this.topPos + this.imageHeight - 28;
        if (!this.menu.snapshot().success()) {
            addRenderableWidget(Button.builder(Component.literal("Continue"), button -> sendChoice(PostRaidResultMenu.CONTINUE_BUTTON))
                    .bounds(this.leftPos + 143, buttonY, 100, 20)
                    .build());
            return;
        }

        addRenderableWidget(Button.builder(Component.literal("Move All To Stash"), button -> {
            this.feedbackMessage = STASH_FULL_WARNING;
            sendChoice(PostRaidResultMenu.MOVE_ALL_TO_STASH_BUTTON);
        })
                .bounds(this.leftPos + 46, buttonY, 138, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Keep On Character"), button -> {
            this.feedbackMessage = "";
            sendChoice(PostRaidResultMenu.KEEP_ON_CHARACTER_BUTTON);
        })
                .bounds(this.leftPos + 202, buttonY, 138, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoveredTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        border(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);

        if (this.menu.snapshot().success()) {
            section(guiGraphics, x + 8, y + 46, 202, 142);
            section(guiGraphics, x + 8, y + 196, 96, 90);
            section(guiGraphics, x + 114, y + 196, 78, 90);
            section(guiGraphics, x + 242, y + 46, 132, 148);
        } else {
            section(guiGraphics, x + 8, y + 64, 202, 152);
            section(guiGraphics, x + 8, y + 220, 96, 86);
            section(guiGraphics, x + 114, y + 220, 112, 86);
            section(guiGraphics, x + 242, y + 58, 132, 154);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PostRaidResultMenu.ResultSnapshot snapshot = this.menu.snapshot();
        if (snapshot.success()) {
            renderSuccessLabels(guiGraphics, snapshot);
        } else {
            renderFailureLabels(guiGraphics, snapshot);
        }
    }

    private void renderSuccessLabels(GuiGraphics guiGraphics, PostRaidResultMenu.ResultSnapshot snapshot) {
        guiGraphics.drawString(this.font, "RAID SUCCESSFUL", 14, 10, SECURED_TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("Time %s | Value %d cr | %.2f wt | Items %d | Stacks %d",
                        formatDuration(snapshot.elapsedSeconds()),
                        snapshot.totalValue(),
                        snapshot.totalWeight(),
                        snapshot.itemCount(),
                        snapshot.stackCount()),
                14,
                27,
                MUTED_TEXT,
                false);

        renderStorageGrid(guiGraphics, "Backpack", snapshot.backpack(), BACKPACK_X, BACKPACK_Y, BACKPACK_COLUMNS, BACKPACK_ROWS, TEXT);
        renderStorageGrid(guiGraphics, "Vest", snapshot.vest(), VEST_X, VEST_Y, VEST_COLUMNS, VEST_ROWS, TEXT);
        renderStorageGrid(guiGraphics, "Safe Box", snapshot.safeBox(), SAFE_X, SAFE_Y, SAFE_BOX_COLUMNS, SAFE_BOX_ROWS, TEXT);
        renderWeapons(guiGraphics, "Weapons", snapshot.weapons(), WEAPON_X, WEAPON_Y, TEXT);
        renderFeedback(guiGraphics);
    }

    private void renderFailureLabels(GuiGraphics guiGraphics, PostRaidResultMenu.ResultSnapshot snapshot) {
        guiGraphics.drawString(this.font, "RAID FAILED", 14, 10, FAILED_TEXT, false);
        guiGraphics.drawString(this.font,
                String.format("Reason: %s | Time: %s",
                        snapshot.reason(),
                        formatDuration(snapshot.elapsedSeconds())),
                14,
                27,
                MUTED_TEXT,
                false);
        guiGraphics.drawString(this.font,
                String.format("Lost: %d cr | %.2f wt | Items %d | Stacks %d",
                        snapshot.totalValue(),
                        snapshot.totalWeight(),
                        snapshot.itemCount(),
                        snapshot.stackCount()),
                14,
                39,
                MUTED_TEXT,
                false);
        guiGraphics.drawString(this.font,
                String.format("Secured: %d cr | %.2f wt | Items %d | Stacks %d",
                        snapshot.securedValue(),
                        snapshot.securedWeight(),
                        snapshot.securedItemCount(),
                        snapshot.securedStackCount()),
                14,
                51,
                SECURED_TEXT,
                false);

        renderStorageGrid(guiGraphics, "Lost Backpack", snapshot.backpack(), FAILED_BACKPACK_X, FAILED_BACKPACK_Y, BACKPACK_COLUMNS, BACKPACK_ROWS, FAILED_TEXT);
        renderStorageGrid(guiGraphics, "Lost Vest", snapshot.vest(), FAILED_VEST_X, FAILED_VEST_Y, VEST_COLUMNS, VEST_ROWS, FAILED_TEXT);
        renderStorageGrid(guiGraphics, "Safe Box Secured", snapshot.safeBox(), FAILED_SAFE_X, FAILED_SAFE_Y, SAFE_BOX_COLUMNS, SAFE_BOX_ROWS, SECURED_TEXT);
        renderWeapons(guiGraphics, "Lost Weapons", snapshot.weapons(), FAILED_WEAPON_X, FAILED_WEAPON_Y, FAILED_TEXT);
    }

    private void renderFeedback(GuiGraphics guiGraphics) {
        if (feedbackMessage.isBlank()) {
            return;
        }

        guiGraphics.drawString(this.font, trim(feedbackMessage, 64), 14, this.imageHeight - 44, WARNING_TEXT, false);
    }

    private void renderStorageGrid(GuiGraphics guiGraphics, String label, List<ItemSnapshot> items, int x, int y, int columns, int rows, int labelColor) {
        guiGraphics.drawString(this.font, label, x, y - 18, labelColor, false);
        int visibleSlots = columns * rows;
        for (int index = 0; index < visibleSlots; index++) {
            int slotX = x + (index % columns) * SLOT_STEP;
            int slotY = y + (index / columns) * SLOT_STEP;
            drawSlot(guiGraphics, slotX, slotY);
            if (index < items.size()) {
                renderItem(guiGraphics, items.get(index), slotX, slotY);
            }
        }
        if (items.size() > visibleSlots) {
            guiGraphics.drawString(this.font, "+" + (items.size() - visibleSlots), x + columns * SLOT_STEP + 4, y + rows * SLOT_STEP - 10, MUTED_TEXT, false);
        }
    }

    private void renderWeapons(GuiGraphics guiGraphics, String label, List<ItemSnapshot> weapons, int x, int y, int labelColor) {
        guiGraphics.drawString(this.font, label, x, y + 8, labelColor, false);
        renderWeaponPanel(guiGraphics, "Primary", weaponByLabel(weapons, "Primary"), x, y + 28);
        renderWeaponPanel(guiGraphics, "Secondary", weaponByLabel(weapons, "Secondary"), x, y + 88);
    }

    private void renderWeaponPanel(GuiGraphics guiGraphics, String label, ItemSnapshot item, int x, int y) {
        guiGraphics.fill(x, y, x + 110, y + 50, 0xFF222832);
        border(guiGraphics, x, y, 110, 50, 0x6649D8E8);
        guiGraphics.drawString(this.font, label, x + 8, y + 6, TEXT, false);
        drawWeaponSlot(guiGraphics, x + 8, y + 24);
        if (item == null) {
            guiGraphics.drawString(this.font, "Empty", x + 32, y + 28, MUTED_TEXT, false);
            return;
        }
        renderItem(guiGraphics, item, x + 9, y + 25);
        guiGraphics.drawString(this.font, trim(item.displayName(), 15), x + 32, y + 25, TEXT, false);
        guiGraphics.drawString(this.font, item.value() + " cr", x + 32, y + 35, MUTED_TEXT, false);
    }

    private void renderHoveredTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        HoveredItem hovered = hoveredItem(mouseX, mouseY);
        if (hovered == null) {
            return;
        }

        ItemStack stack = displayStack(hovered.item());
        if (stack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, Component.literal(hovered.item().displayName()), mouseX, mouseY);
            return;
        }
        guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
    }

    private HoveredItem hoveredItem(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        boolean success = this.menu.snapshot().success();
        int backpackX = success ? BACKPACK_X : FAILED_BACKPACK_X;
        int backpackY = success ? BACKPACK_Y : FAILED_BACKPACK_Y;
        int vestX = success ? VEST_X : FAILED_VEST_X;
        int vestY = success ? VEST_Y : FAILED_VEST_Y;
        int safeX = success ? SAFE_X : FAILED_SAFE_X;
        int safeY = success ? SAFE_Y : FAILED_SAFE_Y;
        int weaponX = success ? WEAPON_X : FAILED_WEAPON_X;
        int weaponY = success ? WEAPON_Y : FAILED_WEAPON_Y;

        HoveredItem item = hoveredStorageItem(localX, localY, this.menu.snapshot().backpack(), backpackX, backpackY, BACKPACK_COLUMNS, BACKPACK_ROWS);
        if (item != null) {
            return item;
        }
        item = hoveredStorageItem(localX, localY, this.menu.snapshot().vest(), vestX, vestY, VEST_COLUMNS, VEST_ROWS);
        if (item != null) {
            return item;
        }
        item = hoveredStorageItem(localX, localY, this.menu.snapshot().safeBox(), safeX, safeY, SAFE_BOX_COLUMNS, SAFE_BOX_ROWS);
        if (item != null) {
            return item;
        }
        if (inside(localX, localY, weaponX + 8, weaponY + 52, 18, 18)) {
            ItemSnapshot weapon = weaponByLabel(this.menu.snapshot().weapons(), "Primary");
            return weapon == null ? null : new HoveredItem(weapon);
        }
        if (inside(localX, localY, weaponX + 8, weaponY + 112, 18, 18)) {
            ItemSnapshot weapon = weaponByLabel(this.menu.snapshot().weapons(), "Secondary");
            return weapon == null ? null : new HoveredItem(weapon);
        }
        return null;
    }

    private HoveredItem hoveredStorageItem(int localX, int localY, List<ItemSnapshot> items, int x, int y, int columns, int rows) {
        for (int index = 0; index < Math.min(items.size(), columns * rows); index++) {
            int slotX = x + (index % columns) * SLOT_STEP;
            int slotY = y + (index / columns) * SLOT_STEP;
            if (inside(localX, localY, slotX, slotY, 16, 16)) {
                return new HoveredItem(items.get(index));
            }
        }
        return null;
    }

    private static ItemSnapshot weaponByLabel(List<ItemSnapshot> weapons, String label) {
        return weapons.stream().filter(item -> label.equals(item.sectionLabel())).findFirst().orElse(null);
    }

    private void sendChoice(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void renderItem(GuiGraphics guiGraphics, ItemSnapshot item, int x, int y) {
        ItemStack stack = displayStack(item);
        guiGraphics.renderItem(stack, x, y);
        guiGraphics.renderItemDecorations(this.font, stack, x, y);
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

    private static String formatDuration(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private static String trim(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF1C2028);
        guiGraphics.fill(x, y, x + 16, y + 16, SLOT_COLOR);
    }

    private static void drawWeaponSlot(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF1C2028);
        guiGraphics.fill(x, y, x + 18, y + 18, SLOT_COLOR);
    }

    private static void section(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, SECTION_COLOR);
        border(guiGraphics, x, y, width, height, 0x6649D8E8);
    }

    private static void border(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static boolean inside(int x, int y, int rectX, int rectY, int width, int height) {
        return x >= rectX && x < rectX + width && y >= rectY && y < rectY + height;
    }

    private static int rows(int slotCount, int columns) {
        return (int) Math.ceil(slotCount / (double) columns);
    }

    private record HoveredItem(ItemSnapshot item) {
    }
}
