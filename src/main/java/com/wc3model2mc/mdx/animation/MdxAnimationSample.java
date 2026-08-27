package com.wc3model2mc.mdx.animation;

import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** An entity's requested sequence and immutable playback settings for one render. */
public record MdxAnimationSample(
        String requestedName,
        float elapsedMillis,
        MdxAnimationLoopMode loopMode,
        boolean returnToStand,
        float rangeStartMillis,
        float rangeEndMillis,
        float speed
) {
    public MdxAnimationSample {
        requestedName = Objects.requireNonNull(requestedName, "requestedName");
        loopMode = Objects.requireNonNull(loopMode, "loopMode");
        if (!Float.isFinite(elapsedMillis)) {
            throw new IllegalArgumentException("elapsedMillis must be finite");
        }
        MdxAnimationPlaybackSource.validateRange(rangeStartMillis, rangeEndMillis);
        MdxAnimationPlaybackSource.validateSpeed(speed);
    }

    /** Backward-compatible sample that follows the looping flag stored in the MDX. */
    public MdxAnimationSample(String requestedName, float elapsedMillis) {
        this(
                requestedName,
                elapsedMillis,
                MdxAnimationLoopMode.MODEL_DEFAULT,
                false,
                MdxAnimationPlaybackSource.DEFAULT_RANGE_START_MILLIS,
                MdxAnimationPlaybackSource.DEFAULT_RANGE_END_MILLIS,
                MdxAnimationPlaybackSource.DEFAULT_SPEED
        );
    }

    public static MdxAnimationSample from(
            Entity entity,
            MdxAnimationSource animationSource,
            float partialTick
    ) {
        float elapsedTicks = entity.tickCount
                - animationSource.getMdxAnimationStartTick()
                + partialTick;
        float elapsedMillis = Math.max(0.0F, elapsedTicks) * 50.0F;
        if (animationSource instanceof MdxAnimationPlaybackSource playbackSource) {
            return new MdxAnimationSample(
                    animationSource.getMdxAnimationName(),
                    elapsedMillis,
                    playbackSource.isMdxAnimationLooping()
                            ? MdxAnimationLoopMode.LOOP
                            : MdxAnimationLoopMode.ONCE,
                    playbackSource.shouldMdxReturnToStand(),
                    playbackSource.getMdxAnimationRangeStartMillis(),
                    playbackSource.getMdxAnimationRangeEndMillis(),
                    playbackSource.getMdxAnimationSpeed()
            );
        }
        return new MdxAnimationSample(animationSource.getMdxAnimationName(), elapsedMillis);
    }
}
