package com.wc3model2mc.mdx.animation;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/** Synchronized lifespan and entity-following controls for an MDX entity. */
public interface MdxEntityLifecycleSource {
    int INFINITE_TICKS = -1;
    int DEFAULT_LIFESPAN_TICKS = 5 * 20;
    int NO_FOLLOW_TICKS = 0;

    int getMdxLifespanTicks();

    /** Sets the lifespan and restarts its countdown. Use {@code -1} for infinite. */
    void setMdxLifespanTicks(int lifespanTicks);

    Optional<UUID> getMdxFollowTargetUuid();

    int getMdxFollowDurationTicks();

    /**
     * Starts following a target and restarts the follow timer. A duration of
     * {@code -1} follows forever; {@code 0} clears the target.
     */
    void setMdxFollowTarget(@Nullable Entity target, int durationTicks);

    /**
     * Starts following with an optional exact client-side position lock.
     * Implementations predating this overload keep their normal movement
     * behavior through the compatibility default.
     */
    default void setMdxFollowTarget(
            @Nullable Entity target,
            int durationTicks,
            boolean instantMoveToTarget
    ) {
        setMdxFollowTarget(target, durationTicks);
    }

    /** Whether active following is visually locked directly to the target. */
    default boolean isMdxInstantMoveToTarget() {
        return false;
    }

    default boolean isMdxFollowingEntity() {
        return getMdxFollowDurationTicks() != NO_FOLLOW_TICKS
                && getMdxFollowTargetUuid().isPresent();
    }

    default void clearMdxFollowTarget() {
        setMdxFollowTarget(null, NO_FOLLOW_TICKS);
    }

    default void setMdxLifespanSeconds(float lifespanSeconds) {
        setMdxLifespanTicks(secondsToTicks(lifespanSeconds, "lifespanSeconds"));
    }

    default void setMdxFollowTargetSeconds(
            @Nullable Entity target,
            float durationSeconds
    ) {
        setMdxFollowTarget(
                target,
                secondsToTicks(durationSeconds, "durationSeconds")
        );
    }

    default void setMdxFollowTargetSeconds(
            @Nullable Entity target,
            float durationSeconds,
            boolean instantMoveToTarget
    ) {
        setMdxFollowTarget(
                target,
                secondsToTicks(durationSeconds, "durationSeconds"),
                instantMoveToTarget
        );
    }

    static void validateDurationTicks(int ticks, String fieldName) {
        if (ticks < INFINITE_TICKS) {
            throw new IllegalArgumentException(fieldName + " must be -1 or non-negative");
        }
    }

    static int secondsToTicks(float seconds, String fieldName) {
        if (seconds == INFINITE_TICKS) {
            return INFINITE_TICKS;
        }
        if (!Float.isFinite(seconds) || seconds < 0.0F) {
            throw new IllegalArgumentException(fieldName + " must be -1 or finite and non-negative");
        }
        double ticks = Math.ceil(seconds * 20.0D);
        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " is too large");
        }
        return (int) ticks;
    }
}
