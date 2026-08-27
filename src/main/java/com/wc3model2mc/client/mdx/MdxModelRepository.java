package com.wc3model2mc.client.mdx;

import com.wc3model2mc.mdx.model.MdxRenderModel;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side handoff point for an MDX/BLP resource loader.
 *
 * <p>A parser or resource-reload listener calls {@link #put(MdxRenderModel)}
 * after resolving BLPs to registered Minecraft texture locations.</p>
 */
public final class MdxModelRepository {
    private static final Map<ResourceLocation, MdxRenderModel> MODELS = new ConcurrentHashMap<>();

    private MdxModelRepository() {
    }

    public static void put(MdxRenderModel model) {
        Objects.requireNonNull(model, "model");
        MODELS.put(model.sourceId(), model);
    }

    public static void replaceAll(Map<ResourceLocation, MdxRenderModel> models) {
        MODELS.clear();
        MODELS.putAll(Objects.requireNonNull(models, "models"));
    }

    public static synchronized void replaceNamespaceModels(
            String namespace,
            String pathPrefix,
            Collection<MdxRenderModel> models
    ) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(pathPrefix, "pathPrefix");
        Objects.requireNonNull(models, "models");
        List<MdxRenderModel> checkedModels = List.copyOf(models);
        for (MdxRenderModel model : checkedModels) {
            if (!model.sourceId().getNamespace().equals(namespace)
                    || !model.sourceId().getPath().startsWith(pathPrefix)) {
                throw new IllegalArgumentException(
                        "Model " + model.sourceId() + " is outside "
                                + namespace + ':' + pathPrefix
                );
            }
        }
        MODELS.keySet().removeIf(location -> location.getNamespace().equals(namespace)
                && location.getPath().startsWith(pathPrefix));
        for (MdxRenderModel model : checkedModels) {
            MODELS.put(model.sourceId(), model);
        }
    }

    @Nullable
    public static MdxRenderModel get(ResourceLocation sourceId) {
        return MODELS.get(Objects.requireNonNull(sourceId, "sourceId"));
    }

    public static void clear() {
        MODELS.clear();
    }
}
