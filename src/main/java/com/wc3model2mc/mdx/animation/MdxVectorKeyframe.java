package com.wc3model2mc.mdx.animation;

import org.joml.Vector3f;

import java.util.Objects;

/** A vector keyframe. Tangents are only read by Hermite and Bezier tracks. */
public final class MdxVectorKeyframe {
    private final float timeMillis;
    private final Vector3f value;
    private final Vector3f inTan;
    private final Vector3f outTan;

    public MdxVectorKeyframe(float timeMillis, Vector3f value) {
        this(timeMillis, value, value, value);
    }

    public MdxVectorKeyframe(float timeMillis, Vector3f value, Vector3f inTan, Vector3f outTan) {
        if (!Float.isFinite(timeMillis)) {
            throw new IllegalArgumentException("timeMillis must be finite");
        }
        this.timeMillis = timeMillis;
        this.value = new Vector3f(Objects.requireNonNull(value, "value"));
        this.inTan = new Vector3f(Objects.requireNonNull(inTan, "inTan"));
        this.outTan = new Vector3f(Objects.requireNonNull(outTan, "outTan"));
    }

    public float timeMillis() {
        return timeMillis;
    }

    Vector3f value(Vector3f destination) {
        return destination.set(value);
    }

    Vector3f inTan(Vector3f destination) {
        return destination.set(inTan);
    }

    Vector3f outTan(Vector3f destination) {
        return destination.set(outTan);
    }

    float valueX() { return value.x; }
    float valueY() { return value.y; }
    float valueZ() { return value.z; }
    float inTanX() { return inTan.x; }
    float inTanY() { return inTan.y; }
    float inTanZ() { return inTan.z; }
    float outTanX() { return outTan.x; }
    float outTanY() { return outTan.y; }
    float outTanZ() { return outTan.z; }
}
