package com.wc3model2mc.mdx.model;

import com.wc3model2mc.mdx.animation.MdxQuaternionTrack;
import com.wc3model2mc.mdx.animation.MdxVectorTrack;
import org.joml.Vector3f;

import java.util.Objects;

/** Parser-independent bone/node data needed by the Minecraft renderer. */
public final class MdxBone {
    private final String name;
    private final int parentIndex;
    private final Vector3f pivot;
    private final MdxVectorTrack translationTrack;
    private final MdxQuaternionTrack rotationTrack;
    private final MdxVectorTrack scalingTrack;

    public MdxBone(
            String name,
            int parentIndex,
            Vector3f pivot,
            MdxVectorTrack translationTrack,
            MdxQuaternionTrack rotationTrack,
            MdxVectorTrack scalingTrack
    ) {
        this.name = Objects.requireNonNull(name, "name");
        if (parentIndex < -1) {
            throw new IllegalArgumentException("parentIndex must be -1 or a non-negative bone index");
        }
        this.parentIndex = parentIndex;
        this.pivot = new Vector3f(Objects.requireNonNull(pivot, "pivot"));
        this.translationTrack = Objects.requireNonNull(translationTrack, "translationTrack");
        this.rotationTrack = Objects.requireNonNull(rotationTrack, "rotationTrack");
        this.scalingTrack = Objects.requireNonNull(scalingTrack, "scalingTrack");
    }

    public String name() {
        return name;
    }

    public int parentIndex() {
        return parentIndex;
    }

    public Vector3f pivot(Vector3f destination) {
        return destination.set(pivot);
    }

    public MdxVectorTrack translationTrack() {
        return translationTrack;
    }

    public MdxQuaternionTrack rotationTrack() {
        return rotationTrack;
    }

    public MdxVectorTrack scalingTrack() {
        return scalingTrack;
    }
}
