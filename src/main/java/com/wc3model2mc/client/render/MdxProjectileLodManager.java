package com.wc3model2mc.client.render;

import com.wc3model2mc.client.MdxClientConfig;
import com.wc3model2mc.client.mdx.MdxRenderDetail;
import com.wc3model2mc.entity.StationaryMdxProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Ranks nearby MDX projectiles once per client tick for stable adaptive LOD. */
final class MdxProjectileLodManager {
    private static final Map<Integer, MdxRenderDetail> DETAIL_BY_ENTITY_ID =
            new HashMap<>();
    private static ClientLevel cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;

    private MdxProjectileLodManager() {
    }

    static MdxRenderDetail detailFor(StationaryMdxProjectile projectile) {
        if (!MdxClientConfig.ADAPTIVE_PROJECTILE_LOD.get()) {
            return MdxRenderDetail.FULL;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return MdxRenderDetail.FULL;
        }
        if (cachedLevel != level
                || cachedGameTime != level.getGameTime()
                || !DETAIL_BY_ENTITY_ID.containsKey(projectile.getId())) {
            rebuild(level, minecraft.gameRenderer.getMainCamera().getPosition());
        }
        return DETAIL_BY_ENTITY_ID.getOrDefault(
                projectile.getId(),
                MdxRenderDetail.FULL
        );
    }

    private static void rebuild(ClientLevel level, Vec3 cameraPosition) {
        cachedLevel = level;
        cachedGameTime = level.getGameTime();
        DETAIL_BY_ENTITY_ID.clear();

        double radius = MdxClientConfig.LOD_RADIUS.get();
        double radiusSquared = radius * radius;
        ArrayList<StationaryMdxProjectile> nearby = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof StationaryMdxProjectile projectile) {
                if (projectile.distanceToSqr(cameraPosition) <= radiusSquared) {
                    nearby.add(projectile);
                } else {
                    DETAIL_BY_ENTITY_ID.put(projectile.getId(), MdxRenderDetail.MINIMAL);
                }
            }
        }

        int activationCount = MdxClientConfig.LOD_ACTIVATION_COUNT.get();
        if (nearby.size() < activationCount) {
            for (StationaryMdxProjectile projectile : nearby) {
                DETAIL_BY_ENTITY_ID.put(projectile.getId(), MdxRenderDetail.FULL);
            }
            return;
        }

        nearby.sort(Comparator.comparingDouble(
                projectile -> projectile.distanceToSqr(cameraPosition)
        ));
        int fullDetailCount = MdxClientConfig.FULL_DETAIL_COUNT.get();
        int mediumDetailEnd = fullDetailCount
                + MdxClientConfig.MEDIUM_DETAIL_COUNT.get();
        for (int index = 0; index < nearby.size(); index++) {
            MdxRenderDetail detail = index < fullDetailCount
                    ? MdxRenderDetail.FULL
                    : index < mediumDetailEnd
                    ? MdxRenderDetail.MEDIUM
                    : MdxRenderDetail.MINIMAL;
            DETAIL_BY_ENTITY_ID.put(nearby.get(index).getId(), detail);
        }
    }
}
