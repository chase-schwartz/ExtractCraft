package com.chaseschwartz.extractcraft.items;

import java.util.List;
import java.util.Optional;

import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

public record ExtractCraftItemMetadata(
        int tier,
        String category,
        String description,
        double weight,
        int gridWidth,
        int gridHeight,
        Optional<RaidEquipmentSlot> equipmentSlot,
        Optional<Integer> storageGridWidth,
        Optional<Integer> storageGridHeight,
        Optional<Double> maxCarryWeight,
        boolean durabilityEnabled,
        Optional<Integer> maxDurability,
        Optional<String> repairCategory,
        Optional<Integer> repairAmount,
        Optional<String> repairTargetCategory,
        Optional<Integer> armorRating,
        Optional<Integer> healAmount,
        Optional<Integer> useTimeTicks,
        boolean consumable,
        boolean fixesBleed,
        boolean fixesBrokenBone,
        boolean allowInSafeBox,
        List<String> extraTooltipLines) {
    public ExtractCraftItemMetadata {
        gridWidth = Math.max(1, gridWidth);
        gridHeight = Math.max(1, gridHeight);
        weight = Math.max(0.0D, weight);
        extraTooltipLines = List.copyOf(extraTooltipLines);
    }

    public static Builder builder(int tier, String category, String description, double weight, int gridWidth, int gridHeight) {
        return new Builder(tier, category, description, weight, gridWidth, gridHeight);
    }

    public static class Builder {
        private final int tier;
        private final String category;
        private final String description;
        private final double weight;
        private final int gridWidth;
        private final int gridHeight;
        private Optional<RaidEquipmentSlot> equipmentSlot = Optional.empty();
        private Optional<Integer> storageGridWidth = Optional.empty();
        private Optional<Integer> storageGridHeight = Optional.empty();
        private Optional<Double> maxCarryWeight = Optional.empty();
        private boolean durabilityEnabled;
        private Optional<Integer> maxDurability = Optional.empty();
        private Optional<String> repairCategory = Optional.empty();
        private Optional<Integer> repairAmount = Optional.empty();
        private Optional<String> repairTargetCategory = Optional.empty();
        private Optional<Integer> armorRating = Optional.empty();
        private Optional<Integer> healAmount = Optional.empty();
        private Optional<Integer> useTimeTicks = Optional.empty();
        private boolean consumable;
        private boolean fixesBleed;
        private boolean fixesBrokenBone;
        private boolean allowInSafeBox;
        private List<String> extraTooltipLines = List.of();

        private Builder(int tier, String category, String description, double weight, int gridWidth, int gridHeight) {
            this.tier = tier;
            this.category = category;
            this.description = description;
            this.weight = weight;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
        }

        public Builder equipmentSlot(RaidEquipmentSlot value) {
            this.equipmentSlot = Optional.of(value);
            return this;
        }

        public Builder storageGrid(int width, int height, double carryWeight) {
            this.storageGridWidth = Optional.of(width);
            this.storageGridHeight = Optional.of(height);
            this.maxCarryWeight = Optional.of(carryWeight);
            return this;
        }

        public Builder durability(String category, int maxDurability) {
            this.durabilityEnabled = true;
            this.repairCategory = Optional.of(category);
            this.maxDurability = Optional.of(maxDurability);
            return this;
        }

        public Builder repairKit(String targetCategory, int amount) {
            this.repairTargetCategory = Optional.of(targetCategory);
            this.repairAmount = Optional.of(amount);
            this.consumable = true;
            return this;
        }

        public Builder armorRating(int value) {
            this.armorRating = Optional.of(value);
            return this;
        }

        public Builder healing(int healAmount, int useTimeTicks) {
            this.healAmount = Optional.of(healAmount);
            this.useTimeTicks = Optional.of(useTimeTicks);
            this.consumable = true;
            return this;
        }

        public Builder treatment(boolean fixesBleed, boolean fixesBrokenBone, int useTimeTicks) {
            this.fixesBleed = fixesBleed;
            this.fixesBrokenBone = fixesBrokenBone;
            this.useTimeTicks = Optional.of(useTimeTicks);
            this.consumable = true;
            return this;
        }

        public Builder allowInSafeBox(boolean value) {
            this.allowInSafeBox = value;
            return this;
        }

        public Builder tooltip(String... lines) {
            this.extraTooltipLines = List.of(lines);
            return this;
        }

        public ExtractCraftItemMetadata build() {
            return new ExtractCraftItemMetadata(
                    tier,
                    category,
                    description,
                    weight,
                    gridWidth,
                    gridHeight,
                    equipmentSlot,
                    storageGridWidth,
                    storageGridHeight,
                    maxCarryWeight,
                    durabilityEnabled,
                    maxDurability,
                    repairCategory,
                    repairAmount,
                    repairTargetCategory,
                    armorRating,
                    healAmount,
                    useTimeTicks,
                    consumable,
                    fixesBleed,
                    fixesBrokenBone,
                    allowInSafeBox,
                    extraTooltipLines);
        }
    }
}
