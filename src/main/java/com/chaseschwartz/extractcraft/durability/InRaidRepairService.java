package com.chaseschwartz.extractcraft.durability;

import java.util.Locale;
import java.util.Optional;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.containers.ActiveLootContainerMenu;
import com.chaseschwartz.extractcraft.raid.inventory.GridMoveResult;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryItem;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryManager;
import com.chaseschwartz.extractcraft.timedaction.TimedAction;
import com.chaseschwartz.extractcraft.timedaction.TimedActionService;
import com.chaseschwartz.extractcraft.timedaction.TimedActionType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InRaidRepairService {
    private static final ResourceLocation HELMET_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "helmet_rebuild_kit");
    private static final ResourceLocation ARMOR_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "armor_rebuild_kit");
    private static final ResourceLocation BACKPACK_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "pack_rebuild_kit");
    private static final double IN_RAID_MAX_LOSS_MULTIPLIER = 1.25D;

    private InRaidRepairService() {
    }

    public static boolean isInRaidRepairKit(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return HELMET_KIT.equals(itemId) || ARMOR_KIT.equals(itemId) || BACKPACK_KIT.equals(itemId);
    }

    public static GridMoveResult startFromContext(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        RepairValidation validation = validate(player, source, sourceIndex, null);
        if (!validation.success()) {
            player.sendSystemMessage(Component.literal(validation.message()));
            return GridMoveResult.failure(validation.message());
        }

        TimedActionService.StartResult started = TimedActionService.start(
                player,
                validation.type(),
                validation.durationTicks(),
                validation.label(),
                true,
                false,
                Optional.of(sourceReference(source, sourceIndex, validation.kitItem().lookupKey())),
                Optional.of(targetReference(validation.targetSlot(), validation.targetItem().lookupKey())));
        if (!started.success()) {
            player.sendSystemMessage(Component.literal(started.message()));
            return GridMoveResult.failure(started.message());
        }

        String message = validation.label() + " (" + ticksToSeconds(validation.durationTicks()) + "s).";
        player.sendSystemMessage(Component.literal(message));
        return GridMoveResult.success(message);
    }

    public static GridMoveResult startFromDrag(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RaidEquipmentSlot target) {
        RepairTarget expectedTarget = repairTargetFromSlot(target);
        if (target != null && expectedTarget == null) {
            return GridMoveResult.failure("Wrong repair kit for target.");
        }
        RepairValidation validation = validate(player, source, sourceIndex, expectedTarget);
        if (!validation.success()) {
            return GridMoveResult.failure(validation.message());
        }
        return startFromContext(player, source, sourceIndex);
    }

    public static CompletionResult complete(ServerPlayer player, TimedAction action) {
        SourceRef sourceRef = parseSource(action.sourceReference().orElse(""));
        TargetRef targetRef = parseTarget(action.targetReference().orElse(""));
        if (sourceRef == null || targetRef == null) {
            return CompletionResult.failure("Repair action data was invalid.");
        }

        RepairValidation validation = validate(player, sourceRef.source(), sourceRef.sourceIndex(), repairTargetFromSlot(targetRef.targetSlot()));
        if (!validation.success()) {
            player.sendSystemMessage(Component.literal(validation.message()));
            return CompletionResult.failure(validation.message());
        }
        if (!validation.kitItem().lookupKey().equals(sourceRef.expectedLookupKey())
                || !validation.targetItem().lookupKey().equals(targetRef.expectedLookupKey())) {
            String message = "Repair canceled: source or target changed.";
            player.sendSystemMessage(Component.literal(message));
            return CompletionResult.failure(message);
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        ItemStack kitStack = validation.kitItem().toItemStack();
        ItemStack targetStack = validation.targetItem().toItemStack();
        DurabilityData kitData = DurabilityService.getOrInitialize(kitStack).orElse(null);
        DurabilityData targetData = DurabilityService.getOrInitialize(targetStack).orElse(null);
        if (kitData == null || targetData == null) {
            String message = "Repair canceled: durability data changed.";
            player.sendSystemMessage(Component.literal(message));
            return CompletionResult.failure(message);
        }

        int remainingKitCapacity = kitData.currentDurability() - validation.capacityCost();
        if (remainingKitCapacity <= 0) {
            inventory.removeCountAt(sourceRef.source(), sourceRef.sourceIndex(), validation.kitItem().count());
        } else {
            DurabilityService.write(kitStack, new DurabilityData(
                    kitData.type(),
                    kitData.pristineMaxDurability(),
                    kitData.currentMaxDurability(),
                    remainingKitCapacity,
                    kitData.repairCount()));
            RaidInventory.AddResult kitReplace = inventory.replaceItemAt(sourceRef.source(), sourceRef.sourceIndex(), validation.kitItem().withStoredStack(kitStack));
            if (!kitReplace.success()) {
                String message = "Repair canceled: kit could not be updated.";
                player.sendSystemMessage(Component.literal(message));
                return CompletionResult.failure(message);
            }
        }

        DurabilityService.write(targetStack, new DurabilityData(
                targetData.type(),
                targetData.pristineMaxDurability(),
                validation.newCurrentMax(),
                validation.newCurrentMax(),
                targetData.repairCount() + 1));
        RaidInventory.AddResult targetReplace = inventory.replaceItemAt(targetRef.targetSlot(), 0, validation.targetItem().withStoredStack(targetStack));
        if (!targetReplace.success()) {
            String message = "Repair failed: target could not be updated.";
            player.sendSystemMessage(Component.literal(message));
            return CompletionResult.failure(message);
        }

        if (player.containerMenu instanceof ActiveLootContainerMenu menu) {
            menu.refreshRaidDisplay();
        }
        RaidDamageMitigationService.sync(player);
        String message = "Repaired " + validation.targetItem().displayName()
                + ". Max condition is now " + validation.newCurrentMax()
                + "/" + targetData.pristineMaxDurability()
                + ". Kit capacity used: " + validation.capacityCost() + ".";
        player.sendSystemMessage(Component.literal(message));
        ExtractCraft.LOGGER.info("In-raid repair complete: player={}, kit={}#{}, target={}, cost={}, maxLoss={}, newMax={}, kitRemaining={}",
                player.getGameProfile().getName(),
                sourceRef.source(),
                sourceRef.sourceIndex(),
                targetRef.targetSlot(),
                validation.capacityCost(),
                validation.maxConditionLoss(),
                validation.newCurrentMax(),
                Math.max(0, remainingKitCapacity));
        return CompletionResult.success(message);
    }

    private static RepairValidation validate(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, RepairTarget expectedTarget) {
        if (player == null) {
            return RepairValidation.failure("No player.");
        }
        if (!RaidManager.isInRaid(player)) {
            return RepairValidation.failure("Repair kits can only be used in raid.");
        }
        if (!isStorageSlot(source)) {
            return RepairValidation.failure("Repair kit must be in your raid inventory.");
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        RaidInventoryItem kitItem = inventory.itemAt(source, sourceIndex);
        if (kitItem == null) {
            return RepairValidation.failure("Repair kit is no longer available.");
        }
        if (kitItem.count() != 1) {
            return RepairValidation.failure("Repair kits must be unstacked before use.");
        }

        ItemStack kitStack = kitItem.toItemStack();
        RepairTarget target = repairTargetForKit(kitStack);
        if (target == null) {
            return RepairValidation.failure("That item is not an in-raid repair kit.");
        }
        if (expectedTarget != null && expectedTarget != target) {
            return RepairValidation.failure("Wrong repair kit for target.");
        }

        RaidInventoryItem targetItem = inventory.itemAt(target.slot(), 0);
        if (targetItem == null) {
            return RepairValidation.failure("No " + target.displayName().toLowerCase(Locale.ROOT) + " equipped.");
        }

        ItemStack targetStack = targetItem.toItemStack();
        DurabilityProfile targetProfile = DurabilityService.profileFor(targetStack).orElse(null);
        DurabilityData targetData = DurabilityService.getOrInitialize(targetStack).orElse(null);
        if (targetProfile == null || targetData == null || !target.profileType().equalsIgnoreCase(targetProfile.type())) {
            return RepairValidation.failure("Target cannot be repaired.");
        }

        int missing = Math.max(0, targetData.currentMaxDurability() - targetData.currentDurability());
        if (missing <= 0) {
            return RepairValidation.failure("Target is not damaged.");
        }
        int repairFloor = PaidRepairService.repairFloor(targetData.pristineMaxDurability());
        if (targetData.currentMaxDurability() <= repairFloor) {
            return RepairValidation.failure(target.displayName() + " is too worn to repair further.");
        }

        DurabilityData kitData = DurabilityService.getOrInitialize(kitStack).orElse(null);
        if (kitData == null || !"repair_kit".equalsIgnoreCase(kitData.type())) {
            return RepairValidation.failure("Repair kit has no usable capacity.");
        }

        int tier = effectiveTier(targetProfile.tier());
        int capacityCost = capacityCost(target, tier, missing);
        if (kitData.currentDurability() < capacityCost) {
            return RepairValidation.failure("Repair kit capacity too low. Need " + capacityCost + ", has " + kitData.currentDurability() + ".");
        }

        int paidMaxLoss = PaidRepairService.maxConditionLoss(tier, missing);
        int maxLoss = Math.max(1, (int) Math.ceil(paidMaxLoss * IN_RAID_MAX_LOSS_MULTIPLIER));
        int newCurrentMax = Math.max(repairFloor, targetData.currentMaxDurability() - maxLoss);
        return RepairValidation.success(
                kitItem,
                targetItem,
                target,
                tier,
                missing,
                capacityCost,
                maxLoss,
                newCurrentMax,
                durationTicks(target, tier));
    }

    private static int capacityCost(RepairTarget target, int tier, int missing) {
        return Math.max(1, (int) Math.ceil(missing * target.typeMultiplier() * tierMultiplier(tier)));
    }

    private static int durationTicks(RepairTarget target, int tier) {
        int effectiveTier = effectiveTier(tier);
        return switch (target) {
            case HELMET -> switch (effectiveTier) {
                case 1 -> 60;
                case 2 -> 80;
                case 3 -> 110;
                default -> 140;
            };
            case ARMOR -> switch (effectiveTier) {
                case 1 -> 90;
                case 2 -> 130;
                case 3 -> 170;
                default -> 220;
            };
            case BACKPACK -> switch (effectiveTier) {
                case 1 -> 70;
                case 2 -> 95;
                case 3 -> 130;
                case 4 -> 170;
                default -> 220;
            };
        };
    }

    private static double tierMultiplier(int tier) {
        return switch (effectiveTier(tier)) {
            case 1 -> 0.65D;
            case 2 -> 0.85D;
            case 3 -> 1.10D;
            case 4 -> 1.40D;
            default -> 1.65D;
        };
    }

    private static int effectiveTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    private static RepairTarget repairTargetForKit(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (HELMET_KIT.equals(itemId)) {
            return RepairTarget.HELMET;
        }
        if (ARMOR_KIT.equals(itemId)) {
            return RepairTarget.ARMOR;
        }
        if (BACKPACK_KIT.equals(itemId)) {
            return RepairTarget.BACKPACK;
        }
        return ItemCarryProfileRegistry.get(stack)
                .flatMap(profile -> profile.repairTargetCategory())
                .map(InRaidRepairService::repairTargetFromCategory)
                .orElse(null);
    }

    private static RepairTarget repairTargetFromCategory(String category) {
        if (category == null) {
            return null;
        }
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "helmet" -> RepairTarget.HELMET;
            case "armor" -> RepairTarget.ARMOR;
            case "backpack", "pack" -> RepairTarget.BACKPACK;
            default -> null;
        };
    }

    private static RepairTarget repairTargetFromSlot(RaidEquipmentSlot slot) {
        if (slot == RaidEquipmentSlot.HELMET) {
            return RepairTarget.HELMET;
        }
        if (slot == RaidEquipmentSlot.ARMOR) {
            return RepairTarget.ARMOR;
        }
        if (slot == RaidEquipmentSlot.EQUIPPED_BACKPACK) {
            return RepairTarget.BACKPACK;
        }
        return null;
    }

    private static boolean isStorageSlot(RaidEquipmentSlot slot) {
        return slot == RaidEquipmentSlot.BACKPACK || slot == RaidEquipmentSlot.VEST || slot == RaidEquipmentSlot.SAFE_BOX;
    }

    private static int ticksToSeconds(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0D));
    }

    private static String sourceReference(RaidEquipmentSlot source, int sourceIndex, String lookupKey) {
        return source.name() + "|" + sourceIndex + "|" + lookupKey;
    }

    private static String targetReference(RaidEquipmentSlot target, String lookupKey) {
        return target.name() + "|" + lookupKey;
    }

    private static SourceRef parseSource(String value) {
        String[] parts = value.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new SourceRef(RaidEquipmentSlot.valueOf(parts[0]), Integer.parseInt(parts[1]), parts[2]);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static TargetRef parseTarget(String value) {
        String[] parts = value.split("\\|", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new TargetRef(RaidEquipmentSlot.valueOf(parts[0]), parts[1]);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record CompletionResult(boolean success, String message) {
        private static CompletionResult success(String message) {
            return new CompletionResult(true, message);
        }

        private static CompletionResult failure(String message) {
            return new CompletionResult(false, message);
        }
    }

    private enum RepairTarget {
        HELMET(RaidEquipmentSlot.HELMET, TimedActionType.REPAIR_HELMET, "Helmet", "helmet", 1.0D),
        ARMOR(RaidEquipmentSlot.ARMOR, TimedActionType.REPAIR_ARMOR, "Armor", "armor", 1.25D),
        BACKPACK(RaidEquipmentSlot.EQUIPPED_BACKPACK, TimedActionType.REPAIR_BACKPACK, "Backpack", "backpack", 1.1D);

        private final RaidEquipmentSlot slot;
        private final TimedActionType actionType;
        private final String displayName;
        private final String profileType;
        private final double typeMultiplier;

        RepairTarget(RaidEquipmentSlot slot, TimedActionType actionType, String displayName, String profileType, double typeMultiplier) {
            this.slot = slot;
            this.actionType = actionType;
            this.displayName = displayName;
            this.profileType = profileType;
            this.typeMultiplier = typeMultiplier;
        }

        private RaidEquipmentSlot slot() {
            return slot;
        }

        private TimedActionType actionType() {
            return actionType;
        }

        private String displayName() {
            return displayName;
        }

        private String profileType() {
            return profileType;
        }

        private double typeMultiplier() {
            return typeMultiplier;
        }
    }

    private record RepairValidation(
            boolean success,
            String message,
            RaidInventoryItem kitItem,
            RaidInventoryItem targetItem,
            RepairTarget target,
            int tier,
            int missingDurability,
            int capacityCost,
            int maxConditionLoss,
            int newCurrentMax,
            int durationTicks) {
        private static RepairValidation success(RaidInventoryItem kitItem, RaidInventoryItem targetItem, RepairTarget target, int tier, int missingDurability,
                int capacityCost, int maxConditionLoss, int newCurrentMax, int durationTicks) {
            return new RepairValidation(true, "", kitItem, targetItem, target, tier, missingDurability, capacityCost, maxConditionLoss, newCurrentMax, durationTicks);
        }

        private static RepairValidation failure(String message) {
            return new RepairValidation(false, message, null, null, null, 0, 0, 0, 0, 0, 0);
        }

        private TimedActionType type() {
            return target.actionType();
        }

        private RaidEquipmentSlot targetSlot() {
            return target.slot();
        }

        private String label() {
            return "Repairing " + target.displayName() + "...";
        }
    }

    private record SourceRef(RaidEquipmentSlot source, int sourceIndex, String expectedLookupKey) {
    }

    private record TargetRef(RaidEquipmentSlot targetSlot, String expectedLookupKey) {
    }
}
