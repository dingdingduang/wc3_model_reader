package com.wc3model2mc.mdx.model;

import com.wc3model2mc.mdx.animation.MdxFloatTrack;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Immutable renderer-ready representation of an MDX PRE2 emitter. */
public final class MdxParticleEmitter2 {
    public static final int FLAG_UNSHADED = 0x8000;
    public static final int FLAG_SORT_FAR_Z = 0x10000;
    public static final int FLAG_LINE_EMITTER = 0x20000;
    public static final int FLAG_UNFOGGED = 0x40000;
    public static final int FLAG_MODEL_SPACE = 0x80000;
    public static final int FLAG_XY_QUAD = 0x100000;

    private final String name;
    private final int boneIndex;
    private final int flags;
    private final float speed;
    private final float variation;
    private final float latitudeDegrees;
    private final float gravity;
    private final float lifespanSeconds;
    private final float emissionRate;
    private final float length;
    private final float width;
    private final int filterMode;
    private final int rows;
    private final int columns;
    private final int headOrTail;
    private final float tailLength;
    private final float segmentMiddle;
    private final List<Vector3f> segmentColors;
    private final int[] segmentAlpha;
    private final float[] segmentScaling;
    private final List<MdxParticleUvInterval> intervals;
    private final ResourceLocation texture;
    private final boolean squirt;
    private final int priorityPlane;
    private final int replaceableId;
    private final MdxFloatTrack visibilityTrack;
    private final MdxFloatTrack variationTrack;
    private final MdxFloatTrack gravityTrack;
    private final MdxFloatTrack emissionRateTrack;
    private final MdxFloatTrack widthTrack;
    private final MdxFloatTrack lengthTrack;
    private final MdxFloatTrack speedTrack;
    private final MdxFloatTrack latitudeTrack;

    public MdxParticleEmitter2(
            String name,
            int boneIndex,
            int flags,
            float speed,
            float variation,
            float latitudeDegrees,
            float gravity,
            float lifespanSeconds,
            float emissionRate,
            float length,
            float width,
            int filterMode,
            int rows,
            int columns,
            int headOrTail,
            float tailLength,
            float segmentMiddle,
            List<Vector3f> segmentColors,
            int[] segmentAlpha,
            float[] segmentScaling,
            List<MdxParticleUvInterval> intervals,
            ResourceLocation texture,
            boolean squirt,
            int priorityPlane,
            int replaceableId,
            MdxFloatTrack visibilityTrack,
            MdxFloatTrack variationTrack,
            MdxFloatTrack gravityTrack,
            MdxFloatTrack emissionRateTrack,
            MdxFloatTrack widthTrack,
            MdxFloatTrack lengthTrack,
            MdxFloatTrack speedTrack,
            MdxFloatTrack latitudeTrack
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.boneIndex = boneIndex;
        this.flags = flags;
        this.speed = finite(speed, "speed");
        this.variation = finite(variation, "variation");
        this.latitudeDegrees = finite(latitudeDegrees, "latitudeDegrees");
        this.gravity = finite(gravity, "gravity");
        this.lifespanSeconds = Math.max(0.0F, finite(lifespanSeconds, "lifespanSeconds"));
        this.emissionRate = Math.max(0.0F, finite(emissionRate, "emissionRate"));
        this.length = finite(length, "length");
        this.width = finite(width, "width");
        this.filterMode = filterMode;
        this.rows = Math.max(1, rows);
        this.columns = Math.max(1, columns);
        this.headOrTail = headOrTail;
        this.tailLength = finite(tailLength, "tailLength");
        this.segmentMiddle = Math.max(0.0F, Math.min(1.0F, finite(segmentMiddle, "segmentMiddle")));
        if (segmentColors.size() != 3 || segmentAlpha.length != 3
                || segmentScaling.length != 3 || intervals.size() != 4) {
            throw new IllegalArgumentException("PRE2 segment and UV arrays must have their MDX sizes");
        }
        this.segmentColors = segmentColors.stream().map(Vector3f::new).toList();
        this.segmentAlpha = segmentAlpha.clone();
        this.segmentScaling = segmentScaling.clone();
        this.intervals = List.copyOf(intervals);
        this.texture = Objects.requireNonNull(texture, "texture");
        this.squirt = squirt;
        this.priorityPlane = priorityPlane;
        this.replaceableId = replaceableId;
        this.visibilityTrack = Objects.requireNonNull(visibilityTrack, "visibilityTrack");
        this.variationTrack = Objects.requireNonNull(variationTrack, "variationTrack");
        this.gravityTrack = Objects.requireNonNull(gravityTrack, "gravityTrack");
        this.emissionRateTrack = Objects.requireNonNull(emissionRateTrack, "emissionRateTrack");
        this.widthTrack = Objects.requireNonNull(widthTrack, "widthTrack");
        this.lengthTrack = Objects.requireNonNull(lengthTrack, "lengthTrack");
        this.speedTrack = Objects.requireNonNull(speedTrack, "speedTrack");
        this.latitudeTrack = Objects.requireNonNull(latitudeTrack, "latitudeTrack");
    }

    public String name() { return name; }
    public int boneIndex() { return boneIndex; }
    public int flags() { return flags; }
    public float lifespanSeconds() { return lifespanSeconds; }
    public int filterMode() { return filterMode; }
    public int rows() { return rows; }
    public int columns() { return columns; }
    public float tailLength() { return tailLength; }
    public float segmentMiddle() { return segmentMiddle; }
    public ResourceLocation texture() { return texture; }
    public boolean squirt() { return squirt; }
    public int priorityPlane() { return priorityPlane; }
    public int replaceableId() { return replaceableId; }
    public boolean emitsHead() { return headOrTail == 0 || headOrTail == 2; }
    public boolean emitsTail() { return headOrTail == 1 || headOrTail == 2; }
    public boolean isLineEmitter() { return (flags & FLAG_LINE_EMITTER) != 0; }
    public boolean isModelSpace() { return (flags & FLAG_MODEL_SPACE) != 0; }
    public boolean isXyQuad() { return (flags & FLAG_XY_QUAD) != 0; }

    public Vector3f segmentColor(int index, Vector3f destination) {
        return destination.set(segmentColors.get(index));
    }

    public float segmentAlpha(int index) {
        return Math.max(0, Math.min(255, segmentAlpha[index])) / 255.0F;
    }

    public float segmentScale(int index) { return segmentScaling[index]; }
    public MdxParticleUvInterval interval(int index) { return intervals.get(index); }

    public float visibility(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return visibilityTrack.sample(sequence, sequenceTime, globalTime, 1.0F);
    }

    public float speed(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return speedTrack.sample(sequence, sequenceTime, globalTime, speed);
    }

    public float variation(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return variationTrack.sample(sequence, sequenceTime, globalTime, variation);
    }

    public float latitudeDegrees(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return latitudeTrack.sample(sequence, sequenceTime, globalTime, latitudeDegrees);
    }

    public float gravity(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return gravityTrack.sample(sequence, sequenceTime, globalTime, gravity);
    }

    public float emissionRate(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return emissionRateTrack.sample(sequence, sequenceTime, globalTime, emissionRate);
    }

    public float length(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return lengthTrack.sample(sequence, sequenceTime, globalTime, length);
    }

    public float width(@Nullable MdxSequence sequence, float sequenceTime, float globalTime) {
        return widthTrack.sample(sequence, sequenceTime, globalTime, width);
    }

    private static float finite(float value, String label) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value;
    }
}
