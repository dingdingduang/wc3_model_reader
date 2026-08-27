package com.wc3model2mc.mdx.animation;

/**
 * Entity-owned animation state consumed by an MDX renderer.
 *
 * <p>Renderer instances are shared by every entity of their registered type, so
 * the current sequence and its start time must live on the entity.</p>
 */
public interface MdxAnimationSource {
    String getMdxAnimationName();

    int getMdxAnimationStartTick();

    void playMdxAnimation(String animationName);
}
