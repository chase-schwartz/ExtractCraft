package com.chaseschwartz.extractcraft.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ExtractRaiderEntity extends Monster {
    private static final EntityDataAccessor<String> DATA_ROLE = SynchedEntityData.defineId(ExtractRaiderEntity.class, EntityDataSerializers.STRING);
    private static final String ROLE_TAG = "ExtractCraftRaiderRole";
    private static final String HAS_HOME_TAG = "ExtractCraftRaiderHasHome";
    private static final String HOME_X_TAG = "ExtractCraftRaiderHomeX";
    private static final String HOME_Y_TAG = "ExtractCraftRaiderHomeY";
    private static final String HOME_Z_TAG = "ExtractCraftRaiderHomeZ";
    private static final double RETURN_HOME_SPEED = 1.05D;
    private static final double STATIONARY_HOME_TOLERANCE = 1.25D;

    private BlockPos homePosition;

    public ExtractRaiderEntity(EntityType<? extends ExtractRaiderEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, RaiderRole.fallback().id());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReturnHomeGoal(this));
        this.goalSelector.addGoal(2, new RoleAwareMeleeAttackGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new RoleAwareStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) {
            this.serverRoleTick();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(ROLE_TAG, getRaiderRole().id());
        if (homePosition != null) {
            tag.putBoolean(HAS_HOME_TAG, true);
            tag.putInt(HOME_X_TAG, homePosition.getX());
            tag.putInt(HOME_Y_TAG, homePosition.getY());
            tag.putInt(HOME_Z_TAG, homePosition.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        RaiderRole loadedRole = tag.contains(ROLE_TAG, Tag.TAG_STRING)
                ? RaiderRole.fromSavedName(tag.getString(ROLE_TAG))
                : RaiderRole.fallback();
        setRaiderRole(loadedRole);
        if (tag.getBoolean(HAS_HOME_TAG)
                && tag.contains(HOME_X_TAG, Tag.TAG_INT)
                && tag.contains(HOME_Y_TAG, Tag.TAG_INT)
                && tag.contains(HOME_Z_TAG, Tag.TAG_INT)) {
            setHomePosition(new BlockPos(tag.getInt(HOME_X_TAG), tag.getInt(HOME_Y_TAG), tag.getInt(HOME_Z_TAG)));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public RaiderRole getRaiderRole() {
        return RaiderRole.fromSavedName(this.entityData.get(DATA_ROLE));
    }

    public void setRaiderRole(RaiderRole role) {
        RaiderRole safeRole = role == null ? RaiderRole.fallback() : role;
        this.entityData.set(DATA_ROLE, safeRole.id());
        updateDebugName();
    }

    public void configureRoleProfile(RaiderRole role, BlockPos homePosition) {
        setRaiderRole(role);
        setHomePosition(homePosition == null ? this.blockPosition() : homePosition);
    }

    public BlockPos getHomePosition() {
        return homePosition;
    }

    public void setHomePosition(BlockPos homePosition) {
        this.homePosition = homePosition == null ? null : homePosition.immutable();
    }

    private void serverRoleTick() {
        if (homePosition == null) {
            setHomePosition(this.blockPosition());
        }

        RaiderRole role = getRaiderRole();
        LivingEntity target = this.getTarget();
        if (target != null && role.isLeashed() && isTargetBeyondLeash(target, role)) {
            this.setTarget(null);
        }
        if (role.isStationary() && homePosition != null && distanceToHomeSqr() <= STATIONARY_HOME_TOLERANCE * STATIONARY_HOME_TOLERANCE) {
            this.getNavigation().stop();
        }
    }

    private boolean shouldReturnHome() {
        RaiderRole role = getRaiderRole();
        if (role.isStationary()) {
            return homePosition != null && distanceToHomeSqr() > STATIONARY_HOME_TOLERANCE * STATIONARY_HOME_TOLERANCE;
        }
        return role.isLeashed() && homePosition != null && distanceToHomeSqr() > role.leashRadius() * role.leashRadius();
    }

    private boolean isTargetBeyondLeash(LivingEntity target, RaiderRole role) {
        if (homePosition == null || target == null) {
            return false;
        }
        double allowed = role.leashRadius() + 4.0D;
        return target.blockPosition().distSqr(homePosition) > allowed * allowed;
    }

    private double distanceToHomeSqr() {
        if (homePosition == null) {
            return 0.0D;
        }
        return this.blockPosition().distSqr(homePosition);
    }

    private void updateDebugName() {
        this.setCustomName(Component.literal("Raider [" + getRaiderRole().name() + "]"));
        this.setCustomNameVisible(true);
    }

    private static final class RoleAwareMeleeAttackGoal extends MeleeAttackGoal {
        private final ExtractRaiderEntity raider;

        private RoleAwareMeleeAttackGoal(ExtractRaiderEntity raider, double speedModifier) {
            super(raider, speedModifier, false);
            this.raider = raider;
        }

        @Override
        public boolean canUse() {
            return !raider.getRaiderRole().isStationary() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !raider.getRaiderRole().isStationary() && super.canContinueToUse();
        }
    }

    private static final class RoleAwareStrollGoal extends WaterAvoidingRandomStrollGoal {
        private final ExtractRaiderEntity raider;

        private RoleAwareStrollGoal(ExtractRaiderEntity raider, double speedModifier) {
            super(raider, speedModifier);
            this.raider = raider;
        }

        @Override
        public boolean canUse() {
            return !raider.getRaiderRole().isStationary() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !raider.getRaiderRole().isStationary() && super.canContinueToUse();
        }
    }

    private static final class ReturnHomeGoal extends Goal {
        private final ExtractRaiderEntity raider;

        private ReturnHomeGoal(ExtractRaiderEntity raider) {
            this.raider = raider;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return raider.shouldReturnHome();
        }

        @Override
        public boolean canContinueToUse() {
            return raider.shouldReturnHome() && !raider.getNavigation().isDone();
        }

        @Override
        public void start() {
            moveHome();
        }

        @Override
        public void tick() {
            if (raider.tickCount % 20 == 0) {
                moveHome();
            }
        }

        private void moveHome() {
            BlockPos home = raider.getHomePosition();
            if (home == null) {
                return;
            }
            raider.setTarget(null);
            raider.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, RETURN_HOME_SPEED);
        }
    }
}
