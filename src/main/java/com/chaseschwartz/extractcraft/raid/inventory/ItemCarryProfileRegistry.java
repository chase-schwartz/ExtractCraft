package com.chaseschwartz.extractcraft.raid.inventory;

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
import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
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
import net.minecraft.world.item.ItemStack;

public class ItemCarryProfileRegistry implements PreparableReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DATA_FOLDER = "extractcraft/carry_profiles";
    private static final Map<String, ItemCarryProfile> PROFILES = new HashMap<>();

    public static Optional<ItemCarryProfile> get(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ItemIdentity identity = ItemIdentityResolver.resolve(stack);
        return get(identity.normalizedKey()).or(() -> get(identity.baseItemId()));
    }

    public static Optional<ItemCarryProfile> get(ResourceLocation itemId) {
        return get(itemId.toString());
    }

    public static Optional<ItemCarryProfile> get(String lookupKey) {
        ItemCarryProfile profile = PROFILES.get(lookupKey);
        if (profile != null) {
            return Optional.of(profile);
        }

        ResourceLocation baseItemId = baseItemId(lookupKey);
        return ItemValueRegistry.get(lookupKey).or(() -> ItemValueRegistry.get(baseItemId)).map(value -> new ItemCarryProfile(
                baseItemId,
                value.category(),
                fallbackWeight(value.category()),
                fallbackSlotCost(value.category()),
                Optional.empty(),
                Optional.empty(),
                true,
                value.category() != ItemCategory.GUNS && value.category() != ItemCategory.ARMOR,
                value.category() == ItemCategory.AMMO || value.category() == ItemCategory.MAGAZINES || value.category() == ItemCategory.MEDICAL,
                List.of("fallback from item value registry")));
    }

    public static String lookupKeyUsed(ItemStack stack) {
        ItemIdentity identity = ItemIdentityResolver.resolve(stack);
        if (PROFILES.containsKey(identity.normalizedKey())) {
            return identity.normalizedKey();
        }
        if (PROFILES.containsKey(identity.baseItemId().toString())) {
            return identity.baseItemId().toString();
        }
        if (ItemValueRegistry.get(identity.normalizedKey()).isPresent()) {
            return identity.normalizedKey() + " (fallback from value)";
        }
        if (ItemValueRegistry.get(identity.baseItemId()).isPresent()) {
            return identity.baseItemId() + " (fallback from value)";
        }
        return "none";
    }

    public static int loadedCount() {
        return PROFILES.size();
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
            ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> loadProfiles(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(profiles -> {
                    PROFILES.clear();
                    PROFILES.putAll(profiles);
                    ExtractCraft.LOGGER.info("Loaded {} ExtractCraft item carry profiles", PROFILES.size());
                }, gameExecutor);
    }

    private static Map<String, ItemCarryProfile> loadProfiles(ResourceManager resourceManager) {
        Map<String, ItemCarryProfile> loaded = new HashMap<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DATA_FOLDER, path -> path.getPath().endsWith(".json"));
        resources.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> loadFile(entry.getKey(), entry.getValue(), loaded));
        return loaded;
    }

    private static void loadFile(ResourceLocation fileId, Resource resource, Map<String, ItemCarryProfile> loaded) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("profiles") || !root.get("profiles").isJsonArray()) {
                ExtractCraft.LOGGER.warn("Skipping carry profile file {} because it has no profiles array", fileId);
                return;
            }

            for (JsonElement element : root.getAsJsonArray("profiles")) {
                parseProfile(fileId, element).ifPresent(parsed -> loaded.put(parsed.lookupKey(), parsed.profile()));
            }
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to load carry profile file {}", fileId, exception);
        }
    }

    private static Optional<ParsedProfile> parseProfile(ResourceLocation fileId, JsonElement element) {
        if (!element.isJsonObject()) {
            ExtractCraft.LOGGER.warn("Skipping non-object carry profile entry in {}", fileId);
            return Optional.empty();
        }

        JsonObject object = element.getAsJsonObject();
        try {
            String lookupKey = requiredString(object, "item");
            ResourceLocation itemId = baseItemId(lookupKey);
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                ExtractCraft.LOGGER.info("Skipping carry profile for missing optional item {}", itemId);
                return Optional.empty();
            }

            ItemCategory category = ItemCategory.fromJson(optionalString(object, "category")
                    .or(() -> ItemValueRegistry.get(itemId).map(value -> value.category().name().toLowerCase()))
                    .orElse("junk"));
            double weight = Math.max(0.0D, optionalDouble(object, "weight").orElse(fallbackWeight(category)));
            int slotCost = Math.max(1, optionalInt(object, "slotCost").orElse(fallbackSlotCost(category)));
            Optional<Integer> gridWidth = optionalInt(object, "gridWidth");
            Optional<Integer> gridHeight = optionalInt(object, "gridHeight");
            boolean canRotate = optionalBoolean(object, "canRotate").orElse(true);
            boolean allowInSafeBox = optionalBoolean(object, "allowInSafeBox").orElse(category != ItemCategory.GUNS && category != ItemCategory.ARMOR);
            boolean allowInVest = optionalBoolean(object, "allowInVest").orElse(category == ItemCategory.AMMO || category == ItemCategory.MAGAZINES || category == ItemCategory.MEDICAL);
            List<String> notes = optionalStringList(object, "notes");
            return Optional.of(new ParsedProfile(lookupKey, new ItemCarryProfile(itemId, category, weight, slotCost, gridWidth, gridHeight, canRotate, allowInSafeBox, allowInVest, notes)));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Skipping invalid carry profile entry in {}: {}", fileId, exception.getMessage());
            return Optional.empty();
        }
    }

    private static ResourceLocation baseItemId(String lookupKey) {
        int separator = lookupKey.indexOf('#');
        return ResourceLocation.parse(separator >= 0 ? lookupKey.substring(0, separator) : lookupKey);
    }

    private static double fallbackWeight(ItemCategory category) {
        return switch (category) {
            case GUNS -> 4.0D;
            case ARMOR -> 6.0D;
            case TOOLS, WEAPON_PARTS -> 2.0D;
            case MEDICAL, ELECTRONICS, VALUABLES -> 1.0D;
            case AMMO, MAGAZINES, ATTACHMENTS -> 0.5D;
            default -> 0.2D;
        };
    }

    private static int fallbackSlotCost(ItemCategory category) {
        return switch (category) {
            case GUNS -> 8;
            case ARMOR -> 6;
            case TOOLS, WEAPON_PARTS -> 4;
            case MEDICAL, ELECTRONICS, VALUABLES -> 2;
            default -> 1;
        };
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

    private static Optional<Double> optionalDouble(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(object.get(name).getAsDouble());
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

    private record ParsedProfile(String lookupKey, ItemCarryProfile profile) {
    }
}
