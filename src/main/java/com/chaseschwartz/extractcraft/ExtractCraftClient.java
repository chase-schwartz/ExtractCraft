package com.chaseschwartz.extractcraft;

import com.chaseschwartz.extractcraft.client.LootContainerOutlineRenderer;
import com.chaseschwartz.extractcraft.client.ClientRaidState;
import com.chaseschwartz.extractcraft.network.OpenRaidInventoryPayload;
import com.chaseschwartz.extractcraft.network.SelectRaidWeaponPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ExtractCraft.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ExtractCraft.MODID, value = Dist.CLIENT)
public class ExtractCraftClient {
    private static final int RAID_WEAPON_BRIDGE_HOTBAR_SLOT = 8;

    public ExtractCraftClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(LootContainerOutlineRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ExtractCraftClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ExtractCraftClient::onClientPreTick);
        NeoForge.EVENT_BUS.addListener(ExtractCraftClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, InputEvent.MouseButton.Pre.class, ExtractCraftClient::onMouseButtonPre);
        NeoForge.EVENT_BUS.addListener(ExtractCraftClient::onInteractionKeyMapping);
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

    private static void onClientPreTick(ClientTickEvent.Pre event) {
        if (!ClientRaidState.isInRaid()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (minecraft.player.getInventory().selected != RAID_WEAPON_BRIDGE_HOTBAR_SLOT) {
            minecraft.player.getInventory().selected = RAID_WEAPON_BRIDGE_HOTBAR_SLOT;
        }

        if (consumeClick(minecraft.options.keyHotbarSlots[0])) {
            PacketDistributor.sendToServer(new SelectRaidWeaponPayload(SelectRaidWeaponPayload.SELECT_PRIMARY));
        }
        if (consumeClick(minecraft.options.keyHotbarSlots[1])) {
            PacketDistributor.sendToServer(new SelectRaidWeaponPayload(SelectRaidWeaponPayload.SELECT_SECONDARY));
        }
    }

    private static boolean consumeClick(KeyMapping keyMapping) {
        boolean consumed = false;
        while (keyMapping.consumeClick()) {
            consumed = true;
        }
        return consumed;
    }

    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ClientRaidState.isInRaid() && minecraft.player != null && minecraft.screen == null) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new SelectRaidWeaponPayload(event.getScrollDeltaY() >= 0.0D
                    ? SelectRaidWeaponPayload.CYCLE_FORWARD
                    : SelectRaidWeaponPayload.CYCLE_BACKWARD));
        }
    }

    private static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        if (tryUseTargetedInteractable()) {
            event.setCanceled(true);
        }
    }

    private static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientRaidState.isInRaid()
                || !event.isUseItem()
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        event.setCanceled(tryUseTargetedInteractable());
    }

    private static boolean tryUseTargetedInteractable() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientRaidState.isInRaid()
                || minecraft.player == null
                || minecraft.level == null
                || minecraft.gameMode == null
                || minecraft.screen != null
                || !(minecraft.hitResult instanceof BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK
                || !isLikelyInteractableBlock(minecraft, blockHitResult.getBlockPos())) {
            return false;
        }

        InteractionResult result = minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, blockHitResult);
        return result.consumesAction();
    }

    private static boolean isLikelyInteractableBlock(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (blockEntity instanceof net.minecraft.world.Container) {
            return true;
        }

        MenuProvider menuProvider = state.getMenuProvider(minecraft.level, pos);
        if (menuProvider != null) {
            return true;
        }

        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof AnvilBlock;
    }

    private static boolean isVanillaInventoryScreen(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }
}
