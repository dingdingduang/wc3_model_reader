package com.wc3model2mc.client.render;

import com.wc3model2mc.client.mdx.CpuSkinnedMdxRenderBackend;
import com.wc3model2mc.client.mdx.MdxModelRepository;
import com.wc3model2mc.client.mdx.MdxRenderBackend;
import com.wc3model2mc.client.mdx.MdxRenderDetail;
import com.wc3model2mc.entity.AnimatedMdxEntity;
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

/** Renders a fully animated MDX model at a living entity's feet-origin. */
public final class AnimatedMdxEntityRenderer extends EntityRenderer<AnimatedMdxEntity> {
    private final MdxRenderBackend backend;

    public AnimatedMdxEntityRenderer(EntityRendererProvider.Context context) {
        this(context, CpuSkinnedMdxRenderBackend.INSTANCE);
    }

    AnimatedMdxEntityRenderer(
            EntityRendererProvider.Context context,
            MdxRenderBackend backend
    ) {
        super(context);
        this.backend = backend;
        shadowRadius = 0.45F;
    }

    @Override
    public boolean shouldRender(
            AnimatedMdxEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)
                || MdxRenderOffsetCulling.shouldRenderOffset(
                        entity,
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ
                );
    }

    @Override
    public void render(
            AnimatedMdxEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        float fadeAlpha = entity.getMdxFadeAlpha(partialTick);
        shadowStrength = fadeAlpha;
        MdxRenderModel model = MdxModelRepository.get(entity.getMdxModelId());
        if (model != null && !model.isEmpty()) {
            poseStack.pushPose();
            try {
                // EntityRenderDispatcher already translated this origin to the entity's feet.
                poseStack.translate(
                        entity.getMdxModelOffsetX(),
                        entity.getMdxModelOffsetY(),
                        entity.getMdxModelOffsetZ()
                );
                float lookYaw = entity.getViewYRot(partialTick);
                float lookPitch = entity.getViewXRot(partialTick);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - lookYaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(lookPitch));
                float modelScale = entity.getMdxModelScale();
                poseStack.scale(modelScale, modelScale, modelScale);

                backend.render(
                        model,
                        MdxAnimationSample.from(entity, entity, partialTick),
                        poseStack,
                        buffers,
                        entity.isMdxAffectedByLight()
                                ? packedLight
                                : LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        MdxRenderDetail.FULL,
                        fadeAlpha
                );
            } finally {
                poseStack.popPose();
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AnimatedMdxEntity entity) {
        MdxRenderModel model = MdxModelRepository.get(entity.getMdxModelId());
        return model == null
                ? MissingTextureAtlasSprite.getLocation()
                : model.primaryTexture();
    }
}
