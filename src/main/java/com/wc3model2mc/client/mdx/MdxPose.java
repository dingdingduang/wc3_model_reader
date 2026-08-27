package com.wc3model2mc.client.mdx;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Bone matrices evaluated for one entity at one rendered frame. */
public final class MdxPose {
    private final Matrix4f[] boneMatrices;
    private final Matrix3f[] normalMatrices;

    MdxPose(Matrix4f[] boneMatrices, Matrix3f[] normalMatrices) {
        this.boneMatrices = boneMatrices;
        this.normalMatrices = normalMatrices;
    }

    public int boneCount() {
        return boneMatrices.length;
    }

    public Matrix4f boneMatrix(int index) {
        return boneMatrices[index];
    }

    public Matrix3f normalMatrix(int index) {
        return normalMatrices[index];
    }
}
