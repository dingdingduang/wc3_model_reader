package com.wc3model2mc.mdx.animation;

import org.joml.Quaternionf;

import java.util.Objects;

/** A rotation keyframe. Tangents are component-interpolated and normalized. */
public final class MdxQuaternionKeyframe {
    private final float timeMillis;
    private final Quaternionf value;
    private final Quaternionf inTan;
    private final Quaternionf outTan;

    public MdxQuaternionKeyframe(float timeMillis, Quaternionf value) {
        this(timeMillis, value, value, value);
    }

    public MdxQuaternionKeyframe(
            float timeMillis,
            Quaternionf value,
            Quaternionf inTan,
            Quaternionf outTan
    ) {
        if (!Float.isFinite(timeMillis)) {
            throw new IllegalArgumentException("timeMillis must be finite");
        }
        this.timeMillis = timeMillis;
        this.value = new Quaternionf(Objects.requireNonNull(value, "value")).normalize();
        this.inTan = new Quaternionf(Objects.requireNonNull(inTan, "inTan"));
        this.outTan = new Quaternionf(Objects.requireNonNull(outTan, "outTan"));
    }

    public float timeMillis() {
        return timeMillis;
    }

    Quaternionf value(Quaternionf destination) {
        return destination.set(value);
    }

    Quaternionf inTan(Quaternionf destination) {
        return destination.set(inTan);
    }

    Quaternionf outTan(Quaternionf destination) {
        return destination.set(outTan);
    }

    float valueX() { return value.x; }
    float valueY() { return value.y; }
    float valueZ() { return value.z; }
    float valueW() { return value.w; }
    float inTanX() { return inTan.x; }
    float inTanY() { return inTan.y; }
    float inTanZ() { return inTan.z; }
    float inTanW() { return inTan.w; }
    float outTanX() { return outTan.x; }
    float outTanY() { return outTan.y; }
    float outTanZ() { return outTan.z; }
    float outTanW() { return outTan.w; }
}
