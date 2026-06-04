package com.chaseschwartz.extractcraft.raid.inventory;

import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.itemidentity.TaczDisplayNameResolver;
import com.chaseschwartz.extractcraft.itemvalues.ItemRarity;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.raid.containers.RaidContainerService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

final class TaczAuditReport {
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(".jar", ".zip", ".pack");
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

    private TaczAuditReport() {
    }

    static Path write() throws Exception {
        String report = build();
        Path reportPath = reportPath();
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        return reportPath;
    }

    private static Path reportPath() {
        return FMLPaths.GAMEDIR.get().resolve("extractcraft").resolve("debug").resolve("tacz_report.txt");
    }

    private static String build() {
        ScanResult scan = scanCandidates();
        Map<String, String> translations = loadTranslations(scan.candidates());
        List<TaczContentEntry> content = scan.candidates().stream()
                .map(candidate -> parseContentEntry(candidate, translations))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(TaczContentEntry::normalizedKey, Function.identity(), TaczAuditReport::preferContentEntry, TreeMap::new))
                .values()
                .stream()
                .toList();
        List<TaczRecipe> recipes = scan.candidates().stream()
                .map(candidate -> parseRecipe(candidate, translations))
                .flatMap(Optional::stream)
                .distinct()
                .sorted(Comparator.comparing(TaczRecipe::recipeId))
                .toList();
        List<UnknownJsonFile> unknownFiles = unknownJsonFiles(scan.candidates(), content, recipes);
        List<ItemValueEntry> spawnable = currentRaidSpawnablePool();
        Set<String> spawnableLookupKeys = spawnable.stream().map(ItemValueEntry::lookupKey).collect(Collectors.toCollection(TreeSet::new));
        Set<String> spawnableBaseIds = spawnable.stream().map(entry -> entry.itemId().toString()).collect(Collectors.toCollection(TreeSet::new));
        Map<String, ItemValueEntry> valuesByLookup = ItemValueRegistry.entries().stream()
                .collect(Collectors.toMap(ItemValueEntry::lookupKey, Function.identity(), (left, right) -> left, TreeMap::new));

        StringBuilder report = new StringBuilder(256_000);
        report.append("ExtractCraft Deep TaCZ Content Audit\n");
        report.append("Generated from filesystem/archive discovery plus current ExtractCraft value and carry profile registries.\n");
        report.append("No loot weights, profiles, recipes, assets, or inventory behavior are modified by this report.\n\n");

        appendRuntimeDiagnostics(report, scan.runtime());
        appendSearchLocations(report, scan.locations());
        appendCandidateSamples(report, scan.candidates());
        appendUnknownJsonFiles(report, unknownFiles);
        appendContentSection(report, "B. Discovered TaCZ guns", "guns", content, valuesByLookup);
        appendContentSection(report, "C. Discovered TaCZ ammo", "ammo", content, valuesByLookup);
        appendContentSection(report, "D. Discovered TaCZ attachments", "attachments", content, valuesByLookup);
        appendContentSection(report, "E. Discovered TaCZ modifiers/parts/other", "other", content, valuesByLookup);
        appendRecipeSection(report, recipes);
        appendIngredientSection(report, recipes, spawnableLookupKeys, spawnableBaseIds);
        appendSpawnableSection(report, spawnable);
        appendSuggestions(report, content, recipes, spawnableLookupKeys, spawnableBaseIds);

        return report.toString();
    }

    private static List<ItemValueEntry> currentRaidSpawnablePool() {
        return ItemValueRegistry.entries().stream()
                .filter(ItemValueEntry::sellable)
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.itemId()))
                .filter(entry -> !ItemStackVariantFactory.isUnsafeBareVariantBase(entry.itemId()) || entry.lookupKey().contains("#"))
                .sorted(TaczAuditReport::compareEntries)
                .toList();
    }

    private static ScanResult scanCandidates() {
        RuntimeScanContext runtime = runtimeContext();
        List<SearchLocation> locations = new ArrayList<>();
        List<CandidateFile> candidates = new ArrayList<>();
        for (ScanRoot root : scanRoots(runtime)) {
            Path path = root.path();
            if (!Files.exists(path)) {
                locations.add(new SearchLocation(root.label(), path.toString(), false, 0, 0));
                continue;
            }
            if (Files.isRegularFile(path)) {
                scanFileRoot(root, locations, candidates);
                continue;
            }
            scanDirectoryRoot(root, locations, candidates);
        }
        return new ScanResult(runtime, List.copyOf(locations), List.copyOf(candidates));
    }

    private static RuntimeScanContext runtimeContext() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path configDir = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        List<Path> projectRoots = projectRootCandidates(cwd, gameDir);
        List<LoadedModFile> loadedMods = loadedModFiles();
        return new RuntimeScanContext(cwd, gameDir, configDir, projectRoots, loadedMods);
    }

    private static List<Path> projectRootCandidates(Path cwd, Path gameDir) {
        Map<String, Path> roots = new LinkedHashMap<>();
        addProjectRootCandidate(roots, cwd);
        addProjectRootCandidate(roots, gameDir);
        addProjectRootCandidate(roots, gameDir.getParent());
        Path cursor = cwd;
        for (int i = 0; i < 4 && cursor != null; i++) {
            addProjectRootCandidate(roots, cursor);
            cursor = cursor.getParent();
        }
        return roots.values().stream().toList();
    }

    private static void addProjectRootCandidate(Map<String, Path> roots, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (Files.exists(normalized.resolve("src").resolve("main").resolve("resources"))
                || Files.exists(normalized.resolve("settings.gradle"))
                || Files.exists(normalized.resolve("build.gradle"))) {
            roots.put(normalized.toString(), normalized);
        }
    }

    private static List<LoadedModFile> loadedModFiles() {
        ModList modList = ModList.get();
        if (modList == null) {
            return List.of();
        }
        Map<String, LoadedModFile> files = new LinkedHashMap<>();
        for (IModInfo mod : modList.getMods()) {
            if (mod.getOwningFile() == null || mod.getOwningFile().getFile() == null) {
                continue;
            }
            Path filePath = mod.getOwningFile().getFile().getFilePath();
            String key = filePath == null ? mod.getModId() : filePath.toAbsolutePath().normalize().toString();
            LoadedModFile existing = files.get(key);
            String modIds = existing == null || existing.modIds().isBlank()
                    ? mod.getModId()
                    : existing.modIds() + "," + mod.getModId();
            boolean relevant = isTaczRelevant(mod.getModId()) || isTaczRelevant(mod.getDisplayName()) || (filePath != null && isTaczRelevant(filePath.toString()));
            if (existing != null) {
                relevant = relevant || existing.relevant();
            }
            files.put(key, new LoadedModFile(modIds, mod.getDisplayName(), filePath, relevant));
        }
        return files.values().stream().toList();
    }

    private static boolean isTaczRelevant(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("tacz")
                || lower.contains("timeless")
                || lower.contains("zero")
                || lower.contains("gunpack")
                || lower.contains("gun_pack")
                || lower.contains("default_gun");
    }

    private static List<ScanRoot> scanRoots(RuntimeScanContext runtime) {
        Map<String, ScanRoot> roots = new LinkedHashMap<>();
        addRoot(roots, "FML gameDir/tacz", runtime.gameDir().resolve("tacz"));
        addRoot(roots, "FML configDir/tacz", runtime.configDir().resolve("tacz"));
        addRoot(roots, "FML configDir/tacz/custom", runtime.configDir().resolve("tacz").resolve("custom"));
        addRoot(roots, "FML configDir/tacz/packs", runtime.configDir().resolve("tacz").resolve("packs"));
        addRoot(roots, "FML gameDir/datapacks", runtime.gameDir().resolve("datapacks"));
        addRoot(roots, "FML gameDir/resourcepacks", runtime.gameDir().resolve("resourcepacks"));
        addRoot(roots, "FML gameDir/dynamic-data-pack-cache", runtime.gameDir().resolve("dynamic-data-pack-cache"));
        addRoot(roots, "FML gameDir/dynamic-resource-pack-cache", runtime.gameDir().resolve("dynamic-resource-pack-cache"));
        addRoot(roots, "FML gameDir/saves", runtime.gameDir().resolve("saves"));
        addRoot(roots, "FML gameDir/mods", runtime.gameDir().resolve("mods"));
        for (Path projectRoot : runtime.projectRoots()) {
            addRoot(roots, "projectRoot/src/main/resources/data", projectRoot.resolve("src").resolve("main").resolve("resources").resolve("data"));
            addRoot(roots, "projectRoot/src/main/resources/assets", projectRoot.resolve("src").resolve("main").resolve("resources").resolve("assets"));
            addRoot(roots, "projectRoot/run/tacz fallback", projectRoot.resolve("run").resolve("tacz"));
            addRoot(roots, "projectRoot/run/mods fallback", projectRoot.resolve("run").resolve("mods"));
        }
        for (LoadedModFile modFile : runtime.loadedMods()) {
            if (modFile.relevant() && modFile.path() != null) {
                addRoot(roots, "loaded mod file " + modFile.modIds(), modFile.path());
            }
        }
        return roots.values().stream().toList();
    }

    private static void addRoot(Map<String, ScanRoot> roots, String label, Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        roots.putIfAbsent(label + "|" + normalized, new ScanRoot(label, normalized));
    }

    private static void scanFileRoot(ScanRoot root, List<SearchLocation> locations, List<CandidateFile> candidates) {
        Path file = root.path();
        if (isArchive(file)) {
            ArchiveScanResult archive = scanArchive(file, candidates);
            locations.add(new SearchLocation(root.label() + " (archive)", file.toString(), true, archive.fileCount(), archive.matchingFiles()));
            return;
        } else if (isJson(file) && isInterestingFilesystemFile(file)) {
            readFilesystemCandidate(file.getParent(), file).ifPresent(candidates::add);
            locations.add(new SearchLocation(root.label(), file.toString(), true, 1, 1));
            return;
        }
        locations.add(new SearchLocation(root.label(), file.toString(), true, 1, 0));
    }

    private static void scanDirectoryRoot(ScanRoot root, List<SearchLocation> locations, List<CandidateFile> candidates) {
        int fileCount = 0;
        int matches = 0;
        try (Stream<Path> paths = Files.walk(root.path())) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            fileCount = files.size();
            for (Path file : files) {
                if (isJson(file) && isInterestingFilesystemFile(file)) {
                    readFilesystemCandidate(root.path(), file).ifPresent(candidates::add);
                    matches++;
                } else if (isArchive(file) && shouldScanArchive(file)) {
                    ArchiveScanResult archive = scanArchive(file, candidates);
                    locations.add(new SearchLocation(root.label() + " child archive", file.toString(), true, archive.fileCount(), archive.matchingFiles()));
                    matches += archive.matchingFiles();
                }
            }
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to scan TaCZ audit root {}", root.path(), exception);
        }
        locations.add(new SearchLocation(root.label(), root.path().toString(), true, fileCount, matches));
    }

    private static Optional<CandidateFile> readFilesystemCandidate(Path root, Path file) {
        try {
            String sourcePath = file.toString().replace('\\', '/');
            String relative = root == null ? file.getFileName().toString() : root.relativize(file).toString().replace('\\', '/');
            return Optional.of(new CandidateFile(sourcePath, relative, Files.readString(file, StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to read TaCZ audit candidate {}", file, exception);
            return Optional.empty();
        }
    }

    private static ArchiveScanResult scanArchive(Path archive, List<CandidateFile> candidates) {
        int fileCount = 0;
        int matches = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                fileCount++;
                if (!entry.getName().endsWith(".json")) {
                    continue;
                }
                try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    String content = readAll(reader);
                    if (isInterestingPath(entry.getName()) || isInterestingJsonText(content)) {
                        candidates.add(new CandidateFile(archive.toString().replace('\\', '/') + "!/" + entry.getName(), entry.getName(), content));
                        matches++;
                    }
                }
            }
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to scan TaCZ audit archive {}", archive, exception);
        }
        return new ArchiveScanResult(fileCount, matches);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, read);
        }
        return builder.toString();
    }

    private static boolean isJson(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static boolean isArchive(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return ARCHIVE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static boolean shouldScanArchive(Path path) {
        String lower = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (lower.contains("/mods/")) {
            return isTaczRelevant(lower);
        }
        return isTaczRelevant(lower) || lower.endsWith(".zip") || lower.endsWith(".pack");
    }

    private static boolean isInterestingFilesystemFile(Path path) {
        if (isInterestingPath(path.toString())) {
            return true;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            return isInterestingJsonText(text);
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean isInterestingPath(String path) {
        String lower = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        return lower.contains("tacz")
                || lower.contains("/index/")
                || lower.contains("/recipe/")
                || lower.contains("/recipes/")
                || lower.contains("workbench")
                || lower.contains("craft")
                || lower.contains("modern_kinetic_gun")
                || lower.contains("attachment")
                || lower.contains("ammo")
                || lower.contains("modifier")
                || lower.contains("part")
                || lower.endsWith("/assets/tacz/lang/en_us.json");
    }

    private static boolean isInterestingJsonText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("tacz")
                || lower.contains("guns")
                || lower.contains("gun")
                || lower.contains("ammo")
                || lower.contains("attachment")
                || lower.contains("attachments")
                || lower.contains("modifier")
                || lower.contains("modifiers")
                || lower.contains("part")
                || lower.contains("recipe")
                || lower.contains("recipes")
                || lower.contains("workbench")
                || lower.contains("index")
                || lower.contains("modern_kinetic_gun");
    }

    private static Map<String, String> loadTranslations(List<CandidateFile> candidates) {
        Map<String, String> translations = new TreeMap<>();
        for (CandidateFile candidate : candidates) {
            String path = candidate.relativePath().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!path.endsWith("/assets/tacz/lang/en_us.json") && !path.endsWith("assets/tacz/lang/en_us.json")) {
                continue;
            }
            parseJson(candidate).ifPresent(element -> {
                if (!element.isJsonObject()) {
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        translations.put(entry.getKey(), stripFormatting(entry.getValue().getAsString()));
                    }
                }
            });
        }
        return translations;
    }

    private static Optional<TaczContentEntry> parseContentEntry(CandidateFile candidate, Map<String, String> translations) {
        PathInfo info = pathInfo(candidate.relativePath());
        if (info.indexKind().isEmpty()) {
            return Optional.empty();
        }
        String kind = info.indexKind().get();
        if (!List.of("guns", "ammo", "attachments", "modifiers", "parts").contains(kind)) {
            return Optional.empty();
        }

        JsonObject object = parseJson(candidate).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(new JsonObject());
        String id = fileStem(info.fileName());
        String variant = info.namespace().orElse("tacz") + ":" + id;
        String normalizedKey = normalizedKey(kind, variant);
        String nameKey = optionalString(object, "name").orElse("");
        String displayName = translations.getOrDefault(nameKey, TaczDisplayNameResolver.displayName(normalizedKey, nameKey.isBlank() ? id : nameKey));
        String type = optionalString(object, "type").or(() -> optionalString(object, "item_type")).orElse("unknown");
        String metadata = Stream.of(
                optionalString(object, "display").map(value -> "display=" + value),
                optionalString(object, "data").map(value -> "data=" + value),
                optionalString(object, "item_type").map(value -> "item_type=" + value),
                optionalString(object, "sort").map(value -> "sort=" + value))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(", "));
        return Optional.of(new TaczContentEntry(normalizedKey, kind, displayName, candidate.sourcePath(), type, metadata));
    }

    private static Optional<TaczRecipe> parseRecipe(CandidateFile candidate, Map<String, String> translations) {
        String normalizedPath = candidate.relativePath().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!normalizedPath.contains("/recipe/") && !normalizedPath.contains("/recipes/")) {
            return Optional.empty();
        }
        JsonObject object = parseJson(candidate).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
        if (object == null) {
            return Optional.empty();
        }
        String recipeType = optionalString(object, "type").orElse("");
        boolean taczRecipe = recipeType.startsWith("tacz:") || normalizedPath.contains("/data/tacz/recipe/");
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

        Output output = output(object.get("result"), candidate.relativePath(), translations);
        return Optional.of(new TaczRecipe(candidate.relativePath(), recipeType.isBlank() ? "unknown" : recipeType, output.normalizedKey(), output.displayName(), output.count(), List.copyOf(ingredients), List.copyOf(new TreeSet<>(tags)), candidate.sourcePath()));
    }

    private static Output output(JsonElement result, String path, Map<String, String> translations) {
        String fallbackId = fileStem(Path.of(path.replace('\\', '/')).getFileName().toString());
        if (result == null || result.isJsonNull()) {
            return new Output(fallbackId, TaczDisplayNameResolver.displayName(fallbackId, fallbackId), 1);
        }
        if (result.isJsonPrimitive()) {
            String id = result.getAsString();
            return new Output(id, TaczDisplayNameResolver.displayName(id, id), 1);
        }
        if (!result.isJsonObject()) {
            return new Output(fallbackId, TaczDisplayNameResolver.displayName(fallbackId, fallbackId), 1);
        }
        JsonObject object = result.getAsJsonObject();
        String resultType = optionalString(object, "type").orElse("");
        String id = optionalString(object, "id").or(() -> optionalString(object, "item")).orElse(fallbackId);
        String normalized = switch (resultType) {
            case "gun" -> "tacz:modern_kinetic_gun#" + id;
            case "ammo" -> "tacz:ammo#" + id;
            case "attachment" -> "tacz:attachment#" + id;
            default -> id;
        };
        String idPath = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return new Output(normalized, translations.getOrDefault("tacz." + resultType + "." + idPath + ".name", TaczDisplayNameResolver.displayName(normalized, id)), optionalInt(object, "count").orElse(1));
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
        if (object.has("tag")) {
            safeString(object.get("tag")).ifPresent(tags::add);
        }
    }

    private static List<UnknownJsonFile> unknownJsonFiles(List<CandidateFile> candidates, List<TaczContentEntry> content, List<TaczRecipe> recipes) {
        Set<String> parsedSources = new TreeSet<>();
        content.stream().map(TaczContentEntry::sourcePath).forEach(parsedSources::add);
        recipes.stream().map(TaczRecipe::sourcePath).forEach(parsedSources::add);
        return candidates.stream()
                .filter(candidate -> !parsedSources.contains(candidate.sourcePath()))
                .filter(candidate -> !candidate.relativePath().replace('\\', '/').toLowerCase(Locale.ROOT).endsWith("/assets/tacz/lang/en_us.json"))
                .map(TaczAuditReport::unknownJsonFile)
                .toList();
    }

    private static UnknownJsonFile unknownJsonFile(CandidateFile candidate) {
        Optional<JsonElement> parsed = parseJson(candidate);
        String reason = parsed.map(element -> {
            if (element.isJsonObject()) {
                return "matched keywords but not recognized as index or recipe JSON";
            }
            return "top-level JSON is " + jsonKind(element);
        }).orElse("invalid JSON");
        String topKeys = parsed.filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(object -> object.keySet().stream().sorted().limit(24).collect(Collectors.joining(", ")))
                .orElse("");
        return new UnknownJsonFile(candidate.sourcePath(), reason, topKeys);
    }

    private static Optional<JsonElement> parseJson(CandidateFile candidate) {
        try {
            return Optional.of(JsonParser.parseString(candidate.content()));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to parse TaCZ audit JSON {}", candidate.sourcePath(), exception);
            return Optional.empty();
        }
    }

    private static PathInfo pathInfo(String path) {
        String normalized = path.replace('\\', '/');
        String[] parts = normalized.split("/");
        Optional<String> namespace = Optional.empty();
        Optional<String> indexKind = Optional.empty();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("index") && i > 1 && parts[i - 2].equals("data") && i + 1 < parts.length) {
                namespace = Optional.of(parts[i - 1]);
                indexKind = Optional.of(parts[i + 1]);
                break;
            }
        }
        String fileName = parts.length == 0 ? normalized : parts[parts.length - 1];
        return new PathInfo(namespace, indexKind, fileName);
    }

    private static String normalizedKey(String kind, String variant) {
        return switch (kind) {
            case "guns" -> "tacz:modern_kinetic_gun#" + variant;
            case "ammo" -> "tacz:ammo#" + variant;
            case "attachments" -> "tacz:attachment#" + variant;
            default -> "tacz:" + kind + "#" + variant;
        };
    }

    private static TaczContentEntry preferContentEntry(TaczContentEntry left, TaczContentEntry right) {
        boolean leftArchive = left.sourcePath().contains("!/");
        boolean rightArchive = right.sourcePath().contains("!/");
        if (leftArchive != rightArchive) {
            return leftArchive ? right : left;
        }
        return left;
    }

    private static void appendSearchLocations(StringBuilder report, List<SearchLocation> locations) {
        report.append("A. Search locations\n");
        for (SearchLocation location : locations) {
            report.append("- ").append(location.label())
                    .append(" | path=").append(location.path())
                    .append(" | exists=").append(location.exists())
                    .append(" | fileCount=").append(location.fileCount())
                    .append(" | matchingFiles=").append(location.matchingFiles())
                    .append('\n');
        }
        report.append('\n');
    }

    private static void appendCandidateSamples(StringBuilder report, List<CandidateFile> candidates) {
        report.append("Matched JSON candidate samples (").append(candidates.size()).append(" total)\n");
        if (candidates.isEmpty()) {
            report.append("- none. If TaCZ content still fails to discover, check the runtime diagnostics and search locations above for the actual game/mod paths.\n\n");
            return;
        }
        candidates.stream()
                .map(CandidateFile::sourcePath)
                .distinct()
                .sorted()
                .limit(30)
                .forEach(path -> report.append("- ").append(path).append('\n'));
        report.append('\n');
    }

    private static void appendUnknownJsonFiles(StringBuilder report, List<UnknownJsonFile> files) {
        report.append("Unparsed/unknown matched JSON files (").append(files.size()).append(")\n");
        if (files.isEmpty()) {
            report.append("- none\n\n");
            return;
        }
        files.stream().limit(80).forEach(file -> report.append("- ").append(file.sourcePath())
                .append(" | reason=").append(file.reason())
                .append(file.topLevelKeys().isBlank() ? "" : " | topLevelKeys=[" + file.topLevelKeys() + "]")
                .append('\n'));
        if (files.size() > 80) {
            report.append("- ... ").append(files.size() - 80).append(" more omitted for readability\n");
        }
        report.append('\n');
    }

    private static void appendRuntimeDiagnostics(StringBuilder report, RuntimeScanContext runtime) {
        report.append("Runtime path diagnostics\n");
        report.append("- current working directory: ").append(runtime.cwd()).append('\n');
        report.append("- FML game directory: ").append(runtime.gameDir()).append(" | exists=").append(Files.exists(runtime.gameDir())).append('\n');
        report.append("- FML config directory: ").append(runtime.configDir()).append(" | exists=").append(Files.exists(runtime.configDir())).append('\n');
        report.append("- resolved project/root candidates:\n");
        if (runtime.projectRoots().isEmpty()) {
            report.append("  - none\n");
        } else {
            runtime.projectRoots().forEach(root -> report.append("  - ").append(root).append(" | exists=").append(Files.exists(root)).append('\n'));
        }
        report.append("- loaded mod IDs/files containing TaCZ-ish identifiers:\n");
        List<LoadedModFile> relevant = runtime.loadedMods().stream().filter(LoadedModFile::relevant).toList();
        if (relevant.isEmpty()) {
            report.append("  - none found through ModList.get(); filesystem scan roots below are still used.\n");
        } else {
            for (LoadedModFile mod : relevant) {
                report.append("  - modIds=").append(mod.modIds())
                        .append(" | display=").append(mod.displayName())
                        .append(" | path=").append(mod.path())
                        .append(" | exists=").append(mod.path() != null && Files.exists(mod.path()))
                        .append('\n');
            }
        }
        report.append('\n');
    }

    private static void appendContentSection(StringBuilder report, String title, String requestedKind, List<TaczContentEntry> content, Map<String, ItemValueEntry> valuesByLookup) {
        List<TaczContentEntry> entries = content.stream()
                .filter(entry -> requestedKind.equals("other") ? !List.of("guns", "ammo", "attachments").contains(entry.kind()) : entry.kind().equals(requestedKind))
                .sorted(Comparator.comparing(TaczContentEntry::normalizedKey))
                .toList();
        report.append(title).append(" (").append(entries.size()).append(")\n");
        if (entries.isEmpty()) {
            report.append("- none\n\n");
            return;
        }
        for (TaczContentEntry entry : entries) {
            boolean hasValue = ItemValueRegistry.get(entry.normalizedKey()).isPresent();
            boolean hasProfile = ItemCarryProfileRegistry.get(entry.normalizedKey()).isPresent();
            Optional<ItemValueEntry> value = Optional.ofNullable(valuesByLookup.get(entry.normalizedKey()));
            report.append("- ").append(entry.normalizedKey())
                    .append(" | display=").append(entry.displayName())
                    .append(" | type=").append(entry.type())
                    .append(" | source=").append(entry.sourcePath())
                    .append(" | hasValue=").append(hasValue ? "yes" : "no")
                    .append(" | hasProfile=").append(hasProfile ? "yes" : "no")
                    .append(" | firstClassLoot=").append(value.filter(RaidContainerService::isFirstClassTaczLoot).isPresent() ? "yes" : "no")
                    .append(" | legacyFallback=").append(value.isPresent() && !RaidContainerService.isFirstClassTaczLoot(value.get()) ? "yes" : "no");
            if (!entry.metadata().isBlank()) {
                report.append(" | ").append(entry.metadata());
            }
            report.append('\n');
        }
        report.append('\n');
    }

    private static void appendRecipeSection(StringBuilder report, List<TaczRecipe> recipes) {
        report.append("F. TaCZ recipes (").append(recipes.size()).append(")\n");
        if (recipes.isEmpty()) {
            report.append("- none found. See section A for every searched location and matching file count.\n\n");
            return;
        }
        for (TaczRecipe recipe : recipes) {
            report.append("- ").append(recipe.recipeId())
                    .append(" | type=").append(recipe.recipeType())
                    .append(" | output=").append(recipe.outputKey())
                    .append(" (").append(recipe.outputDisplayName()).append(") x").append(recipe.outputCount())
                    .append(" | source=").append(recipe.sourcePath())
                    .append(" | ingredients=");
            String direct = recipe.ingredients().stream().map(ingredient -> ingredient.itemId() + " x" + ingredient.count()).collect(Collectors.joining(", "));
            String tags = recipe.tags().stream().map(tag -> "#" + tag).collect(Collectors.joining(", "));
            String materials = Stream.of(direct, tags).filter(text -> !text.isBlank()).collect(Collectors.joining(", "));
            report.append(materials.isBlank() ? "none" : materials).append('\n');
        }
        report.append('\n');
    }

    private static void appendIngredientSection(StringBuilder report, List<TaczRecipe> recipes, Set<String> spawnableLookupKeys, Set<String> spawnableBaseIds) {
        Map<String, Integer> directCounts = new TreeMap<>();
        Map<String, Long> directRecipeUses = new TreeMap<>();
        Map<String, Long> tagUses = new TreeMap<>();
        for (TaczRecipe recipe : recipes) {
            Set<String> directInRecipe = new TreeSet<>();
            for (Ingredient ingredient : recipe.ingredients()) {
                directCounts.merge(ingredient.itemId(), ingredient.count(), Integer::sum);
                directInRecipe.add(ingredient.itemId());
            }
            directInRecipe.forEach(id -> directRecipeUses.merge(id, 1L, Long::sum));
            recipe.tags().forEach(tag -> tagUses.merge(tag, 1L, Long::sum));
        }

        report.append("G. Aggregate recipe ingredients\n");
        report.append("- direct ingredient item IDs: ").append(directCounts.size()).append('\n');
        report.append("- ingredient tags: ").append(tagUses.size()).append('\n');
        appendIngredientAvailability(report, "vanilla recipe ingredients already spawnable in raid", directCounts.keySet().stream().filter(id -> id.startsWith("minecraft:")).filter(id -> isSpawnable(id, spawnableLookupKeys, spawnableBaseIds)).toList(), directCounts, directRecipeUses);
        appendIngredientAvailability(report, "vanilla recipe ingredients missing from raid loot", directCounts.keySet().stream().filter(id -> id.startsWith("minecraft:")).filter(id -> !isSpawnable(id, spawnableLookupKeys, spawnableBaseIds)).toList(), directCounts, directRecipeUses);
        appendIngredientAvailability(report, "non-vanilla recipe ingredients already spawnable", directCounts.keySet().stream().filter(id -> !id.startsWith("minecraft:")).filter(id -> isSpawnable(id, spawnableLookupKeys, spawnableBaseIds)).toList(), directCounts, directRecipeUses);
        appendIngredientAvailability(report, "non-vanilla recipe ingredients missing", directCounts.keySet().stream().filter(id -> !id.startsWith("minecraft:")).filter(id -> !isSpawnable(id, spawnableLookupKeys, spawnableBaseIds)).toList(), directCounts, directRecipeUses);
        report.append("- tags requiring manual review:\n");
        if (tagUses.isEmpty()) {
            report.append("  - none\n");
        } else {
            tagUses.forEach((tag, count) -> report.append("  - #").append(tag)
                    .append(" | usedByRecipes=").append(count)
                    .append(COMMON_TAG_EXAMPLES.containsKey(tag) ? " | example=" + COMMON_TAG_EXAMPLES.get(tag) : "")
                    .append(COMMON_TAG_EXAMPLES.containsKey(tag) ? " | exampleSpawnable=" + (isSpawnable(COMMON_TAG_EXAMPLES.get(tag), spawnableLookupKeys, spawnableBaseIds) ? "yes" : "no") : "")
                    .append('\n'));
        }
        report.append('\n');
    }

    private static void appendIngredientAvailability(StringBuilder report, String title, List<String> ids, Map<String, Integer> countTotals, Map<String, Long> recipeUses) {
        report.append("- ").append(title).append(" (").append(ids.size()).append("):\n");
        if (ids.isEmpty()) {
            report.append("  - none\n");
            return;
        }
        for (String id : ids) {
            report.append("  - ").append(id)
                    .append(" | totalCount=").append(countTotals.getOrDefault(id, 0))
                    .append(" | usedByRecipes=").append(recipeUses.getOrDefault(id, 0L))
                    .append('\n');
        }
    }

    private static void appendSpawnableSection(StringBuilder report, List<ItemValueEntry> spawnable) {
        report.append("H. Current full raid-spawnable item list (").append(spawnable.size()).append(")\n");
        Map<String, List<ItemValueEntry>> grouped = spawnable.stream().collect(Collectors.groupingBy(TaczAuditReport::spawnPath, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ItemValueEntry>> group : grouped.entrySet()) {
            report.append(group.getKey()).append(" (").append(group.getValue().size()).append(")\n");
            for (ItemValueEntry entry : group.getValue().stream().sorted(TaczAuditReport::compareEntries).toList()) {
                Optional<ItemCarryProfile> profile = ItemCarryProfileRegistry.get(entry.lookupKey()).or(() -> ItemCarryProfileRegistry.get(entry.itemId()));
                report.append("  - ").append(entry.lookupKey())
                        .append(" | display=").append(TaczDisplayNameResolver.displayName(entry.lookupKey(), BuiltInRegistries.ITEM.get(entry.itemId()).getDescription().getString()))
                        .append(" | itemId=").append(entry.itemId())
                        .append(" | category=").append(entry.category().name().toLowerCase(Locale.ROOT))
                        .append(" | rarity=").append(entry.rarity().name().toLowerCase(Locale.ROOT))
                        .append(" | value=").append(entry.value());
                profile.ifPresent(itemCarryProfile -> report.append(" | weight=").append(String.format(Locale.ROOT, "%.2f", itemCarryProfile.weight()))
                        .append(" | grid=").append(itemCarryProfile.gridWidth().orElse(1)).append('x').append(itemCarryProfile.gridHeight().orElse(1)));
                report.append('\n');
            }
        }
        report.append('\n');
    }

    private static void appendSuggestions(StringBuilder report, List<TaczContentEntry> content, List<TaczRecipe> recipes, Set<String> spawnableLookupKeys, Set<String> spawnableBaseIds) {
        report.append("I. Suggestions for later tuning only (not applied)\n");
        List<TaczContentEntry> missingProfiles = content.stream()
                .filter(entry -> ItemValueRegistry.get(entry.normalizedKey()).isEmpty() || ItemCarryProfileRegistry.get(entry.normalizedKey()).isEmpty())
                .sorted(Comparator.comparing(TaczContentEntry::kind).thenComparing(TaczContentEntry::normalizedKey))
                .toList();
        report.append("- TaCZ entries missing ExtractCraft value/carry profiles: ").append(missingProfiles.size()).append('\n');
        missingProfiles.stream().limit(80).forEach(entry -> report.append("  - ").append(entry.normalizedKey())
                .append(" | display=").append(entry.displayName())
                .append(" | kind=").append(entry.kind())
                .append(" | suggestedContext=").append(suggestedContext(entry))
                .append('\n'));
        Set<String> missingTagExamples = recipes.stream()
                .flatMap(recipe -> recipe.tags().stream())
                .map(COMMON_TAG_EXAMPLES::get)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !isSpawnable(id, spawnableLookupKeys, spawnableBaseIds))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missingTagExamples.isEmpty()) {
            report.append("- Vanilla tag-example ingredients to consider adding to raid loot: ").append(String.join(", ", missingTagExamples)).append('\n');
        }
        report.append("- Military: TaCZ guns, ammo, attachments, weapon parts, optics, armor materials.\n");
        report.append("- Generic: common ammo, lower-tier attachments, tools, survival, blue loose loot.\n");
        report.append("- Safe: rare compact attachments, restricted intel, red/gold valuables, compact high-value TaCZ parts.\n");
        report.append("- Industrial: recipe ingredients, copper/iron/gold materials, gunpowder-adjacent crafting materials.\n");
        report.append("- Office: intel/access/electronics/security items; only small rare TaCZ parts if desired.\n");
        report.append("- Medical: medical_tech/survival/tools; no TaCZ unless future balancing says otherwise.\n");
    }

    private static String suggestedContext(TaczContentEntry entry) {
        return switch (entry.kind()) {
            case "guns", "ammo", "attachments" -> "military";
            case "modifiers", "parts" -> "military/industrial";
            default -> "generic";
        };
    }

    private static boolean isSpawnable(String id, Set<String> spawnableLookupKeys, Set<String> spawnableBaseIds) {
        return spawnableLookupKeys.contains(id) || spawnableBaseIds.contains(id);
    }

    private static String spawnPath(ItemValueEntry entry) {
        if (entry.itemId().getNamespace().equals(ExtractCraft.MODID) && Set.of(ItemRarity.BLUE, ItemRarity.PURPLE, ItemRarity.GOLD, ItemRarity.RED).contains(entry.rarity())) {
            return "loose_loot_first_class";
        }
        if (RaidContainerService.isFirstClassTaczLoot(entry)) {
            return "tacz_first_class";
        }
        if (entry.itemId().getNamespace().equals("minecraft")) {
            return "vanilla_fallback";
        }
        if (entry.itemId().getNamespace().equals(ExtractCraft.MODID)) {
            return "extractcraft_equipment_med_repair_fallback";
        }
        return "other_fallback";
    }

    private static String fileStem(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§.", "");
    }

    private static Optional<String> optionalString(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return Optional.empty();
        }
        return safeString(object.get(name));
    }

    private static Optional<Integer> optionalInt(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return Optional.empty();
        }
        JsonElement element = object.get(name);
        if (!element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsInt());
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> safeString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (element.isJsonPrimitive()) {
            try {
                return Optional.of(element.getAsString());
            } catch (Exception exception) {
                return Optional.empty();
            }
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return optionalString(object, "id")
                    .or(() -> optionalString(object, "item"))
                    .or(() -> optionalString(object, "tag"))
                    .or(() -> optionalString(object, "type"));
        }
        return Optional.empty();
    }

    private static String jsonKind(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonObject()) {
            return "object";
        }
        if (element.isJsonArray()) {
            return "array";
        }
        if (element.isJsonPrimitive()) {
            return "primitive";
        }
        return "unknown";
    }

    private static int compareEntries(ItemValueEntry left, ItemValueEntry right) {
        return Comparator.comparing((ItemValueEntry entry) -> entry.itemId().getNamespace())
                .thenComparing(ItemValueEntry::lookupKey)
                .compare(left, right);
    }

    private record ScanResult(RuntimeScanContext runtime, List<SearchLocation> locations, List<CandidateFile> candidates) {
    }

    private record RuntimeScanContext(Path cwd, Path gameDir, Path configDir, List<Path> projectRoots, List<LoadedModFile> loadedMods) {
    }

    private record LoadedModFile(String modIds, String displayName, Path path, boolean relevant) {
    }

    private record ScanRoot(String label, Path path) {
    }

    private record SearchLocation(String label, String path, boolean exists, int fileCount, int matchingFiles) {
    }

    private record ArchiveScanResult(int fileCount, int matchingFiles) {
    }

    private record CandidateFile(String sourcePath, String relativePath, String content) {
    }

    private record PathInfo(Optional<String> namespace, Optional<String> indexKind, String fileName) {
    }

    private record TaczContentEntry(String normalizedKey, String kind, String displayName, String sourcePath, String type, String metadata) {
    }

    private record Ingredient(String itemId, int count) {
    }

    private record Output(String normalizedKey, String displayName, int count) {
    }

    private record TaczRecipe(String recipeId, String recipeType, String outputKey, String outputDisplayName, int outputCount, List<Ingredient> ingredients, List<String> tags, String sourcePath) {
    }

    private record UnknownJsonFile(String sourcePath, String reason, String topLevelKeys) {
    }
}
