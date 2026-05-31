package com.chaseschwartz.extractcraft.raid.inventory;

import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class RaidInventoryCommands {
    private RaidInventoryCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("extractcraft")
                .then(Commands.literal("carryprofile")
                        .then(Commands.literal("held")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> carryProfileHeld(context.getSource()))))
                .then(Commands.literal("raidinv")
                        .then(Commands.literal("status")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("setbackpack")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(context -> setBackpack(context.getSource(), StringArgumentType.getString(context, "type")))))
                        .then(Commands.literal("addheld")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> addHeld(context.getSource())))
                        .then(Commands.literal("clear")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> clear(context.getSource())))
                        .then(Commands.literal("weight")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> weight(context.getSource())))
                        .then(Commands.literal("value")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> value(context.getSource())))));
    }

    private static int carryProfileHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to inspect its carry profile."));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(itemId).orElse(null);
        if (profile == null) {
            player.sendSystemMessage(Component.literal(itemId + " has no carry profile. Loaded explicit profiles: " + ItemCarryProfileRegistry.loadedCount()));
            return 0;
        }

        player.sendSystemMessage(Component.literal(itemId
                + " | category " + profile.category().name().toLowerCase()
                + " | weight " + profile.weight()
                + " | slotCost " + profile.slotCost()
                + " | safe=" + profile.allowInSafeBox()
                + " | vest=" + profile.allowInVest()));
        return 1;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        sendStatus(source.getPlayerOrException());
        return 1;
    }

    private static int setBackpack(CommandSourceStack source, String type) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BackpackDefinition backpack = RaidInventoryDefinitions.backpack(type).orElse(null);
        if (backpack == null) {
            player.sendSystemMessage(Component.literal("Unknown backpack type '" + type + "'. Available: " + RaidInventoryDefinitions.backpackIds()));
            return 0;
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        inventory.setBackpack(backpack);
        player.sendSystemMessage(Component.literal("Raid backpack set to " + backpack.name() + ". Current raid inventory contents were cleared."));
        sendStatus(player);
        return 1;
    }

    private static int addHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RaidInventory.AddResult result = RaidInventoryManager.addHeld(player);
        player.sendSystemMessage(Component.literal(result.message()));
        if (result.success()) {
            sendStatus(player);
            return 1;
        }
        return 0;
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RaidInventoryManager.clear(player);
        player.sendSystemMessage(Component.literal("Cleared parallel raid inventory contents."));
        return 1;
    }

    private static int weight(CommandSourceStack source) throws CommandSyntaxException {
        RaidInventory inventory = RaidInventoryManager.get(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal(String.format("Raid inventory weight: %.2f", inventory.totalWeight())), false);
        return 1;
    }

    private static int value(CommandSourceStack source) throws CommandSyntaxException {
        RaidInventory inventory = RaidInventoryManager.get(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("Raid inventory value: " + inventory.totalValue() + " credits."), false);
        return 1;
    }

    private static void sendStatus(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        player.sendSystemMessage(Component.literal("Raid inventory loadout: "
                + inventory.loadout().backpack().id()
                + ", "
                + inventory.loadout().vest().id()
                + ", "
                + inventory.loadout().safeBox().id()));
        player.sendSystemMessage(Component.literal(formatContainer("Backpack", inventory.backpack())));
        player.sendSystemMessage(Component.literal(formatContainer("Vest", inventory.vest())));
        player.sendSystemMessage(Component.literal(formatContainer("Safe box", inventory.safeBox())));
        player.sendSystemMessage(Component.literal(String.format("Total: %.2f weight, %d credits value. Explicit carry profiles loaded: %d, item values loaded: %d.",
                inventory.totalWeight(),
                inventory.totalValue(),
                ItemCarryProfileRegistry.loadedCount(),
                ItemValueRegistry.loadedCount())));
    }

    private static String formatContainer(String label, RaidStorageContainer container) {
        return String.format("%s: %d/%d slots, %.2f/%.2f weight, %d items, %d credits",
                label,
                container.usedCapacity(),
                container.capacity(),
                container.usedWeight(),
                container.maxWeight(),
                container.itemCount(),
                container.totalValue());
    }
}
