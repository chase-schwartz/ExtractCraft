package com.chaseschwartz.extractcraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.chaseschwartz.extractcraft.items.ExtractCraftItemMetadata;
import com.chaseschwartz.extractcraft.items.ExtractCraftProfiledItem;
import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;
import com.chaseschwartz.extractcraft.itemvalues.ItemRarity;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.GridDisplayMetadata;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfile;
import com.chaseschwartz.extractcraft.raid.inventory.ItemCarryProfileRegistry;
import com.chaseschwartz.extractcraft.raid.inventory.RaidEquipmentSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public final class ExtractCraftTooltipBuilder {
    private ExtractCraftTooltipBuilder() {
    }

    public static List<Component> build(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }

        Optional<ItemValueEntry> value = ItemValueRegistry.get(stack);
        Optional<ItemCarryProfile> profile = ItemCarryProfileRegistry.get(stack);
        if (stack.getItem() instanceof ExtractCraftProfiledItem profiledItem) {
            return profiledTooltip(stack, profiledItem.metadata(), value, profile);
        }

        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        if (value.isPresent() || profile.isPresent()) {
            addRegistryLines(lines, stack, value, profile);
        }
        return lines;
    }

    private static List<Component> profiledTooltip(ItemStack stack, ExtractCraftItemMetadata metadata, Optional<ItemValueEntry> value, Optional<ItemCarryProfile> profile) {
        List<Component> lines = new ArrayList<>();
        ChatFormatting tierColor = rarityColor(value.map(ItemValueEntry::rarity).orElse(ItemRarity.COMMON));
        lines.add(stack.getHoverName().copy().withStyle(tierColor));
        lines.add(Component.literal("Tier " + metadata.tier() + " " + metadata.category()).withStyle(tierColor));
        value.ifPresent(entry -> lines.add(Component.literal("Rarity: " + label(entry.rarity().name())).withStyle(rarityColor(entry.rarity()))));
        lines.add(Component.literal("Category: " + metadata.category()).withStyle(ChatFormatting.GRAY));
        value.ifPresent(entry -> lines.add(Component.literal(valueText(entry.value(), stack.getCount())).withStyle(ChatFormatting.GREEN)));
        lines.add(Component.literal("Weight: " + format(metadata.weight() * stack.getCount()) + " kg").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Size: " + metadata.gridWidth() + "x" + metadata.gridHeight()).withStyle(ChatFormatting.GRAY));

        metadata.equipmentSlot().ifPresent(slot -> lines.add(Component.literal("Slot: " + equipmentLabel(slot)).withStyle(ChatFormatting.YELLOW)));
        if (metadata.storageGridWidth().isPresent() && metadata.storageGridHeight().isPresent()) {
            String label = switch (metadata.equipmentSlot().orElse(RaidEquipmentSlot.BACKPACK)) {
                case EQUIPPED_BACKPACK -> "Backpack Storage";
                case EQUIPPED_VEST -> "Vest Storage";
                case EQUIPPED_SAFE_CONTAINER -> "Secure Storage";
                default -> "Storage";
            };
            lines.add(Component.literal(label + ": " + metadata.storageGridWidth().orElse(0) + "x" + metadata.storageGridHeight().orElse(0)).withStyle(ChatFormatting.BLUE));
        }
        metadata.maxCarryWeight().ifPresent(weight -> lines.add(Component.literal("Carry Limit: +" + format(weight) + " kg").withStyle(ChatFormatting.BLUE)));
        metadata.armorRating().ifPresent(rating -> lines.add(Component.literal("Armor: " + protectionLabel(rating)).withStyle(ChatFormatting.YELLOW)));
        if (metadata.durabilityEnabled()) {
            lines.add(Component.literal("Durability: Configured"
                    + metadata.maxDurability().map(max -> " (" + max + " max)").orElse("")).withStyle(ChatFormatting.DARK_GRAY));
        }
        metadata.healAmount().ifPresent(heal -> lines.add(Component.literal("Heal: " + heal
                + metadata.useTimeTicks().map(ticks -> " | Use: " + ticks + " ticks").orElse("")).withStyle(ChatFormatting.GREEN)));
        if (metadata.fixesBleed() || metadata.fixesBrokenBone()) {
            lines.add(Component.literal("Treatment: "
                    + (metadata.fixesBleed() ? "Bleed" : "")
                    + (metadata.fixesBleed() && metadata.fixesBrokenBone() ? " / " : "")
                    + (metadata.fixesBrokenBone() ? "Broken bone" : "")
                    + metadata.useTimeTicks().map(ticks -> " | Use: " + ticks + " ticks").orElse("")).withStyle(ChatFormatting.GREEN));
        }
        metadata.repairTargetCategory().ifPresent(target -> lines.add(Component.literal("Target: " + target
                + metadata.repairAmount().map(amount -> " | Repair: " + amount).orElse(" | Repair: Configured")).withStyle(ChatFormatting.YELLOW)));
        if (metadata.consumable()) {
            lines.add(Component.literal("Consumable").withStyle(ChatFormatting.YELLOW));
        }
        if (metadata.allowInSafeBox() || metadata.equipmentSlot().filter(slot -> slot == RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER).isPresent()) {
            lines.add(Component.literal("Protected on death/failure").withStyle(ChatFormatting.AQUA));
        }
        if (!metadata.description().isBlank()) {
            lines.add(Component.literal(metadata.description()).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        }
        for (String line : metadata.extraTooltipLines()) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
        }
        profile.ifPresent(carry -> appendProfileNotes(lines, carry));
        return lines;
    }

    private static void addRegistryLines(List<Component> lines, ItemStack stack, Optional<ItemValueEntry> value, Optional<ItemCarryProfile> profile) {
        value.ifPresent(entry -> {
            lines.add(Component.literal("Rarity: " + label(entry.rarity().name())).withStyle(rarityColor(entry.rarity())));
            lines.add(Component.literal("Category: " + categoryLabel(entry.category())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(valueText(entry.value(), stack.getCount())).withStyle(ChatFormatting.GREEN));
            entry.lootTier().ifPresent(tier -> lines.add(Component.literal("Tier " + tier).withStyle(rarityColor(entry.rarity()))));
        });

        profile.ifPresent(carry -> {
            if (value.isEmpty()) {
                lines.add(Component.literal("Category: " + categoryLabel(carry.category())).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.literal("Weight: " + format(carry.weight() * stack.getCount()) + " kg").withStyle(ChatFormatting.GRAY));
            int width = carry.gridWidth().orElseGet(() -> gridMetadata(stack).map(GridDisplayMetadata.Metadata::footprintWidth).orElse(1));
            int height = carry.gridHeight().orElseGet(() -> gridMetadata(stack).map(GridDisplayMetadata.Metadata::footprintHeight).orElse(1));
            lines.add(Component.literal("Size: " + width + "x" + height).withStyle(ChatFormatting.GRAY));
            carry.tier().ifPresent(tier -> lines.add(Component.literal("Tier " + tier).withStyle(ChatFormatting.YELLOW)));
            carry.equipmentSlot().ifPresent(slot -> lines.add(Component.literal("Slot: " + equipmentLabel(slot)).withStyle(ChatFormatting.YELLOW)));
            carry.storageGridDefinition().ifPresent(grid -> lines.add(Component.literal("Storage: " + grid).withStyle(ChatFormatting.BLUE)));
            carry.maxCarryWeight().ifPresent(weight -> lines.add(Component.literal("Carry Limit: +" + format(weight) + " kg").withStyle(ChatFormatting.BLUE)));
            carry.armorRating().ifPresent(rating -> lines.add(Component.literal("Armor: " + protectionLabel(rating)).withStyle(ChatFormatting.YELLOW)));
            if (carry.durabilityEnabled()) {
                lines.add(Component.literal("Durability: Configured"
                        + carry.maxDurability().map(max -> " (" + max + " max)").orElse("")).withStyle(ChatFormatting.DARK_GRAY));
            }
            carry.healAmount().ifPresent(heal -> lines.add(Component.literal("Heal: " + heal
                    + carry.useTimeTicks().map(ticks -> " | Use: " + ticks + " ticks").orElse("")).withStyle(ChatFormatting.GREEN)));
            if (carry.fixesBleed() || carry.fixesBrokenBone()) {
                lines.add(Component.literal("Treatment: "
                        + (carry.fixesBleed() ? "Bleed" : "")
                        + (carry.fixesBleed() && carry.fixesBrokenBone() ? " / " : "")
                        + (carry.fixesBrokenBone() ? "Broken bone" : "")
                        + carry.useTimeTicks().map(ticks -> " | Use: " + ticks + " ticks").orElse("")).withStyle(ChatFormatting.GREEN));
            }
            carry.repairTargetCategory().ifPresent(target -> lines.add(Component.literal("Target: " + target
                    + carry.repairAmount().map(amount -> " | Repair: " + amount).orElse(" | Repair: Configured")).withStyle(ChatFormatting.YELLOW)));
            if (carry.consumable()) {
                lines.add(Component.literal("Consumable").withStyle(ChatFormatting.YELLOW));
            }
            if (carry.allowInSafeBox()) {
                lines.add(Component.literal("Safe Box allowed").withStyle(ChatFormatting.AQUA));
            }
            appendProfileNotes(lines, carry);
        });
    }

    private static Optional<GridDisplayMetadata.Metadata> gridMetadata(ItemStack stack) {
        GridDisplayMetadata.Metadata metadata = GridDisplayMetadata.read(stack);
        return metadata.present() ? Optional.of(metadata) : Optional.empty();
    }

    private static void appendProfileNotes(List<Component> lines, ItemCarryProfile profile) {
        for (String note : profile.notes()) {
            if (!note.isBlank() && !note.startsWith("ExtractCraft equipment/content profile")) {
                lines.add(Component.literal(note).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static String valueText(int unitValue, int count) {
        int total = unitValue * Math.max(1, count);
        if (count > 1) {
            return "Value: " + total + " cr (" + unitValue + " each)";
        }
        return "Value: " + total + " cr";
    }

    private static String categoryLabel(ItemCategory category) {
        return label(category.name());
    }

    private static String equipmentLabel(RaidEquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> "Helmet";
            case ARMOR -> "Armor";
            case EQUIPPED_BACKPACK -> "Backpack";
            case EQUIPPED_VEST -> "Vest";
            case EQUIPPED_SAFE_CONTAINER -> "Safe Container";
            case PRIMARY_WEAPON -> "Primary";
            case SECONDARY_WEAPON -> "Secondary";
            case BACKPACK -> "Backpack Storage";
            case VEST -> "Vest Storage";
            case SAFE_BOX -> "Safe Box";
        };
    }

    private static String equipmentLabel(String slot) {
        return switch (slot.toLowerCase(Locale.ROOT)) {
            case "helmet" -> "Helmet";
            case "armor" -> "Armor";
            case "equipped_backpack" -> "Backpack";
            case "equipped_vest" -> "Vest";
            case "equipped_safe_container" -> "Safe Container";
            case "primary_weapon" -> "Primary";
            case "secondary_weapon" -> "Secondary";
            default -> label(slot);
        };
    }

    private static String protectionLabel(int rating) {
        if (rating >= 5) {
            return "Elite";
        }
        if (rating >= 4) {
            return "Heavy";
        }
        if (rating >= 3) {
            return "Reinforced";
        }
        if (rating >= 2) {
            return "Medium";
        }
        return "Light";
    }

    private static ChatFormatting rarityColor(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatFormatting.GRAY;
            case UNCOMMON, RARE, BLUE -> ChatFormatting.AQUA;
            case EPIC, PURPLE -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY, GOLD -> ChatFormatting.GOLD;
            case RED, QUEST -> ChatFormatting.RED;
        };
    }

    private static String label(String raw) {
        String normalized = raw.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return raw;
        }
        String[] words = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
