package com.wc3model2mc.mdx.model;

import java.util.Objects;

/** A named interval from the MDX SEQS chunk. Times are milliseconds. */
public record MdxSequence(
        String name,
        float intervalStartMillis,
        float intervalEndMillis,
        boolean looping
) {
    public MdxSequence {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Sequence name cannot be blank");
        }
        if (!Float.isFinite(intervalStartMillis) || !Float.isFinite(intervalEndMillis)) {
            throw new IllegalArgumentException("Sequence interval must be finite");
        }
        if (intervalEndMillis < intervalStartMillis) {
            throw new IllegalArgumentException("Sequence end cannot be before its start");
        }
    }

    public float durationMillis() {
        return intervalEndMillis - intervalStartMillis;
    }

    public float timelineTime(float elapsedMillis) {
        float duration = durationMillis();
        if (duration <= 0.0F) {
            return intervalStartMillis;
        }
        if (!looping) {
            return intervalStartMillis + Math.min(Math.max(elapsedMillis, 0.0F), duration);
        }
        float wrapped = elapsedMillis % duration;
        if (wrapped < 0.0F) {
            wrapped += duration;
        }
        return intervalStartMillis + wrapped;
    }
}
