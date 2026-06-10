package com.chaseschwartz.extractcraft.items;

import java.util.List;

import com.chaseschwartz.extractcraft.durability.DurabilityData;
import com.chaseschwartz.extractcraft.durability.DurabilityService;
import com.chaseschwartz.extractcraft.raid.inventory.QuickUseService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ExtractCraftProfiledItem extends Item {
    private final ExtractCraftItemMetadata metadata;

    public ExtractCraftProfiledItem(Properties properties, ExtractCraftItemMetadata metadata) {
        super(properties);
        this.metadata = metadata;
    }

    public ExtractCraftItemMetadata metadata() {
        return metadata;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.literal("Tier " + metadata.tier() + " " + metadata.category()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(metadata.description()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Footprint: " + metadata.gridWidth() + "x" + metadata.gridHeight()
                + " | Weight: " + format(metadata.weight()) + " wt").withStyle(ChatFormatting.DARK_GRAY));

        metadata.equipmentSlot().ifPresent(slot -> tooltip.add(Component.literal("Equipment: " + equipmentLabel(slot)).withStyle(ChatFormatting.BLUE)));
        if (metadata.storageGridWidth().isPresent() && metadata.storageGridHeight().isPresent()) {
            String carry = metadata.maxCarryWeight().map(value -> " | Carry: " + format(value) + " wt").orElse("");
            tooltip.add(Component.literal("Storage: " + metadata.storageGridWidth().orElse(0)
                    + "x" + metadata.storageGridHeight().orElse(0) + carry).withStyle(ChatFormatting.BLUE));
        }
        metadata.armorRating().ifPresent(value -> tooltip.add(Component.literal("Protection: " + protectionLabel(value)).withStyle(ChatFormatting.BLUE)));
        if (DurabilityService.getOrInitialize(stack).isPresent()) {
            DurabilityData data = DurabilityService.getOrInitialize(stack).orElseThrow();
            String label = data.type().equalsIgnoreCase("repair_kit") ? "Repair Capacity" : "Durability";
            tooltip.add(Component.literal(label + ": " + data.currentDurability() + "/" + data.currentMaxDurability()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (metadata.durabilityEnabled()) {
            tooltip.add(Component.literal("Durability: Configured later"
                    + metadata.maxDurability().map(value -> " (" + value + " max)").orElse("")).withStyle(ChatFormatting.DARK_GRAY));
        }
        appendMedicalCapacity(stack, tooltip);
        if (metadata.fixesBleed() || metadata.fixesBrokenBone()) {
            tooltip.add(Component.literal("Treatment: "
                    + (metadata.fixesBleed() ? "Bleed" : "")
                    + (metadata.fixesBleed() && metadata.fixesBrokenBone() ? " / " : "")
                    + (metadata.fixesBrokenBone() ? "Broken bone" : "")
                    + metadata.useTimeTicks().map(ticks -> " | Use: " + ticks + " ticks").orElse("")).withStyle(ChatFormatting.GREEN));
        }
        metadata.repairTargetCategory().ifPresent(target -> tooltip.add(Component.literal("Repairs: " + target
                + metadata.repairAmount().map(amount -> " +" + amount).orElse("")).withStyle(ChatFormatting.YELLOW)));
        if (metadata.consumable()) {
            tooltip.add(Component.literal("Consumable: Configured for future use").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (String line : metadata.extraTooltipLines()) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendMedicalCapacity(ItemStack stack, List<Component> tooltip) {
        QuickUseService.capacityInfo(stack).ifPresent(capacity -> {
            tooltip.add(Component.literal("Heal Capacity: " + capacity.current() + " / " + capacity.max()).withStyle(ChatFormatting.GREEN));
            QuickUseService.useTimeTicks(stack)
                    .ifPresent(ticks -> tooltip.add(Component.literal("Use Time: " + ticks + " ticks").withStyle(ChatFormatting.GREEN)));
        });
    }

    private static String equipmentLabel(com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> "Helmet";
            case ARMOR -> "Armor";
            case EQUIPPED_BACKPACK -> "Backpack";
            case EQUIPPED_VEST -> "Vest";
            case EQUIPPED_SAFE_CONTAINER -> "Safe Container";
            case PRIMARY_WEAPON -> "Primary";
            case SECONDARY_WEAPON -> "Secondary";
            case BACKPACK, VEST, SAFE_BOX -> slot.name();
        };
    }

    private static String protectionLabel(int rating) {
        if (rating >= 5) {
            return "Elite";
        }
        if (rating >= 4) {
            return "High";
        }
        if (rating >= 3) {
            return "Medium-High";
        }
        if (rating >= 2) {
            return "Medium";
        }
        return "Low";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
