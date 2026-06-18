package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

@Mixin(MouseHandler.class)
public class MixinMouseHandlerListenable {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        if (CustomFabricEvents.MOUSE_BUTTON_EVENT.invoker().onMouseButton(handle, rawButtonInfo, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void handleAccumulatedMovement(CallbackInfo ci) {
        InputSimulator.updatePlayerRotation();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (CustomFabricEvents.MOUSE_SCROLL_EVENT.invoker().onMouseScroll(handle, xoffset, yoffset)) {
            ci.cancel();
        }
    }
}
