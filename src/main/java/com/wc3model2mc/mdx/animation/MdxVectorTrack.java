package com.wc3model2mc.mdx.animation;

import com.wc3model2mc.mdx.model.MdxSequence;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable translation or scaling timeline. */
public final class MdxVectorTrack {
    public static final MdxVectorTrack EMPTY = new MdxVectorTrack(
            MdxInterpolation.DONT_INTERPOLATE,
            List.of(),
            0.0F
    );

    private final MdxInterpolation interpolation;
    private final List<MdxVectorKeyframe> keyframes;
    private final float globalSequenceDurationMillis;

    public MdxVectorTrack(
            MdxInterpolation interpolation,
            List<MdxVectorKeyframe> keyframes,
            float globalSequenceDurationMillis
    ) {
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
        if (!Float.isFinite(globalSequenceDurationMillis) || globalSequenceDurationMillis < 0.0F) {
            throw new IllegalArgumentException("globalSequenceDurationMillis must be finite and non-negative");
        }
        ArrayList<MdxVectorKeyframe> sorted = new ArrayList<>(Objects.requireNonNull(keyframes, "keyframes"));
        sorted.sort(Comparator.comparingDouble(MdxVectorKeyframe::timeMillis));
        this.keyframes = List.copyOf(sorted);
        this.globalSequenceDurationMillis = globalSequenceDurationMillis;
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public Vector3f sample(
            float sequenceTimeMillis,
            float elapsedMillis,
            Vector3f defaultValue,
            Vector3f destination
    ) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(destination, "destination");
        if (keyframes.isEmpty()) {
            return destination.set(defaultValue);
        }

        float time = globalSequenceDurationMillis > 0.0F
                ? positiveModulo(elapsedMillis, globalSequenceDurationMillis)
                : sequenceTimeMillis;
        MdxVectorKeyframe first = keyframes.get(0);
        if (keyframes.size() == 1 || time <= first.timeMillis()) {
            return first.value(destination);
        }

        MdxVectorKeyframe last = keyframes.get(keyframes.size() - 1);
        if (time >= last.timeMillis()) {
            return last.value(destination);
        }

        int upperIndex = findUpperKeyframe(time);
        MdxVectorKeyframe left = keyframes.get(upperIndex - 1);
        MdxVectorKeyframe right = keyframes.get(upperIndex);
        float span = right.timeMillis() - left.timeMillis();
        float factor = span <= 0.0F ? 0.0F : (time - left.timeMillis()) / span;
        return interpolate(left, right, factor, destination);
    }

    /** Samples only keys belonging to the active animation interval. */
    public Vector3f sample(
            @Nullable MdxSequence sequence,
            float elapsedMillis,
            Vector3f defaultValue,
            Vector3f destination
    ) {
        float sequenceTimeMillis = sequence == null
                ? 0.0F
                : sequence.timelineTime(elapsedMillis);
        return sample(
                sequence,
                sequenceTimeMillis,
                elapsedMillis,
                defaultValue,
                destination
        );
    }

    /** Samples an already-resolved absolute sequence time. */
    public Vector3f sample(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            Vector3f defaultValue,
            Vector3f destination
    ) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(destination, "destination");
        if (keyframes.isEmpty()) {
            return destination.set(defaultValue);
        }
        if (globalSequenceDurationMillis > 0.0F) {
            return sampleRange(
                    positiveModulo(globalTimeMillis, globalSequenceDurationMillis),
                    0,
                    keyframes.size(),
                    defaultValue,
                    destination
            );
        }
        if (sequence == null) {
            return destination.set(defaultValue);
        }
        int firstIndex = lowerBound(sequence.intervalStartMillis());
        int endIndex = upperBound(sequence.intervalEndMillis(), 0, keyframes.size());
        if (firstIndex >= endIndex) {
            return destination.set(defaultValue);
        }
        return sampleRange(
                sequenceTimeMillis,
                firstIndex,
                endIndex,
                defaultValue,
                destination
        );
    }

    private Vector3f sampleRange(
            float time,
            int firstIndex,
            int endIndex,
            Vector3f defaultValue,
            Vector3f destination
    ) {
        if (firstIndex >= endIndex) {
            return destination.set(defaultValue);
        }
        MdxVectorKeyframe first = keyframes.get(firstIndex);
        if (endIndex - firstIndex == 1) {
            return first.value(destination);
        }
        if (time < first.timeMillis()) {
            return keyframes.get(endIndex - 1).value(destination);
        }
        int upperIndex = upperBound(time, firstIndex, endIndex);
        if (upperIndex >= endIndex) {
            return keyframes.get(endIndex - 1).value(destination);
        }
        MdxVectorKeyframe left = keyframes.get(upperIndex - 1);
        MdxVectorKeyframe right = keyframes.get(upperIndex);
        float span = right.timeMillis() - left.timeMillis();
        float factor = span <= 0.0F ? 0.0F : (time - left.timeMillis()) / span;
        return interpolate(left, right, factor, destination);
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

    private int findUpperKeyframe(float time) {
        int low = 1;
        int high = keyframes.size() - 1;
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

    private Vector3f interpolate(
            MdxVectorKeyframe left,
            MdxVectorKeyframe right,
            float factor,
            Vector3f destination
    ) {
        if (interpolation == MdxInterpolation.DONT_INTERPOLATE) {
            return left.value(destination);
        }

        if (interpolation == MdxInterpolation.LINEAR) {
            return destination.set(
                    lerp(left.valueX(), right.valueX(), factor),
                    lerp(left.valueY(), right.valueY(), factor),
                    lerp(left.valueZ(), right.valueZ(), factor)
            );
        }

        float t2 = factor * factor;
        float t3 = t2 * factor;
        if (interpolation == MdxInterpolation.HERMITE) {
            float h00 = 2.0F * t3 - 3.0F * t2 + 1.0F;
            float h10 = t3 - 2.0F * t2 + factor;
            float h01 = -2.0F * t3 + 3.0F * t2;
            float h11 = t3 - t2;
            return destination.set(
                    h00 * left.valueX() + h10 * left.outTanX()
                            + h01 * right.valueX() + h11 * right.inTanX(),
                    h00 * left.valueY() + h10 * left.outTanY()
                            + h01 * right.valueY() + h11 * right.inTanY(),
                    h00 * left.valueZ() + h10 * left.outTanZ()
                            + h01 * right.valueZ() + h11 * right.inTanZ()
            );
        }

        float inverse = 1.0F - factor;
        float b0 = inverse * inverse * inverse;
        float b1 = 3.0F * factor * inverse * inverse;
        float b2 = 3.0F * t2 * inverse;
        float b3 = t3;
        return destination.set(
                b0 * left.valueX() + b1 * left.outTanX()
                        + b2 * right.inTanX() + b3 * right.valueX(),
                b0 * left.valueY() + b1 * left.outTanY()
                        + b2 * right.inTanY() + b3 * right.valueY(),
                b0 * left.valueZ() + b1 * left.outTanZ()
                        + b2 * right.inTanZ() + b3 * right.valueZ()
        );
    }

    private static float lerp(float left, float right, float factor) {
        return left + (right - left) * factor;
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }
}
