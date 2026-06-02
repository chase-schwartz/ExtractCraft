package com.chaseschwartz.extractcraft.raid.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.RaidState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class RaidResultService {
    private static final int DETAIL_LIMIT = 8;
    private static final Map<UUID, RaidResultSummary> LAST_RESULTS = new HashMap<>();
    private static final Map<UUID, PendingRaidResult> PENDING_RESULTS = new HashMap<>();

    private RaidResultService() {
    }

    public static void recordSuccessfulExtract(ServerPlayer player, RaidState raidState) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        SectionSummary backpack = summarizeStorage("Backpack", inventory.backpack());
        SectionSummary vest = summarizeStorage("Vest", inventory.vest());
        SectionSummary safeBox = summarizeStorage("Safe Box", inventory.safeBox());
        SectionSummary weapons = summarizeWeapons("Weapons kept", inventory.primaryWeapon(), inventory.secondaryWeapon());
        PendingRaidResult pending = PendingRaidResult.fromInventory(player.getUUID(), true, elapsedSeconds(player, raidState), inventory);

        int totalValue = backpack.value() + vest.value() + safeBox.value() + weapons.value();
        double totalWeight = backpack.weight() + vest.weight() + safeBox.weight() + weapons.weight();

        List<String> lines = new ArrayList<>();
        lines.add("Raid extracted successfully.");
        lines.add("Time in raid: " + formatDuration(pending.elapsedSeconds()) + ".");
        lines.add(String.format("Extracted total: %d credits | %.2f weight.", totalValue, totalWeight));
        lines.add(backpack.formatLine());
        lines.add(vest.formatLine());
        lines.add(safeBox.formatLine());
        lines.add(weapons.formatLine());
        lines.add("Post-raid pending: use /extractcraft raidresult stash or /extractcraft raidresult keep.");
        storeAndSend(player, "EXTRACTED", lines);
        PENDING_RESULTS.put(player.getUUID(), pending);

        inventory.clear();
        ExtractCraft.LOGGER.info("Recorded successful raid result for {}: {} credits, {} weight",
                player.getGameProfile().getName(),
                totalValue,
                String.format("%.2f", totalWeight));
    }

    public static void recordFailure(ServerPlayer player, String reason, RaidState raidState) {
        RaidInventory inventory = RaidInventoryManager.get(player);
        SectionSummary backpack = summarizeStorage("Lost backpack", inventory.backpack());
        SectionSummary vest = summarizeStorage("Lost vest", inventory.vest());
        SectionSummary weapons = summarizeWeapons("Lost weapons", inventory.primaryWeapon(), inventory.secondaryWeapon());
        SectionSummary safeBox = summarizeStorage("Secured safe box", inventory.safeBox());

        int lostValue = backpack.value() + vest.value() + weapons.value();
        double lostWeight = backpack.weight() + vest.weight() + weapons.weight();

        List<String> lines = new ArrayList<>();
        lines.add("Raid failed: " + reason + ".");
        lines.add("Time in raid: " + formatDuration(elapsedSeconds(player, raidState)) + ".");
        lines.add(String.format("Lost: %d credits | %.2f weight.", lostValue, lostWeight));
        lines.add(backpack.formatLine());
        lines.add(vest.formatLine());
        lines.add(weapons.formatLine());
        lines.add(safeBox.formatLine());
        PlayerStashService.StashTransferResult secured = PlayerStashService.addToStash(player, inventory.safeBox().items());
        if (safeBox.items() > 0) {
            lines.add("Safe box auto-secured to stash: " + secured.summary("stash"));
        }
        storeAndSend(player, "FAILED", lines);
        PENDING_RESULTS.remove(player.getUUID());

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

    public static boolean movePendingToStash(ServerPlayer player) {
        PendingRaidResult pending = PENDING_RESULTS.get(player.getUUID());
        if (pending == null || !pending.success()) {
            player.sendSystemMessage(Component.literal("No successful pending raid result to move into stash."));
            return false;
        }

        PlayerStashService.StashTransferResult result = PlayerStashService.addToStash(player, pending.allItems());
        if (result.movedAll()) {
            player.sendSystemMessage(Component.literal(result.summary("stash")));
            PENDING_RESULTS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Pending extracted result cleared after moving everything to stash."));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("Stash is full. Choose Keep On Character or free stash space."));
            player.sendSystemMessage(Component.literal(result.summary("stash")));
            return false;
        }
    }

    public static boolean keepPendingOnCharacter(ServerPlayer player) {
        PendingRaidResult pending = PENDING_RESULTS.get(player.getUUID());
        if (pending == null || !pending.success()) {
            player.sendSystemMessage(Component.literal("No successful pending raid result to keep on character."));
            return false;
        }

        PlayerStashService.StashTransferResult result = PlayerStashService.addToBaseInventory(player, pending);
        player.sendSystemMessage(Component.literal(result.summary("persistent base inventory")));
        if (result.movedAll()) {
            PENDING_RESULTS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Pending extracted result cleared after keeping everything on character."));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("Some extracted items did not fit. Pending result is still available."));
            return false;
        }
    }

    public static Optional<PendingRaidResult> pendingResult(ServerPlayer player) {
        return Optional.ofNullable(PENDING_RESULTS.get(player.getUUID()));
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

    private static RaidInventoryItem copyItem(RaidInventoryItem item) {
        return new RaidInventoryItem(
                item.itemId(),
                item.lookupKey(),
                item.displayName(),
                item.category(),
                item.count(),
                item.slotCost(),
                item.totalWeight(),
                item.totalValue(),
                item.toItemStack());
    }

    private static int elapsedSeconds(ServerPlayer player, RaidState raidState) {
        if (raidState == null) {
            return 0;
        }

        long now = player.server.overworld().getGameTime();
        long remainingTicks = Math.max(0L, raidState.expiresAtGameTime() - now);
        long elapsedTicks = Math.max(0L, raidState.raidMap().raidDurationTicks() - remainingTicks);
        return (int) Math.ceil(elapsedTicks / 20.0D);
    }

    private static String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes + "m " + remainder + "s";
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

    public record PendingRaidResult(UUID playerId, boolean success, int elapsedSeconds, List<RaidInventoryItem> backpackItems, List<RaidInventoryItem> vestItems,
            List<RaidInventoryItem> safeBoxItems, RaidInventoryItem primaryWeapon, RaidInventoryItem secondaryWeapon) {
        private static PendingRaidResult fromInventory(UUID playerId, boolean success, int elapsedSeconds, RaidInventory inventory) {
            return new PendingRaidResult(
                    playerId,
                    success,
                    elapsedSeconds,
                    copyItems(inventory.backpack().items()),
                    copyItems(inventory.vest().items()),
                    copyItems(inventory.safeBox().items()),
                    inventory.primaryWeapon() == null ? null : copyItem(inventory.primaryWeapon()),
                    inventory.secondaryWeapon() == null ? null : copyItem(inventory.secondaryWeapon()));
        }

        public List<RaidInventoryItem> allItems() {
            List<RaidInventoryItem> items = new ArrayList<>();
            items.addAll(backpackItems);
            items.addAll(vestItems);
            items.addAll(safeBoxItems);
            if (primaryWeapon != null) {
                items.add(primaryWeapon);
            }
            if (secondaryWeapon != null) {
                items.add(secondaryWeapon);
            }
            return items;
        }

        private static List<RaidInventoryItem> copyItems(List<RaidInventoryItem> items) {
            return items.stream().map(RaidResultService::copyItem).toList();
        }
    }
}
