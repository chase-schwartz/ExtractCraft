package com.chaseschwartz.extractcraft.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ArmorMitigationHudRenderer {
    private static final ResourceLocation ARMOR_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final int TEXT = 0xFFDFFBFF;
    private static final float TEXT_SCALE = 0.78F;
    private static final int PULSE_MILLIS = 550;

    private ArmorMitigationHudRenderer() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        int combined = ClientArmorMitigationState.combinedPercent();
        if (combined <= 0) {
            return;
        }

        String text = combined + "%";
        int iconSize = 9;
        int gap = 2;
        int x = minecraft.getWindow().getGuiScaledWidth() / 2 - 91;
        int heartY = minecraft.getWindow().getGuiScaledHeight() - 39;
        int y = Math.max(4, heartY - 13);
        long pulseAge = Util.getMillis() - ClientArmorMitigationState.lastPulseMillis();
        double pulse = pulseAge >= 0L && pulseAge < PULSE_MILLIS ? 1.0D - pulseAge / (double) PULSE_MILLIS : 0.0D;
        int pulseGrow = pulse > 0.0D ? 2 : 0;
        int pulseAlpha = (int) Math.round(0x80 * pulse);

        if (pulse > 0.0D) {
            guiGraphics.fill(x - 1, y, x + iconSize + 1, y + iconSize + 2, (pulseAlpha << 24) | 0xDFFBFF);
        }
        guiGraphics.blitSprite(ARMOR_FULL_SPRITE, x - pulseGrow / 2, y + 1 - pulseGrow / 2, iconSize + pulseGrow, iconSize + pulseGrow);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + iconSize + gap, y + 2, 0.0F);
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        guiGraphics.drawString(minecraft.font, text, 0, 0, brighten(TEXT, pulse), false);
        guiGraphics.pose().popPose();
    }

    private static int brighten(int color, double pulse) {
        if (pulse <= 0.0D) {
            return color;
        }
        int alpha = color & 0xFF000000;
        int red = Math.min(255, ((color >> 16) & 0xFF) + (int) Math.round(28 * pulse));
        int green = Math.min(255, ((color >> 8) & 0xFF) + (int) Math.round(28 * pulse));
        int blue = Math.min(255, (color & 0xFF) + (int) Math.round(28 * pulse));
        return alpha | (red << 16) | (green << 8) | blue;
    }
}
