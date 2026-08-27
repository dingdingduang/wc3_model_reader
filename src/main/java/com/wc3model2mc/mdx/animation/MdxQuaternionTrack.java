package com.wc3model2mc.mdx.animation;

import com.wc3model2mc.mdx.model.MdxSequence;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable bone rotation timeline. */
public final class MdxQuaternionTrack {
    public static final MdxQuaternionTrack EMPTY = new MdxQuaternionTrack(
            MdxInterpolation.DONT_INTERPOLATE,
            List.of(),
            0.0F
    );

    private final MdxInterpolation interpolation;
    private final List<MdxQuaternionKeyframe> keyframes;
    private final float globalSequenceDurationMillis;

    public MdxQuaternionTrack(
            MdxInterpolation interpolation,
            List<MdxQuaternionKeyframe> keyframes,
            float globalSequenceDurationMillis
    ) {
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
        if (!Float.isFinite(globalSequenceDurationMillis) || globalSequenceDurationMillis < 0.0F) {
            throw new IllegalArgumentException("globalSequenceDurationMillis must be finite and non-negative");
        }
        ArrayList<MdxQuaternionKeyframe> sorted = new ArrayList<>(Objects.requireNonNull(keyframes, "keyframes"));
        sorted.sort(Comparator.comparingDouble(MdxQuaternionKeyframe::timeMillis));
        this.keyframes = List.copyOf(sorted);
        this.globalSequenceDurationMillis = globalSequenceDurationMillis;
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public Quaternionf sample(
            float sequenceTimeMillis,
            float elapsedMillis,
            Quaternionf defaultValue,
            Quaternionf destination
    ) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(destination, "destination");
        if (keyframes.isEmpty()) {
            return destination.set(defaultValue);
        }

        float time = globalSequenceDurationMillis > 0.0F
                ? positiveModulo(elapsedMillis, globalSequenceDurationMillis)
                : sequenceTimeMillis;
        MdxQuaternionKeyframe first = keyframes.get(0);
        if (keyframes.size() == 1 || time <= first.timeMillis()) {
            return first.value(destination);
        }

        MdxQuaternionKeyframe last = keyframes.get(keyframes.size() - 1);
        if (time >= last.timeMillis()) {
            return last.value(destination);
        }

        int upperIndex = findUpperKeyframe(time);
        MdxQuaternionKeyframe left = keyframes.get(upperIndex - 1);
        MdxQuaternionKeyframe right = keyframes.get(upperIndex);
        float span = right.timeMillis() - left.timeMillis();
        float factor = span <= 0.0F ? 0.0F : (time - left.timeMillis()) / span;
        return interpolate(left, right, factor, destination);
    }

    /** Samples only keys belonging to the active animation interval. */
    public Quaternionf sample(
            @Nullable MdxSequence sequence,
            float elapsedMillis,
            Quaternionf defaultValue,
            Quaternionf destination
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
    public Quaternionf sample(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            Quaternionf defaultValue,
            Quaternionf destination
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

    private Quaternionf sampleRange(
            float time,
            int firstIndex,
            int endIndex,
            Quaternionf defaultValue,
            Quaternionf destination
    ) {
        if (firstIndex >= endIndex) {
            return destination.set(defaultValue);
        }
        MdxQuaternionKeyframe first = keyframes.get(firstIndex);
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
        MdxQuaternionKeyframe left = keyframes.get(upperIndex - 1);
        MdxQuaternionKeyframe right = keyframes.get(upperIndex);
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

    private Quaternionf interpolate(
            MdxQuaternionKeyframe left,
            MdxQuaternionKeyframe right,
            float factor,
            Quaternionf destination
    ) {
        if (interpolation == MdxInterpolation.DONT_INTERPOLATE) {
            return left.value(destination);
        }
        if (interpolation == MdxInterpolation.LINEAR) {
            return slerp(left, right, factor, destination);
        }

        float t2 = factor * factor;
        float t3 = t2 * factor;
        if (interpolation == MdxInterpolation.HERMITE) {
            return setComponents(
                    destination,
                    (2.0F * t3 - 3.0F * t2 + 1.0F) * left.valueX()
                            + (t3 - 2.0F * t2 + factor) * left.outTanX()
                            + (-2.0F * t3 + 3.0F * t2) * right.valueX()
                            + (t3 - t2) * right.inTanX(),
                    (2.0F * t3 - 3.0F * t2 + 1.0F) * left.valueY()
                            + (t3 - 2.0F * t2 + factor) * left.outTanY()
                            + (-2.0F * t3 + 3.0F * t2) * right.valueY()
                            + (t3 - t2) * right.inTanY(),
                    (2.0F * t3 - 3.0F * t2 + 1.0F) * left.valueZ()
                            + (t3 - 2.0F * t2 + factor) * left.outTanZ()
                            + (-2.0F * t3 + 3.0F * t2) * right.valueZ()
                            + (t3 - t2) * right.inTanZ(),
                    (2.0F * t3 - 3.0F * t2 + 1.0F) * left.valueW()
                            + (t3 - 2.0F * t2 + factor) * left.outTanW()
                            + (-2.0F * t3 + 3.0F * t2) * right.valueW()
                            + (t3 - t2) * right.inTanW()
            );
        }

        float inverse = 1.0F - factor;
        float b0 = inverse * inverse * inverse;
        float b1 = 3.0F * factor * inverse * inverse;
        float b2 = 3.0F * t2 * inverse;
        float b3 = t3;
        return setComponents(
                destination,
                b0 * left.valueX() + b1 * left.outTanX()
                        + b2 * right.inTanX() + b3 * right.valueX(),
                b0 * left.valueY() + b1 * left.outTanY()
                        + b2 * right.inTanY() + b3 * right.valueY(),
                b0 * left.valueZ() + b1 * left.outTanZ()
                        + b2 * right.inTanZ() + b3 * right.valueZ(),
                b0 * left.valueW() + b1 * left.outTanW()
                        + b2 * right.inTanW() + b3 * right.valueW()
        );
    }

    private static Quaternionf slerp(
            MdxQuaternionKeyframe left,
            MdxQuaternionKeyframe right,
            float factor,
            Quaternionf destination
    ) {
        float cos = left.valueX() * right.valueX()
                + left.valueY() * right.valueY()
                + left.valueZ() * right.valueZ()
                + left.valueW() * right.valueW();
        float absoluteCos = Math.abs(cos);
        float leftScale;
        float rightScale;
        if (1.0F - absoluteCos > 1.0E-6F) {
            float sin = (float) Math.sqrt(1.0F - absoluteCos * absoluteCos);
            float angle = (float) Math.atan2(sin, absoluteCos);
            leftScale = (float) Math.sin((1.0F - factor) * angle) / sin;
            rightScale = (float) Math.sin(factor * angle) / sin;
        } else {
            leftScale = 1.0F - factor;
            rightScale = factor;
        }
        if (cos < 0.0F) {
            rightScale = -rightScale;
        }
        return destination.set(
                leftScale * left.valueX() + rightScale * right.valueX(),
                leftScale * left.valueY() + rightScale * right.valueY(),
                leftScale * left.valueZ() + rightScale * right.valueZ(),
                leftScale * left.valueW() + rightScale * right.valueW()
        ).normalize();
    }

    private static Quaternionf setComponents(
            Quaternionf destination,
            float x,
            float y,
            float z,
            float w
    ) {
        return destination.set(x, y, z, w).normalize();
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }
}
