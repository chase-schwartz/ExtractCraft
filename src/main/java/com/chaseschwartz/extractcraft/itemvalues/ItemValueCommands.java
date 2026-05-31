package com.chaseschwartz.extractcraft.itemvalues;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemValueCommands {
    private ItemValueCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("extractcraft")
                .then(Commands.literal("value")
                        .then(Commands.literal("held")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> valueHeld(context.getSource())))
                        .then(Commands.literal("item")
                                .then(Commands.argument("item_id", StringArgumentType.word())
                                        .executes(context -> valueItem(context.getSource(), StringArgumentType.getString(context, "item_id"))))))
                .then(Commands.literal("selltest")
                        .then(Commands.literal("held")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> sellTestHeld(context.getSource())))));
    }

    private static int valueHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to check its ExtractCraft value."));
            return 0;
        }

        player.sendSystemMessage(Component.literal(formatValue(stack, BuiltInRegistries.ITEM.getKey(stack.getItem()))));
        return 1;
    }

    private static int valueItem(CommandSourceStack source, String itemIdText) {
        ResourceLocation itemId;
        try {
            itemId = ResourceLocation.parse(itemIdText);
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Invalid item id: " + itemIdText));
            return 0;
        }

        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            source.sendFailure(Component.literal("Unknown or unloaded item id: " + itemId));
            return 0;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        sendValue(source, new ItemStack(item), itemId);
        return 1;
    }

    private static int sellTestHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to test its sale value."));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemValueEntry value = ItemValueRegistry.get(stack).orElse(null);
        if (value == null) {
            player.sendSystemMessage(Component.literal(itemId + " has no ExtractCraft value entry."));
            return 0;
        }

        if (!value.sellable()) {
            player.sendSystemMessage(Component.literal(itemId + " is not sellable."));
            return 0;
        }

        int total = value.value() * stack.getCount();
        player.sendSystemMessage(Component.literal("Sell test: " + stack.getCount() + "x " + itemId + " = " + total + " credits."));
        return 1;
    }

    private static void sendValue(CommandSourceStack source, ItemStack stack, ResourceLocation itemId) {
        ItemValueEntry value = ItemValueRegistry.get(stack).orElse(null);
        if (value == null) {
            source.sendSuccess(() -> Component.literal(itemId + " has no ExtractCraft value entry. Loaded entries: " + ItemValueRegistry.loadedCount()), false);
            return;
        }

        source.sendSuccess(() -> Component.literal(formatValue(stack, value)), false);
    }

    private static String formatValue(ItemStack stack, ResourceLocation itemId) {
        ItemValueEntry value = ItemValueRegistry.get(itemId).orElse(null);
        if (value == null) {
            return itemId + " has no ExtractCraft value entry. Loaded entries: " + ItemValueRegistry.loadedCount();
        }

        return formatValue(stack, value);
    }

    private static String formatValue(ItemStack stack, ItemValueEntry value) {
        StringBuilder builder = new StringBuilder();
        builder.append(value.itemId())
                .append(" | ")
                .append(value.category().name().toLowerCase())
                .append(" | ")
                .append(value.rarity().name().toLowerCase())
                .append(" | value ")
                .append(value.value());
        if (stack.getCount() > 1) {
            builder.append(" each, stack ").append(value.value() * stack.getCount());
        }
        builder.append(" | sellable=").append(value.sellable());
        if (value.questItem()) {
            builder.append(" | quest item");
        }
        value.traderType().ifPresent(trader -> builder.append(" | trader=").append(trader));
        value.lootTier().ifPresent(tier -> builder.append(" | lootTier=").append(tier));
        if (!value.notes().isEmpty()) {
            builder.append(" | notes=").append(String.join("; ", value.notes()));
        }
        return builder.toString();
    }
}
