package com.chaseschwartz.extractcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class ManagedGridItemRenderer {
    private ManagedGridItemRenderer() {
    }

    public static void renderItemCentered(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int width, int height) {
        int interiorWidth = Math.max(16, width - 2);
        int interiorHeight = Math.max(16, height - 2);
        float scale = Math.min(3.0F, Math.max(1.0F, Math.min(interiorWidth, interiorHeight) / 16.0F));
        double iconX = x + (interiorWidth - 16.0D * scale) / 2.0D;
        double iconY = y + (interiorHeight - 16.0D * scale) / 2.0D;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(iconX, iconY, 0.0D);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        guiGraphics.pose().popPose();
    }
}
