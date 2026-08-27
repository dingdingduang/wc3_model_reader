package com.wc3model2mc.mdx.animation;

/** Entity-owned scale in Minecraft blocks per Warcraft III model unit. */
public interface MdxModelScaleSource {
    float getMdxModelScale();

    void setMdxModelScale(float modelScale);
}
