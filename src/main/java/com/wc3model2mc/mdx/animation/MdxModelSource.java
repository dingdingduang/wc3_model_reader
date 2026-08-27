package com.wc3model2mc.mdx.animation;

import net.minecraft.resources.ResourceLocation;

/** Entity contract for selecting a renderer-ready MDX resource at runtime. */
public interface MdxModelSource {
    ResourceLocation getMdxModelId();

    void setMdxModelId(ResourceLocation modelId);
}
