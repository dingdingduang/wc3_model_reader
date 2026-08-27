package com.wc3model2mc.mdx.model;

import com.wc3model2mc.mdx.animation.MdxFloatTrack;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** A triangle-list geoset and its resolved material layer. */
public final class MdxMesh {
    private final List<MdxVertex> vertices;
    private final int[] triangleIndices;
    private final MdxMaterial material;
    private final float geosetAlpha;
    private final MdxFloatTrack geosetAlphaTrack;

    public MdxMesh(List<MdxVertex> vertices, int[] triangleIndices, MdxMaterial material) {
        this(vertices, triangleIndices, material, 1.0F, MdxFloatTrack.EMPTY);
    }

    public MdxMesh(
            List<MdxVertex> vertices,
            int[] triangleIndices,
            MdxMaterial material,
            float geosetAlpha,
            MdxFloatTrack geosetAlphaTrack
    ) {
        this.vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        this.triangleIndices = Objects.requireNonNull(triangleIndices, "triangleIndices").clone();
        this.material = Objects.requireNonNull(material, "material");
        this.geosetAlpha = clamp01(geosetAlpha);
        this.geosetAlphaTrack = Objects.requireNonNull(geosetAlphaTrack, "geosetAlphaTrack");
        if (this.triangleIndices.length % 3 != 0) {
            throw new IllegalArgumentException("triangleIndices must contain complete triangles");
        }
        for (int index : this.triangleIndices) {
            if (index < 0 || index >= this.vertices.size()) {
                throw new IllegalArgumentException("Triangle index " + index + " is outside the vertex array");
            }
        }
    }

    public List<MdxVertex> vertices() {
        return vertices;
    }

    public int indexCount() {
        return triangleIndices.length;
    }

    public int index(int index) {
        return triangleIndices[index];
    }

    public MdxMaterial material() {
        return material;
    }

    public Vector4f applyGeosetAlpha(
            @Nullable MdxSequence sequence,
            float elapsedMillis,
            Vector4f color
    ) {
        float alpha = geosetAlphaTrack.sample(sequence, elapsedMillis, geosetAlpha);
        color.w *= clamp01(alpha);
        return color;
    }

    public Vector4f applyGeosetAlpha(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            Vector4f color
    ) {
        float alpha = geosetAlphaTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                geosetAlpha
        );
        color.w *= clamp01(alpha);
        return color;
    }

    public float geosetAlpha(@Nullable MdxSequence sequence, float elapsedMillis) {
        return clamp01(geosetAlphaTrack.sample(sequence, elapsedMillis, geosetAlpha));
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }
}
