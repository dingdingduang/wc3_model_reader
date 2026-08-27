package com.wc3model2mc.client.mdx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wc3model2mc.mdx.animation.MdxAnimationFrame;
import com.wc3model2mc.mdx.model.MdxParticleEmitter2;
import com.wc3model2mc.mdx.model.MdxParticleUvInterval;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.wc3model2mc.mdx.model.MdxSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** CPU ParticleEmitter2 renderer that writes only to Minecraft-owned buffers. */
final class MdxParticleRenderer {
    private static final int MAX_PARTICLES_PER_EMITTER = 1024;
    private static final int MAX_PARTICLES_PER_MODEL = 4096;

    /* Render-thread scratch avoids allocating dozens of math objects per particle. */
    private final Matrix3f modelRotationMatrix = new Matrix3f();
    private final Quaternionf modelRotation = new Quaternionf();
    private final Quaternionf cameraRotation = new Quaternionf();
    private final Quaternionf localBillboard = new Quaternionf();
    private final Vector3f billboardRight = new Vector3f();
    private final Vector3f billboardUp = new Vector3f();
    private final Vector3f billboardForward = new Vector3f();
    private final Vector3f emitterPivot = new Vector3f();
    private final Vector3f xyQuadRight = new Vector3f(1.0F, 0.0F, 0.0F);
    private final Vector3f xyQuadUp = new Vector3f(0.0F, 0.0F, -1.0F);
    private final Vector3f scaledRight = new Vector3f();
    private final Vector3f scaledUp = new Vector3f();
    private final Vector3f tailStart = new Vector3f();
    private final Vector3f tailEnd = new Vector3f();
    private final Vector3f tailDirection = new Vector3f();
    private final Vector3f tailSide = new Vector3f();
    private final Vector3f quad0 = new Vector3f();
    private final Vector3f quad1 = new Vector3f();
    private final Vector3f quad2 = new Vector3f();
    private final Vector3f quad3 = new Vector3f();
    private final ParticleScratch particle = new ParticleScratch();

    void render(
            MdxRenderModel model,
            MdxAnimationFrame frame,
            MdxPose pose,
            PoseStack.Pose minecraftPose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            float particleDensity,
            float modelOpacity
    ) {
        float opacity = clamp01(modelOpacity);
        if (model.particleEmitters().isEmpty() || opacity <= 1.0E-4F) {
            return;
        }

        modelRotationMatrix.set(minecraftPose.pose())
                .getNormalizedRotation(modelRotation);
        cameraRotation.set(
                Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation()
        );
        localBillboard.set(modelRotation)
                .conjugate()
                .mul(cameraRotation)
                .normalize();
        billboardRight.set(1.0F, 0.0F, 0.0F);
        billboardUp.set(0.0F, 1.0F, 0.0F);
        billboardForward.set(0.0F, 0.0F, 1.0F);
        localBillboard.transform(billboardRight);
        localBillboard.transform(billboardUp);
        localBillboard.transform(billboardForward);

        MdxSequence sequence = frame.sequence();
        float sequenceTime = frame.sequenceTimeMillis();
        float globalTime = frame.globalTimeMillis();
        float elapsedSeconds = Math.max(0.0F, globalTime * 0.001F);
        int totalRendered = 0;

        for (int emitterIndex = 0;
             emitterIndex < model.particleEmitters().size()
                     && totalRendered < MAX_PARTICLES_PER_MODEL;
             emitterIndex++) {
            MdxParticleEmitter2 emitter = model.particleEmitters().get(emitterIndex);
            float visibility = clamp01(emitter.visibility(
                    sequence,
                    sequenceTime,
                    globalTime
            ));
            float lifespan = emitter.lifespanSeconds();
            float rate = Math.max(0.0F, emitter.emissionRate(
                    sequence,
                    sequenceTime,
                    globalTime
            ));
            if (visibility <= 1.0E-4F || lifespan <= 1.0E-4F || rate <= 1.0E-4F
                    || emitter.boneIndex() < 0 || emitter.boneIndex() >= pose.boneCount()) {
                continue;
            }

            int aliveCount = Math.min(
                    MAX_PARTICLES_PER_EMITTER,
                    Math.max(1, (int) Math.ceil(rate * lifespan * visibility))
            );
            int particleStep = Math.max(
                    1,
                    Math.round(1.0F / Math.max(0.01F, particleDensity))
            );
            long newestSpawn = (long) Math.floor(elapsedSeconds * rate);
            VertexConsumer consumer = buffers.getBuffer(renderType(emitter, opacity));
            Matrix4f emitterMatrix = pose.boneMatrix(emitter.boneIndex());
            model.bones().get(emitter.boneIndex()).pivot(emitterPivot);

            for (int slot = 0;
                 slot < aliveCount && totalRendered < MAX_PARTICLES_PER_MODEL;
                 slot += particleStep) {
                long spawnIndex = newestSpawn - slot;
                if (spawnIndex < 0) {
                    continue;
                }
                float spawnTime = spawnIndex / rate;
                float age = elapsedSeconds - spawnTime;
                if (age < 0.0F || age >= lifespan) {
                    continue;
                }

                sampleParticle(
                        emitter,
                        emitterIndex,
                        spawnIndex,
                        age,
                        lifespan,
                        sequence,
                        sequenceTime,
                        globalTime,
                        emitterPivot,
                        emitterMatrix,
                        opacity
                );
                if (emitter.emitsHead()) {
                    renderHead(
                            emitter,
                            emitter.isXyQuad() ? xyQuadRight : billboardRight,
                            emitter.isXyQuad() ? xyQuadUp : billboardUp,
                            minecraftPose,
                            consumer,
                            packedLight,
                            packedOverlay
                    );
                    totalRendered++;
                }
                if (emitter.emitsTail() && totalRendered < MAX_PARTICLES_PER_MODEL) {
                    renderTail(
                            emitter,
                            billboardForward,
                            minecraftPose,
                            consumer,
                            packedLight,
                            packedOverlay
                    );
                    totalRendered++;
                }
            }
        }
    }

    private void sampleParticle(
            MdxParticleEmitter2 emitter,
            int emitterIndex,
            long spawnIndex,
            float age,
            float lifespan,
            MdxSequence sequence,
            float sequenceTime,
            float globalTime,
            Vector3f pivot,
            Matrix4f emitterMatrix,
            float modelOpacity
    ) {
        long seed = mix64(((long) emitterIndex << 32) ^ spawnIndex);
        float randomX = signedRandom(seed);
        float randomY = signedRandom(mix64(seed + 1));
        float randomLatitudeX = signedRandom(mix64(seed + 2));
        float randomLatitudeY = signedRandom(mix64(seed + 3));
        float randomVariation = signedRandom(mix64(seed + 4));

        float emitterLength = emitter.length(sequence, sequenceTime, globalTime);
        float emitterWidth = emitter.width(sequence, sequenceTime, globalTime);
        particle.localPosition.set(pivot).add(
                randomX * emitterLength * 0.5F,
                0.0F,
                -randomY * emitterWidth * 0.5F
        );

        float latitude = (float) Math.toRadians(emitter.latitudeDegrees(
                sequence,
                sequenceTime,
                globalTime
        ));
        particle.directionRotation.identity()
                .rotateX(randomLatitudeX * latitude);
        if (!emitter.isLineEmitter()) {
            particle.directionRotation.rotateY(randomLatitudeY * latitude);
        }
        particle.wc3Direction.set(0.0F, 0.0F, 1.0F);
        particle.directionRotation.transform(particle.wc3Direction);
        particle.localVelocity.set(
                particle.wc3Direction.x,
                particle.wc3Direction.z,
                -particle.wc3Direction.y
        ).mul(Math.max(0.0F, emitter.speed(sequence, sequenceTime, globalTime)
                + randomVariation * emitter.variation(
                        sequence,
                        sequenceTime,
                        globalTime
                )));

        float gravity = emitter.gravity(sequence, sequenceTime, globalTime);
        particle.localPosition.fma(age, particle.localVelocity);
        particle.localPosition.y -= 0.5F * gravity * age * age;
        emitterMatrix.transformPosition(particle.localPosition, particle.center);
        emitterMatrix.transformDirection(particle.localVelocity, particle.velocity);
        particle.velocity.y -= gravity * age;

        float lifeFactor = clamp01(age / lifespan);
        float middle = emitter.segmentMiddle();
        int firstSegment;
        if (lifeFactor < middle && middle > 1.0E-6F) {
            firstSegment = 0;
            particle.segmentFactor = lifeFactor / middle;
            particle.intervalIndex = 0;
        } else {
            firstSegment = 1;
            particle.segmentFactor = middle >= 1.0F
                    ? 1.0F
                    : (lifeFactor - middle) / (1.0F - middle);
            particle.intervalIndex = 1;
        }
        particle.segmentFactor = clamp01(particle.segmentFactor);

        emitter.segmentColor(firstSegment, particle.leftColor);
        emitter.segmentColor(firstSegment + 1, particle.rightColor);
        particle.color.set(particle.leftColor).lerp(
                particle.rightColor,
                particle.segmentFactor
        );
        particle.alpha = lerp(
                emitter.segmentAlpha(firstSegment),
                emitter.segmentAlpha(firstSegment + 1),
                particle.segmentFactor
        ) * modelOpacity;
        particle.scale = lerp(
                emitter.segmentScale(firstSegment),
                emitter.segmentScale(firstSegment + 1),
                particle.segmentFactor
        );
    }

    private void renderHead(
            MdxParticleEmitter2 emitter,
            Vector3f right,
            Vector3f up,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay
    ) {
        scaledRight.set(right).mul(particle.scale);
        scaledUp.set(up).mul(particle.scale);
        quad0.set(particle.center).sub(scaledRight).add(scaledUp);
        quad1.set(particle.center).sub(scaledRight).sub(scaledUp);
        quad2.set(particle.center).add(scaledRight).sub(scaledUp);
        quad3.set(particle.center).add(scaledRight).add(scaledUp);
        emitQuad(
                emitter,
                particle.intervalIndex,
                quad0,
                quad1,
                quad2,
                quad3,
                pose,
                consumer,
                light,
                overlay
        );
    }

    private void renderTail(
            MdxParticleEmitter2 emitter,
            Vector3f cameraForward,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay
    ) {
        tailEnd.set(particle.center);
        tailStart.set(tailEnd).fma(-emitter.tailLength(), particle.velocity);
        tailDirection.set(tailEnd).sub(tailStart);
        if (tailDirection.lengthSquared() <= 1.0E-8F) {
            tailDirection.set(0.0F, 1.0F, 0.0F);
        } else {
            tailDirection.normalize();
        }
        tailSide.set(cameraForward).cross(tailDirection);
        if (tailSide.lengthSquared() <= 1.0E-8F) {
            tailSide.set(1.0F, 0.0F, 0.0F);
        } else {
            tailSide.normalize();
        }
        tailSide.mul(particle.scale);
        quad0.set(tailStart).sub(tailSide);
        quad1.set(tailEnd).sub(tailSide);
        quad2.set(tailEnd).add(tailSide);
        quad3.set(tailStart).add(tailSide);
        emitQuad(
                emitter,
                particle.intervalIndex + 2,
                quad0,
                quad1,
                quad2,
                quad3,
                pose,
                consumer,
                light,
                overlay
        );
    }

    private void emitQuad(
            MdxParticleEmitter2 emitter,
            int intervalIndex,
            Vector3f topLeft,
            Vector3f bottomLeft,
            Vector3f bottomRight,
            Vector3f topRight,
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            int overlay
    ) {
        MdxParticleUvInterval interval = emitter.interval(intervalIndex);
        int spriteSpan = interval.end() - interval.start();
        int sprite = interval.start();
        if (spriteSpan > 0 && (emitter.rows() > 1 || emitter.columns() > 1)) {
            int repeat = Math.max(1, interval.repeat());
            sprite += Math.floorMod(
                    (int) Math.floor(spriteSpan * repeat * particle.segmentFactor),
                    spriteSpan
            );
        }
        float left = Math.floorMod(sprite, emitter.columns()) / (float) emitter.columns();
        float top = Math.floorDiv(sprite, emitter.columns()) / (float) emitter.rows();
        float right = left + 1.0F / emitter.columns();
        float bottom = top + 1.0F / emitter.rows();

        int red = channel(particle.color.x);
        int green = channel(particle.color.y);
        int blue = channel(particle.color.z);
        int alpha = channel(particle.alpha);
        vertex(consumer, pose, topLeft, left, top, red, green, blue, alpha, light, overlay);
        vertex(consumer, pose, bottomLeft, left, bottom, red, green, blue, alpha, light, overlay);
        vertex(consumer, pose, bottomRight, right, bottom, red, green, blue, alpha, light, overlay);
        vertex(consumer, pose, topLeft, left, top, red, green, blue, alpha, light, overlay);
        vertex(consumer, pose, bottomRight, right, bottom, red, green, blue, alpha, light, overlay);
        vertex(consumer, pose, topRight, right, top, red, green, blue, alpha, light, overlay);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vector3f position,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            int light,
            int overlay
    ) {
        consumer.vertex(pose.pose(), position.x, position.y, position.z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    private static RenderType renderType(MdxParticleEmitter2 emitter, float opacity) {
        return switch (emitter.filterMode()) {
            case 1, 3 -> MdxRenderTypes.additive(emitter.texture(), true);
            case 4 -> opacity < 0.9999F
                    ? MdxRenderTypes.translucent(emitter.texture(), true)
                    : MdxRenderTypes.cutout(emitter.texture(), true);
            default -> MdxRenderTypes.translucent(emitter.texture(), true);
        };
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static float signedRandom(long value) {
        return (((value >>> 40) & 0xFFFFFFL) / 8388607.5F) - 1.0F;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    private static float lerp(float left, float right, float factor) {
        return left + (right - left) * factor;
    }

    private static int channel(float value) {
        return Math.round(clamp01(value) * 255.0F);
    }

    private static final class ParticleScratch {
        private final Vector3f center = new Vector3f();
        private final Vector3f velocity = new Vector3f();
        private final Vector3f color = new Vector3f();
        private final Vector3f localPosition = new Vector3f();
        private final Vector3f localVelocity = new Vector3f();
        private final Vector3f wc3Direction = new Vector3f();
        private final Vector3f leftColor = new Vector3f();
        private final Vector3f rightColor = new Vector3f();
        private final Quaternionf directionRotation = new Quaternionf();
        private float alpha;
        private float scale;
        private float segmentFactor;
        private int intervalIndex;
    }
}
