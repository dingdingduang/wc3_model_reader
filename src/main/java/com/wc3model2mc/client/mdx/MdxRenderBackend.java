package com.wc3model2mc.client.mdx;

import com.wc3model2mc.mdx.animation.MdxAnimationSample;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Minecraft-facing renderer for parser-independent MDX render data. */
public interface MdxRenderBackend {
    void render(
            MdxRenderModel model,
            MdxAnimationSample animation,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    );

    /**
     * Renders with a client-selected detail tier. Custom backends remain
     * source-compatible and receive full detail unless they override this.
     */
    default void render(
            MdxRenderModel model,
            MdxAnimationSample animation,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            MdxRenderDetail detail
    ) {
        render(model, animation, poseStack, buffers, packedLight, packedOverlay);
    }

    /**
     * Renders with entity opacity. Existing custom backends remain compatible;
     * override this overload to support lifespan fading themselves.
     */
    default void render(
            MdxRenderModel model,
            MdxAnimationSample animation,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            MdxRenderDetail detail,
            float opacity
    ) {
        render(
                model,
                animation,
                poseStack,
                buffers,
                packedLight,
                packedOverlay,
                detail
        );
    }
}
