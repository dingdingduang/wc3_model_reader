package com.wc3model2mc.registry;

import com.wc3model2mc.WC3Model2MC;
import com.wc3model2mc.entity.AnimatedMdxEntity;
import com.wc3model2mc.entity.AnimatedMdxProjectile;
import com.wc3model2mc.entity.BillboardMdxProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WC3Model2MC.MODID);

    public static final RegistryObject<EntityType<AnimatedMdxEntity>> ANIMATED_MDX_ENTITY =
            ENTITY_TYPES.register(
                    "animated_mdx_entity",
                    () -> EntityType.Builder
                            .of(AnimatedMdxEntity::new, MobCategory.CREATURE)
                            .sized(0.65F, 1.9F)
                            .clientTrackingRange(10)
                            .build(WC3Model2MC.MODID + ":animated_mdx_entity")
            );

    public static final RegistryObject<EntityType<BillboardMdxProjectile>> BILLBOARD_MDX_PROJECTILE =
            ENTITY_TYPES.register(
                    "billboard_mdx_projectile",
                    () -> EntityType.Builder
                            .<BillboardMdxProjectile>of(
                                    BillboardMdxProjectile::new,
                                    MobCategory.MISC
                            )
                            .sized(0.35F, 0.35F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(WC3Model2MC.MODID + ":billboard_mdx_projectile")
            );

    public static final RegistryObject<EntityType<AnimatedMdxProjectile>> ANIMATED_MDX_PROJECTILE =
            ENTITY_TYPES.register(
                    "animated_mdx_projectile",
                    () -> EntityType.Builder
                            .<AnimatedMdxProjectile>of(
                                    AnimatedMdxProjectile::new,
                                    MobCategory.MISC
                            )
                            .sized(0.35F, 0.35F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(WC3Model2MC.MODID + ":animated_mdx_projectile")
            );

    private ModEntityTypes() {
    }
}
