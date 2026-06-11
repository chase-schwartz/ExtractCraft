package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.BleedStatus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class FractureHudRenderer {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "textures/gui/status/fracture.png");
    private static final int BOX_SIZE = 18;
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 4;

    private FractureHudRenderer() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !ClientRaidState.isInRaid() || !ClientFractureState.fractured()) {
            return;
        }

        int statusAnchorX = minecraft.getWindow().getGuiScaledWidth() / 2 + 72;
        int x = ClientBleedState.status() == BleedStatus.NONE ? statusAnchorX : statusAnchorX - BOX_SIZE - ICON_GAP;
        int heartY = minecraft.getWindow().getGuiScaledHeight() - 39;
        int y = Math.max(4, heartY - 7);
        int iconInset = (BOX_SIZE - ICON_SIZE) / 2;
        guiGraphics.blit(ICON, x + iconInset, y + iconInset, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, 64, 64, 64, 64);
    }
}
