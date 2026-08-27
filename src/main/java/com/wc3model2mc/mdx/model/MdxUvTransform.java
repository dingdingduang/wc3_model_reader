package com.wc3model2mc.mdx.model;

import org.joml.Vector2f;

import java.util.Objects;

/** A sampled Warcraft III texture-coordinate transform. */
public record MdxUvTransform(
        float translationX,
        float translationY,
        float rotationZ,
        float rotationW,
        float scaleX,
        float scaleY
) {
    public static final MdxUvTransform IDENTITY = new MdxUvTransform(
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            1.0F,
            1.0F
    );

    /**
     * Applies Warcraft III's TXAN order: translate, rotate around the UV
     * center, and finally scale around the UV center.
     */
    public Vector2f transform(float u, float v, Vector2f destination) {
        Objects.requireNonNull(destination, "destination");

        float translatedU = u + translationX;
        float translatedV = v + translationY;
        float centeredU = translatedU - 0.5F;
        float centeredV = translatedV - 0.5F;

        // MDX texture rotations use the Z/W components of a quaternion.
        float rotatedU = centeredU
                + 2.0F * (-rotationZ * centeredV * rotationW
                - rotationZ * rotationZ * centeredU);
        float rotatedV = centeredV
                + 2.0F * (rotationZ * centeredU * rotationW
                - rotationZ * rotationZ * centeredV);

        return destination.set(
                scaleX * rotatedU + 0.5F,
                scaleY * rotatedV + 0.5F
        );
    }
}
