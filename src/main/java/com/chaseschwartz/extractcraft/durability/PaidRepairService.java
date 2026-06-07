package com.chaseschwartz.extractcraft.durability;

import java.util.Optional;

import net.minecraft.world.item.ItemStack;

public final class PaidRepairService {
    private static final double REPAIR_FLOOR_RATIO = 0.30D;

    private PaidRepairService() {
    }

    public static Optional<RepairEstimate> estimate(ItemStack stack) {
        Optional<DurabilityProfile> profile = DurabilityService.profileFor(stack);
        Optional<DurabilityData> data = DurabilityService.getOrInitialize(stack);
        if (profile.isEmpty() || data.isEmpty()) {
            return Optional.empty();
        }
        return estimate(profile.get(), data.get());
    }

    public static Optional<RepairEstimate> estimate(DurabilityProfile profile, DurabilityData data) {
        if (!isRepairableType(profile.type())) {
            return Optional.empty();
        }
        int missing = Math.max(0, data.currentMaxDurability() - data.currentDurability());
        int floor = repairFloor(data.pristineMaxDurability());
        if (missing <= 0) {
            return Optional.of(RepairEstimate.unavailable(profile, data, floor, "Item is already at full current condition."));
        }
        if (data.currentMaxDurability() <= floor) {
            return Optional.of(RepairEstimate.unavailable(profile, data, floor, "Too worn to repair further."));
        }

        int tier = effectiveTier(profile.tier());
        int cost = Math.max(1, missing * repairRate(tier));
        int maxLoss = maxConditionLoss(tier, missing);
        int newCurrentMax = Math.max(floor, data.currentMaxDurability() - maxLoss);
        return Optional.of(new RepairEstimate(
                true,
                profile.itemId().toString(),
                profile.type(),
                tier,
                data.currentDurability(),
                data.currentMaxDurability(),
                data.pristineMaxDurability(),
                data.repairCount(),
                missing,
                cost,
                floor,
                maxLoss,
                newCurrentMax,
                newCurrentMax,
                repairRate(tier),
                repairSeverityLossFactor(tier),
                ""));
    }

    public static Optional<RepairResult> repair(ItemStack stack, int availableCredits) {
        Optional<DurabilityProfile> profile = DurabilityService.profileFor(stack);
        Optional<DurabilityData> data = DurabilityService.getOrInitialize(stack);
        if (profile.isEmpty() || data.isEmpty()) {
            return Optional.of(RepairResult.failure("Item is not paid-repairable."));
        }

        RepairEstimate estimate = estimate(profile.get(), data.get()).orElse(null);
        if (estimate == null || !estimate.available()) {
            return Optional.of(RepairResult.failure(estimate == null ? "Item is not paid-repairable." : estimate.message()));
        }
        if (availableCredits < estimate.cost()) {
            return Optional.of(RepairResult.failure("Not enough credits. Repair costs " + estimate.cost() + " cr."));
        }

        DurabilityData repaired = new DurabilityData(
                data.get().type(),
                data.get().pristineMaxDurability(),
                estimate.predictedCurrentMax(),
                estimate.predictedCurrentDurability(),
                data.get().repairCount() + 1);
        DurabilityService.write(stack, repaired);
        return Optional.of(RepairResult.success(estimate, repaired));
    }

    public static boolean isRepairableType(String type) {
        return "helmet".equalsIgnoreCase(type)
                || "armor".equalsIgnoreCase(type)
                || "backpack".equalsIgnoreCase(type);
    }

    public static int repairRate(int tier) {
        return switch (effectiveTier(tier)) {
            case 1 -> 8;
            case 2 -> 14;
            case 3 -> 24;
            case 4 -> 40;
            default -> 55;
        };
    }

    public static int repairBaseLoss(int tier) {
        return switch (effectiveTier(tier)) {
            case 1 -> 4;
            case 2 -> 5;
            case 3 -> 6;
            case 4 -> 7;
            default -> 8;
        };
    }

    public static double repairSeverityLossFactor(int tier) {
        return switch (effectiveTier(tier)) {
            case 1 -> 0.18D;
            case 2 -> 0.16D;
            case 3 -> 0.14D;
            case 4 -> 0.12D;
            default -> 0.10D;
        };
    }

    public static int maxConditionLoss(int tier, int missingDurability) {
        int missing = Math.max(0, missingDurability);
        return Math.max(1, repairBaseLoss(tier) + (int) Math.ceil(missing * repairSeverityLossFactor(tier)));
    }

    public static int repairFloor(int pristineMaxDurability) {
        return Math.max(1, (int) Math.ceil(Math.max(1, pristineMaxDurability) * REPAIR_FLOOR_RATIO));
    }

    private static int effectiveTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    public record RepairEstimate(
            boolean available,
            String itemId,
            String durabilityType,
            int tier,
            int currentDurability,
            int currentMaxDurability,
            int pristineMaxDurability,
            int repairCount,
            int missingDurability,
            int cost,
            int repairFloor,
            int maxLoss,
            int predictedCurrentMax,
            int predictedCurrentDurability,
            int repairRate,
            double repairSeverityLossFactor,
            String message) {
        private static RepairEstimate unavailable(DurabilityProfile profile, DurabilityData data, int floor, String message) {
            int tier = effectiveTier(profile.tier());
            return new RepairEstimate(
                    false,
                    profile.itemId().toString(),
                    profile.type(),
                    tier,
                    data.currentDurability(),
                    data.currentMaxDurability(),
                    data.pristineMaxDurability(),
                    data.repairCount(),
                    Math.max(0, data.currentMaxDurability() - data.currentDurability()),
                    0,
                    floor,
                    PaidRepairService.maxConditionLoss(tier, Math.max(0, data.currentMaxDurability() - data.currentDurability())),
                    data.currentMaxDurability(),
                    data.currentDurability(),
                    PaidRepairService.repairRate(tier),
                    PaidRepairService.repairSeverityLossFactor(tier),
                    message);
        }
    }

    public record RepairResult(boolean success, String message, Optional<RepairEstimate> estimate, Optional<DurabilityData> repairedData) {
        private static RepairResult success(RepairEstimate estimate, DurabilityData repairedData) {
            return new RepairResult(true, "Repair complete.", Optional.of(estimate), Optional.of(repairedData));
        }

        public static RepairResult failure(String message) {
            return new RepairResult(false, message, Optional.empty(), Optional.empty());
        }
    }
}
