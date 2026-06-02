package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RaidResultService {
    private static final int DETAIL_LIMIT = 8;
    private static final Map<UUID, RaidResultSummary> LAST_RESULTS = new HashMap<>();

    private RaidResultService() {
    }

    public static void recordSuccessfulExtract(ServerPlayer player) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        SectionSummary backpack = summarizeStorage("Backpack", inventory.backpack());
        SectionSummary vest = summarizeStorage("Vest", inventory.vest());
        SectionSummary safeBox = summarizeStorage("Safe Box", inventory.safeBox());
        SectionSummary weapons = summarizeWeapons("Weapons kept", inventory.primaryWeapon(), inventory.secondaryWeapon());

        int totalValue = backpack.value() + vest.value() + safeBox.value() + weapons.value();
        double totalWeight = backpack.weight() + vest.weight() + safeBox.weight() + weapons.weight();

        List<String> lines = new ArrayList<>();
        lines.add("Raid extracted successfully.");
        lines.add(String.format("Extracted total: %d credits | %.2f weight.", totalValue, totalWeight));
        lines.add(backpack.formatLine());
        lines.add(vest.formatLine());
        lines.add(safeBox.formatLine());
        lines.add(weapons.formatLine());
        storeAndSend(player, "EXTRACTED", lines);

        inventory.clear();
        ExtractCraft.LOGGER.info("Recorded successful raid result for {}: {} credits, {} weight",
                player.getGameProfile().getName(),
                totalValue,
                String.format("%.2f", totalWeight));
    }

    public static void recordFailure(ServerPlayer player, String reason) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        SectionSummary backpack = summarizeStorage("Lost backpack", inventory.backpack());
        SectionSummary vest = summarizeStorage("Lost vest", inventory.vest());
        SectionSummary weapons = summarizeWeapons("Lost weapons", inventory.primaryWeapon(), inventory.secondaryWeapon());
        SectionSummary safeBox = summarizeStorage("Secured safe box", inventory.safeBox());

        int lostValue = backpack.value() + vest.value() + weapons.value();
        double lostWeight = backpack.weight() + vest.weight() + weapons.weight();

        List<String> lines = new ArrayList<>();
        lines.add("Raid failed: " + reason + ".");
        lines.add(String.format("Lost: %d credits | %.2f weight.", lostValue, lostWeight));
        lines.add(backpack.formatLine());
        lines.add(vest.formatLine());
        lines.add(weapons.formatLine());
        lines.add(safeBox.formatLine());
        storeAndSend(player, "FAILED", lines);

        inventory.clear();
        ExtractCraft.LOGGER.info("Recorded failed raid result for {} via {}: lost {} credits, secured safe box {} credits",
                player.getGameProfile().getName(),
                reason,
                lostValue,
                safeBox.value());
    }

    public static void sendLastResult(ServerPlayer player) {
        RaidResultSummary result = LAST_RESULTS.get(player.getUUID());
        if (result == null) {
            player.sendSystemMessage(Component.literal("No raid result recorded yet."));
            return;
        }

        player.sendSystemMessage(Component.literal("Last raid result: " + result.outcome()));
        for (String line : result.lines()) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    private static void storeAndSend(ServerPlayer player, String outcome, List<String> lines) {
        LAST_RESULTS.put(player.getUUID(), new RaidResultSummary(outcome, List.copyOf(lines)));
        for (String line : lines) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    private static SectionSummary summarizeStorage(String label, RaidStorageContainer container) {
        List<String> details = new ArrayList<>();
        for (RaidInventoryItem item : container.items()) {
            details.add(formatItem(item));
        }
        return new SectionSummary(label, container.itemCount(), totalItemCount(container.items()), container.totalValue(), container.usedWeight(), details);
    }

    private static SectionSummary summarizeWeapons(String label, RaidInventoryItem primary, RaidInventoryItem secondary) {
        List<RaidInventoryItem> items = new ArrayList<>();
        if (primary != null) {
            items.add(primary);
        }
        if (secondary != null) {
            items.add(secondary);
        }

        int value = items.stream().mapToInt(RaidInventoryItem::totalValue).sum();
        double weight = items.stream().mapToDouble(RaidInventoryItem::totalWeight).sum();
        List<String> details = new ArrayList<>();
        if (primary != null) {
            details.add("Primary: " + formatItem(primary));
        }
        if (secondary != null) {
            details.add("Secondary: " + formatItem(secondary));
        }
        return new SectionSummary(label, items.size(), totalItemCount(items), value, weight, details);
    }

    private static int totalItemCount(List<RaidInventoryItem> items) {
        return items.stream().mapToInt(RaidInventoryItem::count).sum();
    }

    private static String formatItem(RaidInventoryItem item) {
        String key = item.lookupKey().equals(item.itemId().toString()) ? "" : " [" + item.lookupKey() + "]";
        return item.displayName() + " x" + item.count() + key;
    }

    private record SectionSummary(String label, int stacks, int items, int value, double weight, List<String> details) {
        private String formatLine() {
            StringBuilder line = new StringBuilder(String.format("%s: %d stacks, %d items, %d credits, %.2f weight",
                    label,
                    stacks,
                    items,
                    value,
                    weight));
            if (!details.isEmpty()) {
                line.append(" - ");
                int limit = Math.min(DETAIL_LIMIT, details.size());
                line.append(String.join("; ", details.subList(0, limit)));
                if (details.size() > limit) {
                    line.append("; +").append(details.size() - limit).append(" more");
                }
            }
            return line.toString();
        }
    }

    private record RaidResultSummary(String outcome, List<String> lines) {
    }
}
