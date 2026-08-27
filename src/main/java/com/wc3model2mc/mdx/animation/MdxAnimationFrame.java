package com.wc3model2mc.mdx.animation;

import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.wc3model2mc.mdx.model.MdxSequence;

import javax.annotation.Nullable;

/** One fully resolved MDX sequence frame shared by every animated track. */
public record MdxAnimationFrame(
        @Nullable MdxSequence sequence,
        float sequenceTimeMillis,
        float globalTimeMillis
) {
    public MdxAnimationFrame {
        if (!Float.isFinite(sequenceTimeMillis) || !Float.isFinite(globalTimeMillis)) {
            throw new IllegalArgumentException("resolved animation times must be finite");
        }
    }

    public static MdxAnimationFrame resolve(
            MdxRenderModel model,
            MdxAnimationSample sample
    ) {
        MdxSequence sequence = model.resolveSequence(sample.requestedName());
        float elapsedMillis = Math.max(0.0F, sample.elapsedMillis());
        float scaledElapsedMillis = elapsedMillis * sample.speed();
        if (sequence == null) {
            return new MdxAnimationFrame(null, 0.0F, scaledElapsedMillis);
        }

        float durationMillis = sequence.durationMillis();
        float rangeStartMillis = clamp(sample.rangeStartMillis(), 0.0F, durationMillis);
        float requestedEndMillis = sample.rangeEndMillis()
                == MdxAnimationPlaybackSource.FULL_SEQUENCE_END
                ? durationMillis
                : sample.rangeEndMillis();
        float rangeEndMillis = clamp(requestedEndMillis, rangeStartMillis, durationMillis);
        float rangeDurationMillis = rangeEndMillis - rangeStartMillis;

        if (rangeDurationMillis <= 0.0F) {
            return new MdxAnimationFrame(
                    sequence,
                    sequence.intervalStartMillis() + rangeStartMillis,
                    scaledElapsedMillis
            );
        }

        if (sample.loopMode().isLooping(sequence)) {
            float localTime = positiveModulo(scaledElapsedMillis, rangeDurationMillis);
            return new MdxAnimationFrame(
                    sequence,
                    sequence.intervalStartMillis() + rangeStartMillis + localTime,
                    scaledElapsedMillis
            );
        }

        if (scaledElapsedMillis < rangeDurationMillis) {
            return new MdxAnimationFrame(
                    sequence,
                    sequence.intervalStartMillis() + rangeStartMillis + scaledElapsedMillis,
                    scaledElapsedMillis
            );
        }

        if (sample.returnToStand()) {
            MdxSequence stand = model.findSequence("stand");
            if (stand != null) {
                float completionWallTimeMillis = rangeDurationMillis / sample.speed();
                float standElapsedMillis = Math.max(
                        0.0F,
                        elapsedMillis - completionWallTimeMillis
                );
                return new MdxAnimationFrame(
                        stand,
                        stand.timelineTime(standElapsedMillis),
                        standElapsedMillis
                );
            }
        }

        return new MdxAnimationFrame(
                sequence,
                sequence.intervalStartMillis() + rangeEndMillis,
                rangeDurationMillis
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }
}
