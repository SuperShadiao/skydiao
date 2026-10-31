package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(LocalPlayer.class)
public abstract class MixinBlockAnimationSprintStop {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void skydiao$stopSprintWhileBlocking(CallbackInfo callbackInfo) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!BlockAnimation.active()) return;
        if (!self.isSprinting()) return;
        if (!BlockAnimation.blocking(self)) return;
        self.setSprinting(false);
    }
}