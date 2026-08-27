package com.wc3model2mc.client.mdx;

/** Client-side MDX detail tier used when many effects are visible. */
public enum MdxRenderDetail {
    FULL(1, 1.0F),
    MEDIUM(2, 0.5F),
    MINIMAL(Integer.MAX_VALUE, 0.125F);

    private final int blendedLayerStride;
    private final float particleDensity;

    MdxRenderDetail(int blendedLayerStride, float particleDensity) {
        this.blendedLayerStride = blendedLayerStride;
        this.particleDensity = particleDensity;
    }

    public boolean shouldRenderBlendedLayer(int visibleBlendedLayerIndex) {
        return visibleBlendedLayerIndex == 0
                || visibleBlendedLayerIndex % blendedLayerStride == 0;
    }

    public float particleDensity() {
        return particleDensity;
    }
}
