package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.network.QuickUseStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class QuickUseHudRenderer {
    private static final int WIDTH = 112;
    private static final int HEIGHT = 24;

    private QuickUseHudRenderer() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !ClientRaidState.isInRaid()) {
            return;
        }
        QuickUseStatePayload.Option option = ClientQuickUseState.selectedOption().orElse(null);
        if (option == null) {
            return;
        }

        int x = minecraft.getWindow().getGuiScaledWidth() - WIDTH - 10;
        int y = minecraft.getWindow().getGuiScaledHeight() - 62;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xAA10151B);
        guiGraphics.fill(x, y, x + WIDTH, y + 1, 0xCC4CECF4);
        guiGraphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, 0x7730525A);
        ItemStack stack = stackFor(option.itemId());
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 4, y + 4);
        }
        String name = trimToWidth(minecraft, option.displayName(), WIDTH - 28);
        guiGraphics.drawString(minecraft.font, name, x + 24, y + 5, 0xFFDFFBFF, false);
        guiGraphics.drawString(minecraft.font, "Quick Use", x + 24, y + 15, 0xFF8EA1B3, false);
    }

    static ItemStack stackFor(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
        } catch (RuntimeException ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static String trimToWidth(Minecraft minecraft, String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) {
            return text;
        }
        return minecraft.font.plainSubstrByWidth(text, Math.max(1, maxWidth - minecraft.font.width("..."))) + "...";
    }
}
