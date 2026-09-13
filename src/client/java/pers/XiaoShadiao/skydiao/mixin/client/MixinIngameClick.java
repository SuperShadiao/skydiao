package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

@Mixin(Minecraft.class)
public class MixinIngameClick {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    public void attackEvent(CallbackInfoReturnable<Boolean> cir) {
        if(CustomFabricEvents.ON_INGAME_CLICK.invoker().onIngameClick(CustomFabricEvents.ClickType.LEFT)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    public void attackEvent(CallbackInfo ci) {
        if(CustomFabricEvents.ON_INGAME_CLICK.invoker().onIngameClick(CustomFabricEvents.ClickType.RIGHT)) {
            ci.cancel();
        }
    }

}
