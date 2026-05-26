package com.chaseschwartz.extractcraft.raid.map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record RaidExtractionZone(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
    public boolean contains(ServerPlayer player) {
        return contains(player.position());
    }

    public boolean contains(Vec3 position) {
        return position.x >= minX && position.x <= maxX
                && position.y >= minY && position.y <= maxY
                && position.z >= minZ && position.z <= maxZ;
    }
}
