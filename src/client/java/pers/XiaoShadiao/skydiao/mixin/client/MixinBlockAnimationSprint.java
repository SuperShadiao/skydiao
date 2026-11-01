package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(LivingEntity.class)
public abstract class MixinBlockAnimationSprint {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void skydiao$blockSprintStart(boolean sprinting, CallbackInfo callbackInfo) {
        if (!sprinting) return;
        if (!((Object) this instanceof LocalPlayer player)) return;
        if (!BlockAnimation.active()) return;
        if (!BlockAnimation.blocking(player)) return;
        callbackInfo.cancel();
    }
}