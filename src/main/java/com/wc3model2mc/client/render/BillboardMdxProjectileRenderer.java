package com.wc3model2mc.client.render;

import com.wc3model2mc.client.mdx.CpuSkinnedMdxRenderBackend;
import com.wc3model2mc.client.mdx.MdxModelRepository;
import com.wc3model2mc.client.mdx.MdxRenderBackend;
import com.wc3model2mc.entity.BillboardMdxProjectile;
import com.wc3model2mc.mdx.animation.MdxAnimationSample;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders a projectile using a full yaw/pitch/roll camera-facing billboard. */
public final class BillboardMdxProjectileRenderer
        extends EntityRenderer<BillboardMdxProjectile> {
    private final MdxRenderBackend backend;

    public BillboardMdxProjectileRenderer(EntityRendererProvider.Context context) {
        this(context, CpuSkinnedMdxRenderBackend.INSTANCE);
    }

    BillboardMdxProjectileRenderer(
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
            BillboardMdxProjectile projectile,
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
            BillboardMdxProjectile projectile,
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
                        projectile.getMdxModelOffsetY() + projectile.getBbHeight() * 0.5F,
                        projectile.getMdxModelOffsetZ()
                );
                poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
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
    public ResourceLocation getTextureLocation(BillboardMdxProjectile projectile) {
        MdxRenderModel model = MdxModelRepository.get(projectile.getMdxModelId());
        return model == null
                ? MissingTextureAtlasSprite.getLocation()
                : model.primaryTexture();
    }
}
