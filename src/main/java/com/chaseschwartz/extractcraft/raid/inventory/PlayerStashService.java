package com.chaseschwartz.extractcraft.raid.inventory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

public class PlayerStashService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;
    private static final int MAX_DETAIL_LINES = 18;
    private static final List<StashLevel> STASH_LEVELS = List.of(
            new StashLevel(1, 120, 0),
            new StashLevel(2, 180, 10_000),
            new StashLevel(3, 240, 25_000));

    private PlayerStashService() {
    }

    public static PlayerStashData load(ServerPlayer player) {
        Path path = path(player);
        if (!Files.exists(path)) {
            return PlayerStashData.createDefault();
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return PlayerStashData.createDefault();
            }

            int stashLevel = getInt(root, "stashLevel", 1);
            int credits = getInt(root, "credits", 0);
            PlayerStashData data = new PlayerStashData(credits, stashLevel);
            readStorage(root.getAsJsonObject("stash"), data.stash(), player);
            readInventory(root.getAsJsonObject("baseInventory"), data.baseInventory(), player);
            return data;
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Unable to load ExtractCraft stash for {} from {}; using empty stash",
                    player.getGameProfile().getName(),
                    path,
                    exception);
            return PlayerStashData.createDefault();
        }
    }

    public static void save(ServerPlayer player, PlayerStashData data) {
        Path path = path(player);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(data, player), writer);
            }
        } catch (IOException exception) {
            ExtractCraft.LOGGER.warn("Unable to save ExtractCraft stash for {} to {}",
                    player.getGameProfile().getName(),
                    path,
                    exception);
        }
    }

    public static StashTransferResult addToStash(ServerPlayer player, List<RaidInventoryItem> items) {
        PlayerStashData data = load(player);
        StashTransferResult result = addItems(data.stash(), items);
        save(player, data);
        return result;
    }

    public static StashTransferResult addToBaseInventory(ServerPlayer player, RaidResultService.PendingRaidResult result) {
        PlayerStashData data = load(player);
        RaidInventory candidate = copyInventory(data.baseInventory());
        StashTransferResult transfer = new StashTransferResult();
        addWeaponToBase(candidate, result.primaryWeapon(), RaidEquipmentSlot.PRIMARY_WEAPON, transfer);
        addWeaponToBase(candidate, result.secondaryWeapon(), RaidEquipmentSlot.SECONDARY_WEAPON, transfer);
        transfer.merge(addItems(candidate.backpack(), result.backpackItems()));
        transfer.merge(addItems(candidate.vest(), result.vestItems()));
        transfer.merge(addItems(candidate.safeBox(), result.safeBoxItems()));
        if (!transfer.movedAll()) {
            transfer.resetMoved();
            return transfer;
        }

        replaceInventoryContents(data.baseInventory(), candidate);
        save(player, data);
        return transfer;
    }

    public static void addCredits(ServerPlayer player, int amount) {
        PlayerStashData data = load(player);
        data.setCredits(Math.max(0, data.credits() + amount));
        save(player, data);
    }

    public static boolean upgrade(ServerPlayer player) {
        PlayerStashData data = load(player);
        StashLevel next = level(data.stashLevel() + 1);
        if (next == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Stash is already at max level " + data.stashLevel() + "."));
            return false;
        }
        if (data.credits() < next.cost()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Stash level " + next.level() + " costs " + next.cost() + " credits. Current credits: " + data.credits() + "."));
            return false;
        }

        data.setCredits(data.credits() - next.cost());
        data.setStashLevel(next.level());
        data.rebuildStash();
        save(player, data);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Upgraded stash to level " + next.level() + " (" + next.capacity() + " slots)."));
        return true;
    }

    public static void clear(ServerPlayer player) {
        save(player, PlayerStashData.createDefault());
    }

    public static FillTestResult fillTest(ServerPlayer player, int requestedStacks) {
        PlayerStashData data = load(player);
        int targetStacks = requestedStacks <= 0 ? data.stash().capacity() : requestedStacks;
        int addedStacks = 0;
        int skippedStacks = 0;
        int itemCursor = 0;
        int maxAttempts = Math.max(TEST_FILL_ITEMS.size(), targetStacks * TEST_FILL_ITEMS.size());

        while (addedStacks < targetStacks && itemCursor < maxAttempts && data.stash().usedCapacity() < data.stash().capacity()) {
            ResourceLocation itemId = TEST_FILL_ITEMS.get(itemCursor % TEST_FILL_ITEMS.size());
            itemCursor++;

            ItemStack stack = testStack(itemId);
            RaidInventoryItem item = RaidInventoryManager.stackAsItem(player, stack).orElse(null);
            if (item == null) {
                skippedStacks++;
                continue;
            }

            int moved = data.stash().addPartial(item);
            if (moved <= 0) {
                break;
            }
            addedStacks++;
        }

        save(player, data);
        return new FillTestResult(addedStacks, skippedStacks, data.stash().usedCapacity(), data.stash().capacity());
    }

    public static List<String> statusLines(ServerPlayer player, String sortMode) {
        PlayerStashData data = load(player);
        List<RaidInventoryItem> stashItems = new ArrayList<>(data.stash().items());
        stashItems.sort(sortComparator(sortMode));
        List<String> lines = new ArrayList<>();
        lines.add(String.format("Stash: level %d, %d/%d slots, %d stacks, %d credits balance.",
                data.stashLevel(),
                data.stash().usedCapacity(),
                data.stash().capacity(),
                data.stash().itemCount(),
                data.credits()));
        lines.add(String.format("Base inventory: %.2f weight, %d credits value, Primary=%s, Secondary=%s.",
                data.baseInventory().totalWeight(),
                data.baseInventory().totalValue(),
                itemName(data.baseInventory().primaryWeapon()),
                itemName(data.baseInventory().secondaryWeapon())));

        if (stashItems.isEmpty()) {
            lines.add("Stash contents: empty.");
            return lines;
        }

        lines.add("Stash contents (" + normalizedSort(sortMode) + "):");
        for (int index = 0; index < Math.min(MAX_DETAIL_LINES, stashItems.size()); index++) {
            RaidInventoryItem item = stashItems.get(index);
            lines.add("  " + formatItem(item));
        }
        if (stashItems.size() > MAX_DETAIL_LINES) {
            lines.add("  +" + (stashItems.size() - MAX_DETAIL_LINES) + " more stacks.");
        }
        return lines;
    }

    public static void sort(ServerPlayer player, String sortMode) {
        for (String line : statusLines(player, sortMode)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
        }
    }

    private static StashTransferResult addItems(RaidStorageContainer target, List<RaidInventoryItem> items) {
        StashTransferResult result = new StashTransferResult();
        RaidStorageContainer candidate = copyStorage(target);
        for (RaidInventoryItem item : items) {
            RaidInventoryItem copy = copyItem(item);
            int moved = candidate.addPartial(copy);
            result.movedStacks += moved > 0 ? 1 : 0;
            result.movedItems += moved;
            if (moved > 0) {
                RaidInventoryItem movedPart = copy.withCount(moved);
                result.movedValue += movedPart.totalValue();
                result.movedWeight += movedPart.totalWeight();
            }
            if (moved < copy.count()) {
                result.failedStacks++;
                result.failedItems += copy.count() - moved;
                result.lastFailure = copy.displayName() + " x" + (copy.count() - moved) + " did not fit.";
            }
        }
        if (result.failedItems > 0) {
            result.movedStacks = 0;
            result.movedItems = 0;
            result.movedValue = 0;
            result.movedWeight = 0.0D;
            return result;
        }

        target.clear();
        for (RaidInventoryItem item : candidate.items()) {
            target.addPartial(item);
        }
        return result;
    }

    private static RaidStorageContainer copyStorage(RaidStorageContainer source) {
        RaidStorageContainer copy = new RaidStorageContainer(source.id(), source.name(), source.capacity(), source.maxWeight(), source.gridWidth(), source.gridHeight());
        for (RaidInventoryItem item : source.items()) {
            copy.addPartial(copyItem(item));
        }
        return copy;
    }

    static RaidInventory copyInventory(RaidInventory source) {
        RaidInventory copy = new RaidInventory(source.loadout());
        replaceInventoryContents(copy, source);
        return copy;
    }

    static void replaceInventoryContents(RaidInventory target, RaidInventory source) {
        target.clear();
        target.setWeaponSlot(RaidEquipmentSlot.PRIMARY_WEAPON, source.primaryWeapon() == null ? null : copyItem(source.primaryWeapon()));
        target.setWeaponSlot(RaidEquipmentSlot.SECONDARY_WEAPON, source.secondaryWeapon() == null ? null : copyItem(source.secondaryWeapon()));
        for (RaidInventoryItem item : source.backpack().items()) {
            target.backpack().addPartial(copyItem(item));
        }
        for (RaidInventoryItem item : source.vest().items()) {
            target.vest().addPartial(copyItem(item));
        }
        for (RaidInventoryItem item : source.safeBox().items()) {
            target.safeBox().addPartial(copyItem(item));
        }
    }

    private static void addWeaponToBase(RaidInventory baseInventory, RaidInventoryItem weapon, RaidEquipmentSlot slot, StashTransferResult result) {
        if (weapon == null) {
            return;
        }

        RaidInventoryItem copy = copyItem(weapon);
        if (baseInventory.itemAt(slot, 0) == null) {
            baseInventory.setWeaponSlot(slot, copy);
            result.movedStacks++;
            result.movedItems += copy.count();
            result.movedValue += copy.totalValue();
            result.movedWeight += copy.totalWeight();
            return;
        }

        result.merge(addItems(baseInventory.backpack(), List.of(copy)));
    }

    private static void readInventory(JsonObject object, RaidInventory inventory, ServerPlayer player) {
        if (object == null) {
            return;
        }

        inventory.setWeaponSlot(RaidEquipmentSlot.PRIMARY_WEAPON, readItem(object.getAsJsonObject("primaryWeapon"), player));
        inventory.setWeaponSlot(RaidEquipmentSlot.SECONDARY_WEAPON, readItem(object.getAsJsonObject("secondaryWeapon"), player));
        readStorage(object.getAsJsonObject("backpack"), inventory.backpack(), player);
        readStorage(object.getAsJsonObject("vest"), inventory.vest(), player);
        readStorage(object.getAsJsonObject("safeBox"), inventory.safeBox(), player);
    }

    private static void readStorage(JsonObject object, RaidStorageContainer container, ServerPlayer player) {
        if (object == null || !object.has("items") || !object.get("items").isJsonArray()) {
            return;
        }

        for (JsonElement element : object.getAsJsonArray("items")) {
            if (!element.isJsonObject()) {
                continue;
            }
            RaidInventoryItem item = readItem(element.getAsJsonObject(), player);
            if (item != null) {
                container.addPartial(item);
            }
        }
    }

    private static RaidInventoryItem readItem(JsonObject object, ServerPlayer player) {
        if (object == null) {
            return null;
        }

        try {
            if (!object.has("itemId") && !object.has("stackTag")) {
                return null;
            }
            ResourceLocation itemId = ResourceLocation.parse(getString(object, "itemId", "minecraft:air"));
            String lookupKey = getString(object, "lookupKey", itemId.toString());
            String displayName = getString(object, "displayName", lookupKey);
            String category = getString(object, "category", "unknown");
            int count = getInt(object, "count", 1);
            int slotCost = getInt(object, "slotCost", 1);
            double weight = getDouble(object, "weight", 0.0D);
            int value = getInt(object, "value", 0);
            ItemCarryProfile profile = ItemCarryProfileRegistry.get(lookupKey).orElse(null);
            int gridWidth = getInt(object, "gridWidth", profile == null ? 1 : profile.gridWidth().orElse(fallbackGridWidth(profile.category())));
            int gridHeight = getInt(object, "gridHeight", profile == null ? 1 : profile.gridHeight().orElse(fallbackGridHeight(profile.category())));
            int gridX = getInt(object, "gridX", -1);
            int gridY = getInt(object, "gridY", -1);
            boolean rotated = getBoolean(object, "rotated", false);
            boolean canRotate = getBoolean(object, "canRotate", profile == null || profile.canRotate());
            ItemStack stack = ItemStack.EMPTY;
            if (object.has("stackTag")) {
                CompoundTag tag = TagParser.parseTag(object.get("stackTag").getAsString());
                stack = ItemStack.parseOptional(player.registryAccess(), tag);
                if (!stack.isEmpty()) {
                    stack.setCount(count);
                }
            }
            return new RaidInventoryItem(itemId, lookupKey, displayName, category, count, slotCost, weight, value, gridWidth, gridHeight, gridX, gridY, rotated, canRotate, stack);
        } catch (CommandSyntaxException | RuntimeException exception) {
            ExtractCraft.LOGGER.warn("Unable to parse persisted ExtractCraft stash item {}", object, exception);
            return null;
        }
    }

    private static JsonObject toJson(PlayerStashData data, ServerPlayer player) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("credits", data.credits());
        root.addProperty("stashLevel", data.stashLevel());
        root.add("stash", storageToJson(data.stash(), player));
        root.add("baseInventory", inventoryToJson(data.baseInventory(), player));
        return root;
    }

    private static JsonObject inventoryToJson(RaidInventory inventory, ServerPlayer player) {
        JsonObject object = new JsonObject();
        object.add("primaryWeapon", itemToJson(inventory.primaryWeapon(), player));
        object.add("secondaryWeapon", itemToJson(inventory.secondaryWeapon(), player));
        object.add("backpack", storageToJson(inventory.backpack(), player));
        object.add("vest", storageToJson(inventory.vest(), player));
        object.add("safeBox", storageToJson(inventory.safeBox(), player));
        return object;
    }

    private static JsonObject storageToJson(RaidStorageContainer container, ServerPlayer player) {
        JsonObject object = new JsonObject();
        object.addProperty("id", container.id());
        object.addProperty("name", container.name());
        object.addProperty("capacity", container.capacity());
        JsonArray items = new JsonArray();
        for (RaidInventoryItem item : container.items()) {
            items.add(itemToJson(item, player));
        }
        object.add("items", items);
        return object;
    }

    private static JsonObject itemToJson(RaidInventoryItem item, ServerPlayer player) {
        JsonObject object = new JsonObject();
        if (item == null) {
            return object;
        }

        object.addProperty("itemId", item.itemId().toString());
        object.addProperty("lookupKey", item.lookupKey());
        object.addProperty("displayName", item.displayName());
        object.addProperty("category", item.category());
        object.addProperty("count", item.count());
        object.addProperty("slotCost", item.slotCost());
        object.addProperty("weight", item.totalWeight());
        object.addProperty("value", item.totalValue());
        object.addProperty("gridWidth", item.gridWidth());
        object.addProperty("gridHeight", item.gridHeight());
        object.addProperty("gridX", item.gridX());
        object.addProperty("gridY", item.gridY());
        object.addProperty("rotated", item.rotated());
        object.addProperty("canRotate", item.canRotate());
        ItemStack stack = item.toItemStack();
        if (!stack.isEmpty()) {
            Tag tag = stack.saveOptional(player.registryAccess());
            object.addProperty("stackTag", tag.toString());
        }
        return object;
    }

    private static Comparator<RaidInventoryItem> sortComparator(String sortMode) {
        return switch (normalizedSort(sortMode)) {
            case "value" -> Comparator.comparingInt(RaidInventoryItem::totalValue).reversed().thenComparing(RaidInventoryItem::displayName);
            case "weight" -> Comparator.comparingDouble(RaidInventoryItem::totalWeight).reversed().thenComparing(RaidInventoryItem::displayName);
            case "category" -> Comparator.comparing(RaidInventoryItem::category).thenComparing(RaidInventoryItem::displayName);
            default -> Comparator.comparing(RaidInventoryItem::displayName);
        };
    }

    private static String normalizedSort(String sortMode) {
        if (sortMode == null) {
            return "name";
        }
        String lower = sortMode.toLowerCase(Locale.ROOT);
        return lower.equals("value") || lower.equals("weight") || lower.equals("category") ? lower : "name";
    }

    static RaidInventoryItem copyItem(RaidInventoryItem item) {
        return new RaidInventoryItem(
                item.itemId(),
                item.lookupKey(),
                item.displayName(),
                item.category(),
                item.count(),
                item.slotCost(),
                item.totalWeight(),
                item.totalValue(),
                item.gridWidth(),
                item.gridHeight(),
                item.gridX(),
                item.gridY(),
                item.rotated(),
                item.canRotate(),
                item.toItemStack());
    }

    private static String formatItem(RaidInventoryItem item) {
        return item.displayName() + " x" + item.count()
                + " | " + item.category()
                + " | " + item.totalValue() + " cr"
                + " | " + String.format("%.2f", item.totalWeight()) + " wt"
                + " | " + item.lookupKey();
    }

    private static String itemName(RaidInventoryItem item) {
        return item == null ? "empty" : item.displayName() + " [" + item.lookupKey() + "]";
    }

    private static final List<ResourceLocation> TEST_FILL_ITEMS = List.of(
            ResourceLocation.withDefaultNamespace("paper"),
            ResourceLocation.withDefaultNamespace("string"),
            ResourceLocation.withDefaultNamespace("bone"),
            ResourceLocation.withDefaultNamespace("glass_bottle"),
            ResourceLocation.withDefaultNamespace("apple"),
            ResourceLocation.withDefaultNamespace("bread"),
            ResourceLocation.withDefaultNamespace("iron_ingot"));

    private static ItemStack testStack(ResourceLocation itemId) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        stack.setCount(Math.max(1, stack.getMaxStackSize()));
        return stack;
    }

    private static StashLevel level(int level) {
        return STASH_LEVELS.stream().filter(entry -> entry.level() == level).findFirst().orElse(null);
    }

    private static int capacityForLevel(int level) {
        StashLevel stashLevel = level(level);
        return stashLevel == null ? STASH_LEVELS.getFirst().capacity() : stashLevel.capacity();
    }

    private static Path path(ServerPlayer player) {
        return FMLPaths.GAMEDIR.get().resolve("extractcraft").resolve("player_stashes").resolve(player.getUUID() + ".json");
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    public static class PlayerStashData {
        private int credits;
        private int stashLevel;
        private RaidStorageContainer stash;
        private final RaidInventory baseInventory;

        private PlayerStashData(int credits, int stashLevel) {
            this.credits = Math.max(0, credits);
            this.stashLevel = Math.max(1, stashLevel);
            this.stash = stashContainer(this.stashLevel);
            this.baseInventory = new RaidInventory(baseLoadout());
        }

        private static PlayerStashData createDefault() {
            return new PlayerStashData(0, 1);
        }

        private static RaidLoadout baseLoadout() {
            return new RaidLoadout(
                    RaidInventoryDefinitions.backpack("large_backpack").orElse(RaidInventoryDefinitions.defaultLoadout().backpack()),
                    RaidInventoryDefinitions.defaultLoadout().vest(),
                    RaidInventoryDefinitions.defaultLoadout().safeBox());
        }

        private void rebuildStash() {
            RaidStorageContainer upgraded = stashContainer(stashLevel);
            for (RaidInventoryItem item : stash.items()) {
                upgraded.addPartial(item);
            }
            stash = upgraded;
        }

        public int credits() {
            return credits;
        }

        private void setCredits(int credits) {
            this.credits = Math.max(0, credits);
        }

        public int stashLevel() {
            return stashLevel;
        }

        private void setStashLevel(int stashLevel) {
            this.stashLevel = Math.max(1, stashLevel);
        }

        public RaidStorageContainer stash() {
            return stash;
        }

        public RaidInventory baseInventory() {
            return baseInventory;
        }
    }

    private static RaidStorageContainer stashContainer(int stashLevel) {
        int capacity = capacityForLevel(stashLevel);
        int columns = stashColumnsForCapacity(capacity);
        int rows = Math.max(1, (int) Math.ceil(capacity / (double) columns));
        return new RaidStorageContainer("stash", "Persistent Stash", capacity, 1_000_000.0D, columns, rows);
    }

    private static int stashColumnsForCapacity(int capacity) {
        if (capacity >= 220) {
            return 16;
        }
        if (capacity >= 170) {
            return 15;
        }
        return 10;
    }

    private static int fallbackGridWidth(com.chaseschwartz.extractcraft.itemvalues.ItemCategory category) {
        return switch (category) {
            case GUNS -> 2;
            case ARMOR -> 3;
            case TOOLS, WEAPON_PARTS, MEDICAL -> 2;
            default -> 1;
        };
    }

    private static int fallbackGridHeight(com.chaseschwartz.extractcraft.itemvalues.ItemCategory category) {
        return switch (category) {
            case GUNS -> 5;
            case ARMOR -> 3;
            case TOOLS, WEAPON_PARTS, MEDICAL, MAGAZINES -> 2;
            default -> 1;
        };
    }

    public static class StashTransferResult {
        private int movedStacks;
        private int movedItems;
        private int movedValue;
        private double movedWeight;
        private int failedStacks;
        private int failedItems;
        private String lastFailure = "";

        private void merge(StashTransferResult other) {
            movedStacks += other.movedStacks;
            movedItems += other.movedItems;
            movedValue += other.movedValue;
            movedWeight += other.movedWeight;
            failedStacks += other.failedStacks;
            failedItems += other.failedItems;
            if (!other.lastFailure.isBlank()) {
                lastFailure = other.lastFailure;
            }
        }

        private void resetMoved() {
            movedStacks = 0;
            movedItems = 0;
            movedValue = 0;
            movedWeight = 0.0D;
        }

        public String summary(String target) {
            String message = String.format("Moved %d stacks/%d items to %s: %d credits, %.2f weight.",
                    movedStacks,
                    movedItems,
                    target,
                    movedValue,
                    movedWeight);
            if (failedItems > 0) {
                message += " Could not fit " + failedStacks + " stacks/" + failedItems + " items. " + lastFailure;
            }
            return message;
        }

        public boolean movedAll() {
            return failedItems == 0;
        }
    }

    public record FillTestResult(int addedStacks, int skippedStacks, int usedCapacity, int maxCapacity) {
    }

    private record StashLevel(int level, int capacity, int cost) {
    }
}
