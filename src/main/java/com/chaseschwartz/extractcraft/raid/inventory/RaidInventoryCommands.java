package com.chaseschwartz.extractcraft.raid.inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentity;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentityResolver;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.itemidentity.TaczDisplayNameResolver;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.items.LooseLootDefinition;
import com.chaseschwartz.extractcraft.network.OpenVanillaInventoryPayload;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerEntry;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerLayout;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.neoforged.neoforge.network.PacketDistributor;

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
                        .then(Commands.literal("screen")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> openScreen(context.getSource())))
                        .then(Commands.literal("open")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> openScreen(context.getSource())))
                        .then(Commands.literal("summary")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> openSummaryScreen(context.getSource())))
                        .then(Commands.literal("vanilla")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> openVanillaInventory(context.getSource())))
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
                        .then(Commands.literal("clearall")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> clear(context.getSource())))
                        .then(Commands.literal("weight")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> weight(context.getSource())))
                        .then(Commands.literal("value")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> value(context.getSource()))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("vanilla_inventory")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> openVanillaInventory(context.getSource())))
                        .then(Commands.literal("givegun")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("gun", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("ak");
                                            builder.suggest("ak47");
                                            builder.suggest("glock");
                                            builder.suggest("m95");
                                            builder.suggest("rpg");
                                            builder.suggest("m249");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest("stash");
                                                    builder.suggest("backpack");
                                                    builder.suggest("primary");
                                                    builder.suggest("secondary");
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> debugGiveGun(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "gun"),
                                                        StringArgumentType.getString(context, "target"))))))
                        .then(Commands.literal("givegear")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("gear", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("backpack_small");
                                            builder.suggest("backpack_medium");
                                            builder.suggest("backpack_large");
                                            builder.suggest("vest_basic");
                                            builder.suggest("safe_alpha");
                                            builder.suggest("helmet_test");
                                            builder.suggest("armor_test");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest("stash");
                                                    builder.suggest("backpack");
                                                    builder.suggest("equipment");
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> debugGiveGear(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "gear"),
                                                        StringArgumentType.getString(context, "target"))))))
                        .then(Commands.literal("giveitem")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String id : debugExtractCraftItemIds()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest("stash");
                                                    builder.suggest("backpack");
                                                    builder.suggest("equipment");
                                                    builder.suggest("player");
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> debugGiveItem(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "item"),
                                                        StringArgumentType.getString(context, "target"),
                                                        1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                        .executes(context -> debugGiveItem(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "item"),
                                                                StringArgumentType.getString(context, "target"),
                                                        IntegerArgumentType.getInteger(context, "count"))))))))
                        .then(Commands.literal("giveuitestkit")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.literal("stash")
                                        .executes(context -> debugGiveUiTestKit(context.getSource()))))
                        .then(Commands.literal("lootreport")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> debugLootReport(context.getSource())))
                        .then(Commands.literal("lootcatalog")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> debugLootCatalog(context.getSource())))
                        .then(Commands.literal("taczreport")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> debugTaczReport(context.getSource())))
                        .then(Commands.literal("spawnloottest")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> debugSpawnLootTest(context.getSource(), 2))
                                .then(Commands.argument("countPerContext", IntegerArgumentType.integer(1, 12))
                                        .executes(context -> debugSpawnLootTest(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "countPerContext")))))
                        .then(Commands.literal("clearloottest")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> debugClearLootTest(context.getSource())))
                        .then(Commands.literal("giveloottest")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("blue");
                                            builder.suggest("purple");
                                            builder.suggest("gold");
                                            builder.suggest("red");
                                            builder.suggest("all");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> debugGiveLootTest(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "rarity")))))
                        .then(Commands.literal("sampleloot")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("context", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("all");
                                            for (String sampleContext : RaidContainerService.sampleLootContexts()) {
                                                builder.suggest(sampleContext);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                                                .executes(context -> debugSampleLoot(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "context"),
                                                        IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("lootstats")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("context", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("all");
                                            for (String sampleContext : RaidContainerService.sampleLootContexts()) {
                                                builder.suggest(sampleContext);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                                                .executes(context -> debugSampleLoot(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "context"),
                                                        IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("listloot")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> debugListLoot(context.getSource(), "all"))
                                .then(Commands.argument("filter", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("all");
                                            builder.suggest("blue");
                                            builder.suggest("purple");
                                            builder.suggest("gold");
                                            builder.suggest("red");
                                            LooseLootDefinition.DEFINITIONS.stream()
                                                    .map(LooseLootDefinition::category)
                                                    .distinct()
                                                    .sorted()
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> debugListLoot(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "filter")))))
                .then(Commands.literal("dropheld")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> dropHeld(context.getSource())))
                .then(Commands.literal("currencydebug")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> currencyDebug(context.getSource())))
                .then(Commands.literal("raid")
                        .then(Commands.literal("debug_keep_gamemode")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setRaidDebugKeepGameMode(context.getSource(), BoolArgumentType.getBool(context, "enabled"))))))
                .then(Commands.literal("raidresult")
                        .then(Commands.literal("status")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> raidResultStatus(context.getSource())))
                        .then(Commands.literal("stash")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> raidResultStash(context.getSource())))
                        .then(Commands.literal("keep")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> raidResultKeep(context.getSource()))))
                .then(Commands.literal("stash")
                        .then(Commands.literal("status")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> stashStatus(context.getSource(), "name")))
                        .then(Commands.literal("open")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> stashOpen(context.getSource())))
                        .then(Commands.literal("clear")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.literal("confirm")
                                        .executes(context -> stashClear(context.getSource()))))
                        .then(Commands.literal("filltest")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> stashFillTest(context.getSource(), 0))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> stashFillTest(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("sort")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("value");
                                            builder.suggest("weight");
                                            builder.suggest("category");
                                            builder.suggest("name");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> stashStatus(context.getSource(), StringArgumentType.getString(context, "mode")))))
                        .then(Commands.literal("upgrade")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> stashUpgrade(context.getSource()))))
                .then(Commands.literal("credits")
                        .then(Commands.literal("get")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> creditsGet(context.getSource())))
                        .then(Commands.literal("add")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(context -> creditsAdd(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                        .then(Commands.literal("set")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> creditsSet(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("raidweapon")
                        .then(Commands.literal("primary")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> equipRaidWeapon(context.getSource(), RaidEquipmentSlot.PRIMARY_WEAPON)))
                        .then(Commands.literal("secondary")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> equipRaidWeapon(context.getSource(), RaidEquipmentSlot.SECONDARY_WEAPON)))
                        .then(Commands.literal("holster")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> holsterRaidWeapon(context.getSource())))
                        .then(Commands.literal("debugammo")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setRaidWeaponDebugAmmo(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("ammodebug")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> raidWeaponAmmoDebug(context.getSource())))
                        .then(Commands.literal("status")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> raidWeaponStatus(context.getSource())))));
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

    private static int openScreen(CommandSourceStack source) throws CommandSyntaxException {
        RaidInventoryScreenOpener.openGrid(source.getPlayerOrException());
        return 1;
    }

    private static int openSummaryScreen(CommandSourceStack source) throws CommandSyntaxException {
        RaidInventoryScreenOpener.open(source.getPlayerOrException());
        return 1;
    }

    private static int openVanillaInventory(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("Debug: opening vanilla inventory during an active raid. This bypass is for testing only."));
        }
        PacketDistributor.sendToPlayer(player, OpenVanillaInventoryPayload.INSTANCE);
        return 1;
    }

    private static int debugGiveGun(CommandSourceStack source, String gunAlias, String targetName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String lookupKey = debugGunLookupKey(gunAlias);
        if (lookupKey == null) {
            player.sendSystemMessage(Component.literal("Unknown debug gun '" + gunAlias + "'. Try ak, ak47, glock, m95, rpg, or m249."));
            return 0;
        }

        ItemStack stack = ItemStackVariantFactory.create(lookupKey, 1).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Could not create TaCZ variant stack for " + lookupKey + ". Is TaCZ loaded?"));
            return 0;
        }

        RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, stack).orElse(null);
        if (item == null) {
            player.sendSystemMessage(Component.literal(lookupKey + " has no carry profile; cannot insert into ExtractCraft storage."));
            return 0;
        }

        String target = targetName.toLowerCase(java.util.Locale.ROOT);
        RaidInventory.AddResult result;
        if (target.equals("stash")) {
            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            RaidInventoryItem stashItem = item.withoutPlacement();
            int moved = data.stash().addPartial(stashItem);
            if (moved < stashItem.count()) {
                player.sendSystemMessage(Component.literal("Stash does not have enough capacity for " + item.displayName() + "."));
                return 0;
            }
            PlayerStashService.save(player, data);
            player.sendSystemMessage(Component.literal("Debug spawned " + item.displayName() + " into persistent stash."));
            return 1;
        }

        RaidEquipmentSlot targetSlot = debugStorageTarget(target);
        if (targetSlot == null) {
            player.sendSystemMessage(Component.literal("Unknown target '" + targetName + "'. Use stash, backpack, primary, or secondary."));
            return 0;
        }

        if (RaidManager.isInRaid(player)) {
            result = RaidInventoryManager.addStackTo(player, stack, targetSlot);
            player.sendSystemMessage(Component.literal("Debug givegun raid target " + target + ": " + result.message()));
            return result.success() ? 1 : 0;
        }

        PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
        result = switch (targetSlot) {
            case PRIMARY_WEAPON, SECONDARY_WEAPON -> data.baseInventory().addToWeaponSlot(item.withoutPlacement(), targetSlot);
            case BACKPACK -> data.baseInventory().addToBackpack(item.withoutPlacement());
            case VEST -> {
                ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
                yield profile == null
                        ? new RaidInventory.AddResult(false, RaidEquipmentSlot.VEST, item.lookupKey() + " has no carry profile.")
                        : data.baseInventory().addToVest(item.withoutPlacement(), profile);
            }
            case SAFE_BOX -> {
                ItemCarryProfile profile = ItemCarryProfileRegistry.get(item.lookupKey()).orElse(null);
                yield profile == null
                        ? new RaidInventory.AddResult(false, RaidEquipmentSlot.SAFE_BOX, item.lookupKey() + " has no carry profile.")
                        : data.baseInventory().addToSafeBox(item.withoutPlacement(), profile);
            }
            default -> new RaidInventory.AddResult(false, targetSlot, "Unsupported debug storage target.");
        };
        if (!result.success()) {
            player.sendSystemMessage(Component.literal("Debug givegun base target " + target + ": " + result.message()));
            return 0;
        }

        PlayerStashService.save(player, data);
        player.sendSystemMessage(Component.literal("Debug spawned " + item.displayName() + " into base " + target + "."));
        return 1;
    }

    private static String debugGunLookupKey(String alias) {
        return switch (alias.toLowerCase(java.util.Locale.ROOT)) {
            case "ak", "ak47", "akm" -> "tacz:modern_kinetic_gun#tacz:ak47";
            case "glock", "glock17", "pistol" -> "tacz:modern_kinetic_gun#tacz:glock_17";
            case "m95", "sniper" -> "tacz:modern_kinetic_gun#tacz:m95";
            case "rpg", "rpg7" -> "tacz:modern_kinetic_gun#tacz:rpg7";
            case "m249", "lmg" -> "tacz:modern_kinetic_gun#tacz:m249";
            default -> null;
        };
    }

    private static int debugGiveGear(CommandSourceStack source, String gearAlias, String targetName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation itemId = debugGearItemId(gearAlias);
        if (itemId == null) {
            player.sendSystemMessage(Component.literal("Unknown debug gear '" + gearAlias + "'. Try backpack_small, backpack_medium, backpack_large, vest_basic, safe_alpha, helmet_test, or armor_test."));
            return 0;
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), 1);
        RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, stack).orElse(null);
        if (item == null) {
            player.sendSystemMessage(Component.literal(itemId + " has no carry profile; cannot insert debug gear."));
            return 0;
        }

        String target = targetName.toLowerCase(java.util.Locale.ROOT);
        if (target.equals("stash")) {
            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            int moved = data.stash().addPartial(item.withoutPlacement());
            if (moved < item.count()) {
                player.sendSystemMessage(Component.literal("Stash does not have enough capacity for " + item.displayName() + "."));
                return 0;
            }
            PlayerStashService.save(player, data);
            player.sendSystemMessage(Component.literal("Debug spawned " + item.displayName() + " into persistent stash."));
            return 1;
        }

        if (target.equals("backpack")) {
            if (RaidManager.isInRaid(player)) {
                RaidInventory.AddResult raidResult = RaidInventoryManager.addStackTo(player, stack, RaidEquipmentSlot.BACKPACK);
                player.sendSystemMessage(Component.literal("Debug givegear raid backpack: " + raidResult.message()));
                return raidResult.success() ? 1 : 0;
            }

            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            RaidInventory.AddResult result = data.baseInventory().addToBackpack(item.withoutPlacement());
            if (!result.success()) {
                player.sendSystemMessage(Component.literal("Debug givegear base backpack: " + result.message()));
                return 0;
            }
            PlayerStashService.save(player, data);
            player.sendSystemMessage(Component.literal("Debug spawned " + item.displayName() + " into base backpack."));
            return 1;
        }

        if (target.equals("equipment")) {
            RaidEquipmentSlot slot = debugGearEquipmentSlot(gearAlias);
            if (slot == null) {
                player.sendSystemMessage(Component.literal("No equipment slot is mapped for " + gearAlias + "."));
                return 0;
            }
            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            RaidInventoryItem previous = data.baseInventory().itemAt(slot, 0);
            if (previous != null && data.stash().countAddable(previous.withoutPlacement(), -1) < previous.count()) {
                player.sendSystemMessage(Component.literal("Stash does not have room for currently equipped " + previous.displayName() + "."));
                return 0;
            }
            RaidInventory.AddResult result = data.baseInventory().setEquipmentSlot(slot, item.withoutPlacement());
            if (!result.success()) {
                player.sendSystemMessage(Component.literal("Debug givegear equipment: " + result.message()));
                return 0;
            }
            if (previous != null) {
                data.stash().addPartial(previous.withoutPlacement());
            }
            PlayerStashService.save(player, data);
            player.sendSystemMessage(Component.literal("Debug equipped " + item.displayName() + " in " + slot.name().toLowerCase(java.util.Locale.ROOT) + "."));
            return 1;
        }

        player.sendSystemMessage(Component.literal("Unknown target '" + targetName + "'. Use stash, backpack, or equipment."));
        return 0;
    }

    private static int debugGiveItem(CommandSourceStack source, String itemName, String targetName, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation itemId = debugItemId(itemName);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            player.sendSystemMessage(Component.literal("Unknown item '" + itemName + "'. Use an ExtractCraft item id such as atlas_raid_pack."));
            return 0;
        }

        String target = targetName.toLowerCase(java.util.Locale.ROOT);
        int remaining = count;
        int movedTotal = 0;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), Math.min(remaining, BuiltInRegistries.ITEM.get(itemId).getDefaultMaxStackSize()));
            int moved = debugInsertItem(player, stack, target);
            if (moved <= 0) {
                break;
            }
            movedTotal += moved;
            remaining -= moved;
            if (target.equals("equipment")) {
                break;
            }
        }

        if (movedTotal <= 0) {
            player.sendSystemMessage(Component.literal("Could not give " + itemId + " to " + target + ". Target may be full or incompatible."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Debug spawned " + movedTotal + "x " + itemId + " to " + target + "."));
        return 1;
    }

    private static int debugGiveLootTest(CommandSourceStack source, String rarityName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String rarity = rarityName.toLowerCase(java.util.Locale.ROOT);
        if (!rarity.equals("blue") && !rarity.equals("purple") && !rarity.equals("gold") && !rarity.equals("red") && !rarity.equals("all")) {
            player.sendSystemMessage(Component.literal("Unknown loot rarity '" + rarityName + "'. Use blue, purple, gold, red, or all."));
            return 0;
        }

        int attempted = 0;
        int moved = 0;
        for (LooseLootDefinition definition : LooseLootDefinition.DEFINITIONS) {
            if (!rarity.equals("all") && !definition.rarity().equals(rarity)) {
                continue;
            }
            attempted++;
            ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, definition.id());
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            moved += debugInsertItem(player, stack, "stash");
        }

        player.sendSystemMessage(Component.literal("Debug spawned " + moved + "/" + attempted + " " + rarity + " loose loot item(s) to stash."));
        return moved > 0 ? 1 : 0;
    }

    private static int debugGiveUiTestKit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<String> itemNames = List.of(
                "scrapline_helmet",
                "apex_assault_helmet",
                "softshell_plate_carrier",
                "juggernaut_assault_armor",
                "sparrow_sling_pack",
                "atlas_raid_pack",
                "scout_chest_rig",
                "specter_combat_rig",
                "pioneer_lockbox",
                "omega_safe_container",
                "combat_stim_syringe",
                "field_med_kit",
                "trauma_response_case",
                "helmet_rebuild_kit",
                "armor_rebuild_kit",
                "pack_rebuild_kit",
                "sealed_cable_bundle",
                "encrypted_usb_token",
                "quantum_signal_chip",
                "heart_of_the_dam",
                "minecraft:string",
                "minecraft:bread");

        int added = 0;
        List<String> failed = new ArrayList<>();
        for (String itemName : itemNames) {
            ResourceLocation itemId = debugItemId(itemName);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                failed.add(itemName + " (missing)");
                continue;
            }

            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            int moved = debugInsertItem(player, stack, "stash");
            if (moved > 0) {
                added += moved;
            } else {
                failed.add(itemName);
            }
        }

        player.sendSystemMessage(Component.literal("UI test kit added " + added + "/" + itemNames.size() + " item(s) to stash."));
        if (!failed.isEmpty()) {
            player.sendSystemMessage(Component.literal("UI test kit skipped: " + String.join(", ", failed)));
        }
        return added > 0 ? 1 : 0;
    }

    private static int debugLootReport(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            java.nio.file.Path path = LootAuditReport.write();
            java.nio.file.Path taczPath = TaczAuditReport.write();
            player.sendSystemMessage(Component.literal("Wrote ExtractCraft loot report: " + path.toAbsolutePath()));
            player.sendSystemMessage(Component.literal("Wrote ExtractCraft TaCZ report: " + taczPath.toAbsolutePath()));
            return 1;
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to write ExtractCraft loot report", exception);
            player.sendSystemMessage(Component.literal("Failed to write loot report: " + exception.getMessage()));
            return 0;
        }
    }

    private static int debugLootCatalog(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            Path path = LootCatalogReport.write();
            player.sendSystemMessage(Component.literal("Wrote ExtractCraft loot catalog: " + path.toAbsolutePath()));
            return 1;
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to write ExtractCraft loot catalog", exception);
            player.sendSystemMessage(Component.literal("Failed to write loot catalog: " + exception.getMessage()));
            return 0;
        }
    }

    private static int debugTaczReport(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            java.nio.file.Path path = TaczAuditReport.write();
            player.sendSystemMessage(Component.literal("Wrote ExtractCraft TaCZ report: " + path.toAbsolutePath()));
            return 1;
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to write ExtractCraft TaCZ report", exception);
            player.sendSystemMessage(Component.literal("Failed to write TaCZ report: " + exception.getMessage()));
            return 0;
        }
    }

    private static int debugSpawnLootTest(CommandSourceStack source, int countPerContext) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            RaidContainerService.SpawnLootTestResult result = RaidContainerService.spawnLootTest(player, countPerContext);
            player.sendSystemMessage(Component.literal("Spawned " + result.spawnedCount()
                    + " ExtractCraft loot test container(s), " + result.countPerContext()
                    + " per context. Blocked/skipped: " + result.blockedCount() + "."));
            for (Map.Entry<String, BlockPos> entry : result.rowStarts().entrySet()) {
                BlockPos pos = entry.getValue();
                player.sendSystemMessage(Component.literal(entry.getKey().toUpperCase(Locale.ROOT)
                        + " row starts at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            }
            player.sendSystemMessage(Component.literal("Right-click these containers to open the ExtractCraft loot UI. Placeholder loose-loot icons are expected for now."));
            player.sendSystemMessage(Component.literal("Debug layout saved at " + result.layoutPath().toAbsolutePath() + ". Use /extractcraft debug clearloottest to remove the last layout."));
            return result.spawnedCount() > 0 ? 1 : 0;
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to spawn ExtractCraft loot test containers", exception);
            player.sendSystemMessage(Component.literal("Failed to spawn loot test containers: " + exception.getMessage()));
            return 0;
        }
    }

    private static int debugClearLootTest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            RaidContainerService.ClearLootTestResult result = RaidContainerService.clearLootTest(player.serverLevel());
            player.sendSystemMessage(Component.literal("Cleared " + result.removedCount()
                    + " loot test container(s). Skipped: " + result.skippedCount() + "."));
            return result.removedCount() > 0 ? 1 : 0;
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to clear ExtractCraft loot test containers", exception);
            player.sendSystemMessage(Component.literal("Failed to clear loot test containers: " + exception.getMessage()));
            return 0;
        }
    }

    private static int debugSampleLoot(CommandSourceStack source, String contextName, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String normalizedContext = contextName.toLowerCase(Locale.ROOT);
        if (normalizedContext.equals("all")) {
            int successful = 0;
            for (String sampleContext : RaidContainerService.sampleLootContexts()) {
                successful += debugSampleLootContext(player, sampleContext, count);
            }
            return successful > 0 ? 1 : 0;
        }

        return debugSampleLootContext(player, normalizedContext, count) > 0 ? 1 : 0;
    }

    private static int debugSampleLootContext(ServerPlayer player, String contextName, int count) {
        List<RaidContainerService.LootSample> rolls = RaidContainerService.sampleLootRolls(contextName, count, new Random(System.nanoTime() ^ player.getUUID().hashCode() ^ contextName.hashCode()));
        List<ItemValueEntry> samples = rolls.stream().map(RaidContainerService.LootSample::entry).toList();
        if (samples.isEmpty()) {
            player.sendSystemMessage(Component.literal("No loot samples available for context '" + contextName + "'."));
            return 0;
        }

        int totalValue = samples.stream().mapToInt(ItemValueEntry::value).sum();
        double averageValue = samples.isEmpty() ? 0.0D : totalValue / (double) samples.size();
        int minValue = samples.stream().mapToInt(ItemValueEntry::value).min().orElse(0);
        int maxValue = samples.stream().mapToInt(ItemValueEntry::value).max().orElse(0);
        Map<String, Long> rarityCounts = orderedCounts(samples.stream()
                .collect(Collectors.groupingBy(entry -> entry.rarity().name().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.counting())),
                List.of("blue", "purple", "gold", "red"));
        Map<String, Long> categoryCounts = samples.stream()
                .collect(Collectors.groupingBy(entry -> entry.category().name().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        Map<String, Long> commonCounts = samples.stream()
                .collect(Collectors.groupingBy(RaidInventoryCommands::lootSampleName, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> sourceCounts = rolls.stream()
                .collect(Collectors.groupingBy(roll -> roll.source().name().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> taczKindCounts = rolls.stream()
                .filter(roll -> roll.source() == RaidContainerService.LootSource.TACZ_FIRST_CLASS)
                .map(RaidContainerService.LootSample::entry)
                .collect(Collectors.groupingBy(entry -> RaidContainerService.taczKind(entry).name().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> countRangeCounts = samples.stream()
                .collect(Collectors.groupingBy(entry -> RaidContainerService.countRange(entry).describe(), LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        String highest = samples.stream()
                .sorted(Comparator.comparingInt(ItemValueEntry::value).reversed().thenComparing(RaidInventoryCommands::lootSampleName))
                .limit(5)
                .map(entry -> lootSampleName(entry) + " (" + entry.value() + " cr)")
                .collect(Collectors.joining(", "));
        String common = commonCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .collect(Collectors.joining(", "));

        player.sendSystemMessage(Component.literal("Loot sample [" + contextName + "] rolls=" + count + " produced=" + samples.size()
                + " | value total=" + totalValue + " avg=" + Math.round(averageValue) + " min=" + minValue + " max=" + maxValue));
        player.sendSystemMessage(Component.literal(RaidContainerService.lootContextExpectationLine(contextName)));
        player.sendSystemMessage(Component.literal("Sources: " + formatCountsWithPercent(sourceCounts, samples.size())
                + (taczKindCounts.isEmpty() ? "" : " | TaCZ: " + formatCounts(taczKindCounts, 8))));
        player.sendSystemMessage(Component.literal("Rarity: " + formatCountsWithPercent(rarityCounts, samples.size())));
        player.sendSystemMessage(Component.literal("Categories: " + formatCounts(categoryCounts, 8)));
        player.sendSystemMessage(Component.literal("Spawn counts: " + formatCounts(countRangeCounts, 8)));
        player.sendSystemMessage(Component.literal("Top value: " + highest));
        player.sendSystemMessage(Component.literal("Most common: " + common));
        return 1;
    }

    private static int debugListLoot(CommandSourceStack source, String filterName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String filter = filterName.toLowerCase(Locale.ROOT);
        List<LooseLootDefinition> matches = LooseLootDefinition.DEFINITIONS.stream()
                .filter(definition -> filter.equals("all")
                        || definition.rarity().equals(filter)
                        || definition.category().equals(filter)
                        || definition.id().contains(filter))
                .sorted(Comparator.comparing(LooseLootDefinition::rarity)
                        .thenComparing(LooseLootDefinition::category)
                        .thenComparing(LooseLootDefinition::id))
                .toList();
        if (matches.isEmpty()) {
            player.sendSystemMessage(Component.literal("No loose loot definitions matched '" + filterName + "'. Try all, blue, purple, gold, red, or a category."));
            return 0;
        }

        long registered = matches.stream()
                .filter(definition -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, definition.id())))
                .count();
        Map<String, Long> rarityCounts = orderedCounts(matches.stream()
                .collect(Collectors.groupingBy(LooseLootDefinition::rarity, LinkedHashMap::new, Collectors.counting())),
                List.of("blue", "purple", "gold", "red"));
        Map<String, Long> categoryCounts = matches.stream()
                .collect(Collectors.groupingBy(LooseLootDefinition::category, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));

        player.sendSystemMessage(Component.literal("Loose loot definitions [" + filter + "]: " + matches.size()
                + " matched, " + registered + "/" + matches.size() + " registered."));
        player.sendSystemMessage(Component.literal("Rarity: " + formatCounts(rarityCounts, 8)));
        player.sendSystemMessage(Component.literal("Categories: " + formatCounts(categoryCounts, 12)));

        for (int i = 0; i < matches.size(); i += 4) {
            String line = matches.subList(i, Math.min(i + 4, matches.size())).stream()
                    .map(definition -> definition.id()
                            + " [" + definition.rarity()
                            + "/" + definition.category()
                            + "/" + definition.value() + "cr"
                            + "/" + definition.gridWidth() + "x" + definition.gridHeight() + "]")
                    .collect(Collectors.joining(" | "));
            player.sendSystemMessage(Component.literal(line));
        }
        return 1;
    }

    private static Map<String, Long> orderedCounts(Map<String, Long> counts, List<String> preferredOrder) {
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (String key : preferredOrder) {
            if (counts.containsKey(key)) {
                ordered.put(key, counts.get(key));
            }
        }
        counts.entrySet().stream()
                .filter(entry -> !ordered.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    private static String formatCountsWithPercent(Map<String, Long> counts, int total) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue() + " (" + formatPercent(entry.getValue(), total) + "%)")
                .collect(Collectors.joining(", "));
    }

    private static String formatCounts(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .limit(limit)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String formatPercent(long count, int total) {
        if (total <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", count * 100.0D / total);
    }

    private static String lootSampleName(ItemValueEntry entry) {
        return TaczDisplayNameResolver.displayName(entry.lookupKey(), BuiltInRegistries.ITEM.get(entry.itemId()).getDescription().getString());
    }

    private static int debugInsertItem(ServerPlayer player, ItemStack stack, String target) {
        if (target.equals("player")) {
            return player.getInventory().add(stack.copy()) ? stack.getCount() : 0;
        }

        RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, stack).orElse(null);
        if (item == null) {
            player.sendSystemMessage(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " has no carry profile; cannot insert into ExtractCraft storage."));
            return 0;
        }

        if (target.equals("stash")) {
            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            int moved = data.stash().addPartial(item.withoutPlacement());
            if (moved > 0) {
                PlayerStashService.save(player, data);
            }
            return moved;
        }

        if (target.equals("backpack")) {
            if (RaidManager.isInRaid(player)) {
                RaidInventory.AddResult result = RaidInventoryManager.addStackTo(player, stack, RaidEquipmentSlot.BACKPACK);
                player.sendSystemMessage(Component.literal("Debug giveitem raid backpack: " + result.message()));
                return result.success() ? result.movedCount() : 0;
            }
            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            RaidInventory.AddResult result = data.baseInventory().addToBackpack(item.withoutPlacement());
            if (result.success()) {
                PlayerStashService.save(player, data);
                return result.movedCount();
            }
            player.sendSystemMessage(Component.literal("Debug giveitem base backpack: " + result.message()));
            return 0;
        }

        if (target.equals("equipment")) {
            RaidEquipmentSlot slot = debugEquipmentSlotFor(item);
            if (slot == null) {
                player.sendSystemMessage(Component.literal(item.displayName() + " is not compatible with any equipment slot."));
                return 0;
            }
            PlayerStashService.PlayerStashData data = PlayerStashService.load(player);
            RaidInventoryItem previous = data.baseInventory().equipmentItem(slot);
            if (previous != null && data.stash().countAddable(previous.withoutPlacement(), -1) < previous.count()) {
                player.sendSystemMessage(Component.literal("Stash does not have room for currently equipped " + previous.displayName() + "."));
                return 0;
            }
            RaidInventory.AddResult result = data.baseInventory().setEquipmentSlot(slot, item.withoutPlacement());
            if (!result.success()) {
                player.sendSystemMessage(Component.literal("Debug giveitem equipment: " + result.message()));
                return 0;
            }
            if (previous != null) {
                data.stash().addPartial(previous.withoutPlacement());
            }
            PlayerStashService.save(player, data);
            return 1;
        }

        player.sendSystemMessage(Component.literal("Unknown target '" + target + "'. Use stash, backpack, equipment, or player."));
        return 0;
    }

    private static RaidEquipmentSlot debugEquipmentSlotFor(RaidInventoryItem item) {
        for (RaidEquipmentSlot slot : List.of(
                RaidEquipmentSlot.HELMET,
                RaidEquipmentSlot.ARMOR,
                RaidEquipmentSlot.EQUIPPED_BACKPACK,
                RaidEquipmentSlot.EQUIPPED_VEST,
                RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER)) {
            if (RaidInventory.canEquipItem(item, slot)) {
                return slot;
            }
        }
        return null;
    }

    private static ResourceLocation debugItemId(String itemName) {
        String alias = itemName.toLowerCase(java.util.Locale.ROOT);
        ResourceLocation mapped = debugGearItemId(alias);
        if (mapped != null) {
            return mapped;
        }
        if (alias.indexOf(':') >= 0) {
            return ResourceLocation.parse(alias);
        }
        return ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, alias);
    }

    private static List<String> debugExtractCraftItemIds() {
        List<String> ids = new ArrayList<>(List.of(
                "scrapline_helmet",
                "ranger_ballistic_helmet",
                "vector_rail_helmet",
                "apex_assault_helmet",
                "softshell_plate_carrier",
                "bulwark_plate_carrier",
                "warden_combat_armor",
                "juggernaut_assault_armor",
                "scout_chest_rig",
                "rangefinder_tactical_vest",
                "operator_load_bearing_vest",
                "specter_combat_rig",
                "sparrow_sling_pack",
                "fieldrunner_pack",
                "mule_tactical_pack",
                "atlas_raid_pack",
                "pioneer_lockbox",
                "blacksite_secure_case",
                "omega_safe_container",
                "combat_stim_syringe",
                "field_med_kit",
                "trauma_response_case",
                "splint_trauma_kit",
                "helmet_rebuild_kit",
                "armor_rebuild_kit",
                "pack_rebuild_kit"));
        LooseLootDefinition.DEFINITIONS.stream().map(LooseLootDefinition::id).forEach(ids::add);
        return List.copyOf(ids);
    }

    private static ResourceLocation debugGearItemId(String alias) {
        return switch (alias.toLowerCase(java.util.Locale.ROOT)) {
            case "backpack_small", "small_backpack", "small" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "sparrow_sling_pack");
            case "backpack_medium", "medium_backpack", "medium" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "mule_tactical_pack");
            case "backpack_large", "large_backpack", "large" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "atlas_raid_pack");
            case "vest_basic", "basic_vest", "vest" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "scout_chest_rig");
            case "safe_alpha", "alpha_safe", "safe" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "pioneer_lockbox");
            case "helmet_test", "helmet" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "scrapline_helmet");
            case "armor_test", "armor" -> ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "softshell_plate_carrier");
            default -> null;
        };
    }

    private static RaidEquipmentSlot debugGearEquipmentSlot(String alias) {
        return switch (alias.toLowerCase(java.util.Locale.ROOT)) {
            case "backpack_small", "small_backpack", "small", "backpack_medium", "medium_backpack", "medium", "backpack_large", "large_backpack", "large" -> RaidEquipmentSlot.EQUIPPED_BACKPACK;
            case "vest_basic", "basic_vest", "vest" -> RaidEquipmentSlot.EQUIPPED_VEST;
            case "safe_alpha", "alpha_safe", "safe" -> RaidEquipmentSlot.EQUIPPED_SAFE_CONTAINER;
            case "helmet_test", "helmet" -> RaidEquipmentSlot.HELMET;
            case "armor_test", "armor" -> RaidEquipmentSlot.ARMOR;
            default -> null;
        };
    }

    private static RaidEquipmentSlot debugStorageTarget(String target) {
        return switch (target) {
            case "backpack" -> RaidEquipmentSlot.BACKPACK;
            case "primary" -> RaidEquipmentSlot.PRIMARY_WEAPON;
            case "secondary" -> RaidEquipmentSlot.SECONDARY_WEAPON;
            default -> null;
        };
    }

    private static int equipRaidWeapon(CommandSourceStack source, RaidEquipmentSlot slot) throws CommandSyntaxException {
        return RaidWeaponService.equip(source.getPlayerOrException(), slot) ? 1 : 0;
    }

    private static int holsterRaidWeapon(CommandSourceStack source) throws CommandSyntaxException {
        return RaidWeaponService.holster(source.getPlayerOrException()) ? 1 : 0;
    }

    private static int raidWeaponStatus(CommandSourceStack source) throws CommandSyntaxException {
        source.getPlayerOrException().sendSystemMessage(Component.literal(RaidWeaponService.status(source.getPlayerOrException())));
        return 1;
    }

    private static int setRaidWeaponDebugAmmo(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        RaidWeaponService.setDebugInfiniteAmmo(enabled);
        source.getPlayerOrException().sendSystemMessage(Component.literal("Raid weapon debug infinite ammo " + (enabled ? "enabled" : "disabled") + ". Re-equip a weapon for the bridge stack to update."));
        return 1;
    }

    private static int setRaidDebugKeepGameMode(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        RaidManager.setDebugKeepGameMode(enabled);
        source.getPlayerOrException().sendSystemMessage(Component.literal("Raid debug keep game mode " + (enabled ? "enabled" : "disabled") + ". Default false forces survival during raids."));
        return 1;
    }

    private static int raidWeaponAmmoDebug(CommandSourceStack source) throws CommandSyntaxException {
        RaidWeaponService.sendAmmoDebug(source.getPlayerOrException());
        return 1;
    }

    private static int raidResultStatus(CommandSourceStack source) throws CommandSyntaxException {
        RaidResultService.sendLastResult(source.getPlayerOrException());
        return 1;
    }

    private static int raidResultStash(CommandSourceStack source) throws CommandSyntaxException {
        RaidResultService.movePendingToStash(source.getPlayerOrException());
        return 1;
    }

    private static int raidResultKeep(CommandSourceStack source) throws CommandSyntaxException {
        RaidResultService.keepPendingOnCharacter(source.getPlayerOrException());
        return 1;
    }

    private static int stashStatus(CommandSourceStack source, String sortMode) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        for (String line : PlayerStashService.statusLines(player, sortMode)) {
            player.sendSystemMessage(Component.literal(line));
        }
        player.sendSystemMessage(Component.literal("First-pass stash UI is command-backed for now. Use /extractcraft raidresult stash, /extractcraft raidresult keep, and /extractcraft stash sort <mode>."));
        return 1;
    }

    private static int stashOpen(CommandSourceStack source) throws CommandSyntaxException {
        BaseStashScreenOpener.open(source.getPlayerOrException());
        return 1;
    }

    private static int stashClear(CommandSourceStack source) throws CommandSyntaxException {
        PlayerStashService.clear(source.getPlayerOrException());
        source.getPlayerOrException().sendSystemMessage(Component.literal("Cleared persistent ExtractCraft stash, base inventory, credits, and stash upgrades."));
        return 1;
    }

    private static int stashFillTest(CommandSourceStack source, int count) throws CommandSyntaxException {
        PlayerStashService.FillTestResult result = PlayerStashService.fillTest(source.getPlayerOrException(), count);
        source.getPlayerOrException().sendSystemMessage(Component.literal(String.format(
                "Stash filltest added %d stacks%s. Stash now %d/%d slots.",
                result.addedStacks(),
                result.skippedStacks() > 0 ? " (skipped " + result.skippedStacks() + " missing/profileless stacks)" : "",
                result.usedCapacity(),
                result.maxCapacity())));
        return result.addedStacks() > 0 ? 1 : 0;
    }

    private static int stashUpgrade(CommandSourceStack source) throws CommandSyntaxException {
        return PlayerStashService.upgrade(source.getPlayerOrException()) ? 1 : 0;
    }

    private static int creditsGet(CommandSourceStack source) throws CommandSyntaxException {
        PlayerStashService.PlayerStashData data = PlayerStashService.load(source.getPlayerOrException());
        source.getPlayerOrException().sendSystemMessage(Component.literal("Emeralds: " + data.credits()));
        return 1;
    }

    private static int creditsAdd(CommandSourceStack source, int amount) throws CommandSyntaxException {
        PlayerStashService.addCredits(source.getPlayerOrException(), amount);
        PlayerStashService.PlayerStashData data = PlayerStashService.load(source.getPlayerOrException());
        source.getPlayerOrException().sendSystemMessage(Component.literal("Emeralds: " + data.credits()));
        return 1;
    }

    private static int creditsSet(CommandSourceStack source, int amount) throws CommandSyntaxException {
        PlayerStashService.setCredits(source.getPlayerOrException(), amount);
        source.getPlayerOrException().sendSystemMessage(Component.literal("Emeralds: " + amount));
        return 1;
    }

    private static int currencyDebug(CommandSourceStack source) throws CommandSyntaxException {
        PlayerStashService.PlayerStashData data = PlayerStashService.load(source.getPlayerOrException());
        source.getPlayerOrException().sendSystemMessage(Component.literal("Emeralds: " + data.credits()));
        source.getPlayerOrException().sendSystemMessage(Component.literal("Stash value: " + data.stash().totalValue() + " credits-equivalent."));
        source.getPlayerOrException().sendSystemMessage(Component.literal("Base inventory value: " + data.baseInventory().totalValue() + " credits-equivalent."));
        return 1;
    }

    private static int dropHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to drop it as an ExtractCraft managed drop."));
            return 0;
        }
        ItemStack drop = stack.copy();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ManagedDropService.spawnManagedDrop(player, drop);
        player.sendSystemMessage(Component.literal("Dropped managed item: " + drop.getCount() + "x " + drop.getHoverName().getString()));
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
        RaidInventory.AddResult result = inventory.setBackpack(backpack);
        player.sendSystemMessage(Component.literal(result.message()));
        if (!result.success()) {
            return 0;
        }
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
        RaidWeightService.WeightStatus status = RaidWeightService.status(inventory);
        source.sendSuccess(() -> Component.literal(String.format(
                "Raid inventory weight: %.2f/%.2f | stage=%s | movement=%.0f%%",
                status.currentWeight(),
                status.maxWeight(),
                status.stage().name().toLowerCase(java.util.Locale.ROOT),
                status.movementMultiplier() * 100.0D)), false);
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
