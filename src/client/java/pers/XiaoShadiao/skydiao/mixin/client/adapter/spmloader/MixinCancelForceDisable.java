package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

@Mixin(LivingAnimatable.class)
public class MixinCancelForceDisable {

    @WrapMethod(method = "setForceDisabled")
    public void setForceDisabled(boolean forceDisabled, Operation<Void> original) {
        original.call(!StatusManager.get().hasStatus() && forceDisabled);
    }

}
