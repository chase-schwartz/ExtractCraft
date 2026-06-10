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

    private static final Map<UUID, ResourceLocation> SELECTED_ITEMS = new java.util.HashMap<>();
    private static final List<ResourceLocation> PRIORITY = List.of(
            TRAUMA_RESPONSE_CASE,
            FIELD_MED_KIT,
            COMBAT_STIM_SYRINGE,
            ARMOR_REBUILD_KIT,
            HELMET_REBUILD_KIT,
            PACK_REBUILD_KIT);
    private static final Map<ResourceLocation, MedicalUse> MEDICAL_USES = Map.of(
            COMBAT_STIM_SYRINGE, new MedicalUse(TimedActionType.USE_MED, 8, 40, "Using Combat Stim..."),
            FIELD_MED_KIT, new MedicalUse(TimedActionType.USE_MED, 20, 60, "Using Field Med Kit..."),
            TRAUMA_RESPONSE_CASE, new MedicalUse(TimedActionType.USE_MED, 45, 80, "Using Trauma Response Case..."));

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

    public static void clear(ServerPlayer player) {
        if (player != null) {
            SELECTED_ITEMS.remove(player.getUUID());
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
        if (selected != null && options.stream().anyMatch(option -> selected.toString().equals(option.itemId()))) {
            return selected;
        }

        ResourceLocation fallback = parseItemId(options.get(0).itemId()).orElse(null);
        if (fallback == null) {
            SELECTED_ITEMS.remove(player.getUUID());
            return null;
        }
        SELECTED_ITEMS.put(player.getUUID(), fallback);
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
        return options.values().stream()
                .sorted(Comparator.comparingInt(option -> priority(ResourceLocation.parse(option.itemId()))))
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
        return MEDICAL_USES.containsKey(itemId) || repairTargetFor(itemId) != null;
    }

    private static GridMoveResult startUse(ServerPlayer player, ResourceLocation selected, QuickUseSource source) {
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

    private static int priority(ResourceLocation itemId) {
        int index = PRIORITY.indexOf(itemId);
        return index < 0 ? Integer.MAX_VALUE : index;
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

    private record MedicalUse(TimedActionType type, int healCapacity, int durationTicks, String label) {
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
}
