package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.FreecamAndFreelook;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    public void extractVisibleEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, CallbackInfo ci) {
        FreecamAndFreelook.CameraEntity cameraEntity = AbstractListener.freecamAndFreelook.getCameraEntity();
        if(cameraEntity != null) {
           if (ToolList.mc.player != null && ToolList.mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                output.entityRenderStates.add(ToolList.mc.getEntityRenderDispatcher().extractEntity(ToolList.mc.player, deltaTracker.getGameTimeDeltaPartialTick(false)));
            }
        }
    }

}
