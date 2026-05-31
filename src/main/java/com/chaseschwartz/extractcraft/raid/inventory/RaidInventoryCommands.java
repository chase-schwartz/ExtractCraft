package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.List;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentity;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentityResolver;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerEntry;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerLayout;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RaidInventoryCommands {
    private RaidInventoryCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("extractcraft")
                .then(Commands.literal("itemdebug")
                        .then(Commands.literal("held")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> itemDebugHeld(context.getSource()))))
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
                        .then(Commands.literal("lootcontainer")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> lootContainer(context.getSource())))
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
        ItemCarryProfile profile = ItemCarryProfileRegistry.get(stack).orElse(null);
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

    private static int itemDebugHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to debug its identity."));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemIdentity identity = ItemIdentityResolver.resolve(stack);
        ItemValueEntry value = ItemValueRegistry.get(stack).orElse(null);
        ItemCarryProfile carryProfile = ItemCarryProfileRegistry.get(stack).orElse(null);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        Tag savedTag = stack.save(player.registryAccess());
        String components = stack.getComponents().toString();
        String componentPatch = stack.getComponentsPatch().toString();

        sendDebugLine(player, "Item debug for held stack:");
        sendDebugLine(player, "registry=" + itemId);
        sendDebugLine(player, "resolvedBaseId=" + identity.baseItemId());
        sendDebugLine(player, "resolvedVariantId=" + identity.variantId().map(ResourceLocation::toString).orElse("none"));
        sendDebugLine(player, "resolvedNormalizedKey=" + identity.normalizedKey());
        sendDebugLine(player, "valueLookupKeyUsed=" + ItemValueRegistry.lookupKeyUsed(stack));
        sendDebugLine(player, "carryProfileLookupKeyUsed=" + ItemCarryProfileRegistry.lookupKeyUsed(stack));
        sendDebugLine(player, "displayName=" + stack.getHoverName().getString());
        sendDebugLine(player, "count=" + stack.getCount() + ", maxStack=" + stack.getMaxStackSize());
        sendDebugLine(player, "valueRegistry=" + (value == null ? "none" : value.category().name().toLowerCase() + ", " + value.rarity().name().toLowerCase() + ", value=" + value.value()));
        sendDebugLine(player, "carryProfile=" + (carryProfile == null ? "none" : carryProfile.category().name().toLowerCase() + ", weight=" + carryProfile.weight() + ", slotCost=" + carryProfile.slotCost()));
        sendDebugLine(player, "customData=" + (customData == null ? "none" : customData.copyTag().toString()));
        sendDebugLine(player, "components=" + components);
        sendDebugLine(player, "componentPatch=" + componentPatch);
        sendDebugLine(player, "savedStackTag=" + savedTag);

        List<String> suspectedFields = suspectedIdentityFields(savedTag);
        if (customData != null) {
            suspectedFields.addAll(suspectedIdentityFields(customData.copyTag()));
        }
        if (suspectedFields.isEmpty()) {
            sendDebugLine(player, "suspectedIdentityFields=none found");
        } else {
            sendDebugLine(player, "suspectedIdentityFields:");
            suspectedFields.stream().distinct().limit(20).forEach(line -> sendDebugLine(player, "  " + line));
        }

        ExtractCraft.LOGGER.info("Item debug held by {}: registry={}, displayName={}, count={}, customData={}, components={}, componentPatch={}, savedStackTag={}, suspectedIdentityFields={}",
                player.getGameProfile().getName(),
                itemId,
                stack.getHoverName().getString(),
                stack.getCount(),
                customData == null ? "none" : customData.copyTag(),
                components,
                componentPatch,
                savedTag,
                suspectedFields);
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

    private static int lootContainer(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos targetPos = targetedBlock(player);
        if (targetPos == null) {
            player.sendSystemMessage(Component.literal("Look at an active loot container first."));
            return 0;
        }

        RaidContainerEntry activeEntry = findActiveContainer(player, targetPos);
        if (activeEntry == null) {
            player.sendSystemMessage(Component.literal("That block is not a saved active loot container."));
            return 0;
        }

        BlockEntity blockEntity = player.serverLevel().getBlockEntity(targetPos);
        if (!(blockEntity instanceof Container container)) {
            player.sendSystemMessage(Component.literal("Target active loot container has no readable inventory."));
            return 0;
        }

        LootResult result = moveContainerLootToBackpack(player, container);
        container.setChanged();
        player.sendSystemMessage(Component.literal(String.format("Looted %d stacks (%d items), %d credits, %.2f weight. Could not fit: %d stacks.",
                result.movedStacks,
                result.movedItems,
                result.movedValue,
                result.movedWeight,
                result.failedStacks)));
        if (!result.failureReason.isBlank()) {
            player.sendSystemMessage(Component.literal("Last blocked item: " + result.failureReason));
        }
        sendStatus(player);
        return result.movedStacks > 0 ? 1 : 0;
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

    private static void sendDebugLine(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal(truncate(line, 260)));
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static List<String> suspectedIdentityFields(Tag tag) {
        List<String> fields = new ArrayList<>();
        collectSuspectedIdentityFields("", tag, fields);
        return fields;
    }

    private static void collectSuspectedIdentityFields(String path, Tag tag, List<String> fields) {
        if (tag instanceof CompoundTag compoundTag) {
            for (String key : compoundTag.getAllKeys()) {
                Tag child = compoundTag.get(key);
                String childPath = path.isBlank() ? key : path + "." + key;
                if (isSuspectedIdentityKey(key) || (child != null && isSuspectedIdentityValue(child.toString()))) {
                    fields.add(childPath + "=" + child);
                }
                if (child != null) {
                    collectSuspectedIdentityFields(childPath, child, fields);
                }
            }
            return;
        }

        if (tag instanceof ListTag listTag) {
            for (int i = 0; i < listTag.size(); i++) {
                collectSuspectedIdentityFields(path + "[" + i + "]", listTag.get(i), fields);
            }
        }
    }

    private static boolean isSuspectedIdentityKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("id")
                || lower.contains("name")
                || lower.contains("ammo")
                || lower.contains("gun")
                || lower.contains("attachment")
                || lower.contains("bullet")
                || lower.contains("caliber")
                || lower.contains("tacz");
    }

    private static boolean isSuspectedIdentityValue(String value) {
        String lower = value.toLowerCase();
        return lower.contains("tacz")
                || lower.contains("ammo")
                || lower.contains("gun")
                || lower.contains("attachment")
                || lower.contains("bullet")
                || lower.contains("9mm")
                || lower.contains("caliber");
    }

    private static BlockPos targetedBlock(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(6.0D));
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }

    private static RaidContainerEntry findActiveContainer(ServerPlayer player, BlockPos targetPos) {
        for (String mapId : RaidContainerService.savedMapIds()) {
            RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
            if (layout == null) {
                continue;
            }

            for (RaidContainerEntry entry : layout.containers()) {
                if (entry.activeLootContainer()
                        && entry.dimensionId().equals(player.serverLevel().dimension().location())
                        && entry.pos().equals(targetPos)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static LootResult moveContainerLootToBackpack(ServerPlayer player, Container container) {
        LootResult result = new LootResult();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack copy = stack.copy();
            RaidInventory.AddResult addResult = RaidInventoryManager.addStackToBackpack(player, copy);
            if (!addResult.success()) {
                result.failedStacks++;
                result.failureReason = BuiltInRegistries.ITEM.getKey(copy.getItem()) + ": " + addResult.message();
                continue;
            }

            ItemCarryProfile profile = RaidInventoryManager.profileFor(copy).orElse(null);
            result.movedStacks++;
            result.movedItems += copy.getCount();
            result.movedValue += RaidInventoryManager.valueFor(copy);
            if (profile != null) {
                result.movedWeight += profile.weight() * copy.getCount();
            }
            container.setItem(slot, ItemStack.EMPTY);
        }
        return result;
    }

    private static class LootResult {
        private int movedStacks;
        private int movedItems;
        private int movedValue;
        private double movedWeight;
        private int failedStacks;
        private String failureReason = "";
    }
}
