package com.wc3model2mc.mdx.animation;

/** Entity-owned switch controlling whether an MDX model uses Minecraft's lightmap. */
public interface MdxLightingSource {
    boolean isMdxAffectedByLight();

    void setMdxAffectedByLight(boolean affectedByLight);
}
