package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.RaidManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

public class ManagedDropService {
    public static final String MANAGED_DROP_TAG = "ExtractCraftManagedDrop";

    public static ItemEntity spawnManagedDrop(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        ItemEntity itemEntity = new ItemEntity(player.serverLevel(), player.getX(), player.getEyeY() - 0.3D, player.getZ(), copy);
        itemEntity.getPersistentData().putBoolean(MANAGED_DROP_TAG, true);
        itemEntity.setPickUpDelay(Short.MAX_VALUE);
        itemEntity.setDeltaMovement(player.getLookAngle().scale(0.25D).add(0.0D, 0.12D, 0.0D));
        player.serverLevel().addFreshEntity(itemEntity);
        return itemEntity;
    }

    public static boolean isManagedDrop(ItemEntity itemEntity) {
        return itemEntity.getPersistentData().getBoolean(MANAGED_DROP_TAG);
    }

    public static void handlePickupRequest(ServerPlayer player, UUID entityId) {
        Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
        if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive() || !isManagedDrop(itemEntity)) {
            player.sendSystemMessage(Component.literal("No managed item drop found."));
            return;
        }
        if (player.distanceToSqr(itemEntity) > 25.0D) {
            player.sendSystemMessage(Component.literal("Too far away."));
            return;
        }

        ItemStack stack = itemEntity.getItem().copy();
        if (stack.isEmpty()) {
            itemEntity.discard();
            return;
        }
        String itemName = stack.getHoverName().getString();

        RaidInventory.AddResult result = addToCustomInventory(player, stack);
        if (result.movedCount() <= 0) {
            player.sendSystemMessage(Component.literal("No space."));
            ExtractCraft.LOGGER.info("Managed drop pickup rejected: player={}, entity={}, item={}x {}, reason={}",
                    player.getGameProfile().getName(),
                    entityId,
                    stack.getCount(),
                    stack.getHoverName().getString(),
                    result.message());
            return;
        }

        stack.shrink(result.movedCount());
        if (stack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(stack);
            itemEntity.setPickUpDelay(Short.MAX_VALUE);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
        player.sendSystemMessage(Component.literal("Picked up " + result.movedCount() + "x " + itemName + "."));
    }

    public static RaidInventory.AddResult addToCustomInventory(ServerPlayer player, ItemStack stack) {
        if (RaidManager.isInRaid(player)) {
            RaidInventory.AddResult backpack = RaidInventoryManager.addStackTo(player, stack.copy(), RaidEquipmentSlot.BACKPACK);
            if (backpack.movedCount() > 0) {
                return backpack;
            }
            RaidInventory.AddResult vest = RaidInventoryManager.addStackTo(player, stack.copy(), RaidEquipmentSlot.VEST);
            if (vest.movedCount() > 0) {
                return vest;
            }
            return RaidInventoryManager.addStackTo(player, stack.copy(), RaidEquipmentSlot.SAFE_BOX);
        }

        PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
        RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, stack).orElse(null);
        if (item == null) {
            return new RaidInventory.AddResult(false, RaidEquipmentSlot.BACKPACK, "Item has no carry profile.");
        }
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
        if (profile == null) {
            return new RaidInventory.AddResult(false, RaidEquipmentSlot.BACKPACK, item.lookupKey() + " has no carry profile.");
        }
        RaidInventory.AddResult result = data.baseInventory().add(item, profile);
        if (result.movedCount() > 0) {
            PlayerStashService.save(player, data);
        }
        return result;
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (isManagedDrop(event.getItemEntity())) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().setPickUpDelay(Short.MAX_VALUE);
        }
    }
}
