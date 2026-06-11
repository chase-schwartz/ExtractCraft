package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.durability.InRaidRepairService;
import com.chaseschwartz.extractcraft.network.QuickUseStatePayload;
import com.chaseschwartz.extractcraft.raid.BleedStatus;
import com.chaseschwartz.extractcraft.raid.BleedStatusService;
import com.chaseschwartz.extractcraft.raid.BleedStatusService.TreatmentStrength;
import com.chaseschwartz.extractcraft.raid.FractureStatusService;
import com.chaseschwartz.extractcraft.raid.RaidManager;
import com.chaseschwartz.extractcraft.timedaction.TimedAction;
import com.chaseschwartz.extractcraft.timedaction.TimedActionService;
import com.chaseschwartz.extractcraft.timedaction.TimedActionType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuickUseService {
    private static final String MEDICAL_CAPACITY_KEY = "extractcraft_med_capacity";
    private static final String CURRENT_KEY = "current";
    private static final String MAX_KEY = "max";
    public static final ResourceLocation HELMET_REBUILD_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "helmet_rebuild_kit");
    public static final ResourceLocation ARMOR_REBUILD_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "armor_rebuild_kit");
    public static final ResourceLocation PACK_REBUILD_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "pack_rebuild_kit");
    public static final ResourceLocation COMBAT_STIM_SYRINGE = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "combat_stim_syringe");
    public static final ResourceLocation FIELD_MED_KIT = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "field_med_kit");
    public static final ResourceLocation TRAUMA_RESPONSE_CASE = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "trauma_response_case");
    public static final ResourceLocation ADHESIVE_BANDAGE_BOX = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "adhesive_bandage_box");
    public static final ResourceLocation STERILE_GAUZE_BRICK = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "sterile_gauze_brick");
    public static final ResourceLocation COMBAT_TOURNIQUET_PACK = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "combat_tourniquet_pack");

    private static final Map<UUID, ResourceLocation> SELECTED_ITEMS = new java.util.HashMap<>();
    private static final java.util.Set<UUID> MANUAL_SELECTIONS = new java.util.HashSet<>();
    private static final List<ResourceLocation> DEFAULT_PRIORITY = List.of(
            TRAUMA_RESPONSE_CASE,
            FIELD_MED_KIT,
            COMBAT_STIM_SYRINGE,
            ARMOR_REBUILD_KIT,
            HELMET_REBUILD_KIT,
            PACK_REBUILD_KIT);
    private static final List<ResourceLocation> FRACTURE_PRIORITY = List.of(
            TRAUMA_RESPONSE_CASE,
            FIELD_MED_KIT,
            STERILE_GAUZE_BRICK,
            COMBAT_STIM_SYRINGE,
            ARMOR_REBUILD_KIT,
            HELMET_REBUILD_KIT,
            PACK_REBUILD_KIT);
    private static final List<ResourceLocation> LIGHT_BLEED_PRIORITY = List.of(
            ADHESIVE_BANDAGE_BOX,
            STERILE_GAUZE_BRICK,
            COMBAT_TOURNIQUET_PACK,
            TRAUMA_RESPONSE_CASE,
            FIELD_MED_KIT,
            COMBAT_STIM_SYRINGE,
            ARMOR_REBUILD_KIT,
            HELMET_REBUILD_KIT,
            PACK_REBUILD_KIT);
    private static final List<ResourceLocation> HEAVY_BLEED_PRIORITY = List.of(
            COMBAT_TOURNIQUET_PACK,
            TRAUMA_RESPONSE_CASE,
            FIELD_MED_KIT,
            STERILE_GAUZE_BRICK,
            COMBAT_STIM_SYRINGE,
            ARMOR_REBUILD_KIT,
            HELMET_REBUILD_KIT,
            PACK_REBUILD_KIT,
            ADHESIVE_BANDAGE_BOX);
    private static final Map<ResourceLocation, MedicalUse> MEDICAL_USES = Map.of(
            COMBAT_STIM_SYRINGE, new MedicalUse(TimedActionType.USE_MED, 8, 40, "Using Combat Stim..."),
            FIELD_MED_KIT, new MedicalUse(TimedActionType.USE_MED, 20, 60, "Using Field Med Kit..."),
            TRAUMA_RESPONSE_CASE, new MedicalUse(TimedActionType.USE_MED, 45, 80, "Using Trauma Response Case..."));
    private static final Map<ResourceLocation, BleedTreatmentUse> BLEED_TREATMENTS = Map.of(
            ADHESIVE_BANDAGE_BOX, new BleedTreatmentUse(TimedActionType.USE_BANDAGE, TreatmentStrength.LIGHT_ONLY, 40, "Applying Bandages...", "Light Bleed"),
            STERILE_GAUZE_BRICK, new BleedTreatmentUse(TimedActionType.USE_BANDAGE, TreatmentStrength.LIGHT_ONLY, 50, "Applying Gauze...", "Light Bleed"),
            COMBAT_TOURNIQUET_PACK, new BleedTreatmentUse(TimedActionType.USE_BANDAGE, TreatmentStrength.HEAVY_AND_LIGHT, 70, "Applying Tourniquet...", "Heavy Bleed"));
    private static final Map<ResourceLocation, FractureTreatmentUse> FRACTURE_TREATMENTS = Map.of(
            STERILE_GAUZE_BRICK, new FractureTreatmentUse(90, "Splinting Fracture...", 0, true),
            FIELD_MED_KIT, new FractureTreatmentUse(80, "Stabilizing Fracture...", 8, false),
            TRAUMA_RESPONSE_CASE, new FractureTreatmentUse(70, "Stabilizing Fracture...", 6, false));

    private QuickUseService() {
    }

    public static void syncOptions(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<QuickUseStatePayload.Option> options = options(player);
        ResourceLocation selected = reconcileSelection(player, options);
        PacketDistributor.sendToPlayer(player, new QuickUseStatePayload(selected == null ? "" : selected.toString(), options));
    }

    public static void setSelected(ServerPlayer player, String itemId) {
        if (player == null) {
            return;
        }
        if (!RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("Quick-use is only available in raid."));
            syncOptions(player);
            return;
        }

        ResourceLocation id = parseItemId(itemId).orElse(null);
        if (id == null || !isEligible(id)) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }
        QuickUseSource source = findFirst(player, id).orElse(null);
        if (source == null) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }

        SELECTED_ITEMS.put(player.getUUID(), id);
        MANUAL_SELECTIONS.add(player.getUUID());
        player.sendSystemMessage(Component.literal("Selected " + source.item().displayName() + "."));
        syncOptions(player);
    }

    public static void useSelected(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!RaidManager.isInRaid(player)) {
            player.sendSystemMessage(Component.literal("Quick-use is only available in raid."));
            syncOptions(player);
            return;
        }
        if (TimedActionService.activeAction(player).isPresent()) {
            player.sendSystemMessage(Component.literal("Already performing an action."));
            syncOptions(player);
            return;
        }

        ResourceLocation selected = SELECTED_ITEMS.get(player.getUUID());
        if (selected == null) {
            player.sendSystemMessage(Component.literal("No quick-use item selected."));
            syncOptions(player);
            return;
        }
        if (!isEligible(selected)) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            SELECTED_ITEMS.remove(player.getUUID());
            syncOptions(player);
            return;
        }

        QuickUseSource source = findFirst(player, selected).orElse(null);
        if (source == null) {
            player.sendSystemMessage(Component.literal("Selected quick-use item not found."));
            syncOptions(player);
            return;
        }

        GridMoveResult result = startUse(player, selected, source);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
        }
        syncOptions(player);
    }

    public static boolean isMedicalOrBleedTreatment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MEDICAL_USES.containsKey(itemId) || BLEED_TREATMENTS.containsKey(itemId) || FRACTURE_TREATMENTS.containsKey(itemId);
    }

    public static GridMoveResult startFromContext(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex) {
        if (player == null) {
            return GridMoveResult.failure("No player.");
        }
        if (!RaidManager.isInRaid(player)) {
            return GridMoveResult.failure("Medical use is only available in raid.");
        }
        if (TimedActionService.activeAction(player).isPresent()) {
            return GridMoveResult.failure("Already performing an action.");
        }
        RaidInventoryItem item = RaidInventoryManager.get(player).itemAt(source, sourceIndex);
        if (item == null) {
            return GridMoveResult.failure("Selected quick-use item not found.");
        }
        if (!MEDICAL_USES.containsKey(item.itemId()) && !BLEED_TREATMENTS.containsKey(item.itemId()) && !FRACTURE_TREATMENTS.containsKey(item.itemId())) {
            return GridMoveResult.failure("Selected quick-use item not found.");
        }
        return startUse(player, item.itemId(), new QuickUseSource(source, sourceIndex, item));
    }

    public static CompletionResult completeMedicalUse(ServerPlayer player, TimedAction action) {
        SourceRef sourceRef = parseSource(action.sourceReference().orElse(""));
        if (sourceRef == null) {
            return CompletionResult.failure("Medical action data was invalid.");
        }

        MedicalValidation validation = validateMedical(player, sourceRef.slot(), sourceRef.index(), sourceRef.expectedLookupKey(), sourceRef.itemId());
        if (!validation.success()) {
            if (player != null) {
                player.sendSystemMessage(Component.literal(validation.message()));
            }
            return CompletionResult.failure(validation.message());
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        ItemStack stack = validation.item().toItemStack();
        MedicalCapacity capacity = getOrInitializeCapacity(stack, validation.use());
        float before = player.getHealth();
        int requestedHeal = Math.min(capacity.current(), Math.max(1, (int) Math.ceil(player.getMaxHealth() - before)));
        player.heal(requestedHeal);
        int healed = Math.max(0, Math.min(capacity.current(), Math.round(player.getHealth() - before)));
        if (healed <= 0) {
            return CompletionResult.failure("Already at full health.");
        }
        int remaining = capacity.current() - healed;
        if (remaining <= 0) {
            inventory.removeCountAt(sourceRef.slot(), sourceRef.index(), 1);
        } else {
            writeCapacity(stack, remaining, validation.use().healCapacity());
            inventory.replaceItemAt(sourceRef.slot(), sourceRef.index(), validation.item().withStoredStack(stack));
        }
        String message = "Used " + validation.item().displayName() + ". Restored " + formatHealth(healed) + " HP.";
        player.sendSystemMessage(Component.literal(message));
        syncOptions(player);
        return CompletionResult.success(message);
    }

    public static CompletionResult completeBleedTreatment(ServerPlayer player, TimedAction action) {
        SourceRef sourceRef = parseSource(action.sourceReference().orElse(""));
        if (sourceRef == null) {
            return CompletionResult.failure("Bleed treatment action data was invalid.");
        }

        BleedTreatmentValidation validation = validateBleedTreatment(player, sourceRef.slot(), sourceRef.index(), sourceRef.expectedLookupKey(), sourceRef.itemId());
        if (!validation.success()) {
            if (player != null) {
                player.sendSystemMessage(Component.literal(validation.message()));
            }
            return CompletionResult.failure(validation.message());
        }

        BleedStatus treated = BleedStatusService.status(player);
        if (!BleedStatusService.clear(player)) {
            return CompletionResult.failure("No bleeding to treat.");
        }
        RaidInventoryManager.get(player).removeCountAt(sourceRef.slot(), sourceRef.index(), 1);
        String message = "Treated " + treated.label() + ".";
        player.sendSystemMessage(Component.literal(message));
        syncOptions(player);
        return CompletionResult.success(message);
    }

    public static CompletionResult completeFractureTreatment(ServerPlayer player, TimedAction action) {
        SourceRef sourceRef = parseSource(action.sourceReference().orElse(""));
        if (sourceRef == null) {
            return CompletionResult.failure("Fracture treatment action data was invalid.");
        }

        FractureTreatmentValidation validation = validateFractureTreatment(player, sourceRef.slot(), sourceRef.index(), sourceRef.expectedLookupKey(), sourceRef.itemId());
        if (!validation.success()) {
            if (player != null) {
                player.sendSystemMessage(Component.literal(validation.message()));
            }
            return CompletionResult.failure(validation.message());
        }

        if (!FractureStatusService.clear(player)) {
            return CompletionResult.failure("No fracture to treat.");
        }

        RaidInventory inventory = RaidInventoryManager.get(player);
        if (validation.use().consumeItem()) {
            inventory.removeCountAt(sourceRef.slot(), sourceRef.index(), 1);
        } else {
            MedicalUse medicalUse = MEDICAL_USES.get(sourceRef.itemId());
            ItemStack stack = validation.item().toItemStack();
            MedicalCapacity capacity = getOrInitializeCapacity(stack, medicalUse);
            int remaining = capacity.current() - validation.use().capacityCost();
            if (remaining <= 0) {
                inventory.removeCountAt(sourceRef.slot(), sourceRef.index(), 1);
            } else {
                writeCapacity(stack, remaining, medicalUse.healCapacity());
                inventory.replaceItemAt(sourceRef.slot(), sourceRef.index(), validation.item().withStoredStack(stack));
            }
        }

        String message = "Treated Fracture.";
        player.sendSystemMessage(Component.literal(message));
        syncOptions(player);
        return CompletionResult.success(message);
    }

    public static Optional<MedicalCapacity> capacityInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        MedicalUse use = MEDICAL_USES.get(itemId);
        if (use == null) {
            clearMedicalCapacity(stack);
            return Optional.empty();
        }
        return Optional.of(getOrInitializeCapacity(stack, use));
    }

    public static Optional<Integer> useTimeTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        MedicalUse use = MEDICAL_USES.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return use == null ? Optional.empty() : Optional.of(use.durationTicks());
    }

    public static Optional<BleedTreatmentInfo> bleedTreatmentInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        BleedTreatmentUse use = BLEED_TREATMENTS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return use == null ? Optional.empty() : Optional.of(new BleedTreatmentInfo(use.treatsLabel(), use.durationTicks()));
    }

    public static Optional<FractureTreatmentInfo> fractureTreatmentInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        FractureTreatmentUse use = FRACTURE_TREATMENTS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return use == null ? Optional.empty() : Optional.of(new FractureTreatmentInfo(use.durationTicks(), use.capacityCost()));
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            SELECTED_ITEMS.remove(player.getUUID());
            MANUAL_SELECTIONS.remove(player.getUUID());
        }
    }

    private static String selectedString(ServerPlayer player) {
        ResourceLocation selected = player == null ? null : SELECTED_ITEMS.get(player.getUUID());
        return selected == null ? "" : selected.toString();
    }

    private static ResourceLocation reconcileSelection(ServerPlayer player, List<QuickUseStatePayload.Option> options) {
        if (player == null || options.isEmpty()) {
            if (player != null) {
                SELECTED_ITEMS.remove(player.getUUID());
            }
            return null;
        }

        ResourceLocation selected = SELECTED_ITEMS.get(player.getUUID());
        boolean manualSelection = MANUAL_SELECTIONS.contains(player.getUUID());
        BleedStatus bleedStatus = BleedStatusService.status(player);
        boolean fractured = FractureStatusService.fractured(player);
        if (selected != null
                && options.stream().anyMatch(option -> selected.toString().equals(option.itemId()))) {
            if (manualSelection || isContextuallySelectable(selected, bleedStatus, fractured)) {
                return selected;
            }
        }

        ResourceLocation fallback = options.stream()
                .map(option -> parseItemId(option.itemId()).orElse(null))
                .filter(id -> id != null && isContextuallySelectable(id, bleedStatus, fractured))
                .findFirst()
                .orElse(null);
        if (fallback == null) {
            SELECTED_ITEMS.remove(player.getUUID());
            MANUAL_SELECTIONS.remove(player.getUUID());
            return null;
        }
        SELECTED_ITEMS.put(player.getUUID(), fallback);
        MANUAL_SELECTIONS.remove(player.getUUID());
        return fallback;
    }

    private static List<QuickUseStatePayload.Option> options(ServerPlayer player) {
        if (player == null || !RaidManager.isInRaid(player)) {
            return List.of();
        }
        RaidInventory inventory = RaidInventoryManager.get(player);
        LinkedHashMap<ResourceLocation, QuickUseStatePayload.Option> options = new LinkedHashMap<>();
        collectOptions(options, inventory.backpack());
        collectOptions(options, inventory.vest());
        collectOptions(options, inventory.safeBox());
        BleedStatus bleedStatus = BleedStatusService.status(player);
        boolean fractured = FractureStatusService.fractured(player);
        return options.values().stream()
                .sorted(Comparator.comparingInt(option -> priority(ResourceLocation.parse(option.itemId()), bleedStatus, fractured)))
                .toList();
    }

    private static void collectOptions(LinkedHashMap<ResourceLocation, QuickUseStatePayload.Option> options, RaidStorageContainer storage) {
        for (RaidInventoryItem item : storage.items()) {
            ResourceLocation itemId = item.itemId();
            if (!isEligible(itemId)) {
                continue;
            }
            QuickUseStatePayload.Option previous = options.get(itemId);
            int count = item.count() + (previous == null ? 0 : previous.count());
            String displayName = previous == null ? item.displayName() : previous.displayName();
            options.put(itemId, new QuickUseStatePayload.Option(itemId.toString(), displayName, count));
        }
    }

    private static Optional<QuickUseSource> findFirst(ServerPlayer player, ResourceLocation itemId) {
        if (player == null || !RaidManager.isInRaid(player)) {
            return Optional.empty();
        }
        RaidInventory inventory = RaidInventoryManager.get(player);
        return findFirst(inventory.backpack(), RaidEquipmentSlot.BACKPACK, itemId)
                .or(() -> findFirst(inventory.vest(), RaidEquipmentSlot.VEST, itemId))
                .or(() -> findFirst(inventory.safeBox(), RaidEquipmentSlot.SAFE_BOX, itemId));
    }

    private static Optional<QuickUseSource> findFirst(RaidStorageContainer storage, RaidEquipmentSlot slot, ResourceLocation itemId) {
        List<RaidInventoryItem> items = storage.items();
        for (int i = 0; i < items.size(); i++) {
            RaidInventoryItem item = items.get(i);
            if (item.itemId().equals(itemId)) {
                return Optional.of(new QuickUseSource(slot, i, item));
            }
        }
        return Optional.empty();
    }

    private static boolean isEligible(ResourceLocation itemId) {
        return MEDICAL_USES.containsKey(itemId) || BLEED_TREATMENTS.containsKey(itemId) || FRACTURE_TREATMENTS.containsKey(itemId) || repairTargetFor(itemId) != null;
    }

    private static GridMoveResult startUse(ServerPlayer player, ResourceLocation selected, QuickUseSource source) {
        BleedTreatmentUse bleedTreatment = BLEED_TREATMENTS.get(selected);
        if (bleedTreatment != null && BleedStatusService.status(player).active()) {
            BleedTreatmentValidation validation = validateBleedTreatment(player, source.slot(), source.index(), source.item().lookupKey(), selected);
            if (!validation.success()) {
                return GridMoveResult.failure(validation.message());
            }
            TimedActionService.StartResult started = TimedActionService.start(
                    player,
                    bleedTreatment.type(),
                    bleedTreatment.durationTicks(),
                    bleedTreatment.label(),
                    true,
                    false,
                    Optional.of(sourceReference(source.slot(), source.index(), source.item().lookupKey(), selected)),
                    Optional.empty());
            if (!started.success()) {
                return GridMoveResult.failure(started.message());
            }
            String message = bleedTreatment.label() + " (" + ticksToSeconds(bleedTreatment.durationTicks()) + "s).";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.success(message);
        }

        FractureTreatmentUse fractureTreatment = FRACTURE_TREATMENTS.get(selected);
        if (fractureTreatment != null && FractureStatusService.fractured(player)) {
            FractureTreatmentValidation validation = validateFractureTreatment(player, source.slot(), source.index(), source.item().lookupKey(), selected);
            if (!validation.success()) {
                return GridMoveResult.failure(validation.message());
            }
            TimedActionService.StartResult started = TimedActionService.start(
                    player,
                    TimedActionType.TREAT_FRACTURE,
                    fractureTreatment.durationTicks(),
                    fractureTreatment.label(),
                    true,
                    false,
                    Optional.of(sourceReference(source.slot(), source.index(), source.item().lookupKey(), selected)),
                    Optional.empty());
            if (!started.success()) {
                return GridMoveResult.failure(started.message());
            }
            String message = fractureTreatment.label() + " (" + ticksToSeconds(fractureTreatment.durationTicks()) + "s).";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.success(message);
        }

        MedicalUse medicalUse = MEDICAL_USES.get(selected);
        if (medicalUse != null) {
            MedicalValidation validation = validateMedical(player, source.slot(), source.index(), source.item().lookupKey(), selected);
            if (!validation.success()) {
                return GridMoveResult.failure(validation.message());
            }
            TimedActionService.StartResult started = TimedActionService.start(
                    player,
                    medicalUse.type(),
                    medicalUse.durationTicks(),
                    medicalUse.label(),
                    true,
                    false,
                    Optional.of(sourceReference(source.slot(), source.index(), source.item().lookupKey(), selected)),
                    Optional.empty());
            if (!started.success()) {
                return GridMoveResult.failure(started.message());
            }
            String message = medicalUse.label() + " (" + ticksToSeconds(medicalUse.durationTicks()) + "s).";
            player.sendSystemMessage(Component.literal(message));
            return GridMoveResult.success(message);
        }

        if (fractureTreatment != null) {
            return GridMoveResult.failure("No fracture to treat.");
        }

        if (bleedTreatment != null) {
            return GridMoveResult.failure("No bleeding to treat.");
        }

        RaidEquipmentSlot target = repairTargetFor(selected);
        if (target == null) {
            return GridMoveResult.failure("Selected quick-use item not found.");
        }
        return InRaidRepairService.startFromDrag(player, source.slot(), source.index(), target);
    }

    private static MedicalValidation validateMedical(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, String expectedLookupKey, ResourceLocation expectedItemId) {
        if (player == null) {
            return MedicalValidation.failure("No player.");
        }
        if (!RaidManager.isInRaid(player)) {
            return MedicalValidation.failure("Quick-use is only available in raid.");
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            return MedicalValidation.failure("Already at full health.");
        }
        MedicalUse use = MEDICAL_USES.get(expectedItemId);
        if (use == null) {
            return MedicalValidation.failure("Selected quick-use item not found.");
        }
        RaidInventoryItem item = RaidInventoryManager.get(player).itemAt(source, sourceIndex);
        if (item == null || !expectedItemId.equals(item.itemId()) || !expectedLookupKey.equals(item.lookupKey())) {
            return MedicalValidation.failure("Selected quick-use item not found.");
        }
        if (item.count() > 1) {
            return MedicalValidation.failure("Medical kits must be unstacked before use.");
        }
        MedicalCapacity capacity = getOrInitializeCapacity(item.toItemStack(), use);
        if (capacity.current() <= 0) {
            return MedicalValidation.failure("Medical kit is empty.");
        }
        return MedicalValidation.success(item, use);
    }

    private static BleedTreatmentValidation validateBleedTreatment(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, String expectedLookupKey, ResourceLocation expectedItemId) {
        if (player == null) {
            return BleedTreatmentValidation.failure("No player.");
        }
        if (!RaidManager.isInRaid(player)) {
            return BleedTreatmentValidation.failure("Quick-use is only available in raid.");
        }
        BleedTreatmentUse use = BLEED_TREATMENTS.get(expectedItemId);
        if (use == null) {
            return BleedTreatmentValidation.failure("Selected quick-use item not found.");
        }
        RaidInventoryItem item = RaidInventoryManager.get(player).itemAt(source, sourceIndex);
        if (item == null || !expectedItemId.equals(item.itemId()) || !expectedLookupKey.equals(item.lookupKey())) {
            return BleedTreatmentValidation.failure("Selected quick-use item not found.");
        }
        BleedStatusService.TreatmentResult result = BleedStatusService.validateTreatment(player, use.strength());
        if (!result.success()) {
            return BleedTreatmentValidation.failure(result.message());
        }
        return BleedTreatmentValidation.success(item, use);
    }

    private static FractureTreatmentValidation validateFractureTreatment(ServerPlayer player, RaidEquipmentSlot source, int sourceIndex, String expectedLookupKey, ResourceLocation expectedItemId) {
        if (player == null) {
            return FractureTreatmentValidation.failure("No player.");
        }
        if (!RaidManager.isInRaid(player)) {
            return FractureTreatmentValidation.failure("Quick-use is only available in raid.");
        }
        FractureTreatmentUse use = FRACTURE_TREATMENTS.get(expectedItemId);
        if (use == null) {
            return FractureTreatmentValidation.failure("Selected quick-use item not found.");
        }
        RaidInventoryItem item = RaidInventoryManager.get(player).itemAt(source, sourceIndex);
        if (item == null || !expectedItemId.equals(item.itemId()) || !expectedLookupKey.equals(item.lookupKey())) {
            return FractureTreatmentValidation.failure("Selected quick-use item not found.");
        }
        if (!FractureStatusService.fractured(player)) {
            return FractureTreatmentValidation.failure("No fracture to treat.");
        }
        if (!use.consumeItem()) {
            MedicalUse medicalUse = MEDICAL_USES.get(expectedItemId);
            if (medicalUse == null) {
                return FractureTreatmentValidation.failure("Selected quick-use item not found.");
            }
            if (item.count() > 1) {
                return FractureTreatmentValidation.failure("Medical kits must be unstacked before use.");
            }
            MedicalCapacity capacity = getOrInitializeCapacity(item.toItemStack(), medicalUse);
            if (capacity.current() < use.capacityCost()) {
                return FractureTreatmentValidation.failure("Not enough medical capacity.");
            }
        }
        return FractureTreatmentValidation.success(item, use);
    }

    private static MedicalCapacity getOrInitializeCapacity(ItemStack stack, MedicalUse use) {
        MedicalCapacity data = readCapacity(stack).orElse(new MedicalCapacity(use.healCapacity(), use.healCapacity()));
        int max = use.healCapacity();
        int current = Math.max(0, Math.min(data.current(), max));
        MedicalCapacity reconciled = new MedicalCapacity(current, max);
        writeCapacity(stack, reconciled.current(), reconciled.max());
        return reconciled;
    }

    private static Optional<MedicalCapacity> readCapacity(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(MEDICAL_CAPACITY_KEY, 10)) {
            return Optional.empty();
        }
        CompoundTag tag = root.getCompound(MEDICAL_CAPACITY_KEY);
        int current = tag.contains(CURRENT_KEY, 3) ? tag.getInt(CURRENT_KEY) : 0;
        int max = tag.contains(MAX_KEY, 3) ? tag.getInt(MAX_KEY) : current;
        return Optional.of(new MedicalCapacity(current, max));
    }

    private static void writeCapacity(ItemStack stack, int current, int max) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt(CURRENT_KEY, Math.max(0, current));
            tag.putInt(MAX_KEY, Math.max(1, max));
            root.put(MEDICAL_CAPACITY_KEY, tag);
        });
    }

    private static void clearMedicalCapacity(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(MEDICAL_CAPACITY_KEY)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.remove(MEDICAL_CAPACITY_KEY));
    }

    private static RaidEquipmentSlot repairTargetFor(ResourceLocation itemId) {
        if (HELMET_REBUILD_KIT.equals(itemId)) {
            return RaidEquipmentSlot.HELMET;
        }
        if (ARMOR_REBUILD_KIT.equals(itemId)) {
            return RaidEquipmentSlot.ARMOR;
        }
        if (PACK_REBUILD_KIT.equals(itemId)) {
            return RaidEquipmentSlot.EQUIPPED_BACKPACK;
        }
        return null;
    }

    private static int priority(ResourceLocation itemId, BleedStatus bleedStatus, boolean fractured) {
        List<ResourceLocation> priority;
        if (bleedStatus == BleedStatus.HEAVY) {
            priority = HEAVY_BLEED_PRIORITY;
        } else if (bleedStatus == BleedStatus.LIGHT) {
            priority = LIGHT_BLEED_PRIORITY;
        } else if (fractured) {
            priority = FRACTURE_PRIORITY;
        } else {
            priority = DEFAULT_PRIORITY;
        }
        int index = priority.indexOf(itemId);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static boolean isContextuallySelectable(ResourceLocation itemId, BleedStatus bleedStatus, boolean fractured) {
        if (isBleedTreatment(itemId) && bleedStatus.active()) {
            return true;
        }
        if (FRACTURE_TREATMENTS.containsKey(itemId) && fractured) {
            return true;
        }
        return MEDICAL_USES.containsKey(itemId) || repairTargetFor(itemId) != null;
    }

    private static boolean isBleedTreatment(ResourceLocation itemId) {
        return BLEED_TREATMENTS.containsKey(itemId);
    }

    private static int ticksToSeconds(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0D));
    }

    private static String sourceReference(RaidEquipmentSlot source, int sourceIndex, String lookupKey, ResourceLocation itemId) {
        return source.name() + "|" + sourceIndex + "|" + lookupKey + "|" + itemId;
    }

    private static SourceRef parseSource(String value) {
        String[] parts = value.split("\\|", 4);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new SourceRef(RaidEquipmentSlot.valueOf(parts[0]), Integer.parseInt(parts[1]), parts[2], ResourceLocation.parse(parts[3]));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String formatHealth(float amount) {
        if (Math.abs(amount - Math.round(amount)) < 0.01F) {
            return Integer.toString(Math.round(amount));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", amount);
    }

    private static Optional<ResourceLocation> parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemId);
            return BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(id) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private record QuickUseSource(RaidEquipmentSlot slot, int index, RaidInventoryItem item) {
    }

    private record SourceRef(RaidEquipmentSlot slot, int index, String expectedLookupKey, ResourceLocation itemId) {
    }

    public record MedicalCapacity(int current, int max) {
    }

    public record BleedTreatmentInfo(String treats, int useTimeTicks) {
    }

    public record FractureTreatmentInfo(int useTimeTicks, int capacityCost) {
    }

    private record MedicalUse(TimedActionType type, int healCapacity, int durationTicks, String label) {
    }

    private record BleedTreatmentUse(TimedActionType type, TreatmentStrength strength, int durationTicks, String label, String treatsLabel) {
    }

    private record FractureTreatmentUse(int durationTicks, String label, int capacityCost, boolean consumeItem) {
    }

    public record CompletionResult(boolean success, String message) {
        private static CompletionResult success(String message) {
            return new CompletionResult(true, message);
        }

        private static CompletionResult failure(String message) {
            return new CompletionResult(false, message);
        }
    }

    private record MedicalValidation(boolean success, String message, RaidInventoryItem item, MedicalUse use) {
        private static MedicalValidation success(RaidInventoryItem item, MedicalUse use) {
            return new MedicalValidation(true, "", item, use);
        }

        private static MedicalValidation failure(String message) {
            return new MedicalValidation(false, message, null, null);
        }
    }

    private record BleedTreatmentValidation(boolean success, String message, RaidInventoryItem item, BleedTreatmentUse use) {
        private static BleedTreatmentValidation success(RaidInventoryItem item, BleedTreatmentUse use) {
            return new BleedTreatmentValidation(true, "", item, use);
        }

        private static BleedTreatmentValidation failure(String message) {
            return new BleedTreatmentValidation(false, message, null, null);
        }
    }

    private record FractureTreatmentValidation(boolean success, String message, RaidInventoryItem item, FractureTreatmentUse use) {
        private static FractureTreatmentValidation success(RaidInventoryItem item, FractureTreatmentUse use) {
            return new FractureTreatmentValidation(true, "", item, use);
        }

        private static FractureTreatmentValidation failure(String message) {
            return new FractureTreatmentValidation(false, message, null, null);
        }
    }
}
