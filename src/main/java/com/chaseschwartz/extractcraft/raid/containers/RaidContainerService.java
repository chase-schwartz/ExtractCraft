package com.chaseschwartz.extractcraft.raid.containers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentity;
import com.chaseschwartz.extractcraft.itemidentity.ItemIdentityResolver;
import com.chaseschwartz.extractcraft.itemidentity.ItemStackVariantFactory;
import com.chaseschwartz.extractcraft.itemvalues.ItemCategory;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueEntry;
import com.chaseschwartz.extractcraft.itemvalues.ItemValueRegistry;
import com.chaseschwartz.extractcraft.itemvalues.ItemRarity;
import com.chaseschwartz.extractcraft.raid.map.RaidDevBounds;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RaidContainerService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CLUSTER_RADIUS = 7;
    private static final int CLUSTER_CELL_SIZE = 24;
    private static final int MAX_CLUSTER_SPAN_XZ = 16;
    private static final int MAX_CLUSTER_SPAN_Y = 8;
    private static final int MAX_CONTAINER_SLOTS_TO_FILL = 6;
    private static final int LOOT_LIGHT_LEVEL = 8;
    private static final int AMBIENT_LIGHT_LEVEL = 5;
    private static final int AMBIENT_GRID_XZ = 10;
    private static final int AMBIENT_GRID_Y = 5;
    private static final List<ResourceLocation> DENYLIST = List.of(
            ResourceLocation.withDefaultNamespace("hopper"),
            ResourceLocation.withDefaultNamespace("furnace"),
            ResourceLocation.withDefaultNamespace("blast_furnace"),
            ResourceLocation.withDefaultNamespace("smoker"),
            ResourceLocation.withDefaultNamespace("brewing_stand"),
            ResourceLocation.withDefaultNamespace("dropper"),
            ResourceLocation.withDefaultNamespace("dispenser"),
            ResourceLocation.withDefaultNamespace("crafter"),
            ResourceLocation.withDefaultNamespace("chiseled_bookshelf"),
            ResourceLocation.withDefaultNamespace("decorated_pot"),
            ResourceLocation.withDefaultNamespace("lectern"),
            ResourceLocation.withDefaultNamespace("jukebox"),
            ResourceLocation.withDefaultNamespace("shulker_box"),
            ResourceLocation.fromNamespaceAndPath("horror_element_mod", "sound_block"),
            ResourceLocation.fromNamespaceAndPath("horror_element_mod", "atmosphere_block"),
            ResourceLocation.fromNamespaceAndPath("refurbished_furniture", "frying_pan"));

    private RaidContainerService() {
    }

    public static RaidContainerLayout scan(ServerLevel level, RaidMapDefinition raidMap) {
        RaidDevBounds bounds = scanBounds(raidMap);
        List<RaidContainerEntry> containers = new ArrayList<>();
        int unsupportedBlockEntities = 0;
        Map<ResourceLocation, Integer> deniedCounts = new HashMap<>();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity == null) {
                        continue;
                    }

                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                    if (isDenied(blockId)) {
                        deniedCounts.merge(blockId, 1, Integer::sum);
                        continue;
                    }

                    if (blockEntity instanceof Container) {
                        containers.add(new RaidContainerEntry(level.dimension().location(), pos, blockId, "scanned", Optional.empty(), false, Optional.empty(), Optional.empty(), Map.of()));
                    } else {
                        unsupportedBlockEntities++;
                    }
                }
            }
        }

        ExtractCraft.LOGGER.info("Scanned raid map {} for containers: {} detected, {} denied, {} unsupported block entities skipped",
                raidMap.id(), containers.size(), deniedCounts.values().stream().mapToInt(Integer::intValue).sum(), unsupportedBlockEntities);
        deniedCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> ExtractCraft.LOGGER.info("Denied raid container candidate {} x{}", entry.getKey(), entry.getValue()));
        return new RaidContainerLayout(raidMap.id(), raidMap.source().layoutOrigin(), assignClusterIds(containers));
    }

    public static RaidContainerLayout selectActiveLootContainers(RaidContainerLayout layout) {
        List<List<RaidContainerEntry>> clusters = cluster(layout.containers());
        List<RaidContainerEntry> selected = new ArrayList<>();
        int clusterNumber = 1;
        for (List<RaidContainerEntry> cluster : clusters) {
            String clusterId = "cluster_" + clusterNumber++;
            int activeLimit = activeLimit(cluster.size());
            for (int i = 0; i < cluster.size(); i++) {
                RaidContainerEntry entry = cluster.get(i);
                boolean active = i < activeLimit;
                int lootTier = active ? lootTierForCluster(cluster.size()) : 0;
                String lootTableId = active ? "extractcraft:prototype/common" : "";
                selected.add(entry.withSelection(clusterId, active, lootTableId, lootTier));
            }
        }

        ExtractCraft.LOGGER.info("Selected active loot containers for {}: {} active out of {} detected across {} clusters",
                layout.mapId(),
                selected.stream().filter(RaidContainerEntry::activeLootContainer).count(),
                selected.size(),
                clusters.size());
        return new RaidContainerLayout(layout.mapId(), layout.layoutOrigin(), sortContainers(selected));
    }

    public static PopulateResult populate(ServerLevel level, RaidMapDefinition raidMap, RaidContainerLayout layout) {
        Random random = new Random(System.nanoTime() ^ level.getGameTime() ^ raidMap.id().hashCode());
        List<ItemValueEntry> pool = ItemValueRegistry.entries().stream()
                .filter(ItemValueEntry::sellable)
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.itemId()))
                .filter(entry -> !ItemStackVariantFactory.isUnsafeBareVariantBase(entry.itemId()) || entry.lookupKey().contains("#"))
                .toList();
        if (pool.isEmpty()) {
            return new PopulateResult(0, 0, "No sellable item values are loaded.");
        }

        int populated = 0;
        int skipped = 0;
        for (RaidContainerEntry entry : layout.containers()) {
            if (!entry.activeLootContainer()) {
                continue;
            }

            if (!isInside(scanBounds(raidMap), entry.pos())) {
                skipped++;
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(entry.pos());
            if (!(blockEntity instanceof Container container)) {
                skipped++;
                continue;
            }

            clear(container);
            fill(container, pool, entry, random);
            container.setChanged();
            populated++;
        }

        ExtractCraft.LOGGER.info("Populated {} active raid containers for {} ({} skipped)", populated, raidMap.id(), skipped);
        return new PopulateResult(populated, skipped, "");
    }

    public static List<FindLootResult> findLoot(ServerLevel level, RaidMapDefinition raidMap, RaidContainerLayout layout, String query, int limit) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<FindLootResult> results = new ArrayList<>();
        RaidDevBounds bounds = scanBounds(raidMap);

        for (RaidContainerEntry entry : layout.containers()) {
            if (!entry.activeLootContainer() || !isInside(bounds, entry.pos())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(entry.pos());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }

            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                ItemIdentity identity = ItemIdentityResolver.resolve(stack);
                if (!matches(identity, normalizedQuery)) {
                    continue;
                }

                results.add(new FindLootResult(entry.pos(), entry.blockId(), stack.getHoverName().getString(), identity.baseItemId(), identity.variantId(), identity.normalizedKey(), stack.getCount()));
                if (results.size() >= limit) {
                    ExtractCraft.LOGGER.info("Loot finder for {} query '{}' hit result limit {}", raidMap.id(), query, limit);
                    return results;
                }
            }
        }

        ExtractCraft.LOGGER.info("Loot finder for {} query '{}' found {} matches", raidMap.id(), query, results.size());
        return results;
    }

    public static LightPassResult lightPass(ServerLevel level, RaidMapDefinition raidMap, RaidContainerLayout layout) {
        RaidDevBounds bounds = scanBounds(raidMap);
        int placed = 0;
        int ambientPlaced = 0;
        int skipped = 0;
        for (RaidContainerEntry entry : layout.containers()) {
            if (!isInside(bounds, entry.pos())) {
                continue;
            }

            Optional<BlockPos> lightPos = lightPositionFor(level, bounds, entry.pos(), entry.activeLootContainer());
            if (lightPos.isEmpty()) {
                if (entry.activeLootContainer()) {
                    skipped++;
                }
                continue;
            }

            int lightLevel = entry.activeLootContainer() ? LOOT_LIGHT_LEVEL : AMBIENT_LIGHT_LEVEL;
            BlockState currentState = level.getBlockState(lightPos.get());
            if (currentState.is(Blocks.LIGHT) && currentState.getValue(LightBlock.LEVEL) >= lightLevel) {
                continue;
            }

            level.setBlock(lightPos.get(), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel), 3);
            placed++;
        }

        ambientPlaced = placeAmbientInteriorLights(level, bounds);
        placed += ambientPlaced;

        ExtractCraft.LOGGER.info("Placed {} loot/container light blocks and {} ambient navigation light blocks for {} ({} active containers skipped)",
                placed - ambientPlaced, ambientPlaced, raidMap.id(), skipped);
        return new LightPassResult(placed, 0, skipped);
    }

    public static LightPassResult clearLightPass(ServerLevel level, RaidMapDefinition raidMap, RaidContainerLayout layout) {
        RaidDevBounds bounds = scanBounds(raidMap);
        int removed = 0;
        for (RaidContainerEntry entry : layout.containers()) {
            if (!entry.activeLootContainer() || !isInside(bounds, entry.pos())) {
                continue;
            }

            BlockPos pos = entry.pos();
            for (int x = pos.getX() - 2; x <= pos.getX() + 2; x++) {
                for (int y = pos.getY(); y <= pos.getY() + 3; y++) {
                    for (int z = pos.getZ() - 2; z <= pos.getZ() + 2; z++) {
                        BlockPos lightPos = new BlockPos(x, y, z);
                        if (isInside(bounds, lightPos) && level.getBlockState(lightPos).is(Blocks.LIGHT)) {
                            level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
                            removed++;
                        }
                    }
                }
            }
        }

        ExtractCraft.LOGGER.info("Removed {} loot light blocks for {}", removed, raidMap.id());
        return new LightPassResult(0, removed, 0);
    }

    public static Optional<RaidContainerLayout> load(String mapId) {
        Path path = layoutPath(mapId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            return Optional.of(parseLayout(GSON.fromJson(reader, JsonObject.class)));
        } catch (Exception exception) {
            ExtractCraft.LOGGER.warn("Failed to load raid container layout {}", path, exception);
            return Optional.empty();
        }
    }

    public static void save(RaidContainerLayout layout) throws IOException {
        Path path = layoutPath(layout.mapId());
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(toJson(layout), writer);
        }
    }

    public static boolean clearSaved(String mapId) throws IOException {
        return Files.deleteIfExists(layoutPath(mapId));
    }

    public static Path layoutPath(String mapId) {
        return Path.of("run", "extractcraft", "raid_maps", mapId + "_containers.json");
    }

    public static List<String> savedMapIds() {
        Path directory = Path.of("run", "extractcraft", "raid_maps");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (var paths = Files.list(directory)) {
            return paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("_containers.json"))
                    .map(name -> name.substring(0, name.length() - "_containers.json".length()))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            ExtractCraft.LOGGER.warn("Failed to list saved raid container layouts in {}", directory, exception);
            return List.of();
        }
    }

    public static Map<ResourceLocation, Long> countByBlock(RaidContainerLayout layout) {
        return layout.containers().stream()
                .collect(Collectors.groupingBy(RaidContainerEntry::blockId, LinkedHashMap::new, Collectors.counting()));
    }

    public static Map<String, Long> countByCluster(RaidContainerLayout layout) {
        return layout.containers().stream()
                .collect(Collectors.groupingBy(entry -> entry.clusterId().orElse("unclustered"), LinkedHashMap::new, Collectors.counting()));
    }

    public static boolean isActiveLootContainer(RaidContainerLayout layout, BlockPos pos) {
        return layout.containers().stream()
                .anyMatch(entry -> entry.activeLootContainer() && entry.pos().equals(pos));
    }

    private static RaidDevBounds scanBounds(RaidMapDefinition raidMap) {
        return raidMap.source().authoringBounds()
                .or(() -> raidMap.source().cleanupBounds())
                .orElseGet(() -> new RaidDevBounds(
                        raidMap.platform().minX(),
                        raidMap.platform().maxX(),
                        raidMap.platform().floorY(),
                        raidMap.platform().airMaxY(),
                        raidMap.platform().minZ(),
                        raidMap.platform().maxZ()));
    }

    private static boolean isDenied(ResourceLocation blockId) {
        return DENYLIST.contains(blockId) || isShulkerBox(blockId) || isDeniedRefurbishedFurniture(blockId);
    }

    private static boolean isShulkerBox(ResourceLocation blockId) {
        return "minecraft".equals(blockId.getNamespace()) && blockId.getPath().endsWith("_shulker_box");
    }

    private static boolean isDeniedRefurbishedFurniture(ResourceLocation blockId) {
        if (!"refurbished_furniture".equals(blockId.getNamespace())) {
            return false;
        }

        String path = blockId.getPath();
        return path.endsWith("_cutting_board")
                || path.endsWith("_toaster")
                || path.endsWith("_stove")
                || path.endsWith("_electricity_generator");
    }

    private static boolean isInside(RaidDevBounds bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private static List<RaidContainerEntry> sortContainers(List<RaidContainerEntry> containers) {
        return containers.stream()
                .sorted(Comparator.comparingInt((RaidContainerEntry entry) -> entry.pos().getX())
                        .thenComparingInt(entry -> entry.pos().getY())
                        .thenComparingInt(entry -> entry.pos().getZ()))
                .toList();
    }

    private static List<List<RaidContainerEntry>> cluster(List<RaidContainerEntry> containers) {
        Map<CellKey, List<RaidContainerEntry>> byCell = sortContainers(containers).stream()
                .collect(Collectors.groupingBy(
                        entry -> CellKey.from(entry.pos()),
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));

        List<List<RaidContainerEntry>> clusters = new ArrayList<>();
        for (List<RaidContainerEntry> cellEntries : byCell.values()) {
            List<List<RaidContainerEntry>> cellClusters = new ArrayList<>();
            for (RaidContainerEntry entry : cellEntries) {
                List<RaidContainerEntry> matchingCluster = null;
                for (List<RaidContainerEntry> cluster : cellClusters) {
                    if (canJoinCluster(cluster, entry)) {
                        matchingCluster = cluster;
                        break;
                    }
                }

                if (matchingCluster == null) {
                    matchingCluster = new ArrayList<>();
                    cellClusters.add(matchingCluster);
                }
                matchingCluster.add(entry);
            }
            clusters.addAll(cellClusters);
        }
        return clusters;
    }

    private static List<RaidContainerEntry> assignClusterIds(List<RaidContainerEntry> containers) {
        List<List<RaidContainerEntry>> clusters = cluster(containers);
        List<RaidContainerEntry> clustered = new ArrayList<>();
        int clusterNumber = 1;
        for (List<RaidContainerEntry> cluster : clusters) {
            String clusterId = "cluster_" + clusterNumber++;
            for (RaidContainerEntry entry : cluster) {
                clustered.add(entry.withSelection(clusterId, false, "", 0));
            }
        }
        return sortContainers(clustered);
    }

    private static boolean canJoinCluster(List<RaidContainerEntry> cluster, RaidContainerEntry entry) {
        if (cluster.stream().noneMatch(existing -> manhattan(existing.pos(), entry.pos()) <= CLUSTER_RADIUS)) {
            return false;
        }

        int minX = entry.pos().getX();
        int maxX = entry.pos().getX();
        int minY = entry.pos().getY();
        int maxY = entry.pos().getY();
        int minZ = entry.pos().getZ();
        int maxZ = entry.pos().getZ();
        for (RaidContainerEntry existing : cluster) {
            minX = Math.min(minX, existing.pos().getX());
            maxX = Math.max(maxX, existing.pos().getX());
            minY = Math.min(minY, existing.pos().getY());
            maxY = Math.max(maxY, existing.pos().getY());
            minZ = Math.min(minZ, existing.pos().getZ());
            maxZ = Math.max(maxZ, existing.pos().getZ());
        }

        return maxX - minX <= MAX_CLUSTER_SPAN_XZ
                && maxZ - minZ <= MAX_CLUSTER_SPAN_XZ
                && maxY - minY <= MAX_CLUSTER_SPAN_Y;
    }

    private static int manhattan(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    private static int activeLimit(int clusterSize) {
        if (clusterSize <= 1) {
            return 1;
        }
        if (clusterSize <= 3) {
            return 1;
        }
        if (clusterSize <= 6) {
            return 1 + deterministicExtra(clusterSize, 2);
        }
        if (clusterSize <= 12) {
            return 2 + deterministicExtra(clusterSize, 3);
        }
        return Math.min(5, 3 + deterministicExtra(clusterSize, 3));
    }

    private static int deterministicExtra(int clusterSize, int modulo) {
        return Math.floorMod(clusterSize * 31 + 7, modulo) == 0 ? 1 : 0;
    }

    private static int lootTierForCluster(int clusterSize) {
        if (clusterSize >= 12) {
            return 3;
        }
        if (clusterSize >= 5) {
            return 2;
        }
        return 1;
    }

    private static void clear(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, ItemStack.EMPTY);
        }
    }

    public static List<ItemValueEntry> sampleLooseLoot(String contextName, int count, Random random) {
        LootContext context = LootContext.fromName(contextName);
        List<ItemValueEntry> pool = lootPool();
        List<ItemValueEntry> samples = new ArrayList<>();
        for (int i = 0; i < Math.max(0, count); i++) {
            chooseLooseLoot(pool, context, Math.max(4, context.minimumTier()), random).ifPresent(samples::add);
        }
        return samples;
    }

    public static List<String> sampleLootContexts() {
        return List.of("generic", "safe", "military", "medical", "office", "industrial");
    }

    private static List<ItemValueEntry> lootPool() {
        return ItemValueRegistry.entries().stream()
                .filter(ItemValueEntry::sellable)
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.itemId()))
                .filter(entry -> !ItemStackVariantFactory.isUnsafeBareVariantBase(entry.itemId()) || entry.lookupKey().contains("#"))
                .toList();
    }

    private static void fill(Container container, List<ItemValueEntry> pool, RaidContainerEntry containerEntry, Random random) {
        int lootTier = containerEntry.lootTier().orElse(1);
        LootContext context = LootContext.fromContainer(containerEntry);
        int slotCount = container.getContainerSize();
        int itemCount = Math.min(MAX_CONTAINER_SLOTS_TO_FILL, Math.max(1, 1 + random.nextInt(Math.min(4, Math.max(1, lootTier + 1)))));
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < slotCount; slot++) {
            slots.add(slot);
        }

        for (int i = 0; i < itemCount && !slots.isEmpty(); i++) {
            int slotIndex = random.nextInt(slots.size());
            int slot = slots.remove(slotIndex);
            ItemValueEntry entry = choose(pool, context, lootTier, random);
            int count = countFor(entry, random);
            ItemStack stack = ItemStackVariantFactory.create(entry.lookupKey(), count)
                    .orElseGet(() -> new ItemStack(BuiltInRegistries.ITEM.get(entry.itemId()), count));
            container.setItem(slot, stack);
        }
    }

    private static ItemValueEntry choose(List<ItemValueEntry> pool, LootContext context, int lootTier, Random random) {
        if (random.nextInt(100) < context.looseLootChance()) {
            Optional<ItemValueEntry> looseLoot = chooseLooseLoot(pool, context, lootTier, random);
            if (looseLoot.isPresent()) {
                return looseLoot.get();
            }
        }
        return chooseLegacy(pool, lootTier, random);
    }

    private static ItemValueEntry chooseLegacy(List<ItemValueEntry> pool, int lootTier, Random random) {
        List<ItemValueEntry> filtered = pool.stream()
                .filter(entry -> entry.lootTier().orElse(1) <= Math.max(1, lootTier + 1))
                .filter(entry -> entry.category() != ItemCategory.GUNS || lootTier >= 3)
                .filter(entry -> !isLooseLoot(entry))
                .toList();
        if (filtered.isEmpty()) {
            filtered = pool.stream()
                    .filter(entry -> entry.lootTier().orElse(1) <= Math.max(1, lootTier + 1))
                    .filter(entry -> entry.category() != ItemCategory.GUNS || lootTier >= 3)
                    .toList();
        }

        int totalWeight = filtered.stream().mapToInt(RaidContainerService::weight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (ItemValueEntry entry : filtered) {
            roll -= weight(entry);
            if (roll < 0) {
                return entry;
            }
        }
        return filtered.getLast();
    }

    private static Optional<ItemValueEntry> chooseLooseLoot(List<ItemValueEntry> pool, LootContext context, int lootTier, Random random) {
        ItemRarity rarity = context.rollRarity(random);
        List<ItemValueEntry> candidates = looseLootCandidates(pool, rarity, context, lootTier);
        if (candidates.isEmpty()) {
            candidates = pool.stream()
                    .filter(RaidContainerService::isLooseLoot)
                    .filter(entry -> entry.rarity() == rarity)
                    .filter(entry -> entry.lootTier().orElse(1) <= Math.max(context.minimumTier(), lootTier + 2))
                    .toList();
        }
        if (candidates.isEmpty()) {
            candidates = pool.stream()
                    .filter(RaidContainerService::isLooseLoot)
                    .filter(entry -> entry.lootTier().orElse(1) <= Math.max(context.minimumTier(), lootTier + 2))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = candidates.stream().mapToInt(RaidContainerService::looseItemWeight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (ItemValueEntry entry : candidates) {
            roll -= looseItemWeight(entry);
            if (roll < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.of(candidates.getLast());
    }

    private static List<ItemValueEntry> looseLootCandidates(List<ItemValueEntry> pool, ItemRarity rarity, LootContext context, int lootTier) {
        int maxTier = Math.max(context.minimumTier(), lootTier + 2);
        return pool.stream()
                .filter(RaidContainerService::isLooseLoot)
                .filter(entry -> entry.rarity() == rarity)
                .filter(entry -> entry.lootTier().orElse(1) <= maxTier)
                .filter(entry -> context.categoryBias().isEmpty() || context.categoryBias().contains(entry.category()))
                .toList();
    }

    private static boolean isLooseLoot(ItemValueEntry entry) {
        return switch (entry.rarity()) {
            case BLUE, PURPLE, GOLD, RED -> entry.itemId().getNamespace().equals(ExtractCraft.MODID);
            default -> false;
        };
    }

    private static int looseItemWeight(ItemValueEntry entry) {
        int valuePenalty = Math.max(1, entry.value() / 2_500);
        int footprintPenalty = switch (entry.category()) {
            case INDUSTRIAL, ARMOR_MATERIALS -> 2;
            default -> 1;
        };
        return Math.max(1, 100 / valuePenalty / footprintPenalty);
    }

    private static int weight(ItemValueEntry entry) {
        return switch (entry.rarity()) {
            case COMMON -> 60;
            case UNCOMMON -> 30;
            case RARE, BLUE -> 10;
            case EPIC, PURPLE -> 3;
            case LEGENDARY, GOLD, RED -> 1;
            case QUEST -> 0;
        };
    }

    private static int countFor(ItemValueEntry entry, Random random) {
        if (isLooseLoot(entry)) {
            return 1;
        }
        return switch (entry.category()) {
            case AMMO -> 4 + random.nextInt(13);
            case FOOD, JUNK, SCRAP_METAL -> 1 + random.nextInt(4);
            default -> 1;
        };
    }

    private enum LootContext {
        GENERIC(
                60,
                2,
                Map.of(ItemRarity.BLUE, 70, ItemRarity.PURPLE, 22, ItemRarity.GOLD, 7, ItemRarity.RED, 1),
                List.of()),
        HIGH_VALUE(
                70,
                4,
                Map.of(ItemRarity.BLUE, 35, ItemRarity.PURPLE, 35, ItemRarity.GOLD, 24, ItemRarity.RED, 6),
                List.of(ItemCategory.INTEL, ItemCategory.ELECTRONICS, ItemCategory.CONTRABAND, ItemCategory.TROPHY, ItemCategory.SECURITY, ItemCategory.ACCESS)),
        MILITARY(
                65,
                3,
                Map.of(ItemRarity.BLUE, 55, ItemRarity.PURPLE, 30, ItemRarity.GOLD, 13, ItemRarity.RED, 2),
                List.of(ItemCategory.WEAPON_PARTS, ItemCategory.OPTICS, ItemCategory.ARMOR_MATERIALS, ItemCategory.ELECTRONICS, ItemCategory.SECURITY, ItemCategory.ACCESS)),
        MEDICAL(
                55,
                2,
                Map.of(ItemRarity.BLUE, 70, ItemRarity.PURPLE, 25, ItemRarity.GOLD, 5, ItemRarity.RED, 0),
                List.of(ItemCategory.MEDICAL_TECH, ItemCategory.SURVIVAL, ItemCategory.TOOLS)),
        OFFICE(
                60,
                2,
                Map.of(ItemRarity.BLUE, 65, ItemRarity.PURPLE, 27, ItemRarity.GOLD, 7, ItemRarity.RED, 1),
                List.of(ItemCategory.INTEL, ItemCategory.ACCESS, ItemCategory.ELECTRONICS, ItemCategory.SECURITY)),
        INDUSTRIAL(
                55,
                2,
                Map.of(ItemRarity.BLUE, 75, ItemRarity.PURPLE, 20, ItemRarity.GOLD, 5, ItemRarity.RED, 0),
                List.of(ItemCategory.TOOLS, ItemCategory.INDUSTRIAL, ItemCategory.ELECTRONICS, ItemCategory.POWER));

        private final int looseLootChance;
        private final int minimumTier;
        private final Map<ItemRarity, Integer> rarityWeights;
        private final List<ItemCategory> categoryBias;

        LootContext(int looseLootChance, int minimumTier, Map<ItemRarity, Integer> rarityWeights, List<ItemCategory> categoryBias) {
            this.looseLootChance = looseLootChance;
            this.minimumTier = minimumTier;
            this.rarityWeights = rarityWeights;
            this.categoryBias = List.copyOf(categoryBias);
        }

        private int looseLootChance() {
            return looseLootChance;
        }

        private int minimumTier() {
            return minimumTier;
        }

        private List<ItemCategory> categoryBias() {
            return categoryBias;
        }

        private ItemRarity rollRarity(Random random) {
            int total = rarityWeights.values().stream().mapToInt(Integer::intValue).sum();
            int roll = random.nextInt(Math.max(1, total));
            for (Map.Entry<ItemRarity, Integer> entry : rarityWeights.entrySet()) {
                roll -= entry.getValue();
                if (roll < 0) {
                    return entry.getKey();
                }
            }
            return ItemRarity.BLUE;
        }

        private static LootContext fromContainer(RaidContainerEntry entry) {
            String text = (entry.blockId() + " " + entry.sourceType() + " " + entry.lootTableId().orElse("") + " " + entry.metadata()).toLowerCase(Locale.ROOT);
            if (containsAny(text, "safe", "vault", "lock", "secure", "strongbox", "cash", "valuable")) {
                return HIGH_VALUE;
            }
            if (containsAny(text, "weapon", "gun", "ammo", "military", "armory", "locker")) {
                return MILITARY;
            }
            if (containsAny(text, "medical", "med", "clinic", "first_aid", "hospital")) {
                return MEDICAL;
            }
            if (containsAny(text, "filing", "cabinet", "desk", "office", "bookshelf", "mail")) {
                return OFFICE;
            }
            if (containsAny(text, "tool", "industrial", "garage", "workbench", "crate", "barrel")) {
                return INDUSTRIAL;
            }
            return GENERIC;
        }

        private static LootContext fromName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "safe", "vault", "high_value", "highvalue" -> HIGH_VALUE;
                case "military", "weapon", "weapons" -> MILITARY;
                case "medical", "med" -> MEDICAL;
                case "office", "filing", "filing_cabinet" -> OFFICE;
                case "industrial", "tool", "tools" -> INDUSTRIAL;
                default -> GENERIC;
            };
        }

        private static boolean containsAny(String text, String... needles) {
            for (String needle : needles) {
                if (text.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean matches(ItemIdentity identity, String normalizedQuery) {
        return identity.baseItemId().toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || identity.normalizedKey().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || identity.variantId().map(variant -> variant.toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)).orElse(false)
                || identity.displayName().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static JsonObject toJson(RaidContainerLayout layout) {
        JsonObject root = new JsonObject();
        root.addProperty("mapId", layout.mapId());
        root.add("layoutOrigin", posToJson(layout.layoutOrigin()));
        JsonArray containers = new JsonArray();
        for (RaidContainerEntry entry : layout.containers()) {
            JsonObject object = new JsonObject();
            object.addProperty("dimension", entry.dimensionId().toString());
            object.add("pos", posToJson(entry.pos()));
            object.addProperty("block", entry.blockId().toString());
            object.addProperty("sourceType", entry.sourceType());
            entry.clusterId().ifPresent(clusterId -> object.addProperty("clusterId", clusterId));
            object.addProperty("activeLootContainer", entry.activeLootContainer());
            entry.lootTableId().ifPresent(lootTableId -> object.addProperty("lootTableId", lootTableId));
            entry.lootTier().ifPresent(lootTier -> object.addProperty("lootTier", lootTier));
            if (!entry.metadata().isEmpty()) {
                JsonObject metadata = new JsonObject();
                entry.metadata().forEach(metadata::addProperty);
                object.add("metadata", metadata);
            }
            containers.add(object);
        }
        root.add("containers", containers);
        return root;
    }

    private static RaidContainerLayout parseLayout(JsonObject root) {
        String mapId = root.get("mapId").getAsString();
        BlockPos layoutOrigin = jsonToPos(root.getAsJsonObject("layoutOrigin"));
        List<RaidContainerEntry> containers = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("containers")) {
            JsonObject object = element.getAsJsonObject();
            JsonObject metadataObject = object.has("metadata") ? object.getAsJsonObject("metadata") : new JsonObject();
            Map<String, String> metadata = new HashMap<>();
            metadataObject.entrySet().forEach(entry -> metadata.put(entry.getKey(), entry.getValue().getAsString()));
            containers.add(new RaidContainerEntry(
                    ResourceLocation.parse(object.get("dimension").getAsString()),
                    jsonToPos(object.getAsJsonObject("pos")),
                    ResourceLocation.parse(object.get("block").getAsString()),
                    optionalString(object, "sourceType").orElse("scanned"),
                    optionalString(object, "clusterId"),
                    optionalBoolean(object, "activeLootContainer").orElse(false),
                    optionalString(object, "lootTableId"),
                    optionalInt(object, "lootTier"),
                    metadata));
        }
        return new RaidContainerLayout(mapId, layoutOrigin, containers);
    }

    private static JsonObject posToJson(BlockPos pos) {
        JsonObject object = new JsonObject();
        object.addProperty("x", pos.getX());
        object.addProperty("y", pos.getY());
        object.addProperty("z", pos.getZ());
        return object;
    }

    private static BlockPos jsonToPos(JsonObject object) {
        return new BlockPos(object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
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

    public record PopulateResult(int populatedCount, int skippedCount, String warning) {
    }

    public record FindLootResult(BlockPos pos, ResourceLocation blockId, String displayName, ResourceLocation itemId, Optional<ResourceLocation> variantId, String normalizedKey, int count) {
    }

    public record LightPassResult(int placedCount, int removedCount, int skippedCount) {
    }

    private record CellKey(int x, int z) {
        private static CellKey from(BlockPos pos) {
            return new CellKey(Math.floorDiv(pos.getX(), CLUSTER_CELL_SIZE), Math.floorDiv(pos.getZ(), CLUSTER_CELL_SIZE));
        }
    }

    private static Optional<BlockPos> lightPositionFor(ServerLevel level, RaidDevBounds bounds, BlockPos containerPos, boolean activeContainer) {
        BlockPos[] candidates = {
                containerPos.above(),
                containerPos.above(2),
                containerPos.north().above(),
                containerPos.south().above(),
                containerPos.east().above(),
                containerPos.west().above(),
                containerPos.north().above(2),
                containerPos.south().above(2),
                containerPos.east().above(2),
                containerPos.west().above(2)
        };

        for (BlockPos candidate : candidates) {
            if (!isInside(bounds, candidate)) {
                continue;
            }
            if (!activeContainer && level.canSeeSky(candidate)) {
                continue;
            }

            BlockState state = level.getBlockState(candidate);
            if (state.isAir() || state.is(Blocks.LIGHT)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static int placeAmbientInteriorLights(ServerLevel level, RaidDevBounds bounds) {
        int placed = 0;
        for (int x = bounds.minX() + AMBIENT_GRID_XZ / 2; x <= bounds.maxX(); x += AMBIENT_GRID_XZ) {
            for (int z = bounds.minZ() + AMBIENT_GRID_XZ / 2; z <= bounds.maxZ(); z += AMBIENT_GRID_XZ) {
                for (int y = bounds.minY() + 2; y <= bounds.maxY() - 2; y += AMBIENT_GRID_Y) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isAmbientLightCandidate(level, bounds, pos)) {
                        continue;
                    }

                    BlockState currentState = level.getBlockState(pos);
                    if (currentState.is(Blocks.LIGHT) && currentState.getValue(LightBlock.LEVEL) >= AMBIENT_LIGHT_LEVEL) {
                        continue;
                    }

                    level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, AMBIENT_LIGHT_LEVEL), 3);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static boolean isAmbientLightCandidate(ServerLevel level, RaidDevBounds bounds, BlockPos pos) {
        if (!isInside(bounds, pos) || level.canSeeSky(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.is(Blocks.LIGHT)) {
            return false;
        }
        if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) >= AMBIENT_LIGHT_LEVEL) {
            return false;
        }

        return hasNearbyFloor(level, bounds, pos) && hasHeadroom(level, bounds, pos);
    }

    private static boolean hasNearbyFloor(ServerLevel level, RaidDevBounds bounds, BlockPos pos) {
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos below = pos.below(dy);
            if (isInside(bounds, below) && level.getBlockState(below).isSolidRender(level, below)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHeadroom(ServerLevel level, RaidDevBounds bounds, BlockPos pos) {
        BlockPos above = pos.above();
        if (!isInside(bounds, above)) {
            return false;
        }

        BlockState aboveState = level.getBlockState(above);
        return aboveState.isAir() || aboveState.is(Blocks.LIGHT);
    }
}
