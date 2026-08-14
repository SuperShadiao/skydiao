package pers.XiaoShadiao.skydiao.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms")
public class MixinRemoveBlindAndDarkness {

    @Inject(method = "getBlindness", at = @At("HEAD"), cancellable = true)
    private static void getBlindness(CallbackInfoReturnable<Float> cir) {
        if(ConfigManager.noblind.getValue()) cir.setReturnValue(0.0F);
    }

    @Inject(method = "getDarknessFactor", at = @At("HEAD"), cancellable = true)
    private static void getDarknessFactor(CallbackInfoReturnable<Float> cir) {
        if(ConfigManager.noblind.getValue()) cir.setReturnValue(0.0F);
    }

}
