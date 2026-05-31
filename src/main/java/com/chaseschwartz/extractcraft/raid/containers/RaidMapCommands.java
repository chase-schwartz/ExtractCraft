package com.chaseschwartz.extractcraft.raid.containers;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidMapBakeService;
import com.chaseschwartz.extractcraft.raid.map.RaidMapBoundaryService;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMaps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class RaidMapCommands {
    private static final int MAX_FIND_LOOT_RESULTS = 25;
    private static final Map<String, List<RaidContainerService.FindLootResult>> LAST_FIND_RESULTS = new HashMap<>();

    private RaidMapCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raidmap")
                .then(Commands.literal("scancontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> scanContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("selectlootcontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> selectLootContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("savecontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> saveContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("clearcontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> clearContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("listcontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> listContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("populatecontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> populateContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("findloot")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .then(Commands.argument("query", StringArgumentType.greedyString())
                                        .executes(context -> findLoot(context.getSource(), StringArgumentType.getString(context, "map_id"), StringArgumentType.getString(context, "query"))))))
                .then(Commands.literal("tpcontainer")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .then(Commands.argument("result_index", IntegerArgumentType.integer(1))
                                        .executes(context -> teleportToContainer(context.getSource(), StringArgumentType.getString(context, "map_id"), IntegerArgumentType.getInteger(context, "result_index"))))))
                .then(Commands.literal("lightpass")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> lightPass(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("clearlightpass")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> clearLightPass(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("renderlootcontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> renderLootContainers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("bake")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> bake(context.getSource(), StringArgumentType.getString(context, "map_id"), false))
                                .then(Commands.literal("force")
                                        .executes(context -> bake(context.getSource(), StringArgumentType.getString(context, "map_id"), true)))))
                .then(Commands.literal("bakestatus")
                        .executes(context -> bakeStatus(context.getSource())))
                .then(Commands.literal("buildboundary")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> buildBoundary(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("boundarystatus")
                        .executes(context -> boundaryStatus(context.getSource()))));
    }

    private static int scanContainers(CommandSourceStack source, String mapId) {
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        RaidContainerLayout layout = RaidContainerService.scan(context.level(), context.raidMap());
        try {
            RaidContainerService.save(layout);
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Scanned containers but failed to save: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Scanned " + layout.containers().size() + " containers for " + mapId + " and saved detected layout."), false);
        return 1;
    }

    private static int selectLootContainers(CommandSourceStack source, String mapId) {
        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container scan for " + mapId + ". Run /raidmap scancontainers " + mapId + " first."));
            return 0;
        }

        RaidContainerLayout selected = RaidContainerService.selectActiveLootContainers(layout);
        try {
            RaidContainerService.save(selected);
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Selected active containers but failed to save: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Selected " + selected.activeCount() + " active loot containers out of " + selected.containers().size() + " detected containers."), false);
        return 1;
    }

    private static int saveContainers(CommandSourceStack source, String mapId) {
        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No container data for " + mapId + " to save. Run /raidmap scancontainers " + mapId + " first."));
            return 0;
        }

        try {
            RaidContainerService.save(layout);
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Failed to save container data: " + exception.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Saved container data to " + RaidContainerService.layoutPath(mapId)), false);
        return 1;
    }

    private static int clearContainers(CommandSourceStack source, String mapId) {
        try {
            boolean deleted = RaidContainerService.clearSaved(mapId);
            source.sendSuccess(() -> Component.literal(deleted ? "Cleared saved container data for " + mapId + "." : "No saved container data existed for " + mapId + "."), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Failed to clear saved container data: " + exception.getMessage()));
            return 0;
        }
    }

    private static int listContainers(CommandSourceStack source, String mapId) {
        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container data for " + mapId + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Containers for " + mapId + ": " + layout.containers().size() + " detected, " + layout.activeCount() + " active."), false);
        for (Map.Entry<ResourceLocation, Long> entry : RaidContainerService.countByBlock(layout).entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + entry.getKey() + ": " + entry.getValue()), false);
        }
        source.sendSuccess(() -> Component.literal("Clusters: " + RaidContainerService.countByCluster(layout).size()), false);
        return 1;
    }

    private static int populateContainers(CommandSourceStack source, String mapId) {
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container data for " + mapId + ". Run scan/select first."));
            return 0;
        }

        RaidContainerService.PopulateResult result = RaidContainerService.populate(context.level(), context.raidMap(), layout);
        source.sendSuccess(() -> Component.literal("Populated " + result.populatedCount() + " active containers for " + mapId + " (" + result.skippedCount() + " skipped)."), false);
        if (!result.warning().isBlank()) {
            source.sendFailure(Component.literal(result.warning()));
        }
        return result.populatedCount() > 0 ? 1 : 0;
    }

    private static int findLoot(CommandSourceStack source, String mapId, String query) {
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container data for " + mapId + ". Run scan/select/populate first."));
            return 0;
        }

        List<RaidContainerService.FindLootResult> results = RaidContainerService.findLoot(context.level(), context.raidMap(), layout, query, MAX_FIND_LOOT_RESULTS);
        LAST_FIND_RESULTS.put(mapId, results);
        if (results.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active loot containers in " + mapId + " currently contain '" + query + "'."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Found " + results.size() + " loot result(s) for '" + query + "' in " + mapId + ". Use /raidmap tpcontainer " + mapId + " <index>."), false);
        for (int i = 0; i < results.size(); i++) {
            int index = i + 1;
            RaidContainerService.FindLootResult result = results.get(i);
            BlockPos pos = result.pos();
            String variant = result.variantId().map(ResourceLocation::toString).orElse("none");
            source.sendSuccess(() -> Component.literal(index + ". "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " [" + result.blockId() + "] "
                    + result.displayName()
                    + " x" + result.count()
                    + " item=" + result.itemId()
                    + " variant=" + variant
                    + " key=" + result.normalizedKey()), false);
        }
        return 1;
    }

    private static int teleportToContainer(CommandSourceStack source, String mapId, int resultIndex) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        List<RaidContainerService.FindLootResult> results = LAST_FIND_RESULTS.get(mapId);
        if (results == null || results.isEmpty()) {
            source.sendFailure(Component.literal("No cached findloot results for " + mapId + ". Run /raidmap findloot " + mapId + " <query> first."));
            return 0;
        }
        if (resultIndex > results.size()) {
            source.sendFailure(Component.literal("Result index " + resultIndex + " is out of range. Last findloot returned " + results.size() + " result(s)."));
            return 0;
        }

        RaidContainerService.FindLootResult result = results.get(resultIndex - 1);
        BlockPos pos = result.pos();
        player.teleportTo(context.level(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to loot result " + resultIndex + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "."), false);
        return 1;
    }

    private static int renderLootContainers(CommandSourceStack source, String mapId) throws CommandSyntaxException {
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container data for " + mapId + "."));
            return 0;
        }

        int rendered = 0;
        for (RaidContainerEntry entry : layout.containers()) {
            if (!entry.activeLootContainer()) {
                continue;
            }

            BlockPos pos = entry.pos();
            context.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5D, pos.getY() + 1.15D, pos.getZ() + 0.5D, 8, 0.25D, 0.25D, 0.25D, 0.0D);
            rendered++;
        }

        int renderedCount = rendered;
        source.sendSuccess(() -> Component.literal("Rendered particles for " + renderedCount + " active loot containers."), false);
        return 1;
    }

    private static int lightPass(CommandSourceStack source, String mapId) {
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container data for " + mapId + ". Run scan/select first."));
            return 0;
        }

        RaidContainerService.LightPassResult result = RaidContainerService.lightPass(context.level(), context.raidMap(), layout);
        source.sendSuccess(() -> Component.literal("Placed " + result.placedCount() + " loot/ambient light blocks near detected containers for " + mapId + " (" + result.skippedCount() + " active containers skipped)."), false);
        return 1;
    }

    private static int clearLightPass(CommandSourceStack source, String mapId) {
        RaidMapContext context = resolve(source, mapId);
        if (context == null) {
            return 0;
        }

        RaidContainerLayout layout = RaidContainerService.load(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved container data for " + mapId + "."));
            return 0;
        }

        RaidContainerService.LightPassResult result = RaidContainerService.clearLightPass(context.level(), context.raidMap(), layout);
        source.sendSuccess(() -> Component.literal("Removed " + result.removedCount() + " loot light blocks near active containers for " + mapId + "."), false);
        return 1;
    }

    private static int bake(CommandSourceStack source, String mapId, boolean force) {
        RaidMapDefinition raidMap = RaidMaps.byId(mapId).orElse(null);
        if (raidMap == null) {
            source.sendFailure(Component.literal("Unknown raid map '" + mapId + "'. Available maps: " + RaidMaps.availableMapIds()));
            return 0;
        }

        RaidMapBakeService.StartResult result = RaidMapBakeService.startBake(source.getServer(), raidMap, force);
        if (!result.started()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int bakeStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(RaidMapBakeService.statusMessage()), false);
        return 1;
    }

    private static int buildBoundary(CommandSourceStack source, String mapId) {
        RaidMapDefinition raidMap = RaidMaps.byId(mapId).orElse(null);
        if (raidMap == null) {
            source.sendFailure(Component.literal("Unknown raid map '" + mapId + "'. Available maps: " + RaidMaps.availableMapIds()));
            return 0;
        }

        RaidMapBoundaryService.StartResult result = RaidMapBoundaryService.startBoundaryBuild(source.getServer(), raidMap);
        if (!result.started()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(result.message()), false);
        return 1;
    }

    private static int boundaryStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(RaidMapBoundaryService.statusMessage()), false);
        return 1;
    }

    private static RaidMapContext resolve(CommandSourceStack source, String mapId) {
        RaidMapDefinition raidMap = RaidMaps.byId(mapId).orElse(null);
        if (raidMap == null) {
            source.sendFailure(Component.literal("Unknown raid map '" + mapId + "'. Available maps: " + RaidMaps.availableMapIds()));
            return null;
        }

        ServerLevel level = source.getServer().getLevel(raidMap.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Map dimension is unavailable: " + raidMap.dimension().location()));
            return null;
        }

        return new RaidMapContext(raidMap, level);
    }

    private record RaidMapContext(RaidMapDefinition raidMap, ServerLevel level) {
    }
}
