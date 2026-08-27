package com.wc3model2mc.client.mdx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Function;

/** Triangle-list render types for native MDX geometry. */
@OnlyIn(Dist.CLIENT)
public final class MdxRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> CUTOUT_CULLED = Util.memoize(texture ->
            create(
                    "wc3model2mc_mdx_cutout_culled",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES,
                    SMALL_BUFFER_SIZE,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    texture,
                                    false,
                                    false
                            ))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true)
            )
    );

    private static final Function<ResourceLocation, RenderType> CUTOUT_TWO_SIDED =
            Util.memoize(texture -> create(
                    "wc3model2mc_mdx_cutout_two_sided",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES,
                    SMALL_BUFFER_SIZE,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    texture,
                                    false,
                                    false
                            ))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true)
            ));

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_CULLED = Util.memoize(texture ->
            create(
                    "wc3model2mc_mdx_translucent_culled",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES,
                    SMALL_BUFFER_SIZE,
                    false,
                    true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    texture,
                                    false,
                                    false
                            ))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true)
            )
    );

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_TWO_SIDED =
            Util.memoize(texture -> create(
                    "wc3model2mc_mdx_translucent_two_sided",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES,
                    SMALL_BUFFER_SIZE,
                    false,
                    true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    texture,
                                    false,
                                    false
                            ))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true)
            ));

    private static final Function<ResourceLocation, RenderType> ADDITIVE_CULLED = Util.memoize(texture ->
            create(
                    "wc3model2mc_mdx_additive_culled",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES,
                    SMALL_BUFFER_SIZE,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    texture,
                                    false,
                                    false
                            ))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)
            )
    );

    private static final Function<ResourceLocation, RenderType> ADDITIVE_TWO_SIDED =
            Util.memoize(texture -> create(
                    "wc3model2mc_mdx_additive_two_sided",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.TRIANGLES,
                    SMALL_BUFFER_SIZE,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    texture,
                                    false,
                                    false
                            ))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false)
            ));

    private MdxRenderTypes() {
        super(
                "wc3model2mc_mdx_unused",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                TRANSIENT_BUFFER_SIZE,
                false,
                false,
                () -> {
                },
                () -> {
                }
        );
    }

    public static RenderType cutout(ResourceLocation texture) {
        return cutout(texture, true);
    }

    public static RenderType cutout(ResourceLocation texture, boolean twoSided) {
        return (twoSided ? CUTOUT_TWO_SIDED : CUTOUT_CULLED).apply(texture);
    }

    public static RenderType translucent(ResourceLocation texture) {
        return translucent(texture, true);
    }

    public static RenderType translucent(ResourceLocation texture, boolean twoSided) {
        return (twoSided ? TRANSLUCENT_TWO_SIDED : TRANSLUCENT_CULLED).apply(texture);
    }

    public static RenderType additive(ResourceLocation texture) {
        return additive(texture, true);
    }

    public static RenderType additive(ResourceLocation texture, boolean twoSided) {
        return (twoSided ? ADDITIVE_TWO_SIDED : ADDITIVE_CULLED).apply(texture);
    }
}
