package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import com.micaftic.morpher.cloud.client.CloudPlayerMotionSync;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(CloudPlayerMotionSync.class)
public class MixinCloudConnection {

    @Inject(method = "play", at = @At("HEAD"))
    private static void startPlay(AnimatableEntity<?> entity, String key, CallbackInfo ci) {
        if(entity.entity == ToolList.mc.player) AbstractListener.spmlLoaderAdapter.startPlayAnimation(key);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private static void stopPlay(AnimatableEntity<?> entity, CallbackInfo ci) {
        if(entity.entity == ToolList.mc.player) AbstractListener.spmlLoaderAdapter.stopPlayAnimation();
    }

}
