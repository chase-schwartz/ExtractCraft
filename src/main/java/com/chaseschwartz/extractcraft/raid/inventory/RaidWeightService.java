package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class RaidWeightService {
    private static final ResourceLocation MOVEMENT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "raid_overweight_movement");
    private static final Map<UUID, OverweightStage> LAST_STAGE = new HashMap<>();

    private RaidWeightService() {
    }

    public static void tickRaidPlayer(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        WeightStatus status = status(inventory);
        applyMovementModifier(player, status.stage());
        notifyStageChange(player, status.stage());
    }

    public static void clear(ServerPlayer player) {
        removeMovementModifier(player);
        LAST_STAGE.remove(player.getUUID());
    }

    public static WeightStatus status(RaidInventory inventory) {
        double currentWeight = inventory.totalWeight();
        double maxWeight = Math.max(0.0D, inventory.maxCarryWeight());
        OverweightStage stage;
        if (maxWeight <= 0.0D) {
            stage = currentWeight > 0.0D ? OverweightStage.IMMOBILIZED : OverweightStage.NORMAL;
        } else if (currentWeight <= maxWeight) {
            stage = OverweightStage.NORMAL;
        } else if (currentWeight <= maxWeight + maxWeight / 2.0D) {
            stage = OverweightStage.OVERWEIGHT;
        } else {
            stage = OverweightStage.IMMOBILIZED;
        }
        return new WeightStatus(currentWeight, maxWeight, stage, movementMultiplier(stage));
    }

    private static void applyMovementModifier(ServerPlayer player, OverweightStage stage) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        double amount = switch (stage) {
            case NORMAL -> 0.0D;
            case OVERWEIGHT -> -0.5D;
            case IMMOBILIZED -> -1.0D;
        };
        if (amount == 0.0D) {
            movementSpeed.removeModifier(MOVEMENT_MODIFIER_ID);
            return;
        }
        movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeMovementModifier(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_MODIFIER_ID);
        }
    }

    private static void notifyStageChange(ServerPlayer player, OverweightStage stage) {
        OverweightStage previous = LAST_STAGE.put(player.getUUID(), stage);
        if (previous == stage) {
            return;
        }

        Component message = switch (stage) {
            case NORMAL -> Component.literal("Weight normal.");
            case OVERWEIGHT -> Component.literal("Overweight: movement reduced.");
            case IMMOBILIZED -> Component.literal("Severely overweight: cannot move.");
        };
        player.displayClientMessage(message, true);
    }

    private static double movementMultiplier(OverweightStage stage) {
        return switch (stage) {
            case NORMAL -> 1.0D;
            case OVERWEIGHT -> 0.5D;
            case IMMOBILIZED -> 0.0D;
        };
    }

    public enum OverweightStage {
        NORMAL,
        OVERWEIGHT,
        IMMOBILIZED
    }

    public record WeightStatus(double currentWeight, double maxWeight, OverweightStage stage, double movementMultiplier) {
    }
}
