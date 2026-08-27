package com.wc3model2mc.client.mdx.io;

import com.mojang.logging.LogUtils;
import com.wc3model2mc.api.client.Wc3ModelResourceBundle;
import com.wc3model2mc.mdx.animation.MdxFloatKeyframe;
import com.wc3model2mc.mdx.animation.MdxFloatTrack;
import com.wc3model2mc.mdx.animation.MdxInterpolation;
import com.wc3model2mc.mdx.animation.MdxQuaternionKeyframe;
import com.wc3model2mc.mdx.animation.MdxQuaternionTrack;
import com.wc3model2mc.mdx.animation.MdxTextureKeyframe;
import com.wc3model2mc.mdx.animation.MdxTextureTrack;
import com.wc3model2mc.mdx.animation.MdxVectorKeyframe;
import com.wc3model2mc.mdx.animation.MdxVectorTrack;
import com.wc3model2mc.mdx.model.MdxBlendMode;
import com.wc3model2mc.mdx.model.MdxBone;
import com.wc3model2mc.mdx.model.MdxMaterial;
import com.wc3model2mc.mdx.model.MdxMesh;
import com.wc3model2mc.mdx.model.MdxParticleEmitter2;
import com.wc3model2mc.mdx.model.MdxParticleUvInterval;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import com.wc3model2mc.mdx.model.MdxSequence;
import com.wc3model2mc.mdx.model.MdxTextureAnimation;
import com.wc3model2mc.mdx.model.MdxVertex;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for the Warcraft III legacy MDLX version-800 format.
 *
 * <p>The chunk layout follows the MIT-licensed parser in Retera Model Studio,
 * while producing WC3Model2MC's small immutable render model directly.</p>
 */
public final class LegacyMdxParser {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LEGACY_VERSION = 800;
    private static final int SEQUENCE_SIZE = 132;
    private static final int TEXTURE_SIZE = 268;
    private static final int MAX_COLLECTION_SIZE = 10_000_000;
    private static final ResourceLocation MISSING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "missingno");
    private static final Quaternionf WC3_TO_MINECRAFT = new Quaternionf()
            .rotationX((float) (-Math.PI / 2.0));

    private LegacyMdxParser() {
    }

    public static MdxRenderModel parse(Wc3ModelResourceBundle resources) throws IOException {
        RawModel raw = parseRaw(resources.copyMdxBytes());
        if (raw.version != LEGACY_VERSION) {
            throw new IOException(
                    "Legacy decoder supports MDLX version 800; found " + raw.version
                            + " in " + resources.mdxLocation()
            );
        }

        List<MdxBone> bones = buildBones(raw);
        Map<Integer, Integer> boneIndexByObjectId = new HashMap<>();
        List<RawNode> sortedNodes = sortedNodes(raw.nodes);
        for (int index = 0; index < sortedNodes.size(); index++) {
            boneIndexByObjectId.put(sortedNodes.get(index).objectId, index);
        }

        List<ResourceLocation> resolvedTextures = resolveTextures(raw, resources);
        List<List<ResolvedLayer>> materials = resolveMaterials(raw, resolvedTextures);
        ArrayList<MdxMesh> meshes = new ArrayList<>();
        for (int geosetId = 0; geosetId < raw.geosets.size(); geosetId++) {
            RawGeoset geoset = raw.geosets.get(geosetId);
            RawGeosetAnimation geosetAnimation = raw.geosetAnimations.get(geosetId);
            List<ResolvedLayer> layers = geoset.materialId >= 0
                    && geoset.materialId < materials.size()
                    ? materials.get(geoset.materialId)
                    : List.of(missingLayer());
            if (layers.isEmpty()) {
                layers = List.of(missingLayer());
            }
            for (ResolvedLayer layer : layers) {
                meshes.add(buildMesh(
                        geoset,
                        layer,
                        geosetAnimation,
                        raw.globalSequences,
                        boneIndexByObjectId
                ));
            }
        }

        ArrayList<MdxSequence> sequences = new ArrayList<>();
        for (RawSequence sequence : raw.sequences) {
            sequences.add(new MdxSequence(
                    sequence.name,
                    sequence.startMillis,
                    sequence.endMillis,
                    !sequence.nonLooping
            ));
        }

        ResourceLocation primaryTexture = meshes.isEmpty()
                ? resolvedTextures.stream()
                .filter(texture -> !MISSING_TEXTURE.equals(texture))
                .findFirst()
                .orElse(MISSING_TEXTURE)
                : meshes.get(0).material().texture();
        List<MdxParticleEmitter2> particleEmitters = buildParticleEmitters(
                raw,
                boneIndexByObjectId,
                resolvedTextures
        );
        MdxRenderModel model = new MdxRenderModel(
                resources.mdxLocation(),
                primaryTexture,
                meshes,
                bones,
                sequences,
                Map.of(),
                particleEmitters
        );
        long animatedTextureLayerCount = materials.stream()
                .flatMap(List::stream)
                .filter(layer -> layer.material.hasAnimatedTexture())
                .count();
        long animatedGeosetAlphaCount = raw.geosetAnimations.values().stream()
                .filter(animation -> animation.alphaTrack != null)
                .count();
        LOGGER.info(
                "Parsed WC3 model {}: {} geosets, {} render meshes, {} bones, {} sequences, "
                        + "{} KMTF texture tracks, {} TXAN UV layers, {} KGAO alpha tracks, "
                        + "{} PRE2 emitters",
                resources.mdxLocation(),
                raw.geosets.size(),
                meshes.size(),
                bones.size(),
                sequences.size(),
                animatedTextureLayerCount,
                materials.stream().flatMap(List::stream)
                        .filter(layer -> layer.material.hasAnimatedTextureCoordinates()).count(),
                animatedGeosetAlphaCount,
                particleEmitters.size()
        );
        return model;
    }

    private static RawModel parseRaw(byte[] bytes) throws IOException {
        LittleEndianDataReader reader = new LittleEndianDataReader(bytes);
        reader.expectTag("MDLX");
        RawModel raw = new RawModel();
        while (reader.remaining() > 0) {
            if (reader.remaining() < 8) {
                throw new IOException("Incomplete MDX top-level chunk header");
            }
            String tag = reader.readTag();
            int chunkSize = reader.readInt();
            if (chunkSize < 0 || chunkSize > reader.remaining()) {
                throw new IOException(
                        "Invalid MDX " + tag + " chunk size: "
                                + Integer.toUnsignedLong(chunkSize)
                );
            }
            LittleEndianDataReader chunk = reader.readSlice(chunkSize);
            switch (tag) {
                case "VERS" -> parseVersion(chunk, raw);
                case "SEQS" -> parseSequences(chunk, raw);
                case "GLBS" -> parseGlobalSequences(chunk, raw);
                case "MTLS" -> parseMaterials(chunk, raw);
                case "TEXS" -> parseTextures(chunk, raw);
                case "TXAN" -> parseTextureAnimations(chunk, raw);
                case "GEOS" -> parseGeosets(chunk, raw);
                case "GEOA" -> parseGeosetAnimations(chunk, raw);
                case "BONE" -> parseNodes(chunk, raw, true);
                case "HELP" -> parseNodes(chunk, raw, false);
                case "PIVT" -> parsePivots(chunk, raw);
                case "PRE2" -> parseParticleEmitters2(chunk, raw);
                default -> {
                    // Rendering does not need cameras, emitters, sounds, or collision chunks.
                    chunk.skip(chunk.remaining());
                }
            }
            if (chunk.remaining() != 0) {
                throw new IOException(
                        "MDX " + tag + " parser left " + chunk.remaining() + " unread byte(s)"
                );
            }
        }
        return raw;
    }

    private static void parseVersion(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        if (chunk.remaining() != 4) {
            throw new IOException("VERS chunk must contain one integer");
        }
        raw.version = chunk.readInt();
    }

    private static void parseSequences(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        if (chunk.remaining() % SEQUENCE_SIZE != 0) {
            throw new IOException("SEQS chunk size is not a multiple of " + SEQUENCE_SIZE);
        }
        while (chunk.remaining() > 0) {
            String name = chunk.readFixedString(80);
            int start = chunk.readInt();
            int end = chunk.readInt();
            chunk.skip(4); // moveSpeed
            boolean nonLooping = chunk.readInt() != 0;
            chunk.skip(4); // rarity
            chunk.skip(4); // syncPoint
            chunk.skip(4 + 12 + 12); // bounds and extents
            if (name.isBlank()) {
                throw new IOException("MDX sequence has a blank name");
            }
            raw.sequences.add(new RawSequence(name, start, end, nonLooping));
        }
    }

    private static void parseGlobalSequences(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        if (chunk.remaining() % 4 != 0) {
            throw new IOException("GLBS chunk size is not a multiple of four");
        }
        raw.globalSequences = new int[chunk.remaining() / 4];
        for (int index = 0; index < raw.globalSequences.length; index++) {
            raw.globalSequences[index] = chunk.readInt();
        }
    }

    private static void parseTextures(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        if (chunk.remaining() % TEXTURE_SIZE != 0) {
            throw new IOException("TEXS chunk size is not a multiple of " + TEXTURE_SIZE);
        }
        while (chunk.remaining() > 0) {
            int replaceableId = chunk.readInt();
            String fileName = chunk.readFixedString(256);
            chunk.skip(8);
            raw.textures.add(new RawTexture(replaceableId, fileName));
        }
    }

    private static void parseMaterials(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        while (chunk.remaining() > 0) {
            int inclusiveSize = readInclusiveSize(chunk, 12, "material");
            LittleEndianDataReader material = chunk.readSlice(inclusiveSize - 4);
            material.readInt(); // priorityPlane
            material.readInt(); // flags
            ArrayList<RawLayer> layers = new ArrayList<>();
            if (material.remaining() > 0) {
                material.expectTag("LAYS");
                int layerCount = checkedSmallCount(material.readInt(), "material layer");
                for (int index = 0; index < layerCount; index++) {
                    int layerSize = readInclusiveSize(material, 28, "material layer");
                    LittleEndianDataReader layer = material.readSlice(layerSize - 4);
                    int filterMode = layer.readInt();
                    int shadingFlags = layer.readInt();
                    int textureId = layer.readInt();
                    int textureAnimationId = layer.readInt();
                    int coordinateSet = layer.readInt();
                    float alpha = layer.readFloat();
                    RawFloatTrack alphaTrack = null;
                    RawIntTrack textureTrack = null;
                    while (layer.remaining() > 0) {
                        String trackTag = layer.peekTag();
                        switch (trackTag) {
                            case "KMTA" -> alphaTrack = parseFloatTrack(layer, "KMTA");
                            case "KMTF" -> textureTrack = parseIntTrack(layer, "KMTF");
                            default -> throw new IOException(
                                    "Unsupported material layer track " + trackTag
                            );
                        }
                    }
                    layers.add(new RawLayer(
                            filterMode,
                            shadingFlags,
                            textureId,
                            textureAnimationId,
                            coordinateSet,
                            alpha,
                            alphaTrack,
                            textureTrack
                    ));
                }
            }
            material.skip(material.remaining());
            raw.materials.add(List.copyOf(layers));
        }
    }

    private static void parseTextureAnimations(
            LittleEndianDataReader chunk,
            RawModel raw
    ) throws IOException {
        while (chunk.remaining() > 0) {
            int inclusiveSize = readInclusiveSize(chunk, 4, "texture animation");
            LittleEndianDataReader animation = chunk.readSlice(inclusiveSize - 4);
            RawVectorTrack translation = null;
            RawQuaternionTrack rotation = null;
            RawVectorTrack scaling = null;
            while (animation.remaining() > 0) {
                String trackTag = animation.peekTag();
                switch (trackTag) {
                    case "KTAT" -> translation = parseVectorTrack(animation, "KTAT");
                    case "KTAR" -> rotation = parseQuaternionTrack(animation, "KTAR");
                    case "KTAS" -> scaling = parseVectorTrack(animation, "KTAS");
                    default -> throw new IOException(
                            "Unsupported texture animation track " + trackTag
                    );
                }
            }
            raw.textureAnimations.add(new RawTextureAnimation(
                    translation,
                    rotation,
                    scaling
            ));
        }
    }

    private static void parseGeosets(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        while (chunk.remaining() > 0) {
            int inclusiveSize = readInclusiveSize(chunk, 120, "geoset");
            LittleEndianDataReader geoset = chunk.readSlice(inclusiveSize - 4);

            geoset.expectTag("VRTX");
            int vertexCount = checkedCount(geoset, 12, "vertex");
            float[] positions = readFloats(geoset, vertexCount * 3);

            geoset.expectTag("NRMS");
            int normalCount = checkedCount(geoset, 12, "normal");
            float[] normals = readFloats(geoset, normalCount * 3);

            geoset.expectTag("PTYP");
            int primitiveGroupCount = checkedCount(geoset, 4, "primitive group");
            for (int index = 0; index < primitiveGroupCount; index++) {
                int primitiveType = geoset.readInt();
                if (primitiveType != 4) {
                    throw new IOException("Only MDX triangle-list geosets are supported");
                }
            }

            geoset.expectTag("PCNT");
            int primitiveCountGroupCount = checkedCount(geoset, 4, "primitive count group");
            int declaredIndexCount = 0;
            for (int index = 0; index < primitiveCountGroupCount; index++) {
                declaredIndexCount = Math.addExact(declaredIndexCount, geoset.readInt());
            }

            geoset.expectTag("PVTX");
            int indexCount = checkedCount(geoset, 2, "triangle index");
            int[] indices = new int[indexCount];
            for (int index = 0; index < indexCount; index++) {
                indices[index] = geoset.readUnsignedShort();
            }
            if (declaredIndexCount != indexCount || indexCount % 3 != 0) {
                throw new IOException("MDX geoset has inconsistent triangle index counts");
            }

            geoset.expectTag("GNDX");
            int vertexGroupCount = checkedCount(geoset, 1, "vertex group");
            int[] vertexGroups = new int[vertexGroupCount];
            for (int index = 0; index < vertexGroupCount; index++) {
                vertexGroups[index] = geoset.readUnsignedByte();
            }

            geoset.expectTag("MTGC");
            int matrixGroupCount = checkedCount(geoset, 4, "matrix group");
            int[] matrixGroups = readInts(geoset, matrixGroupCount);

            geoset.expectTag("MATS");
            int matrixIndexCount = checkedCount(geoset, 4, "matrix index");
            int[] matrixIndices = readInts(geoset, matrixIndexCount);

            int materialId = geoset.readInt();
            geoset.skip(8); // selection group/type
            geoset.skip(4 + 12 + 12); // bounds and extents
            int extentCount = checkedCount(geoset, 28, "sequence extent");
            geoset.skip(extentCount * 28);

            geoset.expectTag("UVAS");
            int uvSetCount = checkedSmallCount(geoset.readInt(), "UV set");
            float[][] uvSets = new float[uvSetCount][];
            for (int uvSet = 0; uvSet < uvSetCount; uvSet++) {
                geoset.expectTag("UVBS");
                int uvCount = checkedCount(geoset, 8, "UV coordinate");
                uvSets[uvSet] = readFloats(geoset, uvCount * 2);
            }
            if (geoset.remaining() != 0) {
                throw new IOException("Unsupported data at the end of a version-800 geoset");
            }
            if (normalCount != vertexCount || vertexGroupCount != vertexCount) {
                throw new IOException("MDX geoset vertex, normal, and group counts do not match");
            }
            for (float[] uvSet : uvSets) {
                if (uvSet.length != vertexCount * 2) {
                    throw new IOException("MDX geoset UV count does not match its vertex count");
                }
            }
            raw.geosets.add(new RawGeoset(
                    positions,
                    normals,
                    indices,
                    vertexGroups,
                    matrixGroups,
                    matrixIndices,
                    materialId,
                    uvSets
            ));
        }
    }

    private static void parseGeosetAnimations(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        while (chunk.remaining() > 0) {
            int inclusiveSize = readInclusiveSize(chunk, 28, "geoset animation");
            LittleEndianDataReader animation = chunk.readSlice(inclusiveSize - 4);
            float alpha = animation.readFloat();
            int flags = animation.readInt();
            float[] color = readFloats(animation, 3);
            int geosetId = animation.readInt();
            RawFloatTrack alphaTrack = null;
            RawVectorTrack colorTrack = null;
            while (animation.remaining() > 0) {
                String trackTag = animation.peekTag();
                switch (trackTag) {
                    case "KGAO" -> alphaTrack = parseFloatTrack(animation, "KGAO");
                    case "KGAC" -> colorTrack = parseVectorTrack(animation, "KGAC");
                    default -> throw new IOException(
                            "Unsupported geoset animation track " + trackTag
                    );
                }
            }
            if (geosetId < 0 || raw.geosetAnimations.putIfAbsent(
                    geosetId,
                    new RawGeosetAnimation(alpha, flags, color, alphaTrack, colorTrack)
            ) != null) {
                throw new IOException("Duplicate or negative geoset animation ID: " + geosetId);
            }
        }
    }

    private static void parseNodes(
            LittleEndianDataReader chunk,
            RawModel raw,
            boolean bonePayload
    ) throws IOException {
        while (chunk.remaining() > 0) {
            RawNode node = parseNode(chunk);
            if (bonePayload) {
                chunk.skip(8); // geoset and geoset-animation IDs
            }
            addNode(raw, node);
        }
    }

    private static RawNode parseNode(LittleEndianDataReader reader) throws IOException {
        int inclusiveSize = readInclusiveSize(reader, 96, "node");
        LittleEndianDataReader nodeData = reader.readSlice(inclusiveSize - 4);
        RawNode node = new RawNode();
        node.name = nodeData.readFixedString(80);
        node.objectId = nodeData.readInt();
        node.parentId = nodeData.readInt();
        node.flags = nodeData.readInt();
        while (nodeData.remaining() > 0) {
            String trackTag = nodeData.peekTag();
            switch (trackTag) {
                case "KGTR" -> node.translation = parseVectorTrack(nodeData, "KGTR");
                case "KGRT" -> node.rotation = parseQuaternionTrack(nodeData, "KGRT");
                case "KGSC" -> node.scaling = parseVectorTrack(nodeData, "KGSC");
                default -> throw new IOException(
                        "Unsupported node track " + trackTag + " on " + node.name
                );
            }
        }
        return node;
    }

    private static void addNode(RawModel raw, RawNode node) throws IOException {
        if (node.objectId < 0 || raw.nodes.putIfAbsent(node.objectId, node) != null) {
            throw new IOException("Duplicate or negative MDX node object ID: " + node.objectId);
        }
    }

    private static void parseParticleEmitters2(
            LittleEndianDataReader chunk,
            RawModel raw
    ) throws IOException {
        while (chunk.remaining() > 0) {
            int inclusiveSize = readInclusiveSize(chunk, 271, "ParticleEmitter2");
            LittleEndianDataReader emitterData = chunk.readSlice(inclusiveSize - 4);
            RawParticleEmitter2 emitter = new RawParticleEmitter2();
            emitter.node = parseNode(emitterData);
            addNode(raw, emitter.node);

            emitter.speed = emitterData.readFloat();
            emitter.variation = emitterData.readFloat();
            emitter.latitude = emitterData.readFloat();
            emitter.gravity = emitterData.readFloat();
            emitter.lifespan = emitterData.readFloat();
            emitter.emissionRate = emitterData.readFloat();
            emitter.length = emitterData.readFloat();
            emitter.width = emitterData.readFloat();
            emitter.filterMode = emitterData.readInt();
            emitter.rows = emitterData.readInt();
            emitter.columns = emitterData.readInt();
            emitter.headOrTail = emitterData.readInt();
            emitter.tailLength = emitterData.readFloat();
            emitter.time = emitterData.readFloat();
            emitter.segmentColor = readFloats(emitterData, 9);
            emitter.segmentAlpha = new int[] {
                    emitterData.readUnsignedByte(),
                    emitterData.readUnsignedByte(),
                    emitterData.readUnsignedByte()
            };
            emitter.segmentScaling = readFloats(emitterData, 3);
            emitter.intervals = new int[12];
            for (int index = 0; index < emitter.intervals.length; index++) {
                emitter.intervals[index] = emitterData.readInt();
            }
            emitter.textureId = emitterData.readInt();
            emitter.squirt = emitterData.readInt();
            emitter.priorityPlane = emitterData.readInt();
            emitter.replaceableId = emitterData.readInt();

            while (emitterData.remaining() > 0) {
                String trackTag = emitterData.peekTag();
                switch (trackTag) {
                    case "KP2V" -> emitter.visibilityTrack = parseFloatTrack(emitterData, "KP2V");
                    case "KP2R" -> emitter.variationTrack = parseFloatTrack(emitterData, "KP2R");
                    case "KP2G" -> emitter.gravityTrack = parseFloatTrack(emitterData, "KP2G");
                    case "KP2E" -> emitter.emissionRateTrack = parseFloatTrack(emitterData, "KP2E");
                    case "KP2W" -> emitter.widthTrack = parseFloatTrack(emitterData, "KP2W");
                    case "KP2N" -> emitter.lengthTrack = parseFloatTrack(emitterData, "KP2N");
                    case "KP2S" -> emitter.speedTrack = parseFloatTrack(emitterData, "KP2S");
                    case "KP2L" -> emitter.latitudeTrack = parseFloatTrack(emitterData, "KP2L");
                    default -> throw new IOException(
                            "Unsupported ParticleEmitter2 track " + trackTag
                                    + " on " + emitter.node.name
                    );
                }
            }
            raw.particleEmitters.add(emitter);
        }
    }

    private static RawVectorTrack parseVectorTrack(
            LittleEndianDataReader reader,
            String expectedTag
    ) throws IOException {
        reader.expectTag(expectedTag);
        int count = checkedSmallCount(reader.readInt(), expectedTag + " keyframe");
        int interpolation = reader.readInt();
        int globalSequenceId = reader.readInt();
        validateInterpolation(interpolation, expectedTag);
        ArrayList<RawVectorKeyframe> keyframes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int time = reader.readInt();
            float[] value = readFloats(reader, 3);
            float[] inTan = interpolation > 1 ? readFloats(reader, 3) : value;
            float[] outTan = interpolation > 1 ? readFloats(reader, 3) : value;
            keyframes.add(new RawVectorKeyframe(time, value, inTan, outTan));
        }
        return new RawVectorTrack(interpolation, globalSequenceId, keyframes);
    }

    private static RawQuaternionTrack parseQuaternionTrack(
            LittleEndianDataReader reader,
            String expectedTag
    )
            throws IOException {
        reader.expectTag(expectedTag);
        int count = checkedSmallCount(reader.readInt(), expectedTag + " keyframe");
        int interpolation = reader.readInt();
        int globalSequenceId = reader.readInt();
        validateInterpolation(interpolation, expectedTag);
        ArrayList<RawQuaternionKeyframe> keyframes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int time = reader.readInt();
            float[] value = readFloats(reader, 4);
            float[] inTan = interpolation > 1 ? readFloats(reader, 4) : value;
            float[] outTan = interpolation > 1 ? readFloats(reader, 4) : value;
            keyframes.add(new RawQuaternionKeyframe(time, value, inTan, outTan));
        }
        return new RawQuaternionTrack(interpolation, globalSequenceId, keyframes);
    }

    private static RawFloatTrack parseFloatTrack(
            LittleEndianDataReader reader,
            String expectedTag
    ) throws IOException {
        reader.expectTag(expectedTag);
        int count = checkedSmallCount(reader.readInt(), expectedTag + " keyframe");
        int interpolation = reader.readInt();
        int globalSequenceId = reader.readInt();
        validateInterpolation(interpolation, expectedTag);
        ArrayList<RawFloatKeyframe> keyframes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int time = reader.readInt();
            float value = reader.readFloat();
            float inTan = interpolation > 1 ? reader.readFloat() : value;
            float outTan = interpolation > 1 ? reader.readFloat() : value;
            if (!Float.isFinite(value) || !Float.isFinite(inTan) || !Float.isFinite(outTan)) {
                throw new IOException(expectedTag + " contains a non-finite value");
            }
            keyframes.add(new RawFloatKeyframe(time, value, inTan, outTan));
        }
        return new RawFloatTrack(interpolation, globalSequenceId, keyframes);
    }

    private static RawIntTrack parseIntTrack(
            LittleEndianDataReader reader,
            String expectedTag
    ) throws IOException {
        reader.expectTag(expectedTag);
        int count = checkedSmallCount(reader.readInt(), expectedTag + " keyframe");
        int interpolation = reader.readInt();
        int globalSequenceId = reader.readInt();
        validateInterpolation(interpolation, expectedTag);
        ArrayList<RawIntKeyframe> keyframes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int time = reader.readInt();
            int value = reader.readInt();
            if (interpolation > 1) {
                reader.skip(8); // Integer tangents are stored but texture IDs remain discrete.
            }
            keyframes.add(new RawIntKeyframe(time, value));
        }
        return new RawIntTrack(interpolation, globalSequenceId, keyframes);
    }

    private static void parsePivots(LittleEndianDataReader chunk, RawModel raw)
            throws IOException {
        if (chunk.remaining() % 12 != 0) {
            throw new IOException("PIVT chunk size is not a multiple of 12");
        }
        raw.pivots = readFloats(chunk, chunk.remaining() / 4);
    }

    private static List<MdxBone> buildBones(RawModel raw) throws IOException {
        List<RawNode> nodes = sortedNodes(raw.nodes);
        Map<Integer, Integer> indexByObjectId = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            indexByObjectId.put(nodes.get(index).objectId, index);
        }

        ArrayList<MdxBone> bones = new ArrayList<>(nodes.size());
        for (RawNode node : nodes) {
            int pivotOffset = node.objectId * 3;
            Vector3f pivot = pivotOffset >= 0 && pivotOffset + 2 < raw.pivots.length
                    ? toMinecraftPosition(
                            raw.pivots[pivotOffset],
                            raw.pivots[pivotOffset + 1],
                            raw.pivots[pivotOffset + 2]
                    )
                    : new Vector3f();
            int parentIndex = indexByObjectId.getOrDefault(node.parentId, -1);
            bones.add(new MdxBone(
                    node.name,
                    parentIndex,
                    pivot,
                    buildVectorTrack(node.translation, raw.globalSequences, false),
                    buildQuaternionTrack(node.rotation, raw.globalSequences),
                    buildVectorTrack(node.scaling, raw.globalSequences, true)
            ));
        }
        return List.copyOf(bones);
    }

    private static List<RawNode> sortedNodes(Map<Integer, RawNode> nodes) {
        return nodes.values().stream()
                .sorted(Comparator.comparingInt(node -> node.objectId))
                .toList();
    }

    private static MdxVectorTrack buildVectorTrack(
            RawVectorTrack track,
            int[] globalSequences,
            boolean scaling
    ) throws IOException {
        if (track == null) {
            return MdxVectorTrack.EMPTY;
        }
        ArrayList<MdxVectorKeyframe> keyframes = new ArrayList<>(track.keyframes.size());
        for (RawVectorKeyframe keyframe : track.keyframes) {
            keyframes.add(new MdxVectorKeyframe(
                    keyframe.time,
                    convertVector(keyframe.value, scaling),
                    convertVector(keyframe.inTan, scaling),
                    convertVector(keyframe.outTan, scaling)
            ));
        }
        return new MdxVectorTrack(
                interpolation(track.interpolation),
                keyframes,
                globalSequenceDuration(track.globalSequenceId, globalSequences)
        );
    }

    private static MdxQuaternionTrack buildQuaternionTrack(
            RawQuaternionTrack track,
            int[] globalSequences
    ) throws IOException {
        if (track == null) {
            return MdxQuaternionTrack.EMPTY;
        }
        ArrayList<MdxQuaternionKeyframe> keyframes = new ArrayList<>(track.keyframes.size());
        for (RawQuaternionKeyframe keyframe : track.keyframes) {
            keyframes.add(new MdxQuaternionKeyframe(
                    keyframe.time,
                    toMinecraftQuaternion(keyframe.value),
                    toMinecraftQuaternion(keyframe.inTan),
                    toMinecraftQuaternion(keyframe.outTan)
            ));
        }
        return new MdxQuaternionTrack(
                interpolation(track.interpolation),
                keyframes,
                globalSequenceDuration(track.globalSequenceId, globalSequences)
        );
    }

    private static List<ResourceLocation> resolveTextures(
            RawModel raw,
            Wc3ModelResourceBundle resources
    ) throws IOException {
        HashMap<String, List<ResourceLocation>> texturesByFileName = new HashMap<>();
        for (ResourceLocation location : resources.blpLocations()) {
            texturesByFileName.computeIfAbsent(
                    fileName(location.getPath()),
                    ignored -> new ArrayList<>()
            ).add(location);
        }

        ArrayList<ResourceLocation> resolvedTextures = new ArrayList<>(raw.textures.size());
        for (RawTexture rawTexture : raw.textures) {
            ResourceLocation resolved = MISSING_TEXTURE;
            if (rawTexture.replaceableId == 0 && !rawTexture.fileName.isBlank()) {
                List<ResourceLocation> matches = texturesByFileName.getOrDefault(
                        fileName(rawTexture.fileName),
                        List.of()
                );
                if (matches.size() > 1) {
                    throw new IOException(
                            "Texture " + rawTexture.fileName + " in "
                                    + resources.mdxLocation() + " resolves to "
                                    + matches.size() + " provided BLP files"
                    );
                }
                if (matches.size() == 1) {
                    resolved = matches.get(0);
                } else {
                    LOGGER.warn(
                            "WC3 model {} references texture '{}' but no matching BLP was "
                                    + "provided in its wc3model folder",
                            resources.mdxLocation(),
                            rawTexture.fileName
                    );
                }
            }
            resolvedTextures.add(resolved);
        }
        return List.copyOf(resolvedTextures);
    }

    private static List<List<ResolvedLayer>> resolveMaterials(
            RawModel raw,
            List<ResourceLocation> resolvedTextures
    ) throws IOException {
        ArrayList<List<ResolvedLayer>> resolvedMaterials = new ArrayList<>();
        for (List<RawLayer> material : raw.materials) {
            ArrayList<ResolvedLayer> layers = new ArrayList<>();
            for (RawLayer layer : material) {
                ResourceLocation texture = resolveTextureId(layer.textureId, resolvedTextures);
                layers.add(new ResolvedLayer(
                        new MdxMaterial(
                                texture,
                                blendMode(layer.filterMode),
                                // Warcraft's Unshaded flag (0x1) disables its directional
                                // shading, but should not bypass Minecraft's sky/block lightmap.
                                // Reserve forced full-bright for the newer explicit Unlit flag.
                                (layer.shadingFlags & 0x100) != 0,
                                (layer.shadingFlags & 0x10) != 0,
                                new Vector4f(1.0F, 1.0F, 1.0F, clamp01(layer.alpha)),
                                buildTextureTrack(
                                        layer.textureTrack,
                                        raw.globalSequences,
                                        resolvedTextures
                                ),
                                buildFloatTrack(layer.alphaTrack, raw.globalSequences),
                                resolveTextureAnimation(layer.textureAnimationId, raw)
                        ),
                        layer.coordinateSet
                ));
            }
            resolvedMaterials.add(List.copyOf(layers));
        }
        return List.copyOf(resolvedMaterials);
    }

    private static MdxTextureAnimation resolveTextureAnimation(
            int textureAnimationId,
            RawModel raw
    ) throws IOException {
        if (textureAnimationId == -1) {
            return MdxTextureAnimation.EMPTY;
        }
        if (textureAnimationId < 0 || textureAnimationId >= raw.textureAnimations.size()) {
            throw new IOException("Invalid MDX texture animation ID: " + textureAnimationId);
        }
        RawTextureAnimation animation = raw.textureAnimations.get(textureAnimationId);
        return new MdxTextureAnimation(
                buildTextureVectorTrack(animation.translation, raw.globalSequences),
                buildTextureQuaternionTrack(animation.rotation, raw.globalSequences),
                buildTextureVectorTrack(animation.scaling, raw.globalSequences)
        );
    }

    private static MdxVectorTrack buildTextureVectorTrack(
            RawVectorTrack track,
            int[] globalSequences
    ) throws IOException {
        if (track == null) {
            return MdxVectorTrack.EMPTY;
        }
        ArrayList<MdxVectorKeyframe> keyframes = new ArrayList<>(track.keyframes.size());
        for (RawVectorKeyframe keyframe : track.keyframes) {
            keyframes.add(new MdxVectorKeyframe(
                    keyframe.time,
                    new Vector3f(keyframe.value[0], keyframe.value[1], keyframe.value[2]),
                    new Vector3f(keyframe.inTan[0], keyframe.inTan[1], keyframe.inTan[2]),
                    new Vector3f(keyframe.outTan[0], keyframe.outTan[1], keyframe.outTan[2])
            ));
        }
        return new MdxVectorTrack(
                interpolation(track.interpolation),
                keyframes,
                globalSequenceDuration(track.globalSequenceId, globalSequences)
        );
    }

    private static MdxQuaternionTrack buildTextureQuaternionTrack(
            RawQuaternionTrack track,
            int[] globalSequences
    ) throws IOException {
        if (track == null) {
            return MdxQuaternionTrack.EMPTY;
        }
        ArrayList<MdxQuaternionKeyframe> keyframes = new ArrayList<>(track.keyframes.size());
        for (RawQuaternionKeyframe keyframe : track.keyframes) {
            keyframes.add(new MdxQuaternionKeyframe(
                    keyframe.time,
                    rawQuaternion(keyframe.value),
                    rawQuaternion(keyframe.inTan),
                    rawQuaternion(keyframe.outTan)
            ));
        }
        return new MdxQuaternionTrack(
                interpolation(track.interpolation),
                keyframes,
                globalSequenceDuration(track.globalSequenceId, globalSequences)
        );
    }

    private static Quaternionf rawQuaternion(float[] values) {
        return new Quaternionf(values[0], values[1], values[2], values[3]);
    }

    private static ResourceLocation resolveTextureId(
            int textureId,
            List<ResourceLocation> resolvedTextures
    ) {
        return textureId >= 0 && textureId < resolvedTextures.size()
                ? resolvedTextures.get(textureId)
                : MISSING_TEXTURE;
    }

    private static List<MdxParticleEmitter2> buildParticleEmitters(
            RawModel raw,
            Map<Integer, Integer> boneIndexByObjectId,
            List<ResourceLocation> resolvedTextures
    ) throws IOException {
        ArrayList<MdxParticleEmitter2> emitters = new ArrayList<>(
                raw.particleEmitters.size()
        );
        for (RawParticleEmitter2 rawEmitter : raw.particleEmitters) {
            Integer boneIndex = boneIndexByObjectId.get(rawEmitter.node.objectId);
            if (boneIndex == null) {
                throw new IOException(
                        "ParticleEmitter2 references missing node " + rawEmitter.node.objectId
                );
            }
            ArrayList<Vector3f> colors = new ArrayList<>(3);
            for (int index = 0; index < 3; index++) {
                int offset = index * 3;
                // PRE2 stores BGR; the MDL/render representation is RGB.
                colors.add(new Vector3f(
                        rawEmitter.segmentColor[offset + 2],
                        rawEmitter.segmentColor[offset + 1],
                        rawEmitter.segmentColor[offset]
                ));
            }
            ArrayList<MdxParticleUvInterval> intervals = new ArrayList<>(4);
            for (int index = 0; index < 4; index++) {
                int offset = index * 3;
                intervals.add(new MdxParticleUvInterval(
                        rawEmitter.intervals[offset],
                        rawEmitter.intervals[offset + 1],
                        rawEmitter.intervals[offset + 2]
                ));
            }
            emitters.add(new MdxParticleEmitter2(
                    rawEmitter.node.name,
                    boneIndex,
                    rawEmitter.node.flags,
                    rawEmitter.speed,
                    rawEmitter.variation,
                    rawEmitter.latitude,
                    rawEmitter.gravity,
                    rawEmitter.lifespan,
                    rawEmitter.emissionRate,
                    rawEmitter.length,
                    rawEmitter.width,
                    rawEmitter.filterMode,
                    rawEmitter.rows,
                    rawEmitter.columns,
                    rawEmitter.headOrTail,
                    rawEmitter.tailLength,
                    rawEmitter.time,
                    colors,
                    rawEmitter.segmentAlpha,
                    rawEmitter.segmentScaling,
                    intervals,
                    resolveTextureId(rawEmitter.textureId, resolvedTextures),
                    rawEmitter.squirt != 0,
                    rawEmitter.priorityPlane,
                    rawEmitter.replaceableId,
                    buildFloatTrack(rawEmitter.visibilityTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.variationTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.gravityTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.emissionRateTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.widthTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.lengthTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.speedTrack, raw.globalSequences),
                    buildFloatTrack(rawEmitter.latitudeTrack, raw.globalSequences)
            ));
        }
        return List.copyOf(emitters);
    }

    private static MdxTextureTrack buildTextureTrack(
            RawIntTrack track,
            int[] globalSequences,
            List<ResourceLocation> resolvedTextures
    ) throws IOException {
        if (track == null) {
            return MdxTextureTrack.EMPTY;
        }
        ArrayList<MdxTextureKeyframe> keyframes = new ArrayList<>(track.keyframes.size());
        for (RawIntKeyframe keyframe : track.keyframes) {
            keyframes.add(new MdxTextureKeyframe(
                    keyframe.time,
                    resolveTextureId(keyframe.value, resolvedTextures)
            ));
        }
        return new MdxTextureTrack(
                keyframes,
                globalSequenceDuration(track.globalSequenceId, globalSequences)
        );
    }

    private static MdxFloatTrack buildFloatTrack(
            RawFloatTrack track,
            int[] globalSequences
    ) throws IOException {
        if (track == null) {
            return MdxFloatTrack.EMPTY;
        }
        ArrayList<MdxFloatKeyframe> keyframes = new ArrayList<>(track.keyframes.size());
        for (RawFloatKeyframe keyframe : track.keyframes) {
            keyframes.add(new MdxFloatKeyframe(
                    keyframe.time,
                    keyframe.value,
                    keyframe.inTan,
                    keyframe.outTan
            ));
        }
        return new MdxFloatTrack(
                interpolation(track.interpolation),
                keyframes,
                globalSequenceDuration(track.globalSequenceId, globalSequences)
        );
    }

    private static MdxMesh buildMesh(
            RawGeoset geoset,
            ResolvedLayer layer,
            RawGeosetAnimation geosetAnimation,
            int[] globalSequences,
            Map<Integer, Integer> boneIndexByObjectId
    ) throws IOException {
        int vertexCount = geoset.positions.length / 3;
        float[] uvSet = layer.coordinateSet >= 0 && layer.coordinateSet < geoset.uvSets.length
                ? geoset.uvSets[layer.coordinateSet]
                : geoset.uvSets.length == 0 ? new float[vertexCount * 2] : geoset.uvSets[0];

        int[] groupOffsets = new int[geoset.matrixGroups.length];
        int matrixOffset = 0;
        for (int group = 0; group < geoset.matrixGroups.length; group++) {
            int groupSize = geoset.matrixGroups[group];
            if (groupSize < 0 || matrixOffset + groupSize > geoset.matrixIndices.length) {
                throw new IOException("MDX geoset contains an invalid matrix group");
            }
            groupOffsets[group] = matrixOffset;
            matrixOffset += groupSize;
        }
        if (matrixOffset != geoset.matrixIndices.length) {
            throw new IOException("MDX matrix groups do not consume all matrix indices");
        }

        ArrayList<MdxVertex> vertices = new ArrayList<>(vertexCount);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int group = geoset.vertexGroups[vertex];
            if (group < 0 || group >= geoset.matrixGroups.length) {
                throw new IOException("MDX vertex references missing matrix group " + group);
            }
            int groupSize = geoset.matrixGroups[group];
            if (groupSize > MdxVertex.MAX_INFLUENCES) {
                throw new IOException(
                        "MDX vertex has " + groupSize + " bone influences; maximum is "
                                + MdxVertex.MAX_INFLUENCES
                );
            }
            int[] temporaryIndices = new int[groupSize];
            int influenceCount = 0;
            for (int influence = 0; influence < groupSize; influence++) {
                int objectId = geoset.matrixIndices[groupOffsets[group] + influence];
                Integer boneIndex = boneIndexByObjectId.get(objectId);
                if (boneIndex != null) {
                    temporaryIndices[influenceCount++] = boneIndex;
                }
            }
            int[] boneIndices = Arrays.copyOf(temporaryIndices, influenceCount);
            float[] weights = new float[influenceCount];
            if (influenceCount > 0) {
                Arrays.fill(weights, 1.0F / influenceCount);
            }
            int positionOffset = vertex * 3;
            int uvOffset = vertex * 2;
            vertices.add(new MdxVertex(
                    toMinecraftPosition(
                            geoset.positions[positionOffset],
                            geoset.positions[positionOffset + 1],
                            geoset.positions[positionOffset + 2]
                    ),
                    toMinecraftPosition(
                            geoset.normals[positionOffset],
                            geoset.normals[positionOffset + 1],
                            geoset.normals[positionOffset + 2]
                    ).normalize(),
                    uvSet[uvOffset],
                    uvSet[uvOffset + 1],
                    boneIndices,
                    weights
            ));
        }
        float geosetAlpha = geosetAnimation == null ? 1.0F : geosetAnimation.alpha;
        MdxFloatTrack geosetAlphaTrack = geosetAnimation == null
                ? MdxFloatTrack.EMPTY
                : buildFloatTrack(geosetAnimation.alphaTrack, globalSequences);
        return new MdxMesh(
                vertices,
                geoset.indices,
                layer.material,
                geosetAlpha,
                geosetAlphaTrack
        );
    }

    private static ResolvedLayer missingLayer() {
        return new ResolvedLayer(
                new MdxMaterial(
                        MISSING_TEXTURE,
                        MdxBlendMode.OPAQUE,
                        false,
                        new Vector4f(1.0F)
                ),
                0
        );
    }

    private static MdxBlendMode blendMode(int filterMode) throws IOException {
        return switch (filterMode) {
            case 0 -> MdxBlendMode.OPAQUE;
            case 1 -> MdxBlendMode.ALPHA_TEST;
            case 2, 5, 6 -> MdxBlendMode.TRANSLUCENT;
            case 3, 4 -> MdxBlendMode.ADDITIVE;
            default -> throw new IOException("Unsupported MDX material filter mode: " + filterMode);
        };
    }

    private static float globalSequenceDuration(int id, int[] durations) throws IOException {
        if (id == -1) {
            return 0.0F;
        }
        if (id < 0 || id >= durations.length || durations[id] < 0) {
            throw new IOException("Invalid MDX global sequence ID: " + id);
        }
        return durations[id];
    }

    private static MdxInterpolation interpolation(int value) throws IOException {
        validateInterpolation(value, "animation");
        return MdxInterpolation.values()[value];
    }

    private static void validateInterpolation(int value, String label) throws IOException {
        if (value < 0 || value >= MdxInterpolation.values().length) {
            throw new IOException("Invalid " + label + " interpolation type: " + value);
        }
    }

    private static Quaternionf toMinecraftQuaternion(float[] values) {
        Quaternionf source = new Quaternionf(values[0], values[1], values[2], values[3]);
        return new Quaternionf(WC3_TO_MINECRAFT)
                .mul(source)
                .mul(new Quaternionf(WC3_TO_MINECRAFT).conjugate());
    }

    private static Vector3f convertVector(float[] values, boolean scaling) {
        return scaling
                ? new Vector3f(values[0], values[2], values[1])
                : toMinecraftPosition(values[0], values[1], values[2]);
    }

    private static Vector3f toMinecraftPosition(float x, float y, float z) {
        return new Vector3f(x, z, -y);
    }

    private static String fileName(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 1.0F;
    }

    private static int readInclusiveSize(
            LittleEndianDataReader reader,
            int minimum,
            String label
    ) throws IOException {
        int size = reader.readInt();
        if (size < minimum || size - 4 > reader.remaining()) {
            throw new IOException("Invalid MDX " + label + " inclusive size: " + size);
        }
        return size;
    }

    private static int checkedCount(
            LittleEndianDataReader reader,
            int elementSize,
            String label
    ) throws IOException {
        int count = reader.readInt();
        if (count < 0 || count > MAX_COLLECTION_SIZE
                || (long) count * elementSize > reader.remaining()) {
            throw new IOException("Invalid MDX " + label + " count: " + count);
        }
        return count;
    }

    private static int checkedSmallCount(int count, String label) throws IOException {
        if (count < 0 || count > 65_536) {
            throw new IOException("Invalid MDX " + label + " count: " + count);
        }
        return count;
    }

    private static float[] readFloats(LittleEndianDataReader reader, int count)
            throws IOException {
        float[] values = new float[count];
        for (int index = 0; index < count; index++) {
            values[index] = reader.readFloat();
            if (!Float.isFinite(values[index])) {
                throw new IOException("MDX contains a non-finite floating-point value");
            }
        }
        return values;
    }

    private static int[] readInts(LittleEndianDataReader reader, int count)
            throws IOException {
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = reader.readInt();
        }
        return values;
    }

    private static final class RawModel {
        private int version = LEGACY_VERSION;
        private int[] globalSequences = new int[0];
        private float[] pivots = new float[0];
        private final List<RawSequence> sequences = new ArrayList<>();
        private final List<RawTexture> textures = new ArrayList<>();
        private final List<RawTextureAnimation> textureAnimations = new ArrayList<>();
        private final List<RawParticleEmitter2> particleEmitters = new ArrayList<>();
        private final List<List<RawLayer>> materials = new ArrayList<>();
        private final List<RawGeoset> geosets = new ArrayList<>();
        private final Map<Integer, RawGeosetAnimation> geosetAnimations = new HashMap<>();
        private final Map<Integer, RawNode> nodes = new LinkedHashMap<>();
    }

    private record RawSequence(String name, int startMillis, int endMillis, boolean nonLooping) {
    }

    private record RawTexture(int replaceableId, String fileName) {
    }

    private record RawTextureAnimation(
            RawVectorTrack translation,
            RawQuaternionTrack rotation,
            RawVectorTrack scaling
    ) {
    }

    private record RawLayer(
            int filterMode,
            int shadingFlags,
            int textureId,
            int textureAnimationId,
            int coordinateSet,
            float alpha,
            RawFloatTrack alphaTrack,
            RawIntTrack textureTrack
    ) {
    }

    private record RawGeoset(
            float[] positions,
            float[] normals,
            int[] indices,
            int[] vertexGroups,
            int[] matrixGroups,
            int[] matrixIndices,
            int materialId,
            float[][] uvSets
    ) {
    }

    private record RawGeosetAnimation(
            float alpha,
            int flags,
            float[] color,
            RawFloatTrack alphaTrack,
            RawVectorTrack colorTrack
    ) {
    }

    private static final class RawNode {
        private String name;
        private int objectId;
        private int parentId;
        private int flags;
        private RawVectorTrack translation;
        private RawQuaternionTrack rotation;
        private RawVectorTrack scaling;
    }

    private static final class RawParticleEmitter2 {
        private RawNode node;
        private float speed;
        private float variation;
        private float latitude;
        private float gravity;
        private float lifespan;
        private float emissionRate;
        private float length;
        private float width;
        private int filterMode;
        private int rows;
        private int columns;
        private int headOrTail;
        private float tailLength;
        private float time;
        private float[] segmentColor;
        private int[] segmentAlpha;
        private float[] segmentScaling;
        private int[] intervals;
        private int textureId;
        private int squirt;
        private int priorityPlane;
        private int replaceableId;
        private RawFloatTrack visibilityTrack;
        private RawFloatTrack variationTrack;
        private RawFloatTrack gravityTrack;
        private RawFloatTrack emissionRateTrack;
        private RawFloatTrack widthTrack;
        private RawFloatTrack lengthTrack;
        private RawFloatTrack speedTrack;
        private RawFloatTrack latitudeTrack;
    }

    private record RawVectorTrack(
            int interpolation,
            int globalSequenceId,
            List<RawVectorKeyframe> keyframes
    ) {
    }

    private record RawVectorKeyframe(
            int time,
            float[] value,
            float[] inTan,
            float[] outTan
    ) {
    }

    private record RawQuaternionTrack(
            int interpolation,
            int globalSequenceId,
            List<RawQuaternionKeyframe> keyframes
    ) {
    }

    private record RawQuaternionKeyframe(
            int time,
            float[] value,
            float[] inTan,
            float[] outTan
    ) {
    }

    private record RawFloatTrack(
            int interpolation,
            int globalSequenceId,
            List<RawFloatKeyframe> keyframes
    ) {
    }

    private record RawFloatKeyframe(
            int time,
            float value,
            float inTan,
            float outTan
    ) {
    }

    private record RawIntTrack(
            int interpolation,
            int globalSequenceId,
            List<RawIntKeyframe> keyframes
    ) {
    }

    private record RawIntKeyframe(int time, int value) {
    }

    private record ResolvedLayer(MdxMaterial material, int coordinateSet) {
    }
}
