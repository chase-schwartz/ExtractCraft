package com.chaseschwartz.extractcraft.client;

import java.util.List;

import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
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
    private static final int SLOT_STEP = 18;
    private static final int PANEL_PADDING = 10;
    private static final int CONTENT_TOP_PADDING = 10;
    private static final int PANEL_GAP = 16;
    private static final int STORAGE_MIN_WIDTH = 96;
    private static final int WEAPON_PANEL_WIDTH = 154;
    private static final int WEAPON_PANEL_HEIGHT = 154;
    private static final int WEAPON_INNER_PADDING = 12;
    private static final int FOOTER_HEIGHT = 44;
    private static final int VIEWPORT_MARGIN = 8;
    private static final int MIN_SCREEN_WIDTH = 386;
    private static final int MIN_SCREEN_HEIGHT = 300;
    private static final int MAX_SCREEN_WIDTH = 720;
    private static final int MAX_SCREEN_HEIGHT = 520;
    private final ScreenLayout layout;
    private int scrollOffset;
    private String feedbackMessage = "";

    public PostRaidResultScreen(PostRaidResultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.layout = ScreenLayout.from(menu.snapshot());
        this.imageWidth = Math.min(MAX_SCREEN_WIDTH, Math.max(MIN_SCREEN_WIDTH, layout.contentWidth() + VIEWPORT_MARGIN * 2));
        this.imageHeight = Math.min(MAX_SCREEN_HEIGHT, Math.max(MIN_SCREEN_HEIGHT, headerHeight() + FOOTER_HEIGHT + layout.contentHeight()));
    }

    @Override
    protected void init() {
        int availableWidth = Math.max(220, this.width - 16);
        int availableHeight = Math.max(180, this.height - 16);
        int desiredWidth = Math.max(MIN_SCREEN_WIDTH, layout.contentWidth() + VIEWPORT_MARGIN * 2);
        int desiredHeight = Math.max(MIN_SCREEN_HEIGHT, headerHeight() + FOOTER_HEIGHT + Math.min(layout.contentHeight(), 300));
        this.imageWidth = Math.min(Math.min(desiredWidth, MAX_SCREEN_WIDTH), availableWidth);
        this.imageHeight = Math.min(Math.min(desiredHeight, MAX_SCREEN_HEIGHT), availableHeight);
        clampScroll();
        super.init();
        int buttonY = this.topPos + this.imageHeight - 28;
        if (!this.menu.snapshot().success()) {
            addRenderableWidget(Button.builder(Component.literal("Continue"), button -> sendChoice(PostRaidResultMenu.CONTINUE_BUTTON))
                    .bounds(this.leftPos + this.imageWidth / 2 - 50, buttonY, 100, 20)
                    .build());
            return;
        }

        addRenderableWidget(Button.builder(Component.literal("Move All To Stash"), button -> {
            this.feedbackMessage = STASH_FULL_WARNING;
            sendChoice(PostRaidResultMenu.MOVE_ALL_TO_STASH_BUTTON);
        })
                .bounds(this.leftPos + this.imageWidth / 2 - 148, buttonY, 138, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Keep On Character"), button -> {
            this.feedbackMessage = "";
            sendChoice(PostRaidResultMenu.KEEP_ON_CHARACTER_BUTTON);
        })
                .bounds(this.leftPos + this.imageWidth / 2 + 10, buttonY, 138, 20)
                .build());
    }

    @Override
    public void removed() {
        ClientRaidState.clearAllRaidUiState();
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderItemOverlays(guiGraphics);
        renderHoveredTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside((int) mouseX, (int) mouseY, this.leftPos + viewportX(), this.topPos + viewportY(), viewportWidth(), viewportHeight())) {
            scrollOffset -= (int) Math.round(scrollY * SLOT_STEP);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        clampScroll();
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        border(guiGraphics, x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);
        guiGraphics.fill(x + VIEWPORT_MARGIN, y + viewportY(), x + viewportWidth() + VIEWPORT_MARGIN, y + viewportY() + viewportHeight(), 0x8010151D);
        guiGraphics.fill(x + 1, y + headerHeight() - 1, x + this.imageWidth - 1, y + headerHeight(), 0x6649D8E8);
        guiGraphics.fill(x + 1, y + footerY(), x + this.imageWidth - 1, y + footerY() + 1, 0x6649D8E8);

        enableContentScissor(guiGraphics);
        int contentX = x + viewportX();
        int contentY = y + viewportY() - scrollOffset;
        section(guiGraphics, contentX + layout.backpack().x(), contentY + layout.backpack().y(), layout.backpack().width(), layout.backpack().height());
        section(guiGraphics, contentX + layout.vest().x(), contentY + layout.vest().y(), layout.vest().width(), layout.vest().height());
        section(guiGraphics, contentX + layout.safeBox().x(), contentY + layout.safeBox().y(), layout.safeBox().width(), layout.safeBox().height());
        section(guiGraphics, contentX + layout.weapons().x(), contentY + layout.weapons().y(), layout.weapons().width(), layout.weapons().height());
        guiGraphics.disableScissor();
        renderScrollbar(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        PostRaidResultMenu.ResultSnapshot snapshot = this.menu.snapshot();
        if (snapshot.success()) {
            renderSuccessHeader(guiGraphics, snapshot);
            renderScrollableContent(guiGraphics, snapshot, TEXT, "Backpack", "Vest", "Safe Box", "Weapons");
        } else {
            renderFailureHeader(guiGraphics, snapshot);
            renderScrollableContent(guiGraphics, snapshot, FAILED_TEXT, "Lost Backpack", "Lost Vest", "Safe Box Secured", "Lost Weapons");
        }
    }

    private void renderSuccessHeader(GuiGraphics guiGraphics, PostRaidResultMenu.ResultSnapshot snapshot) {
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
        renderFeedback(guiGraphics);
    }

    private void renderFailureHeader(GuiGraphics guiGraphics, PostRaidResultMenu.ResultSnapshot snapshot) {
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
    }

    private void renderScrollableContent(GuiGraphics guiGraphics, PostRaidResultMenu.ResultSnapshot snapshot, int primaryColor,
            String backpackLabel, String vestLabel, String safeLabel, String weaponsLabel) {
        enableContentScissor(guiGraphics);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(viewportX(), viewportY() - scrollOffset, 0.0D);
        renderStoragePanel(guiGraphics, backpackLabel, snapshot.backpackContainer(), snapshot.backpack(), layout.backpack(), primaryColor);
        renderStoragePanel(guiGraphics, vestLabel, snapshot.vestContainer(), snapshot.vest(), layout.vest(), primaryColor);
        renderStoragePanel(guiGraphics, safeLabel, snapshot.safeContainer(), snapshot.safeBox(), layout.safeBox(), snapshot.success() ? primaryColor : SECURED_TEXT);
        renderWeapons(guiGraphics, weaponsLabel, snapshot.weapons(), layout.weapons(), primaryColor);
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void renderFeedback(GuiGraphics guiGraphics) {
        if (feedbackMessage.isBlank()) {
            return;
        }

        guiGraphics.drawString(this.font, trim(feedbackMessage, 64), 14, footerY() + 8, WARNING_TEXT, false);
    }

    private void renderStoragePanel(GuiGraphics guiGraphics, String label, ItemSnapshot containerItem, List<ItemSnapshot> items, StoragePanel panel, int labelColor) {
        int x = panel.x() + PANEL_PADDING;
        int y = panel.y() + 9;
        guiGraphics.drawString(this.font, label, x, y, labelColor, false);
        guiGraphics.drawString(this.font, panel.columns() + "x" + panel.rows() + " | " + items.size() + " stacks", x, y + 12, MUTED_TEXT, false);
        if (containerItem != null) {
            drawSlot(guiGraphics, x, y + 26);
            guiGraphics.drawString(this.font, trim(containerItem.displayName(), Math.max(10, (panel.width() - 44) / 6)), x + 26, y + 29, TEXT, false);
        }

        int gridX = panel.gridX();
        int gridY = panel.gridY();
        int columns = panel.columns();
        int rows = panel.rows();
        int visibleSlots = columns * rows;
        for (int index = 0; index < visibleSlots; index++) {
            int slotX = gridX + (index % columns) * SLOT_STEP;
            int slotY = gridY + (index / columns) * SLOT_STEP;
            drawSlot(guiGraphics, slotX, slotY);
        }

        int hidden = 0;
        for (int index = 0; index < items.size(); index++) {
            ItemSnapshot item = items.get(index);
            GridCell cell = displayCell(item, index, columns, rows);
            if (cell == null) {
                hidden++;
                continue;
            }

            int slotX = gridX + cell.x() * SLOT_STEP;
            int slotY = gridY + cell.y() * SLOT_STEP;
            int widthCells = footprintWidth(item);
            int heightCells = footprintHeight(item);
            drawFootprint(guiGraphics, slotX, slotY, widthCells, heightCells);
        }

        if (hidden > 0) {
            guiGraphics.drawString(this.font, "+" + hidden, gridX + columns * SLOT_STEP + 4, gridY + rows * SLOT_STEP - 10, MUTED_TEXT, false);
        }
    }

    private void renderWeapons(GuiGraphics guiGraphics, String label, List<ItemSnapshot> weapons, Panel panel, int labelColor) {
        int x = panel.x();
        int y = panel.y();
        int innerX = x + WEAPON_INNER_PADDING;
        guiGraphics.drawString(this.font, label, innerX, y + 8, labelColor, false);
        renderWeaponPanel(guiGraphics, "Primary", weaponByLabel(weapons, "Primary"), innerX, y + 28);
        renderWeaponPanel(guiGraphics, "Secondary", weaponByLabel(weapons, "Secondary"), innerX, y + 88);
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
        guiGraphics.drawString(this.font, trim(item.displayName(), 15), x + 32, y + 25, TEXT, false);
        guiGraphics.drawString(this.font, item.value() + " cr", x + 32, y + 35, MUTED_TEXT, false);
    }

    private void renderItemOverlays(GuiGraphics guiGraphics) {
        PostRaidResultMenu.ResultSnapshot snapshot = this.menu.snapshot();
        enableContentScissor(guiGraphics);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos + viewportX(), this.topPos + viewportY() - scrollOffset, 0.0D);
        renderStoragePanelItems(guiGraphics, snapshot.backpackContainer(), snapshot.backpack(), layout.backpack());
        renderStoragePanelItems(guiGraphics, snapshot.vestContainer(), snapshot.vest(), layout.vest());
        renderStoragePanelItems(guiGraphics, snapshot.safeContainer(), snapshot.safeBox(), layout.safeBox());
        renderWeaponItems(guiGraphics, snapshot.weapons(), layout.weapons());
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    private void renderStoragePanelItems(GuiGraphics guiGraphics, ItemSnapshot containerItem, List<ItemSnapshot> items, StoragePanel panel) {
        int x = panel.x() + PANEL_PADDING;
        int y = panel.y() + 9;
        if (containerItem != null) {
            renderItem(guiGraphics, containerItem, x, y + 26);
        }

        int hidden = 0;
        for (int index = 0; index < items.size(); index++) {
            ItemSnapshot item = items.get(index);
            GridCell cell = displayCell(item, index, panel.columns(), panel.rows());
            if (cell == null) {
                hidden++;
                continue;
            }

            int slotX = panel.gridX() + cell.x() * SLOT_STEP;
            int slotY = panel.gridY() + cell.y() * SLOT_STEP;
            renderItem(guiGraphics, item, slotX, slotY, footprintWidth(item) * SLOT_STEP, footprintHeight(item) * SLOT_STEP);
        }
    }

    private void renderWeaponItems(GuiGraphics guiGraphics, List<ItemSnapshot> weapons, Panel panel) {
        int x = panel.x() + WEAPON_INNER_PADDING;
        ItemSnapshot primary = weaponByLabel(weapons, "Primary");
        if (primary != null) {
            renderItem(guiGraphics, primary, x + 8, panel.y() + 52);
        }
        ItemSnapshot secondary = weaponByLabel(weapons, "Secondary");
        if (secondary != null) {
            renderItem(guiGraphics, secondary, x + 8, panel.y() + 112);
        }
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
        guiGraphics.renderComponentTooltip(this.font, ExtractCraftTooltipBuilder.build(stack), mouseX, mouseY, stack);
    }

    private HoveredItem hoveredItem(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        if (!inside(localX, localY, viewportX(), viewportY(), viewportWidth(), viewportHeight())) {
            return null;
        }
        int contentX = localX - viewportX();
        int contentY = localY - viewportY() + scrollOffset;
        PostRaidResultMenu.ResultSnapshot snapshot = this.menu.snapshot();
        HoveredItem item = hoveredStoragePanel(contentX, contentY, snapshot.backpackContainer(), snapshot.backpack(), layout.backpack());
        if (item != null) {
            return item;
        }
        item = hoveredStoragePanel(contentX, contentY, snapshot.vestContainer(), snapshot.vest(), layout.vest());
        if (item != null) {
            return item;
        }
        item = hoveredStoragePanel(contentX, contentY, snapshot.safeContainer(), snapshot.safeBox(), layout.safeBox());
        if (item != null) {
            return item;
        }
        int weaponX = layout.weapons().x() + WEAPON_INNER_PADDING;
        if (inside(contentX, contentY, weaponX + 8, layout.weapons().y() + 52, 18, 18)) {
            ItemSnapshot weapon = weaponByLabel(this.menu.snapshot().weapons(), "Primary");
            return weapon == null ? null : new HoveredItem(weapon);
        }
        if (inside(contentX, contentY, weaponX + 8, layout.weapons().y() + 112, 18, 18)) {
            ItemSnapshot weapon = weaponByLabel(this.menu.snapshot().weapons(), "Secondary");
            return weapon == null ? null : new HoveredItem(weapon);
        }
        return null;
    }

    private HoveredItem hoveredStoragePanel(int localX, int localY, ItemSnapshot containerItem, List<ItemSnapshot> items, StoragePanel panel) {
        int headerX = panel.x() + PANEL_PADDING;
        int headerY = panel.y() + 35;
        if (containerItem != null && inside(localX, localY, headerX, headerY, 18, 18)) {
            return new HoveredItem(containerItem);
        }
        for (int index = 0; index < items.size(); index++) {
            ItemSnapshot item = items.get(index);
            GridCell cell = displayCell(item, index, panel.columns(), panel.rows());
            if (cell == null) {
                continue;
            }

            int slotX = panel.gridX() + cell.x() * SLOT_STEP;
            int slotY = panel.gridY() + cell.y() * SLOT_STEP;
            int width = footprintWidth(item) * SLOT_STEP;
            int height = footprintHeight(item) * SLOT_STEP;
            if (inside(localX, localY, slotX, slotY, width, height)) {
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

    private void renderItem(GuiGraphics guiGraphics, ItemSnapshot item, int x, int y, int width, int height) {
        ItemStack stack = displayStack(item);
        if (width <= SLOT_STEP && height <= SLOT_STEP) {
            renderItem(guiGraphics, item, x, y);
            return;
        }
        ManagedGridItemRenderer.renderItemCentered(guiGraphics, stack, x, y, width, height);
        CustomDurabilityBarRenderer.render(guiGraphics, stack, x, y, width, height);
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

    private int headerHeight() {
        return this.menu.snapshot().success() ? 46 : 70;
    }

    private int footerY() {
        return this.imageHeight - FOOTER_HEIGHT;
    }

    private int viewportX() {
        return VIEWPORT_MARGIN;
    }

    private int viewportY() {
        return headerHeight();
    }

    private int viewportWidth() {
        return Math.max(1, this.imageWidth - VIEWPORT_MARGIN * 2);
    }

    private int viewportHeight() {
        return Math.max(1, footerY() - viewportY());
    }

    private int maxScroll() {
        return Math.max(0, layout.contentHeight() - viewportHeight());
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private void enableContentScissor(GuiGraphics guiGraphics) {
        int x = this.leftPos + viewportX();
        int y = this.topPos + viewportY();
        guiGraphics.enableScissor(x, y, x + viewportWidth(), y + viewportHeight());
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }

        int trackX = this.leftPos + this.imageWidth - 6;
        int trackY = this.topPos + viewportY() + 2;
        int trackHeight = Math.max(8, viewportHeight() - 4);
        int thumbHeight = Math.max(12, (int) Math.round(trackHeight * (viewportHeight() / (double) layout.contentHeight())));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = trackY + (int) Math.round(thumbTravel * (scrollOffset / (double) maxScroll));
        guiGraphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0x442A323A);
        guiGraphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, 0xAA49D8E8);
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

    private static void drawFootprint(GuiGraphics guiGraphics, int x, int y, int widthCells, int heightCells) {
        if (widthCells <= 1 && heightCells <= 1) {
            return;
        }

        int width = widthCells * SLOT_STEP - 2;
        int height = heightCells * SLOT_STEP - 2;
        int color = 0x8849D8E8;
        guiGraphics.fill(x, y, x + width, y + height, 0x2210151D);
        border(guiGraphics, x - 1, y - 1, width + 2, height + 2, color);
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

    private static GridCell displayCell(ItemSnapshot item, int sequentialIndex, int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return null;
        }
        if (item.gridX() >= 0 && item.gridY() >= 0
                && item.gridX() + footprintWidth(item) <= columns
                && item.gridY() + footprintHeight(item) <= rows) {
            return new GridCell(item.gridX(), item.gridY());
        }

        if (sequentialIndex >= columns * rows) {
            return null;
        }
        return new GridCell(sequentialIndex % columns, sequentialIndex / columns);
    }

    private static int footprintWidth(ItemSnapshot item) {
        return Math.max(1, item.rotated() ? item.gridHeight() : item.gridWidth());
    }

    private static int footprintHeight(ItemSnapshot item) {
        return Math.max(1, item.rotated() ? item.gridWidth() : item.gridHeight());
    }

    private static StoragePanel storagePanel(String label, int x, int y, int columns, int rows, boolean hasContainer) {
        int safeColumns = Math.max(0, columns);
        int safeRows = Math.max(0, rows);
        int titleWidth = approximateTextWidth(label);
        int width = Math.max(Math.max(STORAGE_MIN_WIDTH, PANEL_PADDING * 2 + titleWidth), PANEL_PADDING * 2 + Math.max(0, safeColumns * SLOT_STEP));
        int gridY = y + (hasContainer ? 66 : 42);
        int height = gridY - y + Math.max(0, safeRows * SLOT_STEP) + PANEL_PADDING;
        return new StoragePanel(x, y, width, Math.max(64, height), safeColumns, safeRows, x + PANEL_PADDING, gridY);
    }

    private static ScreenLayout layoutForSuccess(PostRaidResultMenu.ResultSnapshot snapshot) {
        StoragePanel backpack = storagePanel("Backpack", 0, CONTENT_TOP_PADDING, snapshot.backpackGridWidth(), snapshot.backpackGridHeight(), snapshot.backpackContainer() != null);
        StoragePanel vest = storagePanel("Vest", 0, backpack.bottom() + 12, snapshot.vestGridWidth(), snapshot.vestGridHeight(), snapshot.vestContainer() != null);
        StoragePanel safeBox = storagePanel("Safe Box", vest.right() + PANEL_GAP, vest.y(), snapshot.safeGridWidth(), snapshot.safeGridHeight(), snapshot.safeContainer() != null);
        int weaponX = Math.max(backpack.right(), safeBox.right()) + 24;
        Panel weapons = new Panel(weaponX, CONTENT_TOP_PADDING, WEAPON_PANEL_WIDTH, WEAPON_PANEL_HEIGHT);
        int width = Math.max(MIN_SCREEN_WIDTH - VIEWPORT_MARGIN * 2, weapons.right());
        int height = Math.max(Math.max(vest.bottom(), safeBox.bottom()), weapons.bottom());
        return new ScreenLayout(width, height, backpack, vest, safeBox, weapons);
    }

    private static ScreenLayout layoutForFailure(PostRaidResultMenu.ResultSnapshot snapshot) {
        StoragePanel backpack = storagePanel("Lost Backpack", 0, CONTENT_TOP_PADDING, snapshot.backpackGridWidth(), snapshot.backpackGridHeight(), snapshot.backpackContainer() != null);
        int rightX = backpack.right() + 24;
        StoragePanel vest = storagePanel("Lost Vest", rightX, CONTENT_TOP_PADDING, snapshot.vestGridWidth(), snapshot.vestGridHeight(), snapshot.vestContainer() != null);
        StoragePanel safeBox = storagePanel("Safe Box Secured", vest.right() + PANEL_GAP, CONTENT_TOP_PADDING, snapshot.safeGridWidth(), snapshot.safeGridHeight(), snapshot.safeContainer() != null);
        Panel weapons = new Panel(rightX, Math.max(vest.bottom(), safeBox.bottom()) + 18, WEAPON_PANEL_WIDTH, WEAPON_PANEL_HEIGHT);
        int width = Math.max(560 - VIEWPORT_MARGIN * 2, Math.max(safeBox.right(), weapons.right()));
        int height = Math.max(backpack.bottom(), weapons.bottom());
        return new ScreenLayout(width, height, backpack, vest, safeBox, weapons);
    }

    private static int approximateTextWidth(String text) {
        return text == null ? 0 : text.length() * 6;
    }

    private record Panel(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private record StoragePanel(int x, int y, int width, int height, int columns, int rows, int gridX, int gridY) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private record ScreenLayout(int contentWidth, int contentHeight, StoragePanel backpack, StoragePanel vest, StoragePanel safeBox, Panel weapons) {
        static ScreenLayout from(PostRaidResultMenu.ResultSnapshot snapshot) {
            return snapshot.success() ? layoutForSuccess(snapshot) : layoutForFailure(snapshot);
        }
    }

    private record HoveredItem(ItemSnapshot item) {
    }

    private record GridCell(int x, int y) {
    }
}
