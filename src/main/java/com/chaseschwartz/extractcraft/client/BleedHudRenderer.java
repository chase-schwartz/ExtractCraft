package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.BleedStatus;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class BleedHudRenderer {
    private static final ResourceLocation LIGHT_ICON = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "textures/gui/status/light_bleed.png");
    private static final ResourceLocation HEAVY_ICON = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "textures/gui/status/heavy_bleed.png");
    private static final int BOX_SIZE = 18;
    private static final int ICON_SIZE = 14;
    private static final int TICK_PULSE_MILLIS = 650;

    private BleedHudRenderer() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !ClientRaidState.isInRaid()) {
            return;
        }
        BleedStatus status = ClientBleedState.status();
        if (!status.active()) {
            return;
        }
        long now = Util.getMillis();
        long pulseAge = now - ClientBleedState.lastDamagePulseMillis();
        double idlePulse = 0.5D + 0.5D * Math.sin(now / 165.0D);
        double tickPulse = pulseAge >= 0L && pulseAge < TICK_PULSE_MILLIS ? 1.0D - pulseAge / (double) TICK_PULSE_MILLIS : 0.0D;

        int x = minecraft.getWindow().getGuiScaledWidth() / 2 + 72;
        int heartY = minecraft.getWindow().getGuiScaledHeight() - 39;
        int y = Math.max(4, heartY - 7);
        int accent = status == BleedStatus.HEAVY ? 0xFFFF3131 : 0xFFFF8C5C;
        if (tickPulse > 0.0D) {
            int flash = 0x22 + (int) Math.round(0x55 * tickPulse);
            guiGraphics.fill(x - 2, y - 2, x + BOX_SIZE + 2, y + BOX_SIZE + 2, withAlpha(accent, flash));
            drawBorder(guiGraphics, x - 2, y - 2, BOX_SIZE + 4, BOX_SIZE + 4, withAlpha(accent, 0x55 + (int) Math.round(0x77 * tickPulse)));
            renderHealthPulse(guiGraphics, minecraft, tickPulse);
        }

        int iconDrawSize = ICON_SIZE + (tickPulse > 0.0D ? 2 : 0);
        int iconInset = (BOX_SIZE - iconDrawSize) / 2;
        ResourceLocation icon = status == BleedStatus.HEAVY ? HEAVY_ICON : LIGHT_ICON;
        guiGraphics.blit(icon, x + iconInset, y + iconInset, iconDrawSize, iconDrawSize, 0.0F, 0.0F, 64, 64, 64, 64);
    }

    private static void renderHealthPulse(GuiGraphics guiGraphics, Minecraft minecraft, double ratio) {
        if (minecraft.player == null) {
            return;
        }
        int x = minecraft.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = minecraft.getWindow().getGuiScaledHeight() - 39;
        int maxHearts = Math.max(10, (int) Math.ceil((minecraft.player.getMaxHealth() + minecraft.player.getAbsorptionAmount()) / 2.0F));
        int rows = Math.max(1, (int) Math.ceil(maxHearts / 10.0D));
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int alpha = 0x35 + (int) Math.round(0x75 * ratio);
        int whiteAlpha = 0x25 + (int) Math.round(0x55 * ratio);
        int offset = ratio > 0.65D ? 1 : 0;

        for (int heart = 0; heart < maxHearts; heart++) {
            int row = heart / 10;
            int column = heart % 10;
            int heartX = x + column * 8;
            int heartY = y - row * rowHeight - offset;
            guiGraphics.fill(heartX + 1, heartY + 1, heartX + 8, heartY + 8, withAlpha(0xFF3030, alpha));
            guiGraphics.fill(heartX + 2, heartY + 2, heartX + 7, heartY + 3, withAlpha(0xFFF8F8, whiteAlpha));
            guiGraphics.fill(heartX + 3, heartY + 6, heartX + 6, heartY + 7, withAlpha(0x5B0000, alpha / 2));
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha))) << 24);
    }

    private static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
