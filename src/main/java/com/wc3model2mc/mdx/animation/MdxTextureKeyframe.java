package com.wc3model2mc.mdx.animation;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One discrete texture choice in an MDX KMTF material track. */
public record MdxTextureKeyframe(float timeMillis, ResourceLocation texture) {
    public MdxTextureKeyframe {
        if (!Float.isFinite(timeMillis)) {
            throw new IllegalArgumentException("timeMillis must be finite");
        }
        texture = Objects.requireNonNull(texture, "texture");
    }
}
