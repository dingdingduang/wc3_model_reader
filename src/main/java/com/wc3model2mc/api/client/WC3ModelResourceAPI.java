package com.wc3model2mc.api.client;

import com.mojang.logging.LogUtils;
import com.wc3model2mc.client.mdx.MdxModelRepository;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client API for dependent mods that ship raw Warcraft III models.
 *
 * <p>Resources are discovered below
 * {@code assets/<modId>/wc3model/<any-folder>/}. Every leaf folder containing
 * WC3 assets must contain exactly one {@code .mdx} and zero or more
 * {@code .blp} textures. Minecraft resource paths and filenames must be lowercase.</p>
 */
public final class WC3ModelResourceAPI {
    public static final String RESOURCE_ROOT = "wc3model";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, MdxResourceDecoder> REGISTERED_NAMESPACES =
            new ConcurrentHashMap<>();

    private WC3ModelResourceAPI() {
    }

    /**
     * Reads and validates all raw model folders belonging to a mod namespace.
     * This method does not decode or register the returned models.
     */
    public static List<Wc3ModelResourceBundle> readModels(String modId) throws IOException {
        return readModels(Minecraft.getInstance().getResourceManager(), modId);
    }

    /** Testable/resource-manager overload of {@link #readModels(String)}. */
    public static List<Wc3ModelResourceBundle> readModels(
            ResourceManager resourceManager,
            String modId
    ) throws IOException {
        String namespace = validateNamespace(modId);
        Map<ResourceLocation, Resource> listed = Objects.requireNonNull(
                resourceManager,
                "resourceManager"
        ).listResources(
                RESOURCE_ROOT,
                location -> location.getNamespace().equals(namespace)
                        && isWc3Asset(location.getPath())
        );

        LinkedHashMap<ResourceLocation, byte[]> resourceBytes = new LinkedHashMap<>();
        List<Map.Entry<ResourceLocation, Resource>> sortedResources = listed.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<ResourceLocation, Resource> entry : sortedResources) {
            try (InputStream stream = entry.getValue().open()) {
                resourceBytes.put(entry.getKey(), stream.readAllBytes());
            }
        }
        return assembleBundles(namespace, resourceBytes);
    }

    /** Reads, decodes, validates, and registers every model in a namespace. */
    public static Map<ResourceLocation, MdxRenderModel> loadModels(String modId)
            throws IOException {
        return loadModels(modId, BuiltinMdxBlpDecoder.INSTANCE);
    }

    /** Reads, decodes, validates, and registers using a custom decoder. */
    public static Map<ResourceLocation, MdxRenderModel> loadModels(
            String modId,
            MdxResourceDecoder decoder
    ) throws IOException {
        return loadModels(Minecraft.getInstance().getResourceManager(), modId, decoder);
    }

    /** Testable/resource-manager overload of {@link #loadModels(String, MdxResourceDecoder)}. */
    public static Map<ResourceLocation, MdxRenderModel> loadModels(
            ResourceManager resourceManager,
            String modId,
            MdxResourceDecoder decoder
    ) throws IOException {
        String namespace = validateNamespace(modId);
        Objects.requireNonNull(decoder, "decoder");
        LinkedHashMap<ResourceLocation, MdxRenderModel> decoded = new LinkedHashMap<>();
        for (Wc3ModelResourceBundle bundle : readModels(resourceManager, namespace)) {
            MdxRenderModel model = Objects.requireNonNull(
                    decoder.decode(bundle),
                    "The decoder returned null for " + bundle.mdxLocation()
            );
            if (!model.sourceId().equals(bundle.mdxLocation())) {
                throw new IOException(
                        "Decoder source ID " + model.sourceId()
                                + " does not match " + bundle.mdxLocation()
                );
            }
            decoded.put(bundle.mdxLocation(), model);
        }

        MdxModelRepository.replaceNamespaceModels(namespace, RESOURCE_ROOT + "/", decoded.values());
        return Map.copyOf(decoded);
    }

    /**
     * Registers a namespace for automatic initial/resource-pack reloads.
     * Call this from the dependent mod's client initialization.
     */
    public static void registerModelNamespace(String modId) {
        registerModelNamespace(modId, BuiltinMdxBlpDecoder.INSTANCE);
    }

    /** Registers a namespace for reloads using a custom decoder. */
    public static void registerModelNamespace(String modId, MdxResourceDecoder decoder) {
        REGISTERED_NAMESPACES.put(
                validateNamespace(modId),
                Objects.requireNonNull(decoder, "decoder")
        );
    }

    public static void unregisterModelNamespace(String modId) {
        REGISTERED_NAMESPACES.remove(validateNamespace(modId));
    }

    /** Called by WC3Model2MC's client reload listener. */
    public static void reloadRegisteredNamespaces(ResourceManager resourceManager) {
        REGISTERED_NAMESPACES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    try {
                        Map<ResourceLocation, MdxRenderModel> loaded = loadModels(
                                resourceManager,
                                entry.getKey(),
                                entry.getValue()
                        );
                        LOGGER.info(
                                "Loaded {} WC3 model folder(s) from namespace {}",
                                loaded.size(),
                                entry.getKey()
                        );
                    } catch (IOException | RuntimeException exception) {
                        LOGGER.error(
                                "Failed to load WC3 models from namespace {}",
                                entry.getKey(),
                                exception
                        );
                    }
                });
    }

    static List<Wc3ModelResourceBundle> assembleBundles(
            String namespace,
            Map<ResourceLocation, byte[]> resources
    ) throws IOException {
        String checkedNamespace = validateNamespace(namespace);
        HashMap<String, FolderResources> folders = new HashMap<>();
        for (Map.Entry<ResourceLocation, byte[]> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            if (!location.getNamespace().equals(checkedNamespace)
                    || !isWc3Asset(location.getPath())) {
                continue;
            }
            String folder = parentPath(location.getPath());
            if (folder.equals(RESOURCE_ROOT) || !folder.startsWith(RESOURCE_ROOT + "/")) {
                throw new IOException(
                        "WC3 assets must be inside a folder below " + RESOURCE_ROOT + ": " + location
                );
            }
            FolderResources folderResources = folders.computeIfAbsent(
                    folder,
                    ignored -> new FolderResources()
            );
            if (hasExtension(location.getPath(), ".mdx")) {
                folderResources.mdxResources.put(location, entry.getValue());
            } else {
                folderResources.blpResources.put(location, entry.getValue());
            }
        }

        ArrayList<Wc3ModelResourceBundle> bundles = new ArrayList<>();
        List<Map.Entry<String, FolderResources>> sortedFolders = folders.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<String, FolderResources> entry : sortedFolders) {
            String folder = entry.getKey();
            FolderResources values = entry.getValue();
            if (values.mdxResources.size() != 1) {
                throw new IOException(
                        folder + " must contain exactly one MDX; found "
                                + values.mdxResources.size()
                );
            }
            Map.Entry<ResourceLocation, byte[]> mdx = values.mdxResources.entrySet()
                    .iterator()
                    .next();
            bundles.add(new Wc3ModelResourceBundle(
                    ResourceLocation.fromNamespaceAndPath(checkedNamespace, folder),
                    mdx.getKey(),
                    mdx.getValue(),
                    values.blpResources
            ));
        }
        return List.copyOf(bundles);
    }

    private static String validateNamespace(String modId) {
        String namespace = Objects.requireNonNull(modId, "modId").strip();
        if (ResourceLocation.tryBuild(namespace, "validation") == null) {
            throw new IllegalArgumentException("Invalid Minecraft mod ID/namespace: " + modId);
        }
        return namespace;
    }

    private static boolean isWc3Asset(String path) {
        return hasExtension(path, ".mdx") || hasExtension(path, ".blp");
    }

    private static boolean hasExtension(String path, String extension) {
        return path.toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static String parentPath(String path) throws IOException {
        int separator = path.lastIndexOf('/');
        if (separator <= 0) {
            throw new IOException("WC3 asset has no containing folder: " + path);
        }
        return path.substring(0, separator);
    }

    private static final class FolderResources {
        private final Map<ResourceLocation, byte[]> mdxResources = new LinkedHashMap<>();
        private final Map<ResourceLocation, byte[]> blpResources = new LinkedHashMap<>();
    }
}
