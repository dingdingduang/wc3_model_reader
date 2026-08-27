package com.wc3model2mc.mdx.animation;

/**
 * Optional extended entity contract for synchronized MDX playback controls.
 * Times exposed by this API are sequence-relative milliseconds.
 */
public interface MdxAnimationPlaybackSource extends MdxAnimationSource {
    float FULL_SEQUENCE_END = -1.0F;
    float DEFAULT_RANGE_START_MILLIS = 0.0F;
    float DEFAULT_RANGE_END_MILLIS = FULL_SEQUENCE_END;
    float DEFAULT_SPEED = 1.0F;
    float MAX_SPEED = 100.0F;

    boolean isMdxAnimationLooping();

    void setMdxAnimationLooping(boolean looping);

    boolean shouldMdxReturnToStand();

    void setMdxReturnToStand(boolean returnToStand);

    float getMdxAnimationRangeStartMillis();

    float getMdxAnimationRangeEndMillis();

    void setMdxAnimationRangeMillis(float startMillis, float endMillis);

    float getMdxAnimationSpeed();

    void setMdxAnimationSpeed(float speed);

    void restartMdxAnimation();

    default void setMdxAnimationRangeSeconds(float startSeconds, float endSeconds) {
        setMdxAnimationRangeMillis(secondsToMillis(startSeconds), secondsToMillis(endSeconds));
    }

    default void resetMdxAnimationRange() {
        setMdxAnimationRangeMillis(DEFAULT_RANGE_START_MILLIS, DEFAULT_RANGE_END_MILLIS);
    }

    default void playMdxAnimation(
            String animationName,
            boolean looping,
            boolean returnToStand,
            float startSeconds,
            float endSeconds,
            float speed
    ) {
        setMdxAnimationLooping(looping);
        setMdxReturnToStand(returnToStand);
        setMdxAnimationRangeSeconds(startSeconds, endSeconds);
        setMdxAnimationSpeed(speed);
        playMdxAnimation(animationName);
        restartMdxAnimation();
    }

    static void validateRange(float startMillis, float endMillis) {
        if (!Float.isFinite(startMillis) || startMillis < 0.0F) {
            throw new IllegalArgumentException("animation range start must be finite and non-negative");
        }
        if (!Float.isFinite(endMillis)
                || (endMillis != FULL_SEQUENCE_END && endMillis <= startMillis)) {
            throw new IllegalArgumentException(
                    "animation range end must be -1 or finite and greater than its start"
            );
        }
    }

    static boolean isValidRange(float startMillis, float endMillis) {
        return Float.isFinite(startMillis)
                && startMillis >= 0.0F
                && Float.isFinite(endMillis)
                && (endMillis == FULL_SEQUENCE_END || endMillis > startMillis);
    }

    static void validateSpeed(float speed) {
        if (!isValidSpeed(speed)) {
            throw new IllegalArgumentException(
                    "animation speed must be finite and in the range (0, " + MAX_SPEED + "]"
            );
        }
    }

    static boolean isValidSpeed(float speed) {
        return Float.isFinite(speed) && speed > 0.0F && speed <= MAX_SPEED;
    }

    private static float secondsToMillis(float seconds) {
        if (seconds == FULL_SEQUENCE_END) {
            return FULL_SEQUENCE_END;
        }
        if (!Float.isFinite(seconds)) {
            throw new IllegalArgumentException("animation range seconds must be finite");
        }
        return seconds * 1_000.0F;
    }
}
