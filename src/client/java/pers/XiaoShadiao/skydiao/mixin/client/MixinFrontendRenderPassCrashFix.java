package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(FrontendRenderPass.class)
public class MixinFrontendRenderPassCrashFix {

    @Shadow
    @Final
    private RenderPassBackend backend;

    @Shadow
    private int pushedDebugGroups;

    /**
     * close() 在 :496 发现 debug group 没平衡就 throw，导致 :500 的 onFinish.run()
     * （即 FrontendCommandEncoder.submitRenderPass）永远不执行。而 FrontendCommandEncoder 是全局单例
     * （FrontendGpuDevice.createCommandEncoder 直接返回同一个 encoder），isInRenderPass 于是永久为 true：
     * 之后 blitFromTexture 抛 "Close the existing render pass before presenting"，
     * present 再抛 "Must blit to surface before presenting!"，表面一直锁在 acquired 状态，
     * renderFrame 整段被跳过 → 画面冻结、FPS 停在旧值，而 tick 照常运行。
     * 关闭前把残留的 debug group 补齐弹出，让 close() 能正常走完。
     */
    @Inject(method = "close", at = @At("HEAD"))
    private void skydiao$popLeftoverDebugGroups(CallbackInfo ci) {
        while (this.pushedDebugGroups > 0) {
            this.pushedDebugGroups--;
            try {
                this.backend.popDebugGroup();
            } catch (Throwable ignored) {
            }
        }
    }

    @WrapMethod(method = "enableScissor")
    public void enableScissor(int x, int y, int width, int height, Operation<Void> original) {
        try {
            original.call(x, y, width, height);
        } catch (IllegalArgumentException e) {
            ToolList.getInstance().log.warn("enableScissor IllegalArgumentException: {}", e);
        }
    }

}
