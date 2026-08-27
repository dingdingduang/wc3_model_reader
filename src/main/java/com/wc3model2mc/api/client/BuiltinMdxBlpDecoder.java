package com.wc3model2mc.api.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.wc3model2mc.client.mdx.io.Blp1Decoder;
import com.wc3model2mc.client.mdx.io.LegacyMdxParser;
import com.wc3model2mc.mdx.model.MdxRenderModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Built-in MDLX-800 and BLP1 decoder used by the no-callback public API. */
@OnlyIn(Dist.CLIENT)
public enum BuiltinMdxBlpDecoder implements MdxResourceDecoder {
    INSTANCE;

    @Override
    public MdxRenderModel decode(Wc3ModelResourceBundle resources) throws IOException {
        // Parse first so malformed models do not mutate Minecraft's texture registry.
        MdxRenderModel model = LegacyMdxParser.parse(resources);
        ArrayList<PreparedTexture> preparedTextures = new ArrayList<>();
        try {
            for (ResourceLocation location : resources.blpLocations()) {
                Blp1Decoder.DecodedImage decoded = Blp1Decoder.decode(
                        resources.copyBlpBytes(location)
                );
                preparedTextures.add(new PreparedTexture(
                        location,
                        toNativeImage(decoded)
                ));
            }
        } catch (IOException | RuntimeException exception) {
            closeAll(preparedTextures);
            throw exception;
        }

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        int registeredCount = 0;
        try {
            for (PreparedTexture texture : preparedTextures) {
                textureManager.register(
                        texture.location,
                        new DynamicTexture(texture.image)
                );
                registeredCount++;
            }
        } catch (RuntimeException exception) {
            // Registered DynamicTextures are now owned by TextureManager. Close only the rest.
            closeAll(preparedTextures.subList(registeredCount, preparedTextures.size()));
            throw exception;
        }
        return model;
    }

    private static NativeImage toNativeImage(Blp1Decoder.DecodedImage decoded) {
        NativeImage nativeImage = new NativeImage(decoded.width(), decoded.height(), false);
        int[] argbPixels = decoded.argbPixels();
        for (int y = 0; y < decoded.height(); y++) {
            for (int x = 0; x < decoded.width(); x++) {
                int argb = argbPixels[x + y * decoded.width()];
                int alpha = argb >>> 24;
                int red = argb >>> 16 & 0xFF;
                int green = argb >>> 8 & 0xFF;
                int blue = argb & 0xFF;
                nativeImage.setPixelRGBA(
                        x,
                        y,
                        alpha << 24 | blue << 16 | green << 8 | red
                );
            }
        }
        return nativeImage;
    }

    private static void closeAll(List<PreparedTexture> textures) {
        for (PreparedTexture texture : textures) {
            texture.image.close();
        }
    }

    private record PreparedTexture(ResourceLocation location, NativeImage image) {
    }
}
