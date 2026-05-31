package com.chaseschwartz.extractcraft.itemvalues;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentity;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentityResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemValueRegistry implements PreparableReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DATA_FOLDER = "extractcraft/item_values";
    private static final Map<String, ItemValueEntry> VALUES = new HashMap<>();

    public static Optional<ItemValueEntry> get(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        ItemIdentity identity = ItemIdentityResolver.resolve(stack);
        return get(identity.normalizedKey()).or(() -> get(identity.baseItemId()));
    }

    public static Optional<ItemValueEntry> get(ResourceLocation itemId) {
        return get(itemId.toString());
    }

    public static Optional<ItemValueEntry> get(String lookupKey) {
        return Optional.ofNullable(VALUES.get(lookupKey));
    }

    public static String lookupKeyUsed(ItemStack stack) {
        ItemIdentity identity = ItemIdentityResolver.resolve(stack);
        if (VALUES.containsKey(identity.normalizedKey())) {
            return identity.normalizedKey();
        }
        if (VALUES.containsKey(identity.baseItemId().toString())) {
            return identity.baseItemId().toString();
        }
        return "none";
    }

    public static int loadedCount() {
        return VALUES.size();
    }

    public static List<ItemValueEntry> entries() {
        return List.copyOf(VALUES.values());
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
            ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> loadValues(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(values -> {
                    VALUES.clear();
                    VALUES.putAll(values);
                    ExtractCraft.LOGGER.info("Loaded {} ExtractCraft item value entries", VALUES.size());
                }, gameExecutor);
    }

    private static Map<String, ItemValueEntry> loadValues(ResourceManager resourceManager) {
        Map<String, ItemValueEntry> loaded = new HashMap<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DATA_FOLDER, path -> path.getPath().endsWith(".json"));
        resources.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> loadFile(entry.getKey(), entry.getValue(), loaded));
        return loaded;
    }

    private static void loadFile(ResourceLocation fileId, Resource resource, Map<String, ItemValueEntry> loaded) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("values") || !root.get("values").isJsonArray()) {
                ExtractCraft.LOGGER.warn("Skipping item value file {} because it has no values array", fileId);
                return;
            }

            for (JsonElement element : root.getAsJsonArray("values")) {
                parseEntry(fileId, element).ifPresent(parsed -> loaded.put(parsed.lookupKey(), parsed.value()));
            }
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to load item value file {}", fileId, exception);
        }
    }

    private static Optional<ParsedValue> parseEntry(ResourceLocation fileId, JsonElement element) {
        if (!element.isJsonObject()) {
            ExtractCraft.LOGGER.warn("Skipping non-object item value entry in {}", fileId);
            return Optional.empty();
        }

        JsonObject object = element.getAsJsonObject();
        try {
            String lookupKey = requiredString(object, "item");
            ResourceLocation itemId = baseItemId(lookupKey);
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                ExtractCraft.LOGGER.info("Skipping item value for missing optional item {}", itemId);
                return Optional.empty();
            }

            ItemCategory category = ItemCategory.fromJson(optionalString(object, "category").orElse("junk"));
            ItemRarity rarity = ItemRarity.fromJson(optionalString(object, "rarity").orElse("common"));
            int value = Math.max(0, optionalInt(object, "value").orElse(0));
            boolean sellable = optionalBoolean(object, "sellable").orElse(true);
            boolean questItem = optionalBoolean(object, "questItem").orElse(false);
            List<String> notes = optionalStringList(object, "notes");
            Optional<String> traderType = optionalString(object, "traderType");
            Optional<Integer> lootTier = optionalInt(object, "lootTier");

            return Optional.of(new ParsedValue(lookupKey, new ItemValueEntry(lookupKey, itemId, category, rarity, value, sellable, questItem, notes, traderType, lootTier)));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Skipping invalid item value entry in {}: {}", fileId, exception.getMessage());
            return Optional.empty();
        }
    }

    private static ResourceLocation baseItemId(String lookupKey) {
        int separator = lookupKey.indexOf('#');
        return ResourceLocation.parse(separator >= 0 ? lookupKey.substring(0, separator) : lookupKey);
    }

    private static String requiredString(JsonObject object, String name) {
        return optionalString(object, name).orElseThrow(() -> new IllegalArgumentException("Missing required field '" + name + "'"));
    }

    private static Optional<String> optionalString(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return Optional.empty();
        }

        return Optional.of(object.get(name).getAsString());
    }

    private static Optional<Integer> optionalInt(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return Optional.empty();
        }

        return Optional.of(object.get(name).getAsInt());
    }

    private static Optional<Boolean> optionalBoolean(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return Optional.empty();
        }

        return Optional.of(object.get(name).getAsBoolean());
    }

    private static List<String> optionalStringList(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        JsonElement element = object.get(name);
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement value : array) {
                values.add(value.getAsString());
            }
        } else {
            values.add(element.getAsString());
        }
        return values;
    }

    private record ParsedValue(String lookupKey, ItemValueEntry value) {
    }
}
