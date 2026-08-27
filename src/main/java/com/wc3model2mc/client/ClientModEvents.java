package com.wc3model2mc.client;

import com.wc3model2mc.WC3Model2MC;
import com.wc3model2mc.api.client.WC3ModelResourceAPI;
import com.wc3model2mc.client.render.AnimatedMdxEntityRenderer;
import com.wc3model2mc.client.render.AnimatedMdxProjectileRenderer;
import com.wc3model2mc.client.render.BillboardMdxProjectileRenderer;
import com.wc3model2mc.registry.ModEntityTypes;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = WC3Model2MC.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.ANIMATED_MDX_ENTITY.get(),
                AnimatedMdxEntityRenderer::new
        );
        event.registerEntityRenderer(
                ModEntityTypes.BILLBOARD_MDX_PROJECTILE.get(),
                BillboardMdxProjectileRenderer::new
        );
        event.registerEntityRenderer(
                ModEntityTypes.ANIMATED_MDX_PROJECTILE.get(),
                AnimatedMdxProjectileRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        WC3ModelResourceAPI.registerModelNamespace(WC3Model2MC.MODID);
        event.registerReloadListener(
                (ResourceManagerReloadListener) WC3ModelResourceAPI::reloadRegisteredNamespaces
        );
    }
}
