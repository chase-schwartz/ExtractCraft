package com.chaseschwartz.extractcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ArmorMitigationHudRenderer {
    private static final ResourceLocation ARMOR_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final int TEXT = 0xFFDFFBFF;
    private static final float TEXT_SCALE = 0.78F;

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

        guiGraphics.blitSprite(ARMOR_FULL_SPRITE, x, y + 1, iconSize, iconSize);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + iconSize + gap, y + 2, 0.0F);
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        guiGraphics.drawString(minecraft.font, text, 0, 0, TEXT, false);
        guiGraphics.pose().popPose();
    }
}
