package com.chaseschwartz.extractcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class TimedActionHudRenderer {
    private static final int BAR_WIDTH = 118;
    private static final int BAR_HEIGHT = 6;

    private TimedActionHudRenderer() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        ClientTimedActionState.activeAction().ifPresentOrElse(
                action -> renderActive(guiGraphics, minecraft, action),
                () -> ClientTimedActionState.terminalMessage().ifPresent(message -> renderTerminal(guiGraphics, minecraft, message)));
    }

    private static void renderActive(GuiGraphics guiGraphics, Minecraft minecraft, ClientTimedActionState.ActiveAction action) {
        int elapsed = ClientTimedActionState.estimatedElapsedTicks(action);
        double ratio = Math.max(0.0D, Math.min(1.0D, elapsed / (double) Math.max(1, action.durationTicks())));
        int x = (minecraft.getWindow().getGuiScaledWidth() - BAR_WIDTH) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 74;
        String label = action.label() == null || action.label().isBlank() ? "Using..." : action.label();
        int labelX = (minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(label)) / 2;
        guiGraphics.drawString(minecraft.font, label, labelX, y - 11, 0xFFDFFBFF, true);
        guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xCC101317);
        guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF252B33);
        int fill = (int) Math.round(BAR_WIDTH * ratio);
        if (fill > 0) {
            guiGraphics.fill(x, y, x + fill, y + BAR_HEIGHT, 0xFFEAF7FF);
        }
    }

    private static void renderTerminal(GuiGraphics guiGraphics, Minecraft minecraft, String message) {
        int x = (minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(message)) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 85;
        guiGraphics.drawString(minecraft.font, message, x, y, 0xFFDFFBFF, true);
    }
}
