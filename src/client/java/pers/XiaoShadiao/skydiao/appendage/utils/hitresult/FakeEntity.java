package pers.XiaoShadiao.skydiao.appendage.utils.hitresult;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

public class FakeEntity {

    public float xRot;
    public float yHeadRot;

    public FakeEntity(float xRot, float yHeadRot) {
        this.xRot = xRot;
        this.yHeadRot = yHeadRot;
    }

    public final Vec3 getViewVector(final float a) {
        return this.calculateViewVector(this.getViewXRot(a), this.getViewYRot(a));
    }

    public float getViewXRot(final float a) {
        return this.getXRot();
    }

    public float getViewYRot(final float a) {
        return this.getYHeadRot();
    }

    public final Vec3 calculateViewVector(final float xRot, final float yRot) {
        float realXRot = xRot * (float) (Math.PI / 180.0);
        float realYRot = -yRot * (float) (Math.PI / 180.0);
        float yCos = Mth.cos(realYRot);
        float ySin = Mth.sin(realYRot);
        float xCos = Mth.cos(realXRot);
        float xSin = Mth.sin(realXRot);
        return new Vec3(ySin * xCos, -xSin, yCos * xCos);
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(final float xRot) {
        if (!Float.isFinite(xRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + xRot + ", discarding.");
        } else {
            this.xRot = Math.clamp(xRot % 360.0F, -90.0F, 90.0F);
        }
    }

    public Vec3 getHeadLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYHeadRot());
    }

    public float getYHeadRot() {
        return this.yHeadRot;
    }
}
