package com.wc3model2mc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wc3model2mc.client.mdx.CpuSkinnedMdxRenderBackend;
import com.wc3model2mc.client.mdx.MdxModelRepository;
import com.wc3model2mc.client.mdx.MdxRenderBackend;
import com.wc3model2mc.entity.AnimatedMdxProjectile;
import com.wc3model2mc.mdx.animation.MdxAnimationSample;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders a stationary animated 3D MDX projectile using its synchronized rotation. */
public final class AnimatedMdxProjectileRenderer
        extends EntityRenderer<AnimatedMdxProjectile> {
    private final MdxRenderBackend backend;

    public AnimatedMdxProjectileRenderer(EntityRendererProvider.Context context) {
        this(context, CpuSkinnedMdxRenderBackend.INSTANCE);
    }

    AnimatedMdxProjectileRenderer(
            EntityRendererProvider.Context context,
            MdxRenderBackend backend
    ) {
        super(context);
        this.backend = backend;
        shadowRadius = 0.0F;
        shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(
            AnimatedMdxProjectile projectile,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        return super.shouldRender(projectile, frustum, cameraX, cameraY, cameraZ)
                || MdxRenderOffsetCulling.shouldRenderOffset(
                        projectile,
                        projectile,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ
                );
    }

    @Override
    public void render(
            AnimatedMdxProjectile projectile,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        MdxRenderModel model = MdxModelRepository.get(projectile.getMdxModelId());
        if (model != null && !model.isEmpty()) {
            poseStack.pushPose();
            try {
                poseStack.translate(
                        projectile.getMdxModelOffsetX(),
                        projectile.getMdxModelOffsetY(),
                        projectile.getMdxModelOffsetZ()
                );
                float projectileYaw = projectile.getHorizontalFacingDeg();
                float projectilePitch = projectile.getVerticalFacingDeg();
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - projectileYaw));
                // Warcraft III models conventionally face local +X. Rotating around
                // X would therefore roll the model along its direction of travel;
                // pitch must rotate around local -Z so Minecraft's positive XRot
                // (looking down) tilts the whole model and its trail downward.
                poseStack.mulPose(Axis.ZN.rotationDegrees(projectilePitch));
                float modelScale = projectile.getMdxModelScale();
                poseStack.scale(modelScale, modelScale, modelScale);

                backend.render(
                        model,
                        MdxAnimationSample.from(projectile, projectile, partialTick),
                        poseStack,
                        buffers,
                        projectile.isMdxAffectedByLight()
                                ? packedLight
                                : LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        MdxProjectileLodManager.detailFor(projectile),
                        projectile.getMdxFadeAlpha(partialTick)
                );
            } finally {
                poseStack.popPose();
            }
        }
        super.render(projectile, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AnimatedMdxProjectile projectile) {
        MdxRenderModel model = MdxModelRepository.get(projectile.getMdxModelId());
        return model == null
                ? MissingTextureAtlasSprite.getLocation()
                : model.primaryTexture();
    }
}
