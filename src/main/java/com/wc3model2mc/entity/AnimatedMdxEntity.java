package com.wc3model2mc.entity;

import com.wc3model2mc.mdx.animation.MdxAnimationSource;
import com.wc3model2mc.mdx.animation.MdxAnimationPlaybackSource;
import com.wc3model2mc.mdx.animation.MdxEntityLifecycleSource;
import com.wc3model2mc.mdx.animation.MdxFadeSource;
import com.wc3model2mc.mdx.animation.MdxLightingSource;
import com.wc3model2mc.mdx.animation.MdxModelOffsetSource;
import com.wc3model2mc.mdx.animation.MdxModelSource;
import com.wc3model2mc.mdx.animation.MdxModelScaleSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

import static com.wc3model2mc.entity.CommonEntityConstants.*;
import static com.wc3model2mc.util.EntityMethodsWc3ModelReader.*;
import static com.wc3model2mc.util.EntityMethodsWc3ModelReader.getEntityVerticalFacingDeg;

/** Minimal standard mob whose MDX animation request is synchronized to clients. */
public class AnimatedMdxEntity extends PathfinderMob
        implements MdxAnimationSource, MdxAnimationPlaybackSource, MdxModelScaleSource,
        MdxModelSource, MdxModelOffsetSource, MdxLightingSource,
        MdxEntityLifecycleSource, MdxFadeSource {
    public static final float DEFAULT_MODEL_SCALE = 1.0F / 128.0F;
    public static final float MAX_MODEL_SCALE = 16.0F;
    public static final ResourceLocation DEFAULT_MODEL_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "wc3model2mc",
                    "wc3model/uuz/uuz.mdx"
            );

    private static final EntityDataAccessor<Float> POS_HORIZONTAL_DEG = SynchedEntityData.<Float>defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> POS_VERTICAL_DEG = SynchedEntityData.<Float>defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<String> ANIMATION_NAME =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIMATION_START_TICK =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MODEL_SCALE =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_OFFSET_X =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_OFFSET_Y =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MODEL_OFFSET_Z =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> MODEL_ID =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> AFFECTED_BY_LIGHT =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ANIMATION_LOOPING =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETURN_TO_STAND =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RANGE_START_MILLIS =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RANGE_END_MILLIS =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANIMATION_SPEED =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFESPAN_TICKS =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> LIFESPAN_START_GAME_TIME =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> FADE_ENABLED =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FADE_FROM_BIRTH =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FADE_DURATION_TICKS =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> FOLLOW_TARGET =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> FOLLOW_DURATION_TICKS =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FOLLOW_TARGET_ID =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FOLLOW_INSTANT_MOVE =
            SynchedEntityData.defineId(AnimatedMdxEntity.class, EntityDataSerializers.BOOLEAN);

    private int lifespanElapsedTicks;
    private int followElapsedTicks;

    public AnimatedMdxEntity(EntityType<? extends AnimatedMdxEntity> entityType, Level level) {
        super(entityType, level);
        resetMdxLifespanClock(0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POS_HORIZONTAL_DEG, 0f);
        entityData.define(POS_VERTICAL_DEG, 0f);

        entityData.define(ANIMATION_NAME, DEFAULT_ANIMATION);
        entityData.define(ANIMATION_START_TICK, 0);
        entityData.define(MODEL_SCALE, DEFAULT_MODEL_SCALE);
        entityData.define(MODEL_OFFSET_X, 0.0F);
        entityData.define(MODEL_OFFSET_Y, 0.0F);
        entityData.define(MODEL_OFFSET_Z, 0.0F);
        entityData.define(MODEL_ID, DEFAULT_MODEL_ID.toString());
        entityData.define(AFFECTED_BY_LIGHT, true);
        entityData.define(ANIMATION_LOOPING, true);
        entityData.define(RETURN_TO_STAND, false);
        entityData.define(RANGE_START_MILLIS, DEFAULT_RANGE_START_MILLIS);
        entityData.define(RANGE_END_MILLIS, DEFAULT_RANGE_END_MILLIS);
        entityData.define(ANIMATION_SPEED, DEFAULT_SPEED);
        entityData.define(LIFESPAN_TICKS, DEFAULT_LIFESPAN_TICKS);
        entityData.define(LIFESPAN_START_GAME_TIME, 0L);
        entityData.define(FADE_ENABLED, false);
        entityData.define(FADE_FROM_BIRTH, false);
        entityData.define(FADE_DURATION_TICKS, DEFAULT_FADE_DURATION_TICKS);
        entityData.define(FOLLOW_TARGET, Optional.empty());
        entityData.define(FOLLOW_DURATION_TICKS, NO_FOLLOW_TICKS);
        entityData.define(FOLLOW_TARGET_ID, -1);
        entityData.define(FOLLOW_INSTANT_MOVE, false);
    }

    @Override
    public String getMdxAnimationName() {
        return entityData.get(ANIMATION_NAME);
    }

    @Override
    public int getMdxAnimationStartTick() {
        return entityData.get(ANIMATION_START_TICK);
    }

    @Override
    public void playMdxAnimation(String animationName) {
        String checkedName = validateAnimationName(animationName);
        if (!checkedName.equals(entityData.get(ANIMATION_NAME))) {
            entityData.set(ANIMATION_NAME, checkedName);
            entityData.set(ANIMATION_START_TICK, tickCount);
        }
    }

    /** Restarts a sequence even when its requested name has not changed. */
    public void restartMdxAnimation() {
        entityData.set(ANIMATION_START_TICK, tickCount);
    }

    @Override
    public boolean isMdxAnimationLooping() {
        return entityData.get(ANIMATION_LOOPING);
    }

    @Override
    public void setMdxAnimationLooping(boolean looping) {
        entityData.set(ANIMATION_LOOPING, looping);
    }

    @Override
    public boolean shouldMdxReturnToStand() {
        return entityData.get(RETURN_TO_STAND);
    }

    @Override
    public void setMdxReturnToStand(boolean returnToStand) {
        entityData.set(RETURN_TO_STAND, returnToStand);
    }

    @Override
    public float getMdxAnimationRangeStartMillis() {
        return entityData.get(RANGE_START_MILLIS);
    }

    @Override
    public float getMdxAnimationRangeEndMillis() {
        return entityData.get(RANGE_END_MILLIS);
    }

    @Override
    public void setMdxAnimationRangeMillis(float startMillis, float endMillis) {
        MdxAnimationPlaybackSource.validateRange(startMillis, endMillis);
        entityData.set(RANGE_START_MILLIS, startMillis);
        entityData.set(RANGE_END_MILLIS, endMillis);
    }

    @Override
    public float getMdxAnimationSpeed() {
        return entityData.get(ANIMATION_SPEED);
    }

    @Override
    public void setMdxAnimationSpeed(float speed) {
        MdxAnimationPlaybackSource.validateSpeed(speed);
        entityData.set(ANIMATION_SPEED, speed);
    }

    @Override
    public float getMdxModelScale() {
        return entityData.get(MODEL_SCALE);
    }

    @Override
    public void setMdxModelScale(float modelScale) {
        validateModelScale(modelScale);
        entityData.set(MODEL_SCALE, modelScale);
    }

    @Override
    public float getMdxModelOffsetX() {
        return entityData.get(MODEL_OFFSET_X);
    }

    @Override
    public float getMdxModelOffsetY() {
        return entityData.get(MODEL_OFFSET_Y);
    }

    @Override
    public float getMdxModelOffsetZ() {
        return entityData.get(MODEL_OFFSET_Z);
    }

    @Override
    public void setMdxModelOffset(float offsetX, float offsetY, float offsetZ) {
        MdxModelOffsetSource.validateOffset(offsetX, offsetY, offsetZ);
        entityData.set(MODEL_OFFSET_X, offsetX);
        entityData.set(MODEL_OFFSET_Y, offsetY);
        entityData.set(MODEL_OFFSET_Z, offsetZ);
    }

    @Override
    public ResourceLocation getMdxModelId() {
        ResourceLocation modelId = ResourceLocation.tryParse(entityData.get(MODEL_ID));
        return modelId == null ? DEFAULT_MODEL_ID : modelId;
    }

    @Override
    public void setMdxModelId(ResourceLocation modelId) {
        if (modelId == null || !modelId.getPath().endsWith(".mdx")) {
            throw new IllegalArgumentException("modelId must identify an .mdx resource");
        }
        entityData.set(MODEL_ID, modelId.toString());
    }

    @Override
    public boolean isMdxAffectedByLight() {
        return entityData.get(AFFECTED_BY_LIGHT);
    }

    @Override
    public void setMdxAffectedByLight(boolean affectedByLight) {
        entityData.set(AFFECTED_BY_LIGHT, affectedByLight);
    }

    @Override
    public int getMdxLifespanTicks() {
        return entityData.get(LIFESPAN_TICKS);
    }

    @Override
    public void setMdxLifespanTicks(int lifespanTicks) {
        MdxEntityLifecycleSource.validateDurationTicks(lifespanTicks, "lifespanTicks");
        entityData.set(LIFESPAN_TICKS, lifespanTicks);
        resetMdxLifespanClock(0);
    }

    @Override
    public boolean isMdxFadeEnabled() {
        return entityData.get(FADE_ENABLED);
    }

    @Override
    public void setMdxFadeEnabled(boolean enabled) {
        entityData.set(FADE_ENABLED, enabled);
    }

    @Override
    public boolean isMdxFadeFromBirth() {
        return entityData.get(FADE_FROM_BIRTH);
    }

    @Override
    public void setMdxFadeFromBirth(boolean fromBirth) {
        entityData.set(FADE_FROM_BIRTH, fromBirth);
    }

    @Override
    public int getMdxFadeDurationTicks() {
        return entityData.get(FADE_DURATION_TICKS);
    }

    @Override
    public void setMdxFadeDurationTicks(int durationTicks) {
        MdxFadeSource.validateFadeDurationTicks(durationTicks);
        entityData.set(FADE_DURATION_TICKS, durationTicks);
    }

    @Override
    public float getMdxLifespanElapsedTicks(float partialTick) {
        long elapsed = level().getGameTime() - entityData.get(LIFESPAN_START_GAME_TIME);
        if (elapsed < 0L) {
            return 0.0F;
        }
        float fraction = Float.isFinite(partialTick)
                ? Math.max(0.0F, Math.min(1.0F, partialTick))
                : 0.0F;
        return Math.min((float) Integer.MAX_VALUE, elapsed + fraction);
    }

    @Override
    public Optional<UUID> getMdxFollowTargetUuid() {
        return entityData.get(FOLLOW_TARGET);
    }

    @Override
    public int getMdxFollowDurationTicks() {
        return entityData.get(FOLLOW_DURATION_TICKS);
    }

    @Override
    public void setMdxFollowTarget(@Nullable Entity target, int durationTicks) {
        setMdxFollowTarget(target, durationTicks, false);
    }

    @Override
    public void setMdxFollowTarget(
            @Nullable Entity target,
            int durationTicks,
            boolean instantMoveToTarget
    ) {
        MdxEntityLifecycleSource.validateDurationTicks(durationTicks, "durationTicks");
        if (durationTicks == NO_FOLLOW_TICKS) {
            clearFollowTargetState();
            return;
        }
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null while following is enabled");
        }
        if (target == this) {
            throw new IllegalArgumentException("an entity cannot follow itself");
        }
        if (target.level() != level()) {
            throw new IllegalArgumentException("follow target must be in the same level");
        }
        entityData.set(FOLLOW_TARGET, Optional.of(target.getUUID()));
        entityData.set(FOLLOW_DURATION_TICKS, durationTicks);
        entityData.set(FOLLOW_TARGET_ID, target.getId());
        entityData.set(FOLLOW_INSTANT_MOVE, instantMoveToTarget);
        followElapsedTicks = 0;
    }

    @Override
    public boolean isMdxInstantMoveToTarget() {
        return entityData.get(FOLLOW_INSTANT_MOVE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClientInstantMoveToTarget();
        } else {
            tickMdxLifecycle();
        }
    }

    private void tickClientInstantMoveToTarget() {
        if (!isMdxFollowingEntity() || !isMdxInstantMoveToTarget()) {
            return;
        }
        int targetId = entityData.get(FOLLOW_TARGET_ID);
        Entity target = targetId < 0 ? null : level().getEntity(targetId);
        if (target == null || target == this || target.isRemoved()) {
            return;
        }

        xo = target.xo;
        yo = target.yo;
        zo = target.zo;
        setPos(target.getX(), target.getY(), target.getZ());
    }

    private void tickMdxLifecycle() {
        int lifespanTicks = getMdxLifespanTicks();
        if (lifespanTicks != INFINITE_TICKS
                && (lifespanTicks == 0 || ++lifespanElapsedTicks >= lifespanTicks)) {
            discard();
            return;
        }

        int durationTicks = getMdxFollowDurationTicks();
        if (durationTicks == NO_FOLLOW_TICKS) {
            return;
        }
        if (durationTicks != INFINITE_TICKS && followElapsedTicks >= durationTicks) {
            clearFollowTargetState();
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            Entity target = getMdxFollowTargetUuid()
                    .map(serverLevel::getEntity)
                    .filter(candidate -> candidate != this && !candidate.isRemoved())
                    .orElse(null);
            int targetId = target == null ? -1 : target.getId();
            if (entityData.get(FOLLOW_TARGET_ID) != targetId) {
                entityData.set(FOLLOW_TARGET_ID, targetId);
            }
            if (target != null) {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0.0F;
                setPos(target.getX(), target.getY(), target.getZ());
            }
        }
        if (durationTicks != INFINITE_TICKS
                && ++followElapsedTicks >= durationTicks) {
            clearFollowTargetState();
        }
    }

    private void clearFollowTargetState() {
        entityData.set(FOLLOW_TARGET, Optional.empty());
        entityData.set(FOLLOW_DURATION_TICKS, NO_FOLLOW_TICKS);
        entityData.set(FOLLOW_TARGET_ID, -1);
        entityData.set(FOLLOW_INSTANT_MOVE, false);
        followElapsedTicks = 0;
    }

    private void resetMdxLifespanClock(int elapsedTicks) {
        lifespanElapsedTicks = Math.max(0, elapsedTicks);
        entityData.set(
                LIFESPAN_START_GAME_TIME,
                level().getGameTime() - lifespanElapsedTicks
        );
    }

    public void setHorizontalFacingDeg(float deg) {
        validateFacingDegrees(deg, "horizontal facing");
        entityData.set(POS_HORIZONTAL_DEG, deg);
        setEntityHorizontalFacingDeg(this, deg);
        setEntityHeadHorizontalFacingDeg(this, deg);
        setEntityBodyHorizontalFacingDeg(this, deg);
        yRotO = deg;
        yHeadRotO = deg;
        yBodyRotO = deg;
    }

    public void setVerticalFacingDeg(float deg) {
        validateFacingDegrees(deg, "vertical facing");
        entityData.set(POS_VERTICAL_DEG, deg);
        setEntityVerticalFacingDeg(this, deg);
        xRotO = deg;
    }

    public float getHorizontalFacingDeg() {
        return entityData.get(POS_HORIZONTAL_DEG);
    }

    public float getVerticalFacingDeg() {
        return entityData.get(POS_VERTICAL_DEG);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (POS_HORIZONTAL_DEG.equals(accessor)) {
            float yaw = getHorizontalFacingDeg();
            setEntityHorizontalFacingDeg(this, yaw);
            setEntityHeadHorizontalFacingDeg(this, yaw);
            setEntityBodyHorizontalFacingDeg(this, yaw);
            yRotO = yaw;
            yHeadRotO = yaw;
            yBodyRotO = yaw;
        } else if (POS_VERTICAL_DEG.equals(accessor)) {
            float pitch = getVerticalFacingDeg();
            setEntityVerticalFacingDeg(this, pitch);
            xRotO = pitch;
        }
    }

    private static void validateFacingDegrees(float degrees, String label) {
        if (!Float.isFinite(degrees)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(POS_HORIZONTAL_FACING_DEG, getHorizontalFacingDeg());
        tag.putFloat(POS_VERTICAL_Facing_DEG, getVerticalFacingDeg());

        tag.putString(ANIMATION_NBT, getMdxAnimationName());
        tag.putInt(
                ANIMATION_ELAPSED_NBT,
                Math.max(0, tickCount - getMdxAnimationStartTick())
        );
        tag.putFloat(MODEL_SCALE_NBT, getMdxModelScale());
        tag.putFloat(MODEL_OFFSET_X_NBT, getMdxModelOffsetX());
        tag.putFloat(MODEL_OFFSET_Y_NBT, getMdxModelOffsetY());
        tag.putFloat(MODEL_OFFSET_Z_NBT, getMdxModelOffsetZ());
        tag.putString(MODEL_ID_NBT, getMdxModelId().toString());
        tag.putBoolean(AFFECTED_BY_LIGHT_NBT, isMdxAffectedByLight());
        tag.putBoolean(ANIMATION_LOOPING_NBT, isMdxAnimationLooping());
        tag.putBoolean(RETURN_TO_STAND_NBT, shouldMdxReturnToStand());
        tag.putFloat(RANGE_START_NBT, getMdxAnimationRangeStartMillis());
        tag.putFloat(RANGE_END_NBT, getMdxAnimationRangeEndMillis());
        tag.putFloat(ANIMATION_SPEED_NBT, getMdxAnimationSpeed());
        tag.putInt(LIFESPAN_NBT, getMdxLifespanTicks());
        tag.putInt(LIFESPAN_ELAPSED_NBT, lifespanElapsedTicks);
        tag.putBoolean(FADE_ENABLED_NBT, isMdxFadeEnabled());
        tag.putBoolean(FADE_FROM_BIRTH_NBT, isMdxFadeFromBirth());
        tag.putInt(FADE_DURATION_NBT, getMdxFadeDurationTicks());
        getMdxFollowTargetUuid().ifPresent(uuid -> tag.putUUID(FOLLOW_TARGET_NBT, uuid));
        tag.putInt(FOLLOW_DURATION_NBT, getMdxFollowDurationTicks());
        tag.putInt(FOLLOW_ELAPSED_NBT, followElapsedTicks);
        tag.putBoolean(FOLLOW_INSTANT_MOVE_NBT, isMdxInstantMoveToTarget());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        float savedTemp;

        savedTemp = tag.contains(POS_HORIZONTAL_FACING_DEG)
                ? tag.getFloat(POS_HORIZONTAL_FACING_DEG)
                : 0;
        setHorizontalFacingDeg(savedTemp);

        savedTemp = tag.contains(POS_VERTICAL_Facing_DEG)
                ? tag.getFloat(POS_VERTICAL_Facing_DEG)
                : 0;
        setVerticalFacingDeg(savedTemp);

        String animation = tag.contains(ANIMATION_NBT)
                ? validateAnimationName(tag.getString(ANIMATION_NBT))
                : DEFAULT_ANIMATION;
        int elapsed = Math.max(0, tag.getInt(ANIMATION_ELAPSED_NBT));
        entityData.set(ANIMATION_NAME, animation);
        entityData.set(ANIMATION_START_TICK, tickCount - elapsed);
        float savedScale = tag.contains(MODEL_SCALE_NBT)
                ? tag.getFloat(MODEL_SCALE_NBT)
                : DEFAULT_MODEL_SCALE;
        entityData.set(
                MODEL_SCALE,
                isValidModelScale(savedScale) ? savedScale : DEFAULT_MODEL_SCALE
        );
        setMdxModelOffset(
                MdxModelOffsetSource.finiteOrZero(tag.contains(MODEL_OFFSET_X_NBT)
                        ? tag.getFloat(MODEL_OFFSET_X_NBT) : 0.0F),
                MdxModelOffsetSource.finiteOrZero(tag.contains(MODEL_OFFSET_Y_NBT)
                        ? tag.getFloat(MODEL_OFFSET_Y_NBT) : 0.0F),
                MdxModelOffsetSource.finiteOrZero(tag.contains(MODEL_OFFSET_Z_NBT)
                        ? tag.getFloat(MODEL_OFFSET_Z_NBT) : 0.0F)
        );
        ResourceLocation savedModelId = tag.contains(MODEL_ID_NBT)
                ? ResourceLocation.tryParse(tag.getString(MODEL_ID_NBT))
                : DEFAULT_MODEL_ID;
        entityData.set(
                MODEL_ID,
                savedModelId != null && savedModelId.getPath().endsWith(".mdx")
                        ? savedModelId.toString()
                        : DEFAULT_MODEL_ID.toString()
        );
        entityData.set(
                AFFECTED_BY_LIGHT,
                !tag.contains(AFFECTED_BY_LIGHT_NBT)
                        || tag.getBoolean(AFFECTED_BY_LIGHT_NBT)
        );
        entityData.set(
                ANIMATION_LOOPING,
                !tag.contains(ANIMATION_LOOPING_NBT) || tag.getBoolean(ANIMATION_LOOPING_NBT)
        );
        entityData.set(
                RETURN_TO_STAND,
                tag.contains(RETURN_TO_STAND_NBT) && tag.getBoolean(RETURN_TO_STAND_NBT)
        );
        float startMillis = tag.contains(RANGE_START_NBT)
                ? tag.getFloat(RANGE_START_NBT)
                : DEFAULT_RANGE_START_MILLIS;
        float endMillis = tag.contains(RANGE_END_NBT)
                ? tag.getFloat(RANGE_END_NBT)
                : DEFAULT_RANGE_END_MILLIS;
        if (!MdxAnimationPlaybackSource.isValidRange(startMillis, endMillis)) {
            startMillis = DEFAULT_RANGE_START_MILLIS;
            endMillis = DEFAULT_RANGE_END_MILLIS;
        }
        entityData.set(RANGE_START_MILLIS, startMillis);
        entityData.set(RANGE_END_MILLIS, endMillis);
        float speed = tag.contains(ANIMATION_SPEED_NBT)
                ? tag.getFloat(ANIMATION_SPEED_NBT)
                : DEFAULT_SPEED;
        entityData.set(
                ANIMATION_SPEED,
                MdxAnimationPlaybackSource.isValidSpeed(speed) ? speed : DEFAULT_SPEED
        );
        int lifespanTicks = tag.contains(LIFESPAN_NBT)
                ? tag.getInt(LIFESPAN_NBT)
                : DEFAULT_LIFESPAN_TICKS;
        entityData.set(
                LIFESPAN_TICKS,
                lifespanTicks >= INFINITE_TICKS ? lifespanTicks : DEFAULT_LIFESPAN_TICKS
        );
        resetMdxLifespanClock(Math.max(0, tag.getInt(LIFESPAN_ELAPSED_NBT)));
        entityData.set(
                FADE_ENABLED,
                tag.contains(FADE_ENABLED_NBT) && tag.getBoolean(FADE_ENABLED_NBT)
        );
        entityData.set(
                FADE_FROM_BIRTH,
                tag.contains(FADE_FROM_BIRTH_NBT) && tag.getBoolean(FADE_FROM_BIRTH_NBT)
        );
        int fadeDurationTicks = tag.contains(FADE_DURATION_NBT)
                ? tag.getInt(FADE_DURATION_NBT)
                : DEFAULT_FADE_DURATION_TICKS;
        entityData.set(
                FADE_DURATION_TICKS,
                fadeDurationTicks >= 0 ? fadeDurationTicks : DEFAULT_FADE_DURATION_TICKS
        );

        int followDurationTicks = tag.contains(FOLLOW_DURATION_NBT)
                ? tag.getInt(FOLLOW_DURATION_NBT)
                : NO_FOLLOW_TICKS;
        if (followDurationTicks < INFINITE_TICKS
                || followDurationTicks == NO_FOLLOW_TICKS
                || !tag.hasUUID(FOLLOW_TARGET_NBT)) {
            clearFollowTargetState();
        } else {
            entityData.set(FOLLOW_TARGET, Optional.of(tag.getUUID(FOLLOW_TARGET_NBT)));
            entityData.set(FOLLOW_DURATION_TICKS, followDurationTicks);
            entityData.set(FOLLOW_TARGET_ID, -1);
            entityData.set(
                    FOLLOW_INSTANT_MOVE,
                    tag.contains(FOLLOW_INSTANT_MOVE_NBT)
                            && tag.getBoolean(FOLLOW_INSTANT_MOVE_NBT)
            );
            followElapsedTicks = Math.max(0, tag.getInt(FOLLOW_ELAPSED_NBT));
        }
    }

    private static String validateAnimationName(String animationName) {
        if (animationName == null) {
            throw new IllegalArgumentException("animationName cannot be null");
        }
        String checkedName = animationName.strip();
        if (checkedName.isEmpty() || checkedName.length() > 80) {
            throw new IllegalArgumentException("animationName must contain 1 to 80 characters");
        }
        return checkedName;
    }

    private static void validateModelScale(float modelScale) {
        if (!isValidModelScale(modelScale)) {
            throw new IllegalArgumentException(
                    "modelScale must be finite and in the range (0, " + MAX_MODEL_SCALE + "]"
            );
        }
    }

    private static boolean isValidModelScale(float modelScale) {
        return Float.isFinite(modelScale)
                && modelScale > 0.0F
                && modelScale <= MAX_MODEL_SCALE;
    }
}
