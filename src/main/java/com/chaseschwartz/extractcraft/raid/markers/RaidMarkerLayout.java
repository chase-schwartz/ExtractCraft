package com.chaseschwartz.extractcraft.raid.markers;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;

public record RaidMarkerLayout(String mapId, BlockPos layoutOrigin, List<RaidMarker> markers) {
    public RaidMarkerLayout {
        markers = markers.stream()
                .sorted(Comparator.comparing((RaidMarker marker) -> marker.type().serializedName())
                        .thenComparingInt(marker -> marker.relativePos().getX())
                        .thenComparingInt(marker -> marker.relativePos().getY())
                        .thenComparingInt(marker -> marker.relativePos().getZ()))
                .toList();
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"mapId\": \"").append(escapeJson(mapId)).append("\",\n");
        appendBlockPos(json, "layoutOrigin", layoutOrigin, "  ");
        json.append(",\n");
        json.append("  \"markers\": [\n");
        for (int index = 0; index < markers.size(); index++) {
            RaidMarker marker = markers.get(index);
            json.append("    {\n");
            json.append("      \"type\": \"").append(marker.type().serializedName()).append("\",\n");
            appendBlockPos(json, "relative", marker.relativePos(), "      ");
            json.append("\n");
            json.append("    }");
            if (index < markers.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendBlockPos(StringBuilder json, String key, BlockPos pos, String indent) {
        json.append(indent)
                .append("\"")
                .append(key)
                .append("\": { \"x\": ")
                .append(pos.getX())
                .append(", \"y\": ")
                .append(pos.getY())
                .append(", \"z\": ")
                .append(pos.getZ())
                .append(" }");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
