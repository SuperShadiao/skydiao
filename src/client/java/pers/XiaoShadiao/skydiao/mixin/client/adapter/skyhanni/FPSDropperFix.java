package pers.XiaoShadiao.skydiao.mixin.client.adapter.skyhanni;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(targets = "at.hannibal2.skyhanni.utils.SkyHanniLogger")
public class FPSDropperFix {

    @Final
    @WrapMethod(method = "log(Ljava/lang/String;)V")
    public final void log(String msg, Operation<Void> original) {
        ToolList.addThreadedTask(() -> original.call(msg));
    }

}
