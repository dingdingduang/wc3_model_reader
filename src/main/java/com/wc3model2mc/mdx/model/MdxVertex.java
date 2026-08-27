package com.wc3model2mc.mdx.model;

import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Objects;

/** A vertex after parsing and coordinate conversion, but before CPU skinning. */
public final class MdxVertex {
    public static final int MAX_INFLUENCES = 4;

    private final Vector3f position;
    private final Vector3f normal;
    private final float u;
    private final float v;
    private final int[] boneIndices;
    private final float[] boneWeights;

    public MdxVertex(
            Vector3f position,
            Vector3f normal,
            float u,
            float v,
            int[] boneIndices,
            float[] boneWeights
    ) {
        this.position = new Vector3f(Objects.requireNonNull(position, "position"));
        this.normal = new Vector3f(Objects.requireNonNull(normal, "normal"));
        if (!Float.isFinite(u) || !Float.isFinite(v)) {
            throw new IllegalArgumentException("UV coordinates must be finite");
        }
        this.u = u;
        this.v = v;
        Objects.requireNonNull(boneIndices, "boneIndices");
        Objects.requireNonNull(boneWeights, "boneWeights");
        if (boneIndices.length != boneWeights.length || boneIndices.length > MAX_INFLUENCES) {
            throw new IllegalArgumentException("Bone index and weight arrays must have equal lengths up to four");
        }
        this.boneIndices = boneIndices.clone();
        this.boneWeights = boneWeights.clone();
        for (int boneIndex : this.boneIndices) {
            if (boneIndex < 0) {
                throw new IllegalArgumentException("Bone indices cannot be negative");
            }
        }
        for (float weight : this.boneWeights) {
            if (!Float.isFinite(weight) || weight < 0.0F) {
                throw new IllegalArgumentException("Bone weights must be finite and non-negative");
            }
        }
    }

    public Vector3f position(Vector3f destination) {
        return destination.set(position);
    }

    public Vector3f normal(Vector3f destination) {
        return destination.set(normal);
    }

    public float u() {
        return u;
    }

    public float v() {
        return v;
    }

    public int influenceCount() {
        return boneIndices.length;
    }

    public int boneIndex(int influence) {
        return boneIndices[influence];
    }

    public float boneWeight(int influence) {
        return boneWeights[influence];
    }

    @Override
    public String toString() {
        return "MdxVertex{" + position + ", bones=" + Arrays.toString(boneIndices) + '}';
    }
}
