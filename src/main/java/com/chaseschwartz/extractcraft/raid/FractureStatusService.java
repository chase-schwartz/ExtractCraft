package com.chaseschwartz.extractcraft.raid;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.chaseschwartz.extractcraft.ExtractCraft;
import com.chaseschwartz.extractcraft.network.FractureStateSyncPayload;
import com.chaseschwartz.extractcraft.raid.inventory.QuickUseService;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FractureStatusService {
    private static final ResourceLocation MOVEMENT_PENALTY_ID = ResourceLocation.fromNamespaceAndPath(ExtractCraft.MODID, "fracture_movement_penalty");
    private static final AttributeModifier MOVEMENT_PENALTY = new AttributeModifier(
            MOVEMENT_PENALTY_ID,
            -0.25D,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final Set<UUID> FRACTURED = new HashSet<>();

    private FractureStatusService() {
    }

    public static boolean fractured(ServerPlayer player) {
        return player != null && FRACTURED.contains(player.getUUID());
    }

    public static void rollForDamage(ServerPlayer player, DamageSource source, float damage) {
        if (player == null || !RaidManager.isInRaid(player) || fractured(player) || damage < 6.0F || shouldSkip(source)) {
            return;
        }

        double chance = damage >= 10.0F ? 0.12D : 0.05D;
        if (source != null && source.is(DamageTypes.FALL)) {
            chance += 0.10D;
        }
        if (source != null && (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION))) {
            chance += 0.15D;
        }

        if (player.getRandom().nextDouble() < chance) {
            apply(player, true);
        }
    }

    public static boolean apply(ServerPlayer player, boolean notify) {
        if (player == null || !RaidManager.isInRaid(player)) {
            return false;
        }
        boolean changed = FRACTURED.add(player.getUUID());
        applyEffects(player);
        sync(player);
        QuickUseService.syncOptions(player);
        if (changed && notify) {
            player.sendSystemMessage(Component.literal("Fracture applied."));
        }
        return changed;
    }

    public static boolean clear(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        boolean removed = FRACTURED.remove(player.getUUID());
        removeEffects(player);
        sync(player);
        QuickUseService.syncOptions(player);
        return removed;
    }

    public static int clear(UUID playerId, MinecraftServer server) {
        boolean removed = FRACTURED.remove(playerId);
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            removeEffects(player);
            sync(player);
            QuickUseService.syncOptions(player);
        }
        return removed ? 1 : 0;
    }

    public static int clearAll(MinecraftServer server) {
        int count = FRACTURED.size();
        for (UUID playerId : Set.copyOf(FRACTURED)) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                removeEffects(player);
                sync(player);
                QuickUseService.syncOptions(player);
            }
        }
        FRACTURED.clear();
        return count;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || FRACTURED.isEmpty()) {
            return;
        }

        for (UUID playerId : Set.copyOf(FRACTURED)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                FRACTURED.remove(playerId);
                continue;
            }
            if (!RaidManager.isInRaid(player) || !player.isAlive()) {
                clear(player);
                continue;
            }
            applyEffects(player);
        }
    }

    private static void applyEffects(ServerPlayer player) {
        player.setSprinting(false);
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null && !movementSpeed.hasModifier(MOVEMENT_PENALTY_ID)) {
            movementSpeed.addOrUpdateTransientModifier(MOVEMENT_PENALTY);
        }
    }

    private static void removeEffects(ServerPlayer player) {
        player.setSprinting(false);
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_PENALTY_ID);
        }
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FractureStateSyncPayload(fractured(player)));
    }

    private static boolean shouldSkip(DamageSource source) {
        return source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
}
