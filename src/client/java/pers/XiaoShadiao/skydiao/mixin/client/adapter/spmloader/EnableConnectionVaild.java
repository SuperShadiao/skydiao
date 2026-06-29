package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

@Mixin(targets = "com.micaftic.morpher.network.NetworkHandler")
public class EnableConnectionVaild {

    @Inject(method = "isConnectionValid", at = @At("HEAD"), cancellable = true)
    private static void valid(Connection connection, CallbackInfoReturnable<Boolean> cir) {
        if(StatusManager.get().hasStatus()) {
            cir.setReturnValue(true);
        }
    }

}
