package com.chaseschwartz.extractcraft.ai;

import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class RaiderDebugService {
    private RaiderDebugService() {
    }

    public static ExtractRaiderEntity spawnNear(ServerPlayer player) {
        return spawnNear(player, RaiderRole.fallback());
    }

    public static ExtractRaiderEntity spawnNear(ServerPlayer player, RaiderRole role) {
        if (player == null) {
            return null;
        }

        RaiderRole safeRole = role == null ? RaiderRole.fallback() : role;
        ServerLevel level = player.serverLevel();
        Vec3 forward = player.getLookAngle().multiply(2.0D, 0.0D, 2.0D);
        if (forward.lengthSqr() < 0.01D) {
            forward = new Vec3(1.5D, 0.0D, 0.0D);
        } else {
            forward = forward.normalize().scale(2.0D);
        }
        Vec3 spawnPosition = player.position().add(forward).add(0.0D, 0.1D, 0.0D);

        ExtractRaiderEntity raider = ExtractCraft.EXTRACT_RAIDER.get().create(level);
        if (raider == null) {
            ExtractCraft.LOGGER.warn("Failed to create ExtractCraft raider near {}", player.getGameProfile().getName());
            return null;
        }
        raider.moveTo(spawnPosition.x, spawnPosition.y, spawnPosition.z, player.getYRot() + 180.0F, 0.0F);
        raider.configureRoleProfile(safeRole, BlockPos.containing(spawnPosition));
        level.addFreshEntity(raider);
        ExtractCraft.LOGGER.info("Spawned ExtractCraft raider {} role={} at [{}, {}, {}] for {}",
                raider.getUUID(),
                raider.getRaiderRole().id(),
                String.format(java.util.Locale.ROOT, "%.2f", raider.getX()),
                String.format(java.util.Locale.ROOT, "%.2f", raider.getY()),
                String.format(java.util.Locale.ROOT, "%.2f", raider.getZ()),
                player.getGameProfile().getName());
        return raider;
    }

    public static int clear(ServerLevel level) {
        if (level == null) {
            return 0;
        }

        int removed = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ExtractRaiderEntity raider && raider.isAlive()) {
                UUID id = raider.getUUID();
                raider.discard();
                removed++;
                ExtractCraft.LOGGER.debug("Removed ExtractCraft raider {}", id);
            }
        }
        ExtractCraft.LOGGER.info("Removed {} ExtractCraft raider(s) from {}", removed, level.dimension().location());
        return removed;
    }
}
