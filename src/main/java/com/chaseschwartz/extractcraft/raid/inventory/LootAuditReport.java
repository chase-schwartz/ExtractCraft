package com.chaseschwartz.extractcraft.raid.inventory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.itemidentity.TaczDisplayNameResolver;
import com.chaseschwartz.extractcraft.items.LooseLootDefinition;
import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;
import com.chaseschwartz.extractcraft.itemvalues.ItemRarity;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

final class LootAuditReport {
    private static final Gson GSON = new Gson();
    private static final Path REPORT_PATH = Path.of("run", "extractcraft", "debug", "loot_report.txt");
    private static final Set<ItemRarity> LOOSE_RARITIES = Set.of(ItemRarity.BLUE, ItemRarity.PURPLE, ItemRarity.GOLD, ItemRarity.RED);
    private static final Map<String, String> COMMON_TAG_EXAMPLES = Map.ofEntries(
            Map.entry("c:ingots/copper", "minecraft:copper_ingot"),
            Map.entry("c:gunpowders", "minecraft:gunpowder"),
            Map.entry("c:nuggets/iron", "minecraft:iron_nugget"),
            Map.entry("c:gems/lapis", "minecraft:lapis_lazuli"),
            Map.entry("c:ingots/iron", "minecraft:iron_ingot"),
            Map.entry("c:gems/diamond", "minecraft:diamond"),
            Map.entry("c:rods/blaze", "minecraft:blaze_rod"),
            Map.entry("c:ingots/gold", "minecraft:gold_ingot"),
            Map.entry("c:gems/emerald", "minecraft:emerald"),
            Map.entry("c:dusts/redstone", "minecraft:redstone"),
            Map.entry("c:storage_blocks/iron", "minecraft:iron_block"),
            Map.entry("c:storage_blocks/gold", "minecraft:gold_block"),
            Map.entry("c:storage_blocks/diamond", "minecraft:diamond_block"));

    private LootAuditReport() {
    }

    static Path write() throws Exception {
        String report = build();
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report, StandardCharsets.UTF_8);
        return REPORT_PATH;
    }

    private static String build() {
        List<ItemValueEntry> spawnable = currentRaidSpawnablePool();
        List<ItemValueEntry> loose = spawnable.stream().filter(LootAuditReport::isLooseLoot).sorted(LootAuditReport::compareEntries).toList();
        List<ItemValueEntry> vanilla = spawnable.stream().filter(entry -> entry.itemId().getNamespace().equals("minecraft")).sorted(LootAuditReport::compareEntries).toList();
        List<ItemValueEntry> tacz = spawnable.stream().filter(LootAuditReport::isTacz).sorted(LootAuditReport::compareEntries).toList();
        List<ItemValueEntry> equipment = spawnable.stream().filter(LootAuditReport::isEquipment).sorted(LootAuditReport::compareEntries).toList();
        List<ItemValueEntry> meds = spawnable.stream().filter(LootAuditReport::isMed).sorted(LootAuditReport::compareEntries).toList();
        List<ItemValueEntry> repair = spawnable.stream().filter(LootAuditReport::isRepair).sorted(LootAuditReport::compareEntries).toList();
        List<TaczDiscoveredEntry> discoveredTacz = discoverTaczEntries();
        List<ItemValueEntry> firstClassTacz = tacz.stream().filter(RaidContainerService::isFirstClassTaczLoot).toList();
        List<ItemValueEntry> legacyOnlyTacz = tacz.stream().filter(entry -> !RaidContainerService.isFirstClassTaczLoot(entry)).toList();
        List<TaczRecipe> recipes = scanTaczRecipes();
        Set<String> spawnableLookupKeys = spawnable.stream().map(ItemValueEntry::lookupKey).collect(Collectors.toCollection(TreeSet::new));
        Set<String> spawnableBaseIds = spawnable.stream().map(entry -> entry.itemId().toString()).collect(Collectors.toCollection(TreeSet::new));

        StringBuilder report = new StringBuilder(64_000);
        report.append("ExtractCraft Loot Ecosystem Audit\n");
        report.append("Generated from current registries and filesystem scan.\n\n");

        appendSpawnSources(report, spawnable, loose, vanilla, tacz, firstClassTacz, legacyOnlyTacz, equipment, meds, repair);
        appendContextWeights(report);
        appendLooseSummary(report);
        appendItemList(report, "Current vanilla raid-spawnable items", vanilla, false);
        appendItemList(report, "Current TaCZ raid-spawnable items", tacz, true);
        appendDiscoveredTacz(report, discoveredTacz, firstClassTacz, legacyOnlyTacz);
        appendItemList(report, "Current equipment/med/repair raid-spawnable ExtractCraft items", Stream.of(equipment, meds, repair).flatMap(List::stream).distinct().sorted(LootAuditReport::compareEntries).toList(), false);
        appendRecipeAudit(report, recipes, spawnableLookupKeys, spawnableBaseIds);
        appendSuggestions(report, tacz, recipes, spawnableLookupKeys, spawnableBaseIds);

        return report.toString();
    }

    private static List<ItemValueEntry> currentRaidSpawnablePool() {
        return ItemValueRegistry.entries().stream()
                .filter(ItemValueEntry::sellable)
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.itemId()))
                .filter(entry -> !ItemStackVariantFactory.isUnsafeBareVariantBase(entry.itemId()) || entry.lookupKey().contains("#"))
                .sorted(LootAuditReport::compareEntries)
                .toList();
    }

    private static void appendSpawnSources(StringBuilder report, List<ItemValueEntry> spawnable, List<ItemValueEntry> loose, List<ItemValueEntry> vanilla,
            List<ItemValueEntry> tacz, List<ItemValueEntry> firstClassTacz, List<ItemValueEntry> legacyOnlyTacz, List<ItemValueEntry> equipment, List<ItemValueEntry> meds, List<ItemValueEntry> repair) {
        report.append("A. Current raid loot spawn sources\n");
        report.append("- Active container population: ").append("com.chaseschwartz.extractcraft.raid.containers.RaidContainerService.populate/fill\n");
        report.append("- Primary registry: ItemValueRegistry sellable entries with registered base items.\n");
        report.append("- Unsafe bare TaCZ variant bases are filtered unless lookupKey contains '#'.\n");
        report.append("- Roll path: context TaCZ chance -> first-class TaCZ candidates -> context loose-loot chance -> loose loot candidates -> legacy fallback candidates.\n");
        report.append("- Loose loot is first-class only for ExtractCraft items with BLUE/PURPLE/GOLD/RED rarity.\n");
        report.append("- TaCZ first-class entries must be normalized variant keys with value + carry profile data.\n\n");
        report.append("Spawnable registry counts:\n");
        report.append("- total current raid-spawnable value entries: ").append(spawnable.size()).append('\n');
        report.append("- loose_loot first-class entries: ").append(loose.size()).append('\n');
        report.append("- vanilla legacy fallback entries: ").append(vanilla.size()).append('\n');
        report.append("- tacz total registry entries: ").append(tacz.size()).append('\n');
        report.append("- tacz first-class entries: ").append(firstClassTacz.size()).append('\n');
        report.append("- tacz legacy-only entries: ").append(legacyOnlyTacz.size()).append('\n');
        report.append("- equipment-tagged entries: ").append(equipment.size()).append('\n');
        report.append("- med-tagged entries: ").append(meds.size()).append('\n');
        report.append("- repair-tagged entries: ").append(repair.size()).append("\n\n");
    }

    private static void appendContextWeights(StringBuilder report) {
        report.append("Current loose-loot context weights:\n");
        for (String line : RaidContainerService.lootContextReportLines()) {
            report.append("- ").append(line).append('\n');
        }
        report.append('\n');
    }

    private static void appendLooseSummary(StringBuilder report) {
        report.append("D. Current loose loot list summary\n");
        report.append("- authored definitions: ").append(LooseLootDefinition.DEFINITIONS.size()).append('\n');
        report.append("- by rarity: ").append(countDefinitions(LooseLootDefinition::rarity)).append('\n');
        report.append("- by category: ").append(countDefinitions(LooseLootDefinition::category)).append('\n');
        report.append("- value by rarity:\n");
        LooseLootDefinition.DEFINITIONS.stream()
                .collect(Collectors.groupingBy(LooseLootDefinition::rarity, TreeMap::new, Collectors.toList()))
                .forEach((rarity, definitions) -> {
                    int min = definitions.stream().mapToInt(LooseLootDefinition::value).min().orElse(0);
                    int max = definitions.stream().mapToInt(LooseLootDefinition::value).max().orElse(0);
                    double avg = definitions.stream().mapToInt(LooseLootDefinition::value).average().orElse(0.0D);
                    report.append("  - ").append(rarity).append(": count=").append(definitions.size())
                            .append(" min=").append(min)
                            .append(" avg=").append(Math.round(avg))
                            .append(" max=").append(max)
                            .append('\n');
                });
        report.append('\n');
    }

    private static Map<String, Long> countDefinitions(java.util.function.Function<LooseLootDefinition, String> classifier) {
        return LooseLootDefinition.DEFINITIONS.stream()
                .collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.counting()));
    }

    private static void appendItemList(StringBuilder report, String title, List<ItemValueEntry> entries, boolean taczList) {
        report.append(title).append(" (").append(entries.size()).append(")\n");
        if (entries.isEmpty()) {
            report.append("- none\n\n");
            return;
        }
        for (ItemValueEntry entry : entries) {
            Optional<ItemCarryProfile> profile = ItemCarryProfileRegistry.get(entry.lookupKey()).or(() -> ItemCarryProfileRegistry.get(entry.itemId()));
            report.append("- ").append(entry.lookupKey())
                    .append(" | itemId=").append(entry.itemId())
                    .append(" | display=").append(displayName(entry))
                    .append(" | category=").append(entry.category().name().toLowerCase(Locale.ROOT))
                    .append(" | rarity=").append(entry.rarity().name().toLowerCase(Locale.ROOT))
                    .append(" | value=").append(entry.value())
                    .append(" | spawnPath=").append(spawnPath(entry))
                    .append(" | rollWeight=").append(isLooseLoot(entry) ? looseItemWeight(entry) : legacyWeight(entry));
            profile.ifPresent(itemCarryProfile -> report.append(" | weight=").append(String.format(Locale.ROOT, "%.2f", itemCarryProfile.weight()))
                    .append(" | grid=").append(itemCarryProfile.gridWidth().orElse(1)).append('x').append(itemCarryProfile.gridHeight().orElse(1))
                    .append(" | profileCategory=").append(itemCarryProfile.category().name().toLowerCase(Locale.ROOT)));
            if (taczList) {
                report.append(" | taczType=").append(taczType(entry.lookupKey()))
                        .append(" | variant=").append(taczVariant(entry.lookupKey()).orElse("none"))
                        .append(" | firstClassLoot=").append(RaidContainerService.isFirstClassTaczLoot(entry));
            }
            report.append('\n');
        }
        report.append('\n');
    }

    private static void appendDiscoveredTacz(StringBuilder report, List<TaczDiscoveredEntry> discovered, List<ItemValueEntry> firstClassTacz, List<ItemValueEntry> legacyOnlyTacz) {
        Set<String> firstClassKeys = firstClassTacz.stream().map(ItemValueEntry::lookupKey).collect(Collectors.toCollection(TreeSet::new));
        Set<String> legacyKeys = legacyOnlyTacz.stream().map(ItemValueEntry::lookupKey).collect(Collectors.toCollection(TreeSet::new));
        report.append("TaCZ discovered content entries (").append(discovered.size()).append(")\n");
        if (discovered.isEmpty()) {
            report.append("- Broader TaCZ content discovery found 0 index entries in the scanned folders. This does not disable TaCZ loot: the active first-class pool below comes from profile-backed normalized ItemValueRegistry keys.\n");
        }
        report.append("- first-class profile-backed keys: ").append(firstClassKeys.size()).append(" ").append(firstClassKeys).append('\n');
        report.append("- legacy-only keys: ").append(legacyKeys.size()).append(" ").append(legacyKeys).append('\n');
        Map<String, Long> byKind = discovered.stream().collect(Collectors.groupingBy(TaczDiscoveredEntry::kind, TreeMap::new, Collectors.counting()));
        report.append("- discovered by kind: ").append(byKind).append('\n');
        List<TaczDiscoveredEntry> missingProfiles = discovered.stream()
                .filter(entry -> ItemValueRegistry.get(entry.normalizedKey()).isEmpty() || ItemCarryProfileRegistry.get(entry.normalizedKey()).isEmpty())
                .toList();
        report.append("- discovered missing value/profile: ").append(missingProfiles.size()).append('\n');
        for (TaczDiscoveredEntry entry : discovered) {
            boolean hasValue = ItemValueRegistry.get(entry.normalizedKey()).isPresent();
            boolean hasProfile = ItemCarryProfileRegistry.get(entry.normalizedKey()).isPresent();
            report.append("  - ").append(entry.normalizedKey())
                    .append(" | kind=").append(entry.kind())
                    .append(" | subtype=").append(entry.subtype().orElse("unknown"))
                    .append(" | value=").append(hasValue ? "yes" : "no")
                    .append(" | profile=").append(hasProfile ? "yes" : "no")
                    .append(" | firstClass=").append(firstClassKeys.contains(entry.normalizedKey()))
                    .append('\n');
        }
        report.append('\n');
    }

    private static void appendRecipeAudit(StringBuilder report, List<TaczRecipe> recipes, Set<String> spawnableLookupKeys, Set<String> spawnableBaseIds) {
        report.append("E. TaCZ recipe ingredient audit\n");
        report.append("- scanned recipe count: ").append(recipes.size()).append('\n');
        if (recipes.isEmpty()) {
            report.append("- no TaCZ recipe JSON files were found in run/tacz, run/datapacks, dynamic-data-pack-cache, or src/main/resources/data.\n\n");
            return;
        }

        Map<String, Long> directIngredientCounts = recipes.stream()
                .flatMap(recipe -> recipe.ingredients().stream())
                .collect(Collectors.groupingBy(Ingredient::itemId, TreeMap::new, Collectors.counting()));
        Map<String, Long> tagCounts = recipes.stream()
                .flatMap(recipe -> recipe.tags().stream())
                .collect(Collectors.groupingBy(tag -> tag, TreeMap::new, Collectors.counting()));

        report.append("- direct ingredient item counts: ").append(directIngredientCounts.size()).append('\n');
        report.append("- ingredient tag counts: ").append(tagCounts.size()).append('\n');
        report.append("- recipes:\n");
        for (TaczRecipe recipe : recipes) {
            report.append("  - ").append(recipe.recipeId())
                    .append(" | type=").append(recipe.recipeType())
                    .append(" | output=").append(recipe.outputKey())
                    .append(" x").append(recipe.outputCount())
                    .append(" | materials=");
            if (recipe.ingredients().isEmpty() && recipe.tags().isEmpty()) {
                report.append("none");
            } else {
                String direct = recipe.ingredients().stream()
                        .map(ingredient -> ingredient.itemId() + " x" + ingredient.count())
                        .collect(Collectors.joining(", "));
                String tags = recipe.tags().stream()
                        .map(tag -> "#" + tag)
                        .collect(Collectors.joining(", "));
                report.append(Stream.of(direct, tags).filter(text -> !text.isBlank()).collect(Collectors.joining(", ")));
            }
            report.append('\n');
        }

        report.append("\nF. Missing ingredient audit\n");
        appendIngredientAvailability(report, "vanilla direct ingredients", directIngredientCounts.keySet().stream().filter(id -> id.startsWith("minecraft:")).toList(), spawnableLookupKeys, spawnableBaseIds);
        appendIngredientAvailability(report, "non-vanilla direct ingredients", directIngredientCounts.keySet().stream().filter(id -> !id.startsWith("minecraft:")).toList(), spawnableLookupKeys, spawnableBaseIds);
        report.append("- ingredient tags requiring review:\n");
        if (tagCounts.isEmpty()) {
            report.append("  - none\n");
        } else {
            tagCounts.forEach((tag, count) -> report.append("  - #").append(tag)
                    .append(" used by ").append(count).append(" recipe(s)")
                    .append(COMMON_TAG_EXAMPLES.containsKey(tag) ? " | likely vanilla example=" + COMMON_TAG_EXAMPLES.get(tag) : "")
                    .append(COMMON_TAG_EXAMPLES.containsKey(tag) && spawnableBaseIds.contains(COMMON_TAG_EXAMPLES.get(tag)) ? " | example spawnable=yes" : "")
                    .append(COMMON_TAG_EXAMPLES.containsKey(tag) && !spawnableBaseIds.contains(COMMON_TAG_EXAMPLES.get(tag)) ? " | example spawnable=no" : "")
                    .append('\n'));
        }
        report.append('\n');
    }

    private static void appendIngredientAvailability(StringBuilder report, String label, List<String> ingredientIds, Set<String> spawnableLookupKeys, Set<String> spawnableBaseIds) {
        report.append("- ").append(label).append(":\n");
        if (ingredientIds.isEmpty()) {
            report.append("  - none\n");
            return;
        }
        for (String itemId : ingredientIds) {
            boolean spawnable = spawnableLookupKeys.contains(itemId) || spawnableBaseIds.contains(itemId);
            report.append("  - ").append(itemId).append(" | spawnable=").append(spawnable ? "yes" : "no").append('\n');
        }
    }

    private static void appendSuggestions(StringBuilder report, List<ItemValueEntry> tacz, List<TaczRecipe> recipes, Set<String> spawnableLookupKeys, Set<String> spawnableBaseIds) {
        report.append("G. Suggested additions for later tuning (not applied)\n");
        report.append("- Promote TaCZ guns/ammo/attachments from legacy fallback into explicit first-class loot categories, especially military containers.\n");
        report.append("- Military: guns, ammo, attachments, weapon parts, optics, armor materials.\n");
        report.append("- Generic: common ammo, low-end attachments, tools, survival, blue loose loot.\n");
        report.append("- Safe/high-value: rare attachments, restricted intel, red/gold loose loot, compact valuables.\n");
        report.append("- Industrial/toolboxes: vanilla recipe ingredients, copper/iron/gold parts, gunpowder-adjacent materials if desired.\n");
        report.append("- Medical: home/field medical, lab bio, rare medical loot, med kits, limited survival/tools.\n");
        long firstClassCount = tacz.stream().filter(RaidContainerService::isFirstClassTaczLoot).count();
        report.append("- Current TaCZ spawnable count is ").append(tacz.size()).append("; first-class TaCZ entries now active: ").append(firstClassCount).append(".\n");
        Set<String> missingTagExamples = recipes.stream()
                .flatMap(recipe -> recipe.tags().stream())
                .map(COMMON_TAG_EXAMPLES::get)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !spawnableLookupKeys.contains(id) && !spawnableBaseIds.contains(id))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missingTagExamples.isEmpty()) {
            report.append("- Likely vanilla recipe ingredients to consider adding to raid loot: ")
                    .append(String.join(", ", missingTagExamples))
                    .append('\n');
        }
        report.append("- Rough first-class TaCZ starting weights to test later: military 20-30% TaCZ, generic 5-10%, safe 5-15% rare compact gear, office/medical mostly no TaCZ.\n");
    }

    private static List<TaczRecipe> scanTaczRecipes() {
        List<Path> roots = List.of(
                Path.of("run", "tacz"),
                Path.of("run", "datapacks"),
                Path.of("run", "dynamic-data-pack-cache"),
                Path.of("src", "main", "resources", "data"));
        List<TaczRecipe> recipes = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(LootAuditReport::looksLikeRecipePath)
                        .forEach(path -> parseRecipe(root, path).ifPresent(recipes::add));
            } catch (Exception exception) {
                ExtractCraft.LOGGER.warn("Failed to scan loot audit recipe root {}", root, exception);
            }
        }
        return recipes.stream()
                .distinct()
                .sorted(Comparator.comparing(TaczRecipe::recipeId))
                .toList();
    }

    private static List<TaczDiscoveredEntry> discoverTaczEntries() {
        List<Path> roots = List.of(
                Path.of("run", "tacz"),
                Path.of("run", "config", "tacz"),
                Path.of("run", "datapacks"),
                Path.of("run", "dynamic-data-pack-cache"),
                Path.of("src", "main", "resources", "data"));
        List<TaczDiscoveredEntry> discovered = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(LootAuditReport::looksLikeTaczIndexPath)
                        .forEach(path -> parseTaczIndex(root, path).ifPresent(discovered::add));
            } catch (Exception exception) {
                ExtractCraft.LOGGER.warn("Failed to scan TaCZ content index root {}", root, exception);
            }
        }
        return discovered.stream()
                .distinct()
                .sorted(Comparator.comparing(TaczDiscoveredEntry::kind).thenComparing(TaczDiscoveredEntry::normalizedKey))
                .toList();
    }

    private static boolean looksLikeTaczIndexPath(Path path) {
        String text = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return text.contains("/data/") && text.contains("/index/")
                && (text.contains("/index/guns/") || text.contains("/index/ammo/") || text.contains("/index/attachments/") || text.contains("/index/modifiers/") || text.contains("/index/parts/"));
    }

    private static Optional<TaczDiscoveredEntry> parseTaczIndex(Path root, Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        String[] parts = normalizedPath.split("/");
        int dataIndex = -1;
        int indexIndex = -1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("data")) {
                dataIndex = i;
            }
            if (parts[i].equals("index")) {
                indexIndex = i;
                break;
            }
        }
        if (dataIndex < 0 || indexIndex < 0 || dataIndex + 1 >= parts.length || indexIndex + 1 >= parts.length) {
            return Optional.empty();
        }

        String namespace = parts[dataIndex + 1];
        String kind = parts[indexIndex + 1];
        String idPath = path.getFileName().toString();
        idPath = idPath.substring(0, idPath.length() - ".json".length());
        String variant = namespace + ":" + idPath;
        String normalizedKey = switch (kind) {
            case "guns" -> "tacz:modern_kinetic_gun#" + variant;
            case "ammo" -> "tacz:ammo#" + variant;
            case "attachments" -> "tacz:attachment#" + variant;
            default -> "tacz:" + kind + "#" + variant;
        };

        Optional<String> subtype = Optional.empty();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonObject()) {
                subtype = optionalString(element.getAsJsonObject(), "type");
            }
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to parse TaCZ index candidate {}", path, exception);
        }
        return Optional.of(new TaczDiscoveredEntry(normalizedKey, kind, subtype, root.relativize(path).toString().replace('\\', '/')));
    }

    private static boolean looksLikeRecipePath(Path path) {
        String text = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return text.contains("/recipe/") || text.contains("/recipes/");
    }

    private static Optional<TaczRecipe> parseRecipe(Path root, Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            String recipeType = optionalString(object, "type").orElse("");
            boolean taczRecipe = recipeType.startsWith("tacz:") || path.toString().replace('\\', '/').contains("/run/tacz/");
            if (!taczRecipe) {
                return Optional.empty();
            }

            List<Ingredient> ingredients = new ArrayList<>();
            List<String> tags = new ArrayList<>();
            if (object.has("materials") && object.get("materials").isJsonArray()) {
                object.getAsJsonArray("materials").forEach(material -> collectIngredient(material, 1, ingredients, tags));
            }
            if (object.has("ingredients") && object.get("ingredients").isJsonArray()) {
                object.getAsJsonArray("ingredients").forEach(ingredient -> collectIngredient(ingredient, 1, ingredients, tags));
            }

            String outputKey = outputKey(object.get("result"));
            int outputCount = outputCount(object.get("result"));
            String recipeId = root.relativize(path).toString().replace('\\', '/');
            return Optional.of(new TaczRecipe(recipeId, recipeType.isBlank() ? "unknown" : recipeType, outputKey, outputCount, List.copyOf(ingredients), List.copyOf(new TreeSet<>(tags))));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to parse TaCZ recipe candidate {}", path, exception);
            return Optional.empty();
        }
    }

    private static void collectIngredient(JsonElement element, int inheritedCount, List<Ingredient> ingredients, List<String> tags) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectIngredient(child, inheritedCount, ingredients, tags));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        int count = optionalInt(object, "count").orElse(inheritedCount);
        if (object.has("item")) {
            JsonElement item = object.get("item");
            if (item.isJsonPrimitive()) {
                ingredients.add(new Ingredient(item.getAsString(), count));
            } else {
                collectIngredient(item, count, ingredients, tags);
            }
        }
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            tags.add(object.get("tag").getAsString());
        }
    }

    private static String outputKey(JsonElement result) {
        if (result == null || result.isJsonNull()) {
            return "unknown";
        }
        if (result.isJsonPrimitive()) {
            return result.getAsString();
        }
        if (!result.isJsonObject()) {
            return "unknown";
        }
        JsonObject object = result.getAsJsonObject();
        String resultType = optionalString(object, "type").orElse("");
        String id = optionalString(object, "id").or(() -> optionalString(object, "item")).orElse("unknown");
        return switch (resultType) {
            case "gun" -> "tacz:modern_kinetic_gun#" + id;
            case "ammo" -> "tacz:ammo#" + id;
            case "attachment" -> "tacz:attachment#" + id;
            default -> id;
        };
    }

    private static int outputCount(JsonElement result) {
        if (result != null && result.isJsonObject()) {
            return optionalInt(result.getAsJsonObject(), "count").orElse(1);
        }
        return 1;
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

    private static boolean isLooseLoot(ItemValueEntry entry) {
        return LOOSE_RARITIES.contains(entry.rarity()) && entry.itemId().getNamespace().equals(ExtractCraft.MODID);
    }

    private static boolean isTacz(ItemValueEntry entry) {
        return entry.itemId().getNamespace().equals("tacz") || entry.lookupKey().startsWith("tacz:");
    }

    private static String spawnPath(ItemValueEntry entry) {
        if (isLooseLoot(entry)) {
            return "loose_loot_first_class";
        }
        if (RaidContainerService.isFirstClassTaczLoot(entry)) {
            return "tacz_first_class_and_legacy_fallback";
        }
        return "legacy_fallback";
    }

    private static boolean isEquipment(ItemValueEntry entry) {
        Optional<ItemCarryProfile> profile = ItemCarryProfileRegistry.get(entry.lookupKey()).or(() -> ItemCarryProfileRegistry.get(entry.itemId()));
        return profile.flatMap(ItemCarryProfile::equipmentSlot).isPresent()
                || profile.flatMap(ItemCarryProfile::storageGridDefinition).isPresent()
                || entry.category() == ItemCategory.ARMOR;
    }

    private static boolean isMed(ItemValueEntry entry) {
        Optional<ItemCarryProfile> profile = ItemCarryProfileRegistry.get(entry.lookupKey()).or(() -> ItemCarryProfileRegistry.get(entry.itemId()));
        return entry.category() == ItemCategory.MEDICAL
                || (!isLooseLoot(entry) && entry.category() == ItemCategory.MEDICAL_TECH)
                || profile.flatMap(ItemCarryProfile::healAmount).isPresent();
    }

    private static boolean isRepair(ItemValueEntry entry) {
        Optional<ItemCarryProfile> profile = ItemCarryProfileRegistry.get(entry.lookupKey()).or(() -> ItemCarryProfileRegistry.get(entry.itemId()));
        return profile.flatMap(ItemCarryProfile::repairAmount).isPresent()
                || entry.lookupKey().contains("rebuild_kit")
                || entry.lookupKey().contains("repair");
    }

    private static String displayName(ItemValueEntry entry) {
        return TaczDisplayNameResolver.displayName(entry.lookupKey(), BuiltInRegistries.ITEM.get(entry.itemId()).getDescription().getString());
    }

    private static String taczType(String lookupKey) {
        if (lookupKey.startsWith("tacz:modern_kinetic_gun#")) {
            return "gun";
        }
        if (lookupKey.startsWith("tacz:ammo#")) {
            return "ammo";
        }
        if (lookupKey.startsWith("tacz:attachment#")) {
            return "attachment";
        }
        if (lookupKey.startsWith("tacz:ammo_box")) {
            return "ammo_box";
        }
        return "base_or_other";
    }

    private static Optional<String> taczVariant(String lookupKey) {
        int separator = lookupKey.indexOf('#');
        return separator >= 0 ? Optional.of(lookupKey.substring(separator + 1)) : Optional.empty();
    }

    private static int looseItemWeight(ItemValueEntry entry) {
        int valuePenalty = Math.max(1, entry.value() / 2_500);
        int footprintPenalty = switch (entry.category()) {
            case INDUSTRIAL, INDUSTRIAL_TOOLS, ARMOR_MATERIALS -> 2;
            default -> 1;
        };
        return Math.max(1, 100 / valuePenalty / footprintPenalty);
    }

    private static int legacyWeight(ItemValueEntry entry) {
        return switch (entry.rarity()) {
            case COMMON -> 60;
            case UNCOMMON -> 30;
            case RARE, BLUE -> 10;
            case EPIC, PURPLE -> 3;
            case LEGENDARY, GOLD, RED -> 1;
            case QUEST -> 0;
        };
    }

    private static int compareEntries(ItemValueEntry left, ItemValueEntry right) {
        return Comparator.comparing((ItemValueEntry entry) -> entry.itemId().getNamespace())
                .thenComparing(ItemValueEntry::lookupKey)
                .compare(left, right);
    }

    private record Ingredient(String itemId, int count) {
    }

    private record TaczRecipe(String recipeId, String recipeType, String outputKey, int outputCount, List<Ingredient> ingredients, List<String> tags) {
    }

    private record TaczDiscoveredEntry(String normalizedKey, String kind, Optional<String> subtype, String sourcePath) {
    }
}
