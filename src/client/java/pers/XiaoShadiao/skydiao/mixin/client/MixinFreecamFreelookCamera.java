package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.FreecamAndFreelook;

@Mixin(Camera.class)
public class MixinFreecamFreelookCamera {

    @Shadow
    private Entity entity;

    @Unique
    private Entity tempStore1;
    @Unique
    private Entity tempStore2;

    @Inject(method = "update", at = @At("HEAD"))
    private void update(DeltaTracker deltaTracker, CallbackInfo ci) {
        tempStore1 = entity;
        FreecamAndFreelook.CameraEntity cameraEntity = AbstractListener.freecamAndFreelook.getCameraEntity();
        if(cameraEntity != null) {
            entity = cameraEntity;
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void updatePost(DeltaTracker deltaTracker, CallbackInfo ci) {
        entity = tempStore1;
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void extractRenderState(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
        tempStore2 = entity;
        FreecamAndFreelook.CameraEntity cameraEntity = AbstractListener.freecamAndFreelook.getCameraEntity();
        if(cameraEntity != null) {
            entity = cameraEntity;
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void extractRenderStatePost(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
        entity = tempStore2;
    }

}
