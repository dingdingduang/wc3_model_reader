package com.wc3model2mc.client.mdx;

import com.wc3model2mc.mdx.animation.MdxAnimationFrame;
import com.wc3model2mc.mdx.animation.MdxAnimationSample;
import com.wc3model2mc.mdx.model.MdxBone;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.wc3model2mc.mdx.model.MdxSequence;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Evaluates the MDX node hierarchy without retaining shared entity state. */
public final class MdxAnimationSampler {
    private static final Vector3f ZERO = new Vector3f();
    private static final Vector3f ONE = new Vector3f(1.0F, 1.0F, 1.0F);
    private static final Quaternionf IDENTITY = new Quaternionf();

    /* Used only by the render backend on Minecraft's render thread. */
    private final Map<MdxRenderModel, PoseWorkspace> renderWorkspaces =
            new WeakHashMap<>();

    public MdxPose sample(MdxRenderModel model, MdxAnimationSample animation) {
        return sample(model, MdxAnimationFrame.resolve(model, animation));
    }

    /** Public samples remain independent and safe for callers to retain. */
    public MdxPose sample(MdxRenderModel model, MdxAnimationFrame animationFrame) {
        PoseWorkspace workspace = new PoseWorkspace(model.bones().size());
        sampleInto(model, animationFrame, workspace);
        return workspace.pose;
    }

    /**
     * Allocation-free render-thread sample. The returned pose is valid until
     * this sampler next samples a different frame for the same model.
     */
    MdxPose sampleReusable(MdxRenderModel model, MdxAnimationFrame animationFrame) {
        PoseWorkspace workspace = renderWorkspaces.computeIfAbsent(
                model,
                ignored -> new PoseWorkspace(model.bones().size())
        );
        if (!workspace.matches(animationFrame)) {
            sampleInto(model, animationFrame, workspace);
            workspace.remember(animationFrame);
        }
        return workspace.pose;
    }

    private void sampleInto(
            MdxRenderModel model,
            MdxAnimationFrame animationFrame,
            PoseWorkspace workspace
    ) {
        List<MdxBone> bones = model.bones();
        if (workspace.boneMatrices.length != bones.size()) {
            throw new IllegalArgumentException("Pose workspace has the wrong bone count");
        }
        Arrays.fill(workspace.visitState, (byte) 0);

        for (int boneIndex = 0; boneIndex < bones.size(); boneIndex++) {
            resolveBone(
                    boneIndex,
                    bones,
                    animationFrame.sequence(),
                    animationFrame.sequenceTimeMillis(),
                    animationFrame.globalTimeMillis(),
                    workspace
            );
        }
    }

    private void resolveBone(
            int boneIndex,
            List<MdxBone> bones,
            @Nullable MdxSequence sequence,
            float sequenceTimeMillis,
            float globalTimeMillis,
            PoseWorkspace workspace
    ) {
        if (workspace.visitState[boneIndex] == 2) {
            return;
        }
        if (workspace.visitState[boneIndex] == 1) {
            throw new IllegalArgumentException(
                    "Cycle in MDX bone hierarchy at index " + boneIndex
            );
        }
        workspace.visitState[boneIndex] = 1;

        MdxBone bone = bones.get(boneIndex);
        int parentIndex = bone.parentIndex();
        if (parentIndex >= 0) {
            resolveBone(
                    parentIndex,
                    bones,
                    sequence,
                    sequenceTimeMillis,
                    globalTimeMillis,
                    workspace
            );
        }

        bone.translationTrack().sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                ZERO,
                workspace.translation
        );
        bone.rotationTrack().sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                IDENTITY,
                workspace.rotation
        );
        bone.scalingTrack().sample(
                sequence,
                sequenceTimeMillis,
                globalTimeMillis,
                ONE,
                workspace.scale
        );
        bone.pivot(workspace.pivot);

        workspace.local.translation(workspace.translation)
                .translate(workspace.pivot)
                .rotate(workspace.rotation)
                .scale(workspace.scale)
                .translate(
                        -workspace.pivot.x,
                        -workspace.pivot.y,
                        -workspace.pivot.z
                );

        Matrix4f matrix = workspace.boneMatrices[boneIndex];
        if (parentIndex >= 0) {
            matrix.set(workspace.boneMatrices[parentIndex]).mul(workspace.local);
        } else {
            matrix.set(workspace.local);
        }

        Matrix3f normal = workspace.normalMatrices[boneIndex];
        normal.set(matrix);
        if (Math.abs(normal.determinant()) > 1.0E-8F) {
            normal.invert().transpose();
        } else {
            normal.identity();
        }
        workspace.visitState[boneIndex] = 2;
    }

    private static final class PoseWorkspace {
        private final Matrix4f[] boneMatrices;
        private final Matrix3f[] normalMatrices;
        private final byte[] visitState;
        private final MdxPose pose;
        private final Vector3f translation = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f scale = new Vector3f();
        private final Vector3f pivot = new Vector3f();
        private final Matrix4f local = new Matrix4f();
        @Nullable
        private MdxSequence lastSequence;
        private int lastSequenceTimeBits;
        private int lastGlobalTimeBits;
        private boolean hasRememberedFrame;

        private PoseWorkspace(int boneCount) {
            boneMatrices = new Matrix4f[boneCount];
            normalMatrices = new Matrix3f[boneCount];
            visitState = new byte[boneCount];
            for (int index = 0; index < boneCount; index++) {
                boneMatrices[index] = new Matrix4f();
                normalMatrices[index] = new Matrix3f();
            }
            pose = new MdxPose(boneMatrices, normalMatrices);
        }

        private boolean matches(MdxAnimationFrame frame) {
            return hasRememberedFrame
                    && lastSequence == frame.sequence()
                    && lastSequenceTimeBits
                    == Float.floatToIntBits(frame.sequenceTimeMillis())
                    && lastGlobalTimeBits
                    == Float.floatToIntBits(frame.globalTimeMillis());
        }

        private void remember(MdxAnimationFrame frame) {
            lastSequence = frame.sequence();
            lastSequenceTimeBits = Float.floatToIntBits(frame.sequenceTimeMillis());
            lastGlobalTimeBits = Float.floatToIntBits(frame.globalTimeMillis());
            hasRememberedFrame = true;
        }
    }
}
