package com.wc3model2mc.client.render;

import com.wc3model2mc.mdx.animation.MdxModelOffsetSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;

/** Keeps a render-offset model visible without changing its physical bounding box. */
final class MdxRenderOffsetCulling {
    private MdxRenderOffsetCulling() {
    }

    static boolean shouldRenderOffset(
            Entity entity,
            MdxModelOffsetSource offset,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        double offsetX = offset.getMdxModelOffsetX();
        double offsetY = offset.getMdxModelOffsetY();
        double offsetZ = offset.getMdxModelOffsetZ();
        if (offsetX == 0.0D && offsetY == 0.0D && offsetZ == 0.0D) {
            return false;
        }

        // Test render distance from the translated visual location, then test
        // that translated box against the camera. Physics still uses the
        // entity's original, unmodified bounding box.
        return entity.shouldRender(
                cameraX - offsetX,
                cameraY - offsetY,
                cameraZ - offsetZ
        ) && frustum.isVisible(entity.getBoundingBoxForCulling()
                .move(offsetX, offsetY, offsetZ)
                .inflate(0.5D));
    }
}
