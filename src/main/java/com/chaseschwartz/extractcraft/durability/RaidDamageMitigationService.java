package com.chaseschwartz.extractcraft.durability;

import java.util.Locale;

import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventory;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryItem;
import com.chaseschwartz.extractcraft.raid.inventory.RaidInventoryManager;
import com.chaseschwartz.extractcraft.network.ArmorMitigationSyncPayload;
import com.chaseschwartz.extractcraft.raid.inventory.PlayerStashService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RaidDamageMitigationService {
    public static final double ARMOR_DURABILITY_LOSS_MULTIPLIER = 3.0D;
    public static final double HELMET_DURABILITY_LOSS_MULTIPLIER = 2.0D;

    private static final double[] ARMOR_MITIGATION_BY_TIER = {0.0D, 0.15D, 0.25D, 0.35D, 0.45D};
    private static final double[] HELMET_MITIGATION_BY_TIER = {0.0D, 0.05D, 0.08D, 0.11D, 0.15D};

    private RaidDamageMitigationService() {
    }

    public static float mitigate(ServerPlayer player, DamageSource source, float incomingDamage) {
        if (player == null || incomingDamage <= 0.0F || shouldSkip(source)) {
            return incomingDamage;
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        MitigationResult armorResult = applyMitigation(
                inventory,
                RaidEquipmentSlot.ARMOR,
                "armor",
                incomingDamage,
                ARMOR_MITIGATION_BY_TIER,
                ARMOR_DURABILITY_LOSS_MULTIPLIER);
        MitigationResult helmetResult = applyMitigation(
                inventory,
                RaidEquipmentSlot.HELMET,
                "helmet",
                armorResult.damage(),
                HELMET_MITIGATION_BY_TIER,
                HELMET_DURABILITY_LOSS_MULTIPLIER);

        if (armorResult.changed() || helmetResult.changed()) {
            player.containerMenu.broadcastChanges();
            sync(player);
        }
        return Math.max(0.0F, helmetResult.damage());
    }

    public static MitigationStatus status(ServerPlayer player) {
        if (player == null) {
            return MitigationStatus.EMPTY;
        }
        RaidInventory inventory = RaidManager.isInRaid(player)
                ? RaidInventoryManager.get(player)
                : PlayerStashService.load(player).baseInventory();
        return status(inventory);
    }

    public static MitigationStatus status(RaidInventory inventory) {
        if (inventory == null) {
            return MitigationStatus.EMPTY;
        }
        GearStatus armor = gearStatus(
                inventory.equipmentItem(RaidEquipmentSlot.ARMOR),
                "armor",
                ARMOR_MITIGATION_BY_TIER,
                ARMOR_DURABILITY_LOSS_MULTIPLIER);
        GearStatus helmet = gearStatus(
                inventory.equipmentItem(RaidEquipmentSlot.HELMET),
                "helmet",
                HELMET_MITIGATION_BY_TIER,
                HELMET_DURABILITY_LOSS_MULTIPLIER);
        double combined = 1.0D - ((1.0D - armor.mitigation()) * (1.0D - helmet.mitigation()));
        return new MitigationStatus(armor, helmet, Math.max(0.0D, combined));
    }

    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MitigationStatus status = status(player);
        PacketDistributor.sendToPlayer(player, new ArmorMitigationSyncPayload(
                percent(status.armor().mitigation()),
                percent(status.helmet().mitigation()),
                percent(status.combinedMitigation())));
    }

    public static void clearSync(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new ArmorMitigationSyncPayload(0, 0, 0));
        }
    }

    public static double mitigationPercent(String type, int tier) {
        return switch (normalizeType(type)) {
            case "armor" -> mitigationForTier(ARMOR_MITIGATION_BY_TIER, tier);
            case "helmet" -> mitigationForTier(HELMET_MITIGATION_BY_TIER, tier);
            default -> 0.0D;
        };
    }

    public static double durabilityLossMultiplier(String type) {
        return switch (normalizeType(type)) {
            case "armor" -> ARMOR_DURABILITY_LOSS_MULTIPLIER;
            case "helmet" -> HELMET_DURABILITY_LOSS_MULTIPLIER;
            default -> 0.0D;
        };
    }

    private static MitigationResult applyMitigation(
            RaidInventory inventory,
            RaidEquipmentSlot slot,
            String expectedType,
            float incomingDamage,
            double[] mitigationByTier,
            double durabilityLossMultiplier) {
        if (incomingDamage <= 0.0F) {
            return new MitigationResult(incomingDamage, false);
        }

        RaidInventoryItem item = inventory.equipmentItem(slot);
        if (item == null) {
            return new MitigationResult(incomingDamage, false);
        }

        ItemStack stack = item.toItemStack();
        DurabilityProfile profile = DurabilityService.profileFor(stack).orElse(null);
        DurabilityData data = DurabilityService.getOrInitialize(stack).orElse(null);
        if (profile == null || data == null || !expectedType.equals(normalizeType(data.type())) || data.currentDurability() <= 0) {
            return new MitigationResult(incomingDamage, false);
        }

        double mitigation = mitigationForTier(mitigationByTier, profile.tier());
        if (mitigation <= 0.0D) {
            return new MitigationResult(incomingDamage, false);
        }

        float absorbedDamage = (float) (incomingDamage * mitigation);
        float reducedDamage = Math.max(0.0F, incomingDamage - absorbedDamage);
        int durabilityLoss = Math.max(1, (int) Math.ceil(absorbedDamage * durabilityLossMultiplier));
        DurabilityService.damage(stack, durabilityLoss);
        inventory.setEquipmentSlot(slot, item.withStoredStack(stack));
        return new MitigationResult(reducedDamage, true);
    }

    private static GearStatus gearStatus(RaidInventoryItem item, String expectedType, double[] mitigationByTier, double durabilityLossMultiplier) {
        if (item == null) {
            return GearStatus.empty(expectedType, durabilityLossMultiplier);
        }

        ItemStack stack = item.toItemStack();
        DurabilityProfile profile = DurabilityService.profileFor(stack).orElse(null);
        DurabilityData data = DurabilityService.getOrInitialize(stack).orElse(null);
        if (profile == null || data == null || !expectedType.equals(normalizeType(data.type()))) {
            return GearStatus.empty(expectedType, durabilityLossMultiplier);
        }

        double mitigation = data.currentDurability() <= 0 ? 0.0D : mitigationForTier(mitigationByTier, profile.tier());
        return new GearStatus(
                item.displayName(),
                expectedType,
                profile.tier(),
                data.currentDurability(),
                data.currentMaxDurability(),
                data.pristineMaxDurability(),
                mitigation,
                durabilityLossMultiplier);
    }

    private static boolean shouldSkip(DamageSource source) {
        return source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    private static double mitigationForTier(double[] table, int tier) {
        int clampedTier = Math.max(0, Math.min(tier, table.length - 1));
        return table[clampedTier];
    }

    private static int percent(double mitigation) {
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, mitigation)) * 100.0D);
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private record MitigationResult(float damage, boolean changed) {
    }

    public record MitigationStatus(GearStatus armor, GearStatus helmet, double combinedMitigation) {
        private static final MitigationStatus EMPTY = new MitigationStatus(
                GearStatus.empty("armor", ARMOR_DURABILITY_LOSS_MULTIPLIER),
                GearStatus.empty("helmet", HELMET_DURABILITY_LOSS_MULTIPLIER),
                0.0D);

        public int combinedPercent() {
            return percent(combinedMitigation);
        }
    }

    public record GearStatus(
            String itemName,
            String type,
            int tier,
            int currentDurability,
            int currentMaxDurability,
            int pristineMaxDurability,
            double mitigation,
            double durabilityLossMultiplier) {
        private static GearStatus empty(String type, double durabilityLossMultiplier) {
            return new GearStatus("None", type, 0, 0, 0, 0, 0.0D, durabilityLossMultiplier);
        }

        public int mitigationPercent() {
            return percent(mitigation);
        }
    }
}
