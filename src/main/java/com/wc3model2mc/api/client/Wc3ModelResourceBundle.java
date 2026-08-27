package com.wc3model2mc.api.client;

import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The raw resources from one {@code assets/<namespace>/wc3model/<folder>}.
 * A bundle always contains exactly one MDX and zero or more BLP textures.
 */
public final class Wc3ModelResourceBundle {
    private final ResourceLocation folderId;
    private final ResourceLocation mdxLocation;
    private final byte[] mdxBytes;
    private final Map<ResourceLocation, byte[]> blpResources;

    Wc3ModelResourceBundle(
            ResourceLocation folderId,
            ResourceLocation mdxLocation,
            byte[] mdxBytes,
            Map<ResourceLocation, byte[]> blpResources
    ) {
        this.folderId = Objects.requireNonNull(folderId, "folderId");
        this.mdxLocation = Objects.requireNonNull(mdxLocation, "mdxLocation");
        this.mdxBytes = Objects.requireNonNull(mdxBytes, "mdxBytes").clone();
        LinkedHashMap<ResourceLocation, byte[]> copiedTextures = new LinkedHashMap<>();
        Objects.requireNonNull(blpResources, "blpResources").forEach((location, bytes) ->
                copiedTextures.put(
                        Objects.requireNonNull(location, "BLP location"),
                        Objects.requireNonNull(bytes, "BLP bytes").clone()
                )
        );
        this.blpResources = Map.copyOf(copiedTextures);
    }

    public ResourceLocation folderId() {
        return folderId;
    }

    public ResourceLocation mdxLocation() {
        return mdxLocation;
    }

    public InputStream openMdx() {
        return new ByteArrayInputStream(mdxBytes);
    }

    public byte[] copyMdxBytes() {
        return mdxBytes.clone();
    }

    public List<ResourceLocation> blpLocations() {
        return blpResources.keySet().stream().sorted().toList();
    }

    public InputStream openBlp(ResourceLocation location) {
        byte[] bytes = blpResources.get(Objects.requireNonNull(location, "location"));
        if (bytes == null) {
            throw new IllegalArgumentException("BLP is not part of " + folderId + ": " + location);
        }
        return new ByteArrayInputStream(bytes);
    }

    public byte[] copyBlpBytes(ResourceLocation location) {
        byte[] bytes = blpResources.get(Objects.requireNonNull(location, "location"));
        if (bytes == null) {
            throw new IllegalArgumentException("BLP is not part of " + folderId + ": " + location);
        }
        return bytes.clone();
    }
}
