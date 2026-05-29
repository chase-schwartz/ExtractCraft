package com.chaseschwartz.extractcraft.raid.markers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.raid.map.RaidMapDefinition;
import com.chaseschwartz.extractcraft.raid.map.RaidMaps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class RaidMarkerCommands {
    private RaidMarkerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raidmarkers")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("scan")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> scanMarkers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("save")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> saveMarkers(context.getSource(), StringArgumentType.getString(context, "map_id")))))
                .then(Commands.literal("render")
                        .then(Commands.argument("map_id", StringArgumentType.word())
                                .executes(context -> renderMarkers(context.getSource(), StringArgumentType.getString(context, "map_id"))))));
    }

    private static int scanMarkers(CommandSourceStack source, String mapId) {
        RaidMarkerLayout layout = scan(source, mapId);
        if (layout == null) {
            return 0;
        }

        sendScanSummary(source, mapId, layout);
        return layout.markers().size();
    }

    private static int saveMarkers(CommandSourceStack source, String mapId) {
        RaidMarkerLayout layout = scan(source, mapId);
        if (layout == null) {
            return 0;
        }

        try {
            Path savedPath = RaidMarkerService.save(layout);
            source.sendSuccess(() -> Component.literal("Saved " + layout.markers().size() + " raid markers to " + savedPath), false);
            ExtractCraft.LOGGER.info("Saved {} raid markers for map {} to {}", layout.markers().size(), mapId, savedPath);
            return layout.markers().size();
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Unable to save raid markers for '" + mapId + "': " + exception.getMessage()));
            ExtractCraft.LOGGER.warn("Unable to save raid markers for map {}", mapId, exception);
            return 0;
        }
    }

    private static int renderMarkers(CommandSourceStack source, String mapId) {
        RaidMapDefinition raidMap = resolveRaidMap(source, mapId);
        if (raidMap == null) {
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(raidMap.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Unable to render raid markers: raid dimension is unavailable."));
            return 0;
        }

        RaidMarkerLayout layout = RaidMarkerService.loadSaved(mapId).orElse(null);
        if (layout == null) {
            source.sendFailure(Component.literal("No saved raid marker layout found for '" + mapId + "' at " + RaidMarkerService.markerFile(mapId)));
            return 0;
        }

        RaidMarkerService.RenderResult renderResult = RaidMarkerService.renderSaved(level, layout);
        RaidMarkerLayout renderedLayout = renderResult.renderedLayout();
        int skippedCount = renderResult.skippedMarkers().size();
        source.sendSuccess(() -> Component.literal("Rendered " + renderedLayout.markers().size() + " raid markers for '" + mapId + "'."), false);
        if (skippedCount > 0) {
            source.sendSuccess(() -> Component.literal("Skipped " + skippedCount + " marker positions because their blocks were not safe to overwrite."), false);
        }
        sendTypeCounts(source, RaidMarkerService.countsByType(renderedLayout));
        return renderedLayout.markers().size();
    }

    private static RaidMarkerLayout scan(CommandSourceStack source, String mapId) {
        RaidMapDefinition raidMap = resolveRaidMap(source, mapId);
        if (raidMap == null) {
            return null;
        }

        ServerLevel level = source.getServer().getLevel(raidMap.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Unable to scan raid markers: raid dimension is unavailable."));
            return null;
        }

        return RaidMarkerService.scan(level, raidMap);
    }

    private static void sendScanSummary(CommandSourceStack source, String mapId, RaidMarkerLayout layout) {
        source.sendSuccess(() -> Component.literal("Found " + layout.markers().size() + " raid markers for '" + mapId + "'."), false);
        sendTypeCounts(source, RaidMarkerService.countsByType(layout));
    }

    private static void sendTypeCounts(CommandSourceStack source, Map<RaidMarkerType, Long> counts) {
        for (RaidMarkerType type : RaidMarkerType.values()) {
            long count = counts.get(type);
            if (count > 0) {
                source.sendSuccess(() -> Component.literal(type.serializedName() + ": " + count), false);
            }
        }
    }

    private static RaidMapDefinition resolveRaidMap(CommandSourceStack source, String mapId) {
        RaidMapDefinition raidMap = RaidMaps.byId(mapId).orElse(null);
        if (raidMap == null) {
            source.sendFailure(Component.literal("Unknown raid map '" + mapId + "'. Available maps: " + RaidMaps.availableMapIds()));
            return null;
        }

        if (raidMap.source().authoringBounds().isEmpty()) {
            source.sendFailure(Component.literal("Raid map '" + mapId + "' does not define marker authoring bounds."));
            return null;
        }

        return raidMap;
    }
}
