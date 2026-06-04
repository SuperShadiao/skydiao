package pers.XiaoShadiao.skydiao.mixin.client.adapter.secretroute;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(targets = "xyz.yourboykyle.secretroutes.utils.LogUtils")
public class FPSDropperFix {

    @Unique
    private static final Object lock = new Object();

    @WrapMethod(method = "appendToFile")
    private static void appendToFile(String str, Operation<Void> original) {
        ToolList.addThreadedTask(() -> {
            synchronized (lock) {
                original.call(str);
            }
            return null;
        });
    }

}
