package com.wc3model2mc.client.mdx;

import com.wc3model2mc.mdx.animation.MdxAnimationSample;
import com.wc3model2mc.mdx.animation.MdxAnimationFrame;
import com.wc3model2mc.mdx.model.MdxBlendMode;
import com.wc3model2mc.mdx.model.MdxMaterial;
import com.wc3model2mc.mdx.model.MdxMesh;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.wc3model2mc.mdx.model.MdxSequence;
import com.wc3model2mc.mdx.model.MdxVertex;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reference CPU skinning backend. It emits only through {@link MultiBufferSource}
 * and never owns or flushes Minecraft's buffers.
 */
public final class CpuSkinnedMdxRenderBackend implements MdxRenderBackend {
    public static final CpuSkinnedMdxRenderBackend INSTANCE = new CpuSkinnedMdxRenderBackend();

    private final MdxAnimationSampler animationSampler = new MdxAnimationSampler();
    private final MdxParticleRenderer particleRenderer = new MdxParticleRenderer();
    private final Map<MdxMesh, MeshScratch> meshScratch = new WeakHashMap<>();
    private final Vector4f materialColor = new Vector4f();
    private final UvTransformScratch uvTransform = new UvTransformScratch();

    private CpuSkinnedMdxRenderBackend() {
    }

    @Override
    public void render(
            MdxRenderModel model,
            MdxAnimationSample animation,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        render(
                model,
                animation,
                poseStack,
                buffers,
                packedLight,
                packedOverlay,
                MdxRenderDetail.FULL,
                1.0F
        );
    }

    @Override
    public void render(
            MdxRenderModel model,
            MdxAnimationSample animation,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            MdxRenderDetail detail
    ) {
        render(
                model,
                animation,
                poseStack,
                buffers,
                packedLight,
                packedOverlay,
                detail,
                1.0F
        );
    }

    @Override
    public void render(
            MdxRenderModel model,
            MdxAnimationSample animation,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            MdxRenderDetail detail,
            float opacity
    ) {
        float modelOpacity = clamp01(opacity);
        if (modelOpacity <= 1.0E-4F) {
            return;
        }
        MdxAnimationFrame animationFrame = MdxAnimationFrame.resolve(model, animation);
        MdxPose pose = animationSampler.sampleReusable(model, animationFrame);
        PoseStack.Pose minecraftPose = poseStack.last();
        MdxSequence sequence = animationFrame.sequence();
        int visibleBlendedLayerIndex = 0;

        for (MdxMesh mesh : model.meshes()) {
            MdxMaterial material = mesh.material();
            material.color(
                    sequence,
                    animationFrame.sequenceTimeMillis(),
                    animationFrame.globalTimeMillis(),
                    materialColor
            );
            mesh.applyGeosetAlpha(
                    sequence,
                    animationFrame.sequenceTimeMillis(),
                    animationFrame.globalTimeMillis(),
                    materialColor
            );
            materialColor.w *= modelOpacity;
            if (materialColor.w <= 1.0E-4F) {
                continue;
            }
            MdxBlendMode blendMode = material.blendMode();
            if (blendMode == MdxBlendMode.ADDITIVE
                    || blendMode == MdxBlendMode.TRANSLUCENT) {
                if (!detail.shouldRenderBlendedLayer(visibleBlendedLayerIndex++)) {
                    continue;
                }
            }
            ResourceLocation texture = material.texture(
                    sequence,
                    animationFrame.sequenceTimeMillis(),
                    animationFrame.globalTimeMillis()
            );
            VertexConsumer consumer = buffers.getBuffer(renderType(
                    material,
                    texture,
                    modelOpacity
            ));
            int meshLight = material.fullBright() ? LightTexture.FULL_BRIGHT : packedLight;
            material.textureCoordinatesInto(
                    sequence,
                    animationFrame.sequenceTimeMillis(),
                    animationFrame.globalTimeMillis(),
                    uvTransform.translation,
                    uvTransform.rotation,
                    uvTransform.scaling
            );
            renderMesh(
                    mesh,
                    pose,
                    minecraftPose,
                    consumer,
                    meshLight,
                    packedOverlay,
                    materialColor,
                    uvTransform
            );
        }
        particleRenderer.render(
                model,
                animationFrame,
                pose,
                minecraftPose,
                buffers,
                packedLight,
                packedOverlay,
                detail.particleDensity(),
                modelOpacity
        );
    }

    private void renderMesh(
            MdxMesh mesh,
            MdxPose pose,
            PoseStack.Pose minecraftPose,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            Vector4f color,
            UvTransformScratch uvTransform
    ) {
        MeshScratch scratch = meshScratch.computeIfAbsent(
                mesh,
                ignored -> new MeshScratch(mesh.vertices().size())
        );

        int red = channel(color.x);
        int green = channel(color.y);
        int blue = channel(color.z);
        int alpha = channel(color.w);

        // An indexed mesh commonly references the same vertex from several
        // triangles. Skin and transform every unique vertex once, then reuse
        // those results while emitting the triangle list.
        for (int vertexIndex = 0; vertexIndex < mesh.vertices().size(); vertexIndex++) {
            MdxVertex vertex = mesh.vertices().get(vertexIndex);
            vertex.position(scratch.sourcePosition);
            vertex.normal(scratch.sourceNormal);
            skinVertex(
                    vertex,
                    pose,
                    scratch.sourcePosition,
                    scratch.sourceNormal,
                    scratch.skinnedPosition,
                    scratch.skinnedNormal,
                    scratch.transformedPosition,
                    scratch.transformedNormal
            );
            uvTransform.transform(vertex.u(), vertex.v(), scratch.transformedUv);
            scratch.store(vertexIndex);
        }

        for (int indexOffset = 0; indexOffset < mesh.indexCount(); indexOffset++) {
            int vertexIndex = mesh.index(indexOffset);
            int positionOffset = vertexIndex * 3;
            int uvOffset = vertexIndex * 2;

            consumer.vertex(
                            minecraftPose.pose(),
                            scratch.positions[positionOffset],
                            scratch.positions[positionOffset + 1],
                            scratch.positions[positionOffset + 2]
                    )
                    .color(red, green, blue, alpha)
                    .uv(scratch.uvs[uvOffset], scratch.uvs[uvOffset + 1])
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(
                            minecraftPose.normal(),
                            scratch.normals[positionOffset],
                            scratch.normals[positionOffset + 1],
                            scratch.normals[positionOffset + 2]
                    )
                    .endVertex();
        }
    }

    private void skinVertex(
            MdxVertex vertex,
            MdxPose pose,
            Vector3f sourcePosition,
            Vector3f sourceNormal,
            Vector3f skinnedPosition,
            Vector3f skinnedNormal,
            Vector3f transformedPosition,
            Vector3f transformedNormal
    ) {
        skinnedPosition.zero();
        skinnedNormal.zero();
        float appliedWeight = 0.0F;

        for (int influence = 0; influence < vertex.influenceCount(); influence++) {
            int boneIndex = vertex.boneIndex(influence);
            float weight = vertex.boneWeight(influence);
            if (weight <= 0.0F || boneIndex >= pose.boneCount()) {
                continue;
            }
            Matrix4f matrix = pose.boneMatrix(boneIndex);
            Matrix3f normalMatrix = pose.normalMatrix(boneIndex);
            matrix.transformPosition(sourcePosition, transformedPosition);
            normalMatrix.transform(sourceNormal, transformedNormal);
            skinnedPosition.fma(weight, transformedPosition);
            skinnedNormal.fma(weight, transformedNormal);
            appliedWeight += weight;
        }

        if (appliedWeight <= 1.0E-6F) {
            skinnedPosition.set(sourcePosition);
            skinnedNormal.set(sourceNormal);
            return;
        }
        if (Math.abs(appliedWeight - 1.0F) > 1.0E-4F) {
            skinnedPosition.div(appliedWeight);
            skinnedNormal.div(appliedWeight);
        }
        if (skinnedNormal.lengthSquared() > 1.0E-8F) {
            skinnedNormal.normalize();
        } else {
            skinnedNormal.set(sourceNormal).normalize();
        }
    }

    private static RenderType renderType(
            MdxMaterial material,
            ResourceLocation texture,
            float opacity
    ) {
        MdxBlendMode blendMode = material.blendMode();
        if (blendMode == MdxBlendMode.ADDITIVE) {
            return MdxRenderTypes.additive(texture, material.twoSided());
        }
        if (blendMode == MdxBlendMode.TRANSLUCENT || opacity < 0.9999F) {
            return MdxRenderTypes.translucent(texture, material.twoSided());
        }
        return MdxRenderTypes.cutout(texture, material.twoSided());
    }

    private static int channel(float value) {
        return Math.round(clamp01(value) * 255.0F);
    }

    private static float clamp01(float value) {
        return Float.isFinite(value)
                ? Math.max(0.0F, Math.min(1.0F, value))
                : 1.0F;
    }

    /** Reused render-thread storage for one immutable mesh. */
    private static final class MeshScratch {
        private final float[] positions;
        private final float[] normals;
        private final float[] uvs;
        private final Vector3f sourcePosition = new Vector3f();
        private final Vector3f sourceNormal = new Vector3f();
        private final Vector3f skinnedPosition = new Vector3f();
        private final Vector3f skinnedNormal = new Vector3f();
        private final Vector3f transformedPosition = new Vector3f();
        private final Vector3f transformedNormal = new Vector3f();
        private final Vector2f transformedUv = new Vector2f();

        private MeshScratch(int vertexCount) {
            positions = new float[vertexCount * 3];
            normals = new float[vertexCount * 3];
            uvs = new float[vertexCount * 2];
        }

        private void store(int vertexIndex) {
            int positionOffset = vertexIndex * 3;
            positions[positionOffset] = skinnedPosition.x;
            positions[positionOffset + 1] = skinnedPosition.y;
            positions[positionOffset + 2] = skinnedPosition.z;
            normals[positionOffset] = skinnedNormal.x;
            normals[positionOffset + 1] = skinnedNormal.y;
            normals[positionOffset + 2] = skinnedNormal.z;

            int uvOffset = vertexIndex * 2;
            uvs[uvOffset] = transformedUv.x;
            uvs[uvOffset + 1] = transformedUv.y;
        }
    }

    /** Reused render-thread storage for one sampled TXAN transform. */
    private static final class UvTransformScratch {
        private final Vector3f translation = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f scaling = new Vector3f(1.0F);

        private void transform(float u, float v, Vector2f destination) {
            float centeredU = u + translation.x - 0.5F;
            float centeredV = v + translation.y - 0.5F;
            float rotationZ = rotation.z;
            float rotationW = rotation.w;
            float rotatedU = centeredU
                    + 2.0F * (-rotationZ * centeredV * rotationW
                    - rotationZ * rotationZ * centeredU);
            float rotatedV = centeredV
                    + 2.0F * (rotationZ * centeredU * rotationW
                    - rotationZ * rotationZ * centeredV);
            destination.set(
                    scaling.x * rotatedU + 0.5F,
                    scaling.y * rotatedV + 0.5F
            );
        }
    }
}
