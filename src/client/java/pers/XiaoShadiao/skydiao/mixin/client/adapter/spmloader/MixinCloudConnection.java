package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import com.micaftic.morpher.cloud.client.CloudMotionSources;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

@Mixin(CloudMotionSources.class)
public class MixinCloudConnection {

    @Inject(method = "play", at = @At("HEAD"))
    private static void startPlay(AnimatableEntity<?> entity, String key, CallbackInfo ci) {
        AbstractListener.spmlLoaderAdapter.startPlayAnimation(key);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private static void stopPlay(AnimatableEntity<?> entity, CallbackInfo ci) {
        AbstractListener.spmlLoaderAdapter.stopPlayAnimation();
    }

}
