package com.chaseschwartz.extractcraft.raid.inventory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

final class LootCatalogReport {
    private static final Set<ItemRarity> LOOSE_RARITIES = Set.of(ItemRarity.BLUE, ItemRarity.PURPLE, ItemRarity.GOLD, ItemRarity.RED);
    private static final Set<String> RECIPE_SUPPORT_ITEMS = Set.of(
            "minecraft:crying_obsidian",
            "minecraft:end_crystal",
            "minecraft:fire_charge",
            "minecraft:blaze_rod",
            "minecraft:glowstone_dust",
            "minecraft:amethyst_shard",
            "minecraft:quartz",
            "minecraft:glass",
            "minecraft:leather",
            "minecraft:netherite_scrap",
            "minecraft:stick",
            "minecraft:oak_log",
            "minecraft:white_wool");
    private static final Map<String, List<ItemCategory>> CONTEXT_CATEGORY_BIAS = Map.of(
            "generic", List.of(),
            "safe", List.of(ItemCategory.INTEL, ItemCategory.ELECTRONICS, ItemCategory.CONTRABAND, ItemCategory.JACKPOT, ItemCategory.WEIRD_LORE, ItemCategory.LUXURY_COLLECTIBLE, ItemCategory.PAWN_COLLECTIBLE, ItemCategory.CIVILIAN_VALUABLE, ItemCategory.SECURITY, ItemCategory.ACCESS, ItemCategory.OFFICE_ADMIN),
            "military", List.of(ItemCategory.WEAPON_PARTS, ItemCategory.OPTICS, ItemCategory.ARMOR_MATERIALS, ItemCategory.ELECTRONICS, ItemCategory.SECURITY, ItemCategory.ACCESS, ItemCategory.FIELD_MEDICAL),
            "medical", List.of(ItemCategory.HOME_MEDICAL, ItemCategory.FIELD_MEDICAL, ItemCategory.MEDICAL_TECH, ItemCategory.LAB_BIO, ItemCategory.RARE_MEDICAL, ItemCategory.SURVIVAL_UTILITY, ItemCategory.TOOLS),
            "office", List.of(ItemCategory.INTEL, ItemCategory.ACCESS, ItemCategory.OFFICE_ADMIN, ItemCategory.ELECTRONICS, ItemCategory.SECURITY, ItemCategory.CIVILIAN_VALUABLE, ItemCategory.LUXURY_COLLECTIBLE, ItemCategory.PAWN_COLLECTIBLE),
            "industrial", List.of(ItemCategory.TOOLS, ItemCategory.INDUSTRIAL, ItemCategory.INDUSTRIAL_TOOLS, ItemCategory.ELECTRONICS, ItemCategory.POWER, ItemCategory.SURVIVAL_UTILITY, ItemCategory.ARMOR_MATERIALS));
    private static final Map<String, Set<RaidContainerService.TaczLootKind>> CONTEXT_TACZ_KINDS = Map.of(
            "generic", Set.of(RaidContainerService.TaczLootKind.AMMO, RaidContainerService.TaczLootKind.ATTACHMENT),
            "safe", Set.of(RaidContainerService.TaczLootKind.ATTACHMENT, RaidContainerService.TaczLootKind.GUN, RaidContainerService.TaczLootKind.PART),
            "military", Set.of(RaidContainerService.TaczLootKind.AMMO, RaidContainerService.TaczLootKind.ATTACHMENT, RaidContainerService.TaczLootKind.GUN, RaidContainerService.TaczLootKind.PART),
            "medical", Set.of(),
            "office", Set.of(),
            "industrial", Set.of(RaidContainerService.TaczLootKind.AMMO, RaidContainerService.TaczLootKind.ATTACHMENT, RaidContainerService.TaczLootKind.PART));

    private LootCatalogReport() {
    }

    static Path write() throws Exception {
        Path reportPath = FMLPaths.GAMEDIR.get().resolve("extractcraft").resolve("debug").resolve("loot_catalog.txt");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, build(), StandardCharsets.UTF_8);
        return reportPath;
    }

    private static String build() {
        List<ItemValueEntry> spawnable = spawnableEntries();
        List<ItemValueEntry> looseLoot = spawnable.stream().filter(LootCatalogReport::isLooseLoot).toList();
        List<ItemValueEntry> taczFirstClass = spawnable.stream().filter(RaidContainerService::isFirstClassTaczLoot).toList();
        List<ItemValueEntry> vanilla = spawnable.stream().filter(entry -> entry.itemId().getNamespace().equals("minecraft")).toList();
        List<ItemValueEntry> fallbackExtractCraft = spawnable.stream()
                .filter(entry -> entry.itemId().getNamespace().equals(ExtractCraft.MODID))
                .filter(entry -> !isLooseLoot(entry))
                .toList();
        List<ItemValueEntry> bannedTacz = ItemValueRegistry.entries().stream()
                .filter(entry -> RaidContainerService.isTaczLoot(entry))
                .filter(entry -> !entry.sellable())
                .sorted(LootCatalogReport::compareEntries)
                .toList();

        StringBuilder report = new StringBuilder(256_000);
        report.append("ExtractCraft Raid Loot Catalog\n");
        report.append("Report only: no loot weights, spawn counts, profiles, recipes, assets, or inventory behavior are modified.\n\n");

        appendSummary(report, spawnable, looseLoot, taczFirstClass, vanilla, fallbackExtractCraft, bannedTacz);
        appendContextOverview(report);
        appendCatalogSection(report, "A. Loose loot first-class", looseLoot, LootCatalogReport::looseNotes);
        appendCatalogSection(report, "B. TaCZ first-class", taczFirstClass, LootCatalogReport::taczNotes);
        appendCatalogSection(report, "C. Vanilla fallback / recipe-support items", vanilla, LootCatalogReport::vanillaNotes);
        appendCatalogSection(report, "D. ExtractCraft equipment/med/repair fallback", fallbackExtractCraft, LootCatalogReport::extractCraftFallbackNotes);
        appendBannedTacz(report, bannedTacz);
        appendSpawnCountSection(report);
        appendProblemFlags(report, spawnable, bannedTacz);
        appendCsvSection(report, looseLoot, taczFirstClass, vanilla, fallbackExtractCraft);
        appendStackRecommendations(report);

        return report.toString();
    }

    private static List<ItemValueEntry> spawnableEntries() {
        return ItemValueRegistry.entries().stream()
                .filter(ItemValueEntry::sellable)
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.itemId()))
                .filter(entry -> !ItemStackVariantFactory.isUnsafeBareVariantBase(entry.itemId()) || entry.lookupKey().contains("#"))
                .sorted(LootCatalogReport::compareEntries)
                .toList();
    }

    private static void appendSummary(StringBuilder report, List<ItemValueEntry> spawnable, List<ItemValueEntry> looseLoot, List<ItemValueEntry> taczFirstClass,
            List<ItemValueEntry> vanilla, List<ItemValueEntry> fallbackExtractCraft, List<ItemValueEntry> bannedTacz) {
        report.append("1. Summary\n");
        report.append("- total spawnable items: ").append(spawnable.size()).append('\n');
        report.append("- total first-class loose loot: ").append(looseLoot.size()).append('\n');
        report.append("- total first-class TaCZ: ").append(taczFirstClass.size()).append('\n');
        report.append("- total vanilla fallback / recipe-support: ").append(vanilla.size()).append('\n');
        report.append("- total equipment/med/repair fallback: ").append(fallbackExtractCraft.size()).append('\n');
        report.append("- banned/non-lootable TaCZ count: ").append(bannedTacz.size()).append('\n');
        report.append("- current contexts: ").append(String.join(", ", RaidContainerService.sampleLootContexts())).append("\n\n");
    }

    private static void appendContextOverview(StringBuilder report) {
        report.append("2. Context chance overview\n");
        for (String context : RaidContainerService.sampleLootContexts()) {
            report.append("- ").append(RaidContainerService.lootContextExpectationLine(context)).append('\n');
            RaidContainerService.lootContextReportLines().stream()
                    .filter(line -> line.startsWith(context) || context.equals("safe") && line.startsWith("safe/high_value"))
                    .findFirst()
                    .ifPresent(line -> report.append("  ").append(line).append('\n'));
        }
        report.append('\n');
    }

    private static void appendCatalogSection(StringBuilder report, String title, List<ItemValueEntry> entries, java.util.function.Function<ItemValueEntry, String> noteBuilder) {
        report.append(title).append(" (").append(entries.size()).append(")\n");
        if (entries.isEmpty()) {
            report.append("- none\n\n");
            return;
        }
        for (ItemValueEntry entry : entries) {
            Optional<ItemCarryProfile> profile = profile(entry);
            report.append("- ").append(entry.lookupKey())
                    .append(" | name=").append(displayName(entry))
                    .append(" | rarity=").append(rarity(entry))
                    .append(" | category=").append(category(entry))
                    .append(" | value=").append(entry.value())
                    .append(" | weight=").append(weight(profile))
                    .append(" | grid=").append(grid(profile))
                    .append(" | stack=").append(stackBehavior(entry))
                    .append(" | contexts=").append(contexts(entry))
                    .append(" | ").append(noteBuilder.apply(entry))
                    .append('\n');
        }
        report.append('\n');
    }

    private static void appendBannedTacz(StringBuilder report, List<ItemValueEntry> bannedTacz) {
        report.append("E. Banned / non-lootable TaCZ entries (").append(bannedTacz.size()).append(")\n");
        if (bannedTacz.isEmpty()) {
            report.append("- none\n\n");
            return;
        }
        for (ItemValueEntry entry : bannedTacz) {
            report.append("- ").append(entry.lookupKey())
                    .append(" | name=").append(displayName(entry))
                    .append(" | kind=").append(taczKindText(entry))
                    .append(" | sellable=false")
                    .append(" | firstClassLoot=").append(RaidContainerService.isFirstClassTaczLoot(entry))
                    .append(" | notes=").append(notes(entry))
                    .append('\n');
        }
        report.append('\n');
    }

    private static void appendSpawnCountSection(StringBuilder report) {
        report.append("4. Spawn count / stack count behavior\n");
        report.append("- TaCZ guns: count 1; correct for weapons.\n");
        report.append("- TaCZ attachments: count 1; correct for attachments.\n");
        report.append("- TaCZ ammo: rarity-driven ranges; common/uncommon 20-60, rare 10-30, epic/heavy 5-20.\n");
        report.append("- Loose loot valuables: count 1 because first-class loose loot is always single-stack value loot.\n");
        report.append("- Vanilla AMMO category items: currently 20-60 unless item-specific overrides apply.\n");
        report.append("- Vanilla FOOD/JUNK/SCRAP_METAL category items: currently 1-4.\n");
        report.append("- Vanilla recipe-support/crafting items: item-specific ranges now cover gunpowder, redstone, glowstone dust, ingots, nuggets, logs, sticks, wool, leather, blaze rods, crystals, and similar materials.\n");
        report.append("- Equipment: count 1.\n");
        report.append("- Meds: count 1 unless categorized as AMMO/FOOD/JUNK/SCRAP_METAL, which current ExtractCraft meds are not.\n");
        report.append("- Repair kits: count 1.\n");
        report.append("- Current implementation: RaidContainerService.countRange is the source of truth for this report and active container population.\n\n");
    }

    private static void appendProblemFlags(StringBuilder report, List<ItemValueEntry> spawnable, List<ItemValueEntry> bannedTacz) {
        report.append("6. Problem flags\n");
        List<ItemValueEntry> missingProfiles = spawnable.stream().filter(entry -> profile(entry).isEmpty()).toList();
        List<ItemValueEntry> unsafeBare = spawnable.stream().filter(entry -> ItemStackVariantFactory.isUnsafeBareVariantBase(entry.itemId()) && !entry.lookupKey().contains("#")).toList();
        List<ItemValueEntry> bannedFirstClass = bannedTacz.stream().filter(RaidContainerService::isFirstClassTaczLoot).toList();
        List<ItemValueEntry> countOneButStackLikely = spawnable.stream()
                .filter(entry -> !isLooseLoot(entry))
                .filter(entry -> !RaidContainerService.isFirstClassTaczLoot(entry) || RaidContainerService.taczKind(entry) == RaidContainerService.TaczLootKind.AMMO)
                .filter(entry -> RaidContainerService.countRange(entry).min() == 1 && RaidContainerService.countRange(entry).max() == 1)
                .filter(entry -> entry.category() != ItemCategory.AMMO && entry.category() != ItemCategory.FOOD && entry.category() != ItemCategory.JUNK && entry.category() != ItemCategory.SCRAP_METAL)
                .filter(entry -> entry.category() != ItemCategory.GUNS && entry.category() != ItemCategory.ATTACHMENTS && entry.category() != ItemCategory.MAGAZINES && entry.category() != ItemCategory.ARMOR && entry.category() != ItemCategory.ARMOR_PARTS)
                .toList();
        report.append("- banned item still first-class: ").append(bannedFirstClass.isEmpty() ? "none" : keys(bannedFirstClass)).append('\n');
        report.append("- unsafe bare TaCZ base could spawn: ").append(unsafeBare.isEmpty() ? "none" : keys(unsafeBare)).append('\n');
        report.append("- missing carry/grid profile: ").append(missingProfiles.isEmpty() ? "none" : keys(missingProfiles)).append('\n');
        report.append("- count is currently 1 but stack range may be better later: ").append(countOneButStackLikely.size()).append(" items\n");
        for (ItemValueEntry entry : countOneButStackLikely.stream().limit(40).toList()) {
            report.append("  - ").append(entry.lookupKey()).append(" | category=").append(category(entry)).append(" | value=").append(entry.value()).append('\n');
        }
        if (countOneButStackLikely.size() > 40) {
            report.append("  - ... ").append(countOneButStackLikely.size() - 40).append(" more\n");
        }
        List<ItemValueEntry> commonContainerFitRisks = spawnable.stream()
                .filter(entry -> profile(entry).map(itemProfile -> itemProfile.gridWidth().orElse(1) > 6 || itemProfile.gridHeight().orElse(1) > 6).orElse(false))
                .toList();
        report.append("- cannot fit a default 6x6 backpack footprint: ").append(commonContainerFitRisks.isEmpty() ? "none" : keys(commonContainerFitRisks)).append("\n\n");
    }

    private static void appendCsvSection(StringBuilder report, List<ItemValueEntry> looseLoot, List<ItemValueEntry> taczFirstClass, List<ItemValueEntry> vanilla,
            List<ItemValueEntry> fallbackExtractCraft) {
        report.append("7. Compact CSV-style catalog\n");
        report.append("source | contexts | id | name | rarity | category | value | weight | size | count | notes\n");
        Stream.of(
                looseLoot.stream().map(entry -> csvLine("loose", entry, looseNotes(entry))),
                taczFirstClass.stream().map(entry -> csvLine("tacz", entry, taczNotes(entry))),
                vanilla.stream().map(entry -> csvLine("vanilla", entry, vanillaNotes(entry))),
                fallbackExtractCraft.stream().map(entry -> csvLine("extractcraft_fallback", entry, extractCraftFallbackNotes(entry))))
                .flatMap(stream -> stream)
                .forEach(line -> report.append(line).append('\n'));
        report.append('\n');
    }

    private static void appendStackRecommendations(StringBuilder report) {
        report.append("8. Stack/count tuning notes\n");
        report.append("- TaCZ guns: count 1.\n");
        report.append("- TaCZ attachments: count 1.\n");
        report.append("- TaCZ ammo: common/uncommon calibers now use 20-60, rare ammo uses 10-30, epic/heavy ammo uses 5-20, banned launcher ammo does not spawn.\n");
        report.append("- gunpowder/redstone now use 4-16.\n");
        report.append("- copper/iron/gold ingots now use controlled small stacks.\n");
        report.append("- iron/gold nuggets now use 4-24.\n");
        report.append("- blaze rods now use 1-4.\n");
        report.append("- netherite scrap: 1.\n");
        report.append("- crying obsidian now uses 1-3.\n");
        report.append("- end crystal: 1.\n");
        report.append("- fire charge now uses 1-4.\n");
        report.append("- leather now uses 1-6.\n");
        report.append("- logs/sticks/wool/glass now use small crafting stacks rather than always 1.\n");
        report.append("- loose loot valuables: count 1.\n");
        report.append("- meds/repair kits remain count 1 unless low-tier consumables are intentionally stackable later.\n\n");
    }

    private static String csvLine(String source, ItemValueEntry entry, String notes) {
        Optional<ItemCarryProfile> profile = profile(entry);
        return String.join(" | ",
                source,
                contexts(entry),
                entry.lookupKey(),
                displayName(entry),
                rarity(entry),
                category(entry),
                Integer.toString(entry.value()),
                weight(profile),
                grid(profile),
                stackBehavior(entry),
                notes.replace(" | ", "; "));
    }

    private static String looseNotes(ItemValueEntry entry) {
        return "source=first-class loose loot" + highValueNote(entry);
    }

    private static String taczNotes(ItemValueEntry entry) {
        return "kind=" + taczKindText(entry)
                + " | subtype=" + taczSubtype(entry)
                + " | firstClassLoot=" + RaidContainerService.isFirstClassTaczLoot(entry)
                + " | banned=false";
    }

    private static String vanillaNotes(ItemValueEntry entry) {
        boolean recipeSupport = RECIPE_SUPPORT_ITEMS.contains(entry.lookupKey());
        return "source=" + (recipeSupport ? "legacy fallback + recipe support" : "legacy fallback");
    }

    private static String extractCraftFallbackNotes(ItemValueEntry entry) {
        Optional<ItemCarryProfile> itemProfile = profile(entry);
        String use = itemProfile.flatMap(ItemCarryProfile::equipmentSlot)
                .or(() -> itemProfile.flatMap(ItemCarryProfile::repairTargetCategory).map(target -> "repair:" + target))
                .or(() -> itemProfile.flatMap(profile -> profile.healAmount().map(amount -> "heal:" + amount)))
                .orElse("fallback");
        return "source=ExtractCraft equipment/med/repair registry | use=" + use;
    }

    private static String contexts(ItemValueEntry entry) {
        if (RaidContainerService.isFirstClassTaczLoot(entry)) {
            RaidContainerService.TaczLootKind kind = RaidContainerService.taczKind(entry);
            return RaidContainerService.sampleLootContexts().stream()
                    .filter(context -> CONTEXT_TACZ_KINDS.getOrDefault(context, Set.of()).contains(kind))
                    .collect(Collectors.joining(","));
        }
        if (isLooseLoot(entry)) {
            return RaidContainerService.sampleLootContexts().stream()
                    .filter(context -> {
                        List<ItemCategory> bias = CONTEXT_CATEGORY_BIAS.getOrDefault(context, List.of());
                        return bias.isEmpty() || bias.contains(entry.category());
                    })
                    .collect(Collectors.joining(","));
        }
        return "legacy fallback in any context after first-class rolls fail";
    }

    private static String stackBehavior(ItemValueEntry entry) {
        int maxStack = maxStack(entry.itemId());
        String current = currentSpawnCount(entry);
        return current + " | maxStack=" + maxStack;
    }

    private static String currentSpawnCount(ItemValueEntry entry) {
        return "current=" + RaidContainerService.countRange(entry).describe();
    }

    private static int maxStack(ResourceLocation itemId) {
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return new ItemStack(item).getMaxStackSize();
    }

    private static Optional<ItemCarryProfile> profile(ItemValueEntry entry) {
        return ItemCarryProfileRegistry.get(entry.lookupKey()).or(() -> ItemCarryProfileRegistry.get(entry.itemId()));
    }

    private static String displayName(ItemValueEntry entry) {
        return TaczDisplayNameResolver.displayName(entry.lookupKey(), BuiltInRegistries.ITEM.get(entry.itemId()).getDescription().getString());
    }

    private static String category(ItemValueEntry entry) {
        return entry.category().name().toLowerCase(Locale.ROOT);
    }

    private static String rarity(ItemValueEntry entry) {
        return entry.rarity().name().toLowerCase(Locale.ROOT);
    }

    private static String weight(Optional<ItemCarryProfile> profile) {
        return profile.map(itemProfile -> String.format(Locale.ROOT, "%.2f", itemProfile.weight())).orElse("missing");
    }

    private static String grid(Optional<ItemCarryProfile> profile) {
        return profile.map(itemProfile -> itemProfile.gridWidth().orElse(1) + "x" + itemProfile.gridHeight().orElse(1)).orElse("missing");
    }

    private static String highValueNote(ItemValueEntry entry) {
        if (entry.rarity() == ItemRarity.RED || entry.rarity() == ItemRarity.GOLD || entry.value() >= 7_500) {
            return " | highValue=true";
        }
        return "";
    }

    private static String taczKindText(ItemValueEntry entry) {
        return RaidContainerService.taczKind(entry).name().toLowerCase(Locale.ROOT);
    }

    private static String taczSubtype(ItemValueEntry entry) {
        String key = entry.lookupKey();
        int hash = key.indexOf('#');
        String variant = hash >= 0 ? key.substring(hash + 1) : key;
        String id = variant.contains(":") ? variant.substring(variant.indexOf(':') + 1) : variant;
        if (key.startsWith("tacz:attachment#")) {
            if (id.contains("extended_mag") || id.startsWith("ammo_mod")) {
                return "extended_mag/ammo_mod";
            }
            if (id.contains("scope") || id.contains("sight")) {
                return "scope";
            }
            if (id.contains("silencer") || id.contains("muzzle") || id.contains("barrel")) {
                return "muzzle";
            }
            if (id.contains("grip")) {
                return "grip";
            }
            if (id.contains("laser")) {
                return "laser";
            }
            if (id.contains("stock")) {
                return "stock";
            }
        }
        return id;
    }

    private static String notes(ItemValueEntry entry) {
        if (entry.notes().isEmpty()) {
            return "none";
        }
        return String.join("; ", entry.notes());
    }

    private static boolean isLooseLoot(ItemValueEntry entry) {
        return entry.itemId().getNamespace().equals(ExtractCraft.MODID) && LOOSE_RARITIES.contains(entry.rarity());
    }

    private static int compareEntries(ItemValueEntry left, ItemValueEntry right) {
        return Comparator.comparing((ItemValueEntry entry) -> entry.category().name())
                .thenComparing(entry -> entry.rarity().name())
                .thenComparing(ItemValueEntry::lookupKey)
                .compare(left, right);
    }

    private static String keys(List<ItemValueEntry> entries) {
        return entries.stream().map(ItemValueEntry::lookupKey).collect(Collectors.joining(", "));
    }
}
