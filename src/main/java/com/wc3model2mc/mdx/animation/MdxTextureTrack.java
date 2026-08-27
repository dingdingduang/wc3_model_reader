package com.wc3model2mc.mdx.animation;

import com.wc3model2mc.mdx.model.MdxSequence;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, discrete KMTF texture-selection timeline. */
public final class MdxTextureTrack {
    public static final MdxTextureTrack EMPTY = new MdxTextureTrack(List.of(), 0.0F);

    private final List<MdxTextureKeyframe> keyframes;
    private final float globalSequenceDurationMillis;

    public MdxTextureTrack(
            List<MdxTextureKeyframe> keyframes,
            float globalSequenceDurationMillis
    ) {
        if (!Float.isFinite(globalSequenceDurationMillis) || globalSequenceDurationMillis < 0.0F) {
            throw new IllegalArgumentException(
                    "globalSequenceDurationMillis must be finite and non-negative"
            );
        }
        ArrayList<MdxTextureKeyframe> sorted = new ArrayList<>(
                Objects.requireNonNull(keyframes, "keyframes")
        );
        sorted.sort(Comparator.comparingDouble(MdxTextureKeyframe::timeMillis));
        this.keyframes = List.copyOf(sorted);
        this.globalSequenceDurationMillis = globalSequenceDurationMillis;
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public ResourceLocation sample(
            @Nullable MdxSequence sequence,
            float elapsedMillis,
            ResourceLocation defaultTexture
    ) {
        float sequenceTimeMillis = sequence == null
                ? 0.0F
                : sequence.timelineTime(elapsedMillis);
        return sample(
                sequence,
                sequenceTimeMillis,
                elapsedMillis,
                defaultTexture
        );
    }

    /** Samples an already-resolved absolute sequence time. */
    public ResourceLocation sample(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            ResourceLocation defaultTexture
    ) {
        Objects.requireNonNull(defaultTexture, "defaultTexture");
        if (keyframes.isEmpty()) {
            return defaultTexture;
        }

        int firstIndex = 0;
        int endIndex = keyframes.size();
        float time;
        if (globalSequenceDurationMillis > 0.0F) {
            time = positiveModulo(globalTimeMillis, globalSequenceDurationMillis);
        } else {
            if (sequence == null) {
                return defaultTexture;
            }
            firstIndex = lowerBound(sequence.intervalStartMillis());
            endIndex = upperBound(sequence.intervalEndMillis());
            if (firstIndex >= endIndex) {
                return defaultTexture;
            }
            time = sequenceTimeMillis;
        }

        int upperIndex = upperBound(time, firstIndex, endIndex);
        int selectedIndex = upperIndex <= firstIndex ? endIndex - 1 : upperIndex - 1;
        return keyframes.get(selectedIndex).texture();
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

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }
}
