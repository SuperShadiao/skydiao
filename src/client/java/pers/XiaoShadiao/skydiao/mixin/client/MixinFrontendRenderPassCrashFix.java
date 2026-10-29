package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(FrontendRenderPass.class)
public class MixinFrontendRenderPassCrashFix {

    @WrapMethod(method = "enableScissor")
    public void enableScissor(int x, int y, int width, int height, Operation<Void> original) {
        try {
            original.call(x, y, width, height);
        } catch (IllegalArgumentException e) {
            ToolList.getInstance().log.warn("enableScissor IllegalArgumentException: {}", e);
        }
    }

}
