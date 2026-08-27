package com.wc3model2mc.entity;

import com.wc3model2mc.mdx.animation.MdxAnimationSource;
import com.wc3model2mc.mdx.animation.MdxLightingSource;
import com.wc3model2mc.mdx.animation.MdxModelSource;
import com.wc3model2mc.mdx.animation.MdxModelScaleSource;
import com.wc3model2mc.registry.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import static com.wc3model2mc.entity.CommonEntityConstants.*;

/** Stationary effect entity rendered with a camera-facing MDX model. */
public class BillboardMdxProjectile extends StationaryMdxProjectile
        implements MdxAnimationSource, MdxModelScaleSource, MdxModelSource, MdxLightingSource {

    public static final float DEFAULT_MODEL_SCALE = 1.0F / 96.0F;
    public static final float MAX_MODEL_SCALE = 16.0F;
    public static final ResourceLocation DEFAULT_MODEL_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "wc3model2mc",
                    "wc3model/genshin_klee/keli_bomb/keli_bomb.mdx"
            );

    private static final EntityDataAccessor<String> ANIMATION_NAME =
            SynchedEntityData.defineId(BillboardMdxProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIMATION_START_TICK =
            SynchedEntityData.defineId(BillboardMdxProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MODEL_SCALE =
            SynchedEntityData.defineId(BillboardMdxProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> MODEL_ID =
            SynchedEntityData.defineId(BillboardMdxProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> AFFECTED_BY_LIGHT =
            SynchedEntityData.defineId(BillboardMdxProjectile.class, EntityDataSerializers.BOOLEAN);

    public BillboardMdxProjectile(
            EntityType<? extends BillboardMdxProjectile> entityType,
            Level level
    ) {
        super(entityType, level);
    }

    public BillboardMdxProjectile(Level level, LivingEntity owner) {
        super(ModEntityTypes.BILLBOARD_MDX_PROJECTILE.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        entityData.define(ANIMATION_NAME, DEFAULT_ANIMATION);
        entityData.define(ANIMATION_START_TICK, 0);
        entityData.define(MODEL_SCALE, DEFAULT_MODEL_SCALE);
        entityData.define(MODEL_ID, DEFAULT_MODEL_ID.toString());
        entityData.define(AFFECTED_BY_LIGHT, true);
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

    public void restartMdxAnimation() {
        entityData.set(ANIMATION_START_TICK, tickCount);
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(ANIMATION_NBT, getMdxAnimationName());
        tag.putInt(
                ANIMATION_ELAPSED_NBT,
                Math.max(0, tickCount - getMdxAnimationStartTick())
        );
        tag.putFloat(MODEL_SCALE_NBT, getMdxModelScale());
        tag.putString(MODEL_ID_NBT, getMdxModelId().toString());
        tag.putBoolean(AFFECTED_BY_LIGHT_NBT, isMdxAffectedByLight());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

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
