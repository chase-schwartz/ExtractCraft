package com.chaseschwartz.extractcraft.client;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ExtractCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ExtractCraftClientModEvents {
    private ExtractCraftClientModEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ExtractCraft.ACTIVE_LOOT_CONTAINER_MENU.get(), ActiveLootContainerScreen::new);
        event.register(ExtractCraft.RAID_INVENTORY_MENU.get(), RaidInventoryScreen::new);
        event.register(ExtractCraft.BASE_STASH_MENU.get(), BaseStashScreen::new);
        event.register(ExtractCraft.POST_RAID_RESULT_MENU.get(), PostRaidResultScreen::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(com.chaseschwartz.extractcraft.ExtractCraftClient.QUICK_USE_KEY);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ExtractCraft.EXTRACT_RAIDER.get(), ExtractRaiderRenderer::new);
        ExtractCraft.LOGGER.info("Registered ExtractCraft raider renderer");
    }
}
