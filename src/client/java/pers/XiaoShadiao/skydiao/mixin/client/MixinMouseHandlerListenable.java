package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

@Mixin(MouseHandler.class)
public class MixinMouseHandlerListenable {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long l, MouseButtonInfo mouseButtonInfo, int i, CallbackInfo ci) {
        if (CustomFabricEvents.MOUSE_BUTTON_EVENT.invoker().onMouseButton(l, mouseButtonInfo, i)) {
            ci.cancel();
        }
    }

}
