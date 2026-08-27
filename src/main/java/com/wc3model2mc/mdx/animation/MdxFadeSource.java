package com.wc3model2mc.mdx.animation;

/** Synchronized lifespan-based opacity controls for an MDX entity. */
public interface MdxFadeSource extends MdxEntityLifecycleSource {
    int DEFAULT_FADE_DURATION_TICKS = 20;

    boolean isMdxFadeEnabled();

    void setMdxFadeEnabled(boolean enabled);

    /** When true, the configured fade window starts at entity birth. */
    boolean isMdxFadeFromBirth();

    void setMdxFadeFromBirth(boolean fromBirth);

    /** Fade-window length used when {@link #isMdxFadeFromBirth()} is false. */
    int getMdxFadeDurationTicks();

    void setMdxFadeDurationTicks(int durationTicks);

    /** Returns the synchronized age used for rendering, including partial ticks. */
    float getMdxLifespanElapsedTicks(float partialTick);

    default void setMdxFadeDurationSeconds(float durationSeconds) {
        setMdxFadeDurationTicks(fadeSecondsToTicks(durationSeconds));
    }

    static int fadeSecondsToTicks(float durationSeconds) {
        if (!Float.isFinite(durationSeconds) || durationSeconds < 0.0F) {
            throw new IllegalArgumentException(
                    "durationSeconds must be finite and non-negative"
            );
        }
        double ticks = Math.ceil(durationSeconds * 20.0D);
        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("durationSeconds is too large");
        }
        return (int) ticks;
    }

    default float getMdxFadeAlpha(float partialTick) {
        return calculateAlpha(
                isMdxFadeEnabled(),
                isMdxFadeFromBirth(),
                getMdxFadeDurationTicks(),
                getMdxLifespanTicks(),
                getMdxLifespanElapsedTicks(partialTick)
        );
    }

    static void validateFadeDurationTicks(int durationTicks) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be non-negative");
        }
    }

    static float calculateAlpha(
            boolean enabled,
            boolean fromBirth,
            int fadeDurationTicks,
            int lifespanTicks,
            float elapsedTicks
    ) {
        validateFadeDurationTicks(fadeDurationTicks);
        if (!enabled) {
            return 1.0F;
        }

        float age = Float.isFinite(elapsedTicks)
                ? Math.max(0.0F, elapsedTicks)
                : 0.0F;
        if (fromBirth) {
            if (fadeDurationTicks == 0) {
                return 0.0F;
            }
            int effectiveDuration = lifespanTicks == INFINITE_TICKS
                    ? fadeDurationTicks
                    : Math.min(fadeDurationTicks, Math.max(0, lifespanTicks));
            if (effectiveDuration <= 0) {
                return 0.0F;
            }
            return clamp01(1.0F - age / effectiveDuration);
        }
        if (lifespanTicks == INFINITE_TICKS) {
            return 1.0F;
        }
        if (lifespanTicks <= 0) {
            return 0.0F;
        }
        int effectiveDuration = Math.min(fadeDurationTicks, lifespanTicks);
        if (effectiveDuration <= 0) {
            return 1.0F;
        }
        float fadeStart = lifespanTicks - effectiveDuration;
        if (age <= fadeStart) {
            return 1.0F;
        }
        return clamp01((lifespanTicks - age) / effectiveDuration);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
