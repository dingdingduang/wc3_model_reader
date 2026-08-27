package com.wc3model2mc.registry;

import com.wc3model2mc.WC3Model2MC;
import com.wc3model2mc.entity.AnimatedMdxEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WC3Model2MC.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                ModEntityTypes.ANIMATED_MDX_ENTITY.get(),
                AnimatedMdxEntity.createAttributes().build()
        );
    }
}
