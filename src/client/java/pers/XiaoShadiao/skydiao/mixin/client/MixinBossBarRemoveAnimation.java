package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(LerpingBossEvent.class)
public class MixinBossBarRemoveAnimation {

    @Shadow
    protected long setTime;

    @Inject(at = @At("TAIL"), method = "setProgress")
    public void setProgress(float f, CallbackInfo ci) {
        if(ConfigManager.bossbar.getValue()) setTime = 0;
    }

}
