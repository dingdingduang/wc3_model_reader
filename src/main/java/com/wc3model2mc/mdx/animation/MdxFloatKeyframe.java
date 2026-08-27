package com.wc3model2mc.mdx.animation;

/** A scalar MDX keyframe. Tangents are used by Hermite and Bezier tracks. */
public record MdxFloatKeyframe(
        float timeMillis,
        float value,
        float inTan,
        float outTan
) {
    public MdxFloatKeyframe(float timeMillis, float value) {
        this(timeMillis, value, value, value);
    }

    public MdxFloatKeyframe {
        if (!Float.isFinite(timeMillis) || !Float.isFinite(value)
                || !Float.isFinite(inTan) || !Float.isFinite(outTan)) {
            throw new IllegalArgumentException("Float keyframe values must be finite");
        }
    }
}
