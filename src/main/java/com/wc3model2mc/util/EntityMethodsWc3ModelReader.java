package com.wc3model2mc.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EntityMethodsWc3ModelReader {
    public static int GetEntityInvulnerableTime(Entity a) { return a.invulnerableTime; }
    public static void SetEntityInvulnerableTime(Entity a, int val) { a.invulnerableTime = val; }

    public static float getEntityHorizontalFacingDeg(Entity a) { return a.getYRot(); }
    public static void setEntityHorizontalFacingDeg(Entity a, float deg) { a.setYRot(deg); }
    public static void setEntityHorizontalFacingDeg(Entity a, double deg) { a.setYRot((float) deg); }

    public static void setEntityHeadHorizontalFacingDeg(Entity a, float deg) { a.setYHeadRot(deg); }
    public static float getEntityHeadHorizontalFacingDeg(Entity a) { return a.getYHeadRot(); }
    public static void setEntityBodyHorizontalFacingDeg(Entity a, float deg) { a.setYBodyRot(deg); }
    public static float getEntityBodyHorizontalFacingDeg(Entity a) {
        if (a instanceof LivingEntity b) {
            return b.yBodyRot;
        }
        else {
            return a.getYRot();
        }
    }

    //setXRot(Mth.clamp(p_19895_, -90.0F, 90.0F) % 360.0F)
    public static float getEntityVerticalFacingDeg(Entity a) { return a.getXRot(); }
    public static void setEntityVerticalFacingDeg(Entity a, float deg) { a.setXRot(deg); }
    public static void setEntityVerticalFacingDeg(Entity a, double deg) { a.setXRot((float) deg); }

}
