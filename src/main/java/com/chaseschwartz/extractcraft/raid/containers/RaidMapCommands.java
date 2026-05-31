package com.chaseschwartz.extractcraft.raid.containers;

import java.io.IOException;
import java.util.Map;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMaps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class RaidMapCommands {
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
                .then(Commands.literal("renderlootcontainers")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> renderLootContainers(context.getSource(), StringArgumentType.getString(context, "map_id"))))));
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
