package com.wc3model2mc.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only quality settings for dense MDX effect scenes. */
public final class MdxClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ADAPTIVE_PROJECTILE_LOD = BUILDER
            .comment("Reduce secondary MDX layers and particles when many projectiles are nearby.")
            .define("adaptiveProjectileLod", false);

    public static final ForgeConfigSpec.IntValue LOD_ACTIVATION_COUNT = BUILDER
            .comment("Adaptive LOD starts when at least this many MDX projectiles are nearby.")
            .defineInRange("projectileLodActivationCount", 50, 1, 4096);

    public static final ForgeConfigSpec.IntValue FULL_DETAIL_COUNT = BUILDER
            .comment("Nearest projectiles kept at full MDX detail after adaptive LOD starts.")
            .defineInRange("fullDetailProjectileCount", 4, 0, 4096);

    public static final ForgeConfigSpec.IntValue MEDIUM_DETAIL_COUNT = BUILDER
            .comment("Additional nearest projectiles rendered at medium MDX detail.")
            .defineInRange("mediumDetailProjectileCount", 4, 0, 4096);

    public static final ForgeConfigSpec.DoubleValue LOD_RADIUS = BUILDER
            .comment("Radius in blocks used to count and rank MDX projectiles.")
            .defineInRange("projectileLodRadius", 64.0D, 1.0D, 1024.0D);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private MdxClientConfig() {
    }
}
