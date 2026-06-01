package com.chaseschwartz.extractcraft;

import com.chaseschwartz.extractcraft.client.LootContainerOutlineRenderer;
import com.chaseschwartz.extractcraft.client.ClientRaidState;
import com.chaseschwartz.extractcraft.network.OpenRaidInventoryPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ExtractCraft.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ExtractCraft.MODID, value = Dist.CLIENT)
public class ExtractCraftClient {
    public ExtractCraftClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(LootContainerOutlineRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ExtractCraftClient::onScreenOpening);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ExtractCraft.LOGGER.info("HELLO FROM CLIENT SETUP");
        ExtractCraft.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (!ClientRaidState.isInRaid() || !isVanillaInventoryScreen(screen)) {
            return;
        }
        if (ClientRaidState.consumeVanillaInventoryBypass()) {
            return;
        }

        event.setCanceled(true);
        PacketDistributor.sendToServer(OpenRaidInventoryPayload.INSTANCE);
    }

    private static boolean isVanillaInventoryScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }
}
