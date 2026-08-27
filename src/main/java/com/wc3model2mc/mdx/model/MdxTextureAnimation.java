package com.wc3model2mc.mdx.model;

import com.wc3model2mc.mdx.animation.MdxQuaternionTrack;
import com.wc3model2mc.mdx.animation.MdxVectorTrack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Objects;

/** Translation, rotation, and scaling timelines from one MDX TXAN entry. */
public final class MdxTextureAnimation {
    public static final MdxTextureAnimation EMPTY = new MdxTextureAnimation(
            MdxVectorTrack.EMPTY,
            MdxQuaternionTrack.EMPTY,
            MdxVectorTrack.EMPTY
    );

    private static final Vector3f ZERO = new Vector3f();
    private static final Vector3f ONE = new Vector3f(1.0F, 1.0F, 1.0F);
    private static final Quaternionf IDENTITY = new Quaternionf();

    private final MdxVectorTrack translationTrack;
    private final MdxQuaternionTrack rotationTrack;
    private final MdxVectorTrack scalingTrack;

    public MdxTextureAnimation(
            MdxVectorTrack translationTrack,
            MdxQuaternionTrack rotationTrack,
            MdxVectorTrack scalingTrack
    ) {
        this.translationTrack = Objects.requireNonNull(translationTrack, "translationTrack");
        this.rotationTrack = Objects.requireNonNull(rotationTrack, "rotationTrack");
        this.scalingTrack = Objects.requireNonNull(scalingTrack, "scalingTrack");
    }

    public boolean isEmpty() {
        return translationTrack.isEmpty() && rotationTrack.isEmpty() && scalingTrack.isEmpty();
    }

    public MdxUvTransform sample(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis
    ) {
        if (isEmpty()) {
            return MdxUvTransform.IDENTITY;
        }
        Vector3f translation = translationTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                ZERO,
                new Vector3f()
        );
        Quaternionf rotation = rotationTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                IDENTITY,
                new Quaternionf()
        );
        Vector3f scaling = scalingTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                ONE,
                new Vector3f()
        );
        return new MdxUvTransform(
                translation.x,
                translation.y,
                rotation.z,
                rotation.w,
                scaling.x,
                scaling.y
        );
    }

    /**
     * Samples into caller-owned storage. Render backends use this overload to
     * avoid allocating four short-lived objects per animated material layer.
     */
    public void sampleInto(
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            Vector3f translationDestination,
            Quaternionf rotationDestination,
            Vector3f scalingDestination
    ) {
        Objects.requireNonNull(translationDestination, "translationDestination");
        Objects.requireNonNull(rotationDestination, "rotationDestination");
        Objects.requireNonNull(scalingDestination, "scalingDestination");
        translationTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                ZERO,
                translationDestination
        );
        rotationTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                IDENTITY,
                rotationDestination
        );
        scalingTrack.sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                ONE,
                scalingDestination
        );
    }
}
