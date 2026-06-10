package com.chaseschwartz.extractcraft.client;

import java.util.ArrayList;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraftClient;
import com.chaseschwartz.extractcraft.network.QuickUseStatePayload;
import com.chaseschwartz.extractcraft.network.SetQuickUseSelectionPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class QuickUseRadialScreen extends Screen {
    private static final int RADIUS = 72;
    private static final int ITEM_SIZE = 22;
    private static final int INFO_WIDTH = 126;
    private static final int INFO_HEIGHT = 54;
    private static final int INFO_TEXT_WIDTH = INFO_WIDTH - 12;

    private boolean confirmed;
    private int ticksOpen;

    public QuickUseRadialScreen() {
        super(Component.literal("Quick Use"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticksOpen++;
        if (ticksOpen > 1 && !ExtractCraftClient.isQuickUseKeyDown()) {
            confirmSelection();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int centerY = height / 2;
        guiGraphics.fill(centerX - 88, centerY - 88, centerX + 88, centerY + 88, 0xAA0B1018);
        guiGraphics.fill(centerX - INFO_WIDTH / 2, centerY - INFO_HEIGHT / 2, centerX + INFO_WIDTH / 2, centerY + INFO_HEIGHT / 2, 0xCC151D27);
        guiGraphics.drawString(font, "Quick Use", centerX - font.width("Quick Use") / 2, centerY - 15, 0xFFDFFBFF, false);

        List<QuickUseStatePayload.Option> options = ClientQuickUseState.options();
        if (options.isEmpty()) {
            String empty = "No quick-use items";
            drawCenteredLines(guiGraphics, List.of(empty), centerX, centerY + 6, 0xFF8EA1B3);
            return;
        }

        int highlighted = highlightedIndex(mouseX, mouseY, options.size());
        for (int i = 0; i < options.size(); i++) {
            QuickUseStatePayload.Option option = options.get(i);
            double angle = -Math.PI / 2.0D + (Math.PI * 2.0D * i / options.size());
            int x = centerX + (int) Math.round(Math.cos(angle) * RADIUS) - ITEM_SIZE / 2;
            int y = centerY + (int) Math.round(Math.sin(angle) * RADIUS) - ITEM_SIZE / 2;
            boolean selected = i == highlighted;
            guiGraphics.fill(x - 2, y - 2, x + ITEM_SIZE + 2, y + ITEM_SIZE + 2, selected ? 0xDD46DDE8 : 0x99445462);
            guiGraphics.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, selected ? 0xDD1B3340 : 0xCC222A34);
            ItemStack stack = QuickUseHudRenderer.stackFor(option.itemId());
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, x + 3, y + 3);
            }
            if (option.count() > 1) {
                guiGraphics.drawString(font, String.valueOf(option.count()), x + ITEM_SIZE - font.width(String.valueOf(option.count())), y + ITEM_SIZE - 8, 0xFFFFFFFF, true);
            }
        }

        if (highlighted >= 0 && highlighted < options.size()) {
            QuickUseStatePayload.Option option = options.get(highlighted);
            drawCenteredLines(guiGraphics, wrapToTwoLines(option.displayName(), INFO_TEXT_WIDTH), centerX, centerY + 5, 0xFFEAF7FF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        confirmSelection();
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ExtractCraftClient.QUICK_USE_KEY.matches(keyCode, scanCode)) {
            confirmSelection();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void confirmSelection() {
        if (confirmed) {
            return;
        }
        confirmed = true;
        List<QuickUseStatePayload.Option> options = ClientQuickUseState.options();
        int highlighted = highlightedIndex(lastMouseX(), lastMouseY(), options.size());
        if (highlighted >= 0 && highlighted < options.size()) {
            PacketDistributor.sendToServer(new SetQuickUseSelectionPayload(options.get(highlighted).itemId()));
        }
        Minecraft.getInstance().setScreen(null);
    }

    private int highlightedIndex(double mouseX, double mouseY, int optionCount) {
        if (optionCount <= 0) {
            return -1;
        }
        double dx = mouseX - width / 2.0D;
        double dy = mouseY - height / 2.0D;
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0D;
        if (angle < 0.0D) {
            angle += Math.PI * 2.0D;
        }
        int index = (int) Math.round(angle / (Math.PI * 2.0D) * optionCount) % optionCount;
        return Math.max(0, Math.min(optionCount - 1, index));
    }

    private double lastMouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / (double) minecraft.getWindow().getScreenWidth();
    }

    private double lastMouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / (double) minecraft.getWindow().getScreenHeight();
    }

    private void drawCenteredLines(GuiGraphics guiGraphics, List<String> lines, int centerX, int y, int color) {
        int lineY = y;
        for (String line : lines) {
            guiGraphics.drawString(font, line, centerX - font.width(line) / 2, lineY, color, false);
            lineY += 10;
        }
    }

    private List<String> wrapToTwoLines(String text, int maxWidth) {
        String safeText = text == null ? "" : text.trim();
        if (safeText.isEmpty()) {
            return List.of("");
        }
        if (font.width(safeText) <= maxWidth) {
            return List.of(safeText);
        }

        List<String> lines = new ArrayList<>(2);
        StringBuilder current = new StringBuilder();
        for (String word : safeText.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (current.length() == 0) {
                lines.add(ellipsize(word, maxWidth));
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
            if (lines.size() == 1) {
                continue;
            }
            break;
        }
        if (lines.size() < 2 && !current.isEmpty()) {
            lines.add(current.toString());
        }
        if (lines.size() > 2) {
            return List.of(lines.get(0), ellipsize(lines.get(1), maxWidth));
        }
        if (lines.size() == 2) {
            return List.of(lines.get(0), ellipsize(lines.get(1), maxWidth));
        }
        return List.of(ellipsize(safeText, maxWidth));
    }

    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width(ellipsis))) + ellipsis;
    }
}
