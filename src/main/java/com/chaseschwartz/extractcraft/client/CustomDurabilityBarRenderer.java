package com.chaseschwartz.extractcraft.client;

import java.util.Optional;

import com.chaseschwartz.extractcraft.durability.DurabilityData;
import com.chaseschwartz.extractcraft.durability.DurabilityService;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class CustomDurabilityBarRenderer {
    private static final int TRACK_COLOR = 0xCC101317;
    private static final int HIGH_COLOR = 0xFF37D85A;
    private static final int MEDIUM_COLOR = 0xFFFFB02E;
    private static final int LOW_COLOR = 0xFFE33E35;

    private CustomDurabilityBarRenderer() {
    }

    public static void render(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int width, int height) {
        if (stack.isEmpty() || width < 8 || height < 8) {
            return;
        }

        Optional<DurabilityData> data = DurabilityService.getOrInitialize(stack);
        if (data.isEmpty()) {
            return;
        }

        int currentMax = Math.max(1, data.get().currentMaxDurability());
        if (data.get().currentDurability() >= currentMax) {
            return;
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, data.get().currentDurability() / (double) currentMax));
        BarGeometry geometry = barGeometry(x, y, width, height);
        int fillWidth = fillWidth(geometry.width(), ratio);

        guiGraphics.fill(geometry.x(), geometry.y(), geometry.x() + geometry.width(), geometry.y() + geometry.height(), TRACK_COLOR);
        if (fillWidth > 0) {
            guiGraphics.fill(geometry.x(), geometry.y(), geometry.x() + fillWidth, geometry.y() + geometry.height(), colorFor(ratio));
        }
    }

    private static BarGeometry barGeometry(int x, int y, int width, int height) {
        int barX = x;
        int barY = y + height - 4;
        int barWidth = Math.max(4, width - 2);
        return new BarGeometry(barX, barY, barWidth, 2);
    }

    private static int fillWidth(int width, double ratio) {
        if (ratio >= 1.0D) {
            return width;
        }
        if (ratio <= 0.0D) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(width * ratio));
    }

    private static int colorFor(double ratio) {
        if (ratio > 0.60D) {
            return HIGH_COLOR;
        }
        if (ratio >= 0.30D) {
            return MEDIUM_COLOR;
        }
        return LOW_COLOR;
    }

    private record BarGeometry(int x, int y, int width, int height) {
    }
}
