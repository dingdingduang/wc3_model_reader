package com.wc3model2mc.mdx.animation;

/** Render-only model translation in Minecraft blocks. */
public interface MdxModelOffsetSource {
    float getMdxModelOffsetX();

    float getMdxModelOffsetY();

    float getMdxModelOffsetZ();

    void setMdxModelOffset(float offsetX, float offsetY, float offsetZ);

    default void setMdxModelOffsetX(float offsetX) {
        setMdxModelOffset(offsetX, getMdxModelOffsetY(), getMdxModelOffsetZ());
    }

    default void setMdxModelOffsetY(float offsetY) {
        setMdxModelOffset(getMdxModelOffsetX(), offsetY, getMdxModelOffsetZ());
    }

    default void setMdxModelOffsetZ(float offsetZ) {
        setMdxModelOffset(getMdxModelOffsetX(), getMdxModelOffsetY(), offsetZ);
    }

    default void clearMdxModelOffset() {
        setMdxModelOffset(0.0F, 0.0F, 0.0F);
    }

    static void validateOffset(float offsetX, float offsetY, float offsetZ) {
        if (!Float.isFinite(offsetX)
                || !Float.isFinite(offsetY)
                || !Float.isFinite(offsetZ)) {
            throw new IllegalArgumentException("model offsets must be finite");
        }
    }

    static float finiteOrZero(float offset) {
        return Float.isFinite(offset) ? offset : 0.0F;
    }
}
