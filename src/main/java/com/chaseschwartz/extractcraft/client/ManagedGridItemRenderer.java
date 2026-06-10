package com.chaseschwartz.extractcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class ManagedGridItemRenderer {
    private ManagedGridItemRenderer() {
    }

    public static void renderItemCentered(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int width, int height) {
        float scale = Math.min(3.0F, Math.max(1.0F, (Math.min(width, height) - 2) / 16.0F));
        double iconX = x + (width - 16.0D * scale) / 2.0D;
        double iconY = y + (height - 16.0D * scale) / 2.0D;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX, iconY, 0.0D);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        guiGraphics.pose().popPose();
    }
}
