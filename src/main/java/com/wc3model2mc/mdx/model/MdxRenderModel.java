package com.wc3model2mc.mdx.model;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable, renderer-ready output expected from an MDX/BLP loader adapter. */
public final class MdxRenderModel {
    private final ResourceLocation sourceId;
    private final ResourceLocation primaryTexture;
    private final List<MdxMesh> meshes;
    private final List<MdxBone> bones;
    private final List<MdxParticleEmitter2> particleEmitters;
    private final Map<String, MdxSequence> sequences;
    private final Map<String, String> aliases;
    @Nullable
    private final MdxSequence fallbackSequence;

    public MdxRenderModel(
            ResourceLocation sourceId,
            ResourceLocation primaryTexture,
            List<MdxMesh> meshes,
            List<MdxBone> bones,
            List<MdxSequence> sequences,
            Map<String, String> aliases
    ) {
        this(sourceId, primaryTexture, meshes, bones, sequences, aliases, List.of());
    }

    public MdxRenderModel(
            ResourceLocation sourceId,
            ResourceLocation primaryTexture,
            List<MdxMesh> meshes,
            List<MdxBone> bones,
            List<MdxSequence> sequences,
            Map<String, String> aliases,
            List<MdxParticleEmitter2> particleEmitters
    ) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.primaryTexture = Objects.requireNonNull(primaryTexture, "primaryTexture");
        this.meshes = List.copyOf(Objects.requireNonNull(meshes, "meshes"));
        this.bones = List.copyOf(Objects.requireNonNull(bones, "bones"));
        this.particleEmitters = List.copyOf(Objects.requireNonNull(
                particleEmitters,
                "particleEmitters"
        ));
        validateParents(this.bones);

        LinkedHashMap<String, MdxSequence> sequenceMap = new LinkedHashMap<>();
        for (MdxSequence sequence : Objects.requireNonNull(sequences, "sequences")) {
            String key = normalizeAnimationName(sequence.name());
            if (sequenceMap.putIfAbsent(key, sequence) != null) {
                throw new IllegalArgumentException("Duplicate animation name after normalization: " + sequence.name());
            }
        }
        this.sequences = Map.copyOf(sequenceMap);

        HashMap<String, String> aliasMap = new HashMap<>();
        Objects.requireNonNull(aliases, "aliases").forEach((requested, actual) ->
                aliasMap.put(normalizeAnimationName(requested), normalizeAnimationName(actual))
        );
        this.aliases = Map.copyOf(aliasMap);
        this.fallbackSequence = findPreferredFallback(sequenceMap);
    }

    public ResourceLocation sourceId() {
        return sourceId;
    }

    public ResourceLocation primaryTexture() {
        return primaryTexture;
    }

    public List<MdxMesh> meshes() {
        return meshes;
    }

    public List<MdxBone> bones() {
        return bones;
    }

    public List<MdxParticleEmitter2> particleEmitters() {
        return particleEmitters;
    }

    @Nullable
    public MdxSequence resolveSequence(String requestedName) {
        if (sequences.isEmpty()) {
            return null;
        }
        MdxSequence resolved = findSequence(requestedName);
        return resolved != null ? resolved : fallbackSequence;
    }

    /** Resolves a name or alias without falling back to an unrelated sequence. */
    @Nullable
    public MdxSequence findSequence(String requestedName) {
        if (sequences.isEmpty()) {
            return null;
        }
        String normalized = normalizeAnimationName(requestedName);
        MdxSequence exact = sequences.get(normalized);
        if (exact != null) {
            return exact;
        }
        String aliasTarget = aliases.get(normalized);
        if (aliasTarget != null) {
            MdxSequence alias = sequences.get(aliasTarget);
            if (alias != null) {
                return alias;
            }
        }

        // WC3 commonly appends qualifiers: "Walk Fast", "Stand 2", etc.
        String prefix = normalized + " ";
        MdxSequence shortestQualifiedMatch = null;
        for (Map.Entry<String, MdxSequence> entry : sequences.entrySet()) {
            if (entry.getKey().startsWith(prefix)
                    && (shortestQualifiedMatch == null
                    || entry.getKey().length() < normalizeAnimationName(shortestQualifiedMatch.name()).length())) {
                shortestQualifiedMatch = entry.getValue();
            }
        }
        return shortestQualifiedMatch;
    }

    public boolean isEmpty() {
        return meshes.isEmpty() && particleEmitters.isEmpty();
    }

    public static String normalizeAnimationName(String name) {
        String normalized = Objects.requireNonNull(name, "name")
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
        return normalized.replaceAll("\\s+", " ");
    }

    private static void validateParents(List<MdxBone> bones) {
        for (int index = 0; index < bones.size(); index++) {
            int parent = bones.get(index).parentIndex();
            if (parent >= bones.size()) {
                throw new IllegalArgumentException("Bone " + index + " has an invalid parent " + parent);
            }
            if (parent == index) {
                throw new IllegalArgumentException("Bone " + index + " cannot parent itself");
            }
        }
    }

    @Nullable
    private static MdxSequence findPreferredFallback(LinkedHashMap<String, MdxSequence> sequenceMap) {
        MdxSequence stand = sequenceMap.get("stand");
        if (stand != null) {
            return stand;
        }
        return sequenceMap.values().stream().findFirst().orElse(null);
    }
}
