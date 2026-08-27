package com.wc3model2mc.mdx.model;

import com.wc3model2mc.mdx.animation.MdxFloatTrack;
import com.wc3model2mc.mdx.animation.MdxTextureTrack;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.Objects;

/** One already-resolved MDX material layer. */
public final class MdxMaterial {
    private final ResourceLocation texture;
    private final MdxBlendMode blendMode;
    private final boolean fullBright;
    private final boolean twoSided;
    private final Vector4f color;
    private final MdxTextureTrack textureTrack;
    private final MdxFloatTrack alphaTrack;
    private final MdxTextureAnimation textureAnimation;

    public MdxMaterial(
            ResourceLocation texture,
            MdxBlendMode blendMode,
            boolean fullBright,
            Vector4f color
    ) {
        this(
                texture,
                blendMode,
                fullBright,
                true,
                color,
                MdxTextureTrack.EMPTY,
                MdxFloatTrack.EMPTY,
                MdxTextureAnimation.EMPTY
        );
    }

    public MdxMaterial(
            ResourceLocation texture,
            MdxBlendMode blendMode,
            boolean fullBright,
            Vector4f color,
            MdxTextureTrack textureTrack,
            MdxFloatTrack alphaTrack
    ) {
        this(
                texture,
                blendMode,
                fullBright,
                true,
                color,
                textureTrack,
                alphaTrack,
                MdxTextureAnimation.EMPTY
        );
    }

    public MdxMaterial(
            ResourceLocation texture,
            MdxBlendMode blendMode,
            boolean fullBright,
            boolean twoSided,
            Vector4f color,
            MdxTextureTrack textureTrack,
            MdxFloatTrack alphaTrack
    ) {
        this(
                texture,
                blendMode,
                fullBright,
                twoSided,
                color,
                textureTrack,
                alphaTrack,
                MdxTextureAnimation.EMPTY
        );
    }

    public MdxMaterial(
            ResourceLocation texture,
            MdxBlendMode blendMode,
            boolean fullBright,
            boolean twoSided,
            Vector4f color,
            MdxTextureTrack textureTrack,
            MdxFloatTrack alphaTrack,
            MdxTextureAnimation textureAnimation
    ) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.blendMode = Objects.requireNonNull(blendMode, "blendMode");
        this.fullBright = fullBright;
        this.twoSided = twoSided;
        this.color = new Vector4f(Objects.requireNonNull(color, "color"));
        this.textureTrack = Objects.requireNonNull(textureTrack, "textureTrack");
        this.alphaTrack = Objects.requireNonNull(alphaTrack, "alphaTrack");
        this.textureAnimation = Objects.requireNonNull(textureAnimation, "textureAnimation");
    }

    public ResourceLocation texture() {
        return texture;
    }

    public ResourceLocation texture(
            @Nullable MdxSequence sequence,
            float elapsedMillis
    ) {
        return textureTrack.sample(sequence, elapsedMillis, texture);
    }

    public ResourceLocation texture(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis
    ) {
        return textureTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                texture
        );
    }

    public MdxBlendMode blendMode() {
        return blendMode;
    }

    public boolean fullBright() {
        return fullBright;
    }

    /** Whether the MDX layer has the TwoSided shading flag. */
    public boolean twoSided() {
        return twoSided;
    }

    public Vector4f color(Vector4f destination) {
        return destination.set(color);
    }

    public Vector4f color(
            @Nullable MdxSequence sequence,
            float elapsedMillis,
            Vector4f destination
    ) {
        destination.set(color);
        destination.w = clamp01(alphaTrack.sample(sequence, elapsedMillis, color.w));
        return destination;
    }

    public Vector4f color(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            Vector4f destination
    ) {
        destination.set(color);
        destination.w = clamp01(alphaTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                color.w
        ));
        return destination;
    }

    public boolean hasAnimatedTexture() {
        return !textureTrack.isEmpty();
    }

    public boolean hasAnimatedTextureCoordinates() {
        return !textureAnimation.isEmpty();
    }

    public MdxUvTransform textureCoordinates(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis
    ) {
        return textureAnimation.sample(sequence, sequenceTimeMillis, globalTimeMillis);
    }

    /** Samples texture animation into reusable caller-owned values. */
    public void textureCoordinatesInto(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            Vector3f translationDestination,
            Quaternionf rotationDestination,
            Vector3f scalingDestination
    ) {
        textureAnimation.sampleInto(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                translationDestination,
                rotationDestination,
                scalingDestination
        );
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }
}
