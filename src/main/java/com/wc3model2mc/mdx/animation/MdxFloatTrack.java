package com.wc3model2mc.mdx.animation;

import com.wc3model2mc.mdx.model.MdxSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable scalar timeline used by MDX material and geoset alpha tracks. */
public final class MdxFloatTrack {
    public static final MdxFloatTrack EMPTY = new MdxFloatTrack(
            MdxInterpolation.DONT_INTERPOLATE,
            List.of(),
            0.0F
    );

    private final MdxInterpolation interpolation;
    private final List<MdxFloatKeyframe> keyframes;
    private final float globalSequenceDurationMillis;

    public MdxFloatTrack(
            MdxInterpolation interpolation,
            List<MdxFloatKeyframe> keyframes,
            float globalSequenceDurationMillis
    ) {
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
        if (!Float.isFinite(globalSequenceDurationMillis) || globalSequenceDurationMillis < 0.0F) {
            throw new IllegalArgumentException(
                    "globalSequenceDurationMillis must be finite and non-negative"
            );
        }
        ArrayList<MdxFloatKeyframe> sorted = new ArrayList<>(
                Objects.requireNonNull(keyframes, "keyframes")
        );
        sorted.sort(Comparator.comparingDouble(MdxFloatKeyframe::timeMillis));
        this.keyframes = List.copyOf(sorted);
        this.globalSequenceDurationMillis = globalSequenceDurationMillis;
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public float sample(
            @Nullable MdxSequence sequence,
            float elapsedMillis,
            float defaultValue
    ) {
        float sequenceTimeMillis = sequence == null
                ? 0.0F
                : sequence.timelineTime(elapsedMillis);
        return sample(sequence, sequenceTimeMillis, elapsedMillis, defaultValue);
    }

    /** Samples an already-resolved absolute sequence time. */
    public float sample(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            float defaultValue
    ) {
        if (keyframes.isEmpty()) {
            return defaultValue;
        }

        int firstIndex = 0;
        int endIndex = keyframes.size();
        float time;
        if (globalSequenceDurationMillis > 0.0F) {
            time = positiveModulo(globalTimeMillis, globalSequenceDurationMillis);
        } else {
            if (sequence == null) {
                return defaultValue;
            }
            firstIndex = lowerBound(sequence.intervalStartMillis());
            endIndex = upperBound(sequence.intervalEndMillis());
            if (firstIndex >= endIndex) {
                return defaultValue;
            }
            time = sequenceTimeMillis;
        }

        if (endIndex - firstIndex == 1) {
            return keyframes.get(firstIndex).value();
        }
        int upperIndex = upperBound(time, firstIndex, endIndex);
        if (upperIndex <= firstIndex) {
            // Warcraft animation intervals wrap to their last key before the first key.
            return keyframes.get(endIndex - 1).value();
        }
        if (upperIndex >= endIndex) {
            return keyframes.get(endIndex - 1).value();
        }

        MdxFloatKeyframe left = keyframes.get(upperIndex - 1);
        MdxFloatKeyframe right = keyframes.get(upperIndex);
        if (interpolation == MdxInterpolation.DONT_INTERPOLATE) {
            return left.value();
        }
        float span = right.timeMillis() - left.timeMillis();
        float factor = span <= 0.0F ? 0.0F : (time - left.timeMillis()) / span;
        if (interpolation == MdxInterpolation.LINEAR) {
            return lerp(left.value(), right.value(), factor);
        }
        if (interpolation == MdxInterpolation.HERMITE) {
            float t2 = factor * factor;
            float t3 = t2 * factor;
            return (2.0F * t3 - 3.0F * t2 + 1.0F) * left.value()
                    + (t3 - 2.0F * t2 + factor) * left.outTan()
                    + (-2.0F * t3 + 3.0F * t2) * right.value()
                    + (t3 - t2) * right.inTan();
        }
        float inverse = 1.0F - factor;
        return inverse * inverse * inverse * left.value()
                + 3.0F * factor * inverse * inverse * left.outTan()
                + 3.0F * factor * factor * inverse * right.inTan()
                + factor * factor * factor * right.value();
    }

    private int lowerBound(float time) {
        int low = 0;
        int high = keyframes.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (keyframes.get(middle).timeMillis() < time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private int upperBound(float time) {
        return upperBound(time, 0, keyframes.size());
    }

    private int upperBound(float time, int low, int high) {
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (keyframes.get(middle).timeMillis() <= time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static float lerp(float left, float right, float factor) {
        return left + (right - left) * factor;
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }
}
