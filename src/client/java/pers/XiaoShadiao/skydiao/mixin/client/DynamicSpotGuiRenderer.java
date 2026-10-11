package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.renderutils.BlurredRoundedSurface;

@Mixin(GuiRenderer.class)
public abstract class DynamicSpotGuiRenderer {
    @Shadow @Final private GuiRenderState renderState;

    @Inject(method = "render", at = @At("HEAD"))
    private void skydiao$renderGlassBeforeGui(GpuBufferSlice fog, CallbackInfo ci) {
        BlurredRoundedSurface.renderPending(renderState);
    }

    /**
     * 哨兵元素会带着 {@link BlurredRoundedSurface} 的玻璃合成管线进入 {@code GuiRenderer.executeDraw}。
     * 这里在绑定管线后补齐该绘制所需的模糊结果与面板参数，使它真正参与 GUI 的 z-order。
     */
    @Redirect(
            method = "executeDraw",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void skydiao$bindGlassComposite(RenderPass pass, RenderPipeline pipeline) {
        pass.setPipeline(pipeline);
        if (BlurredRoundedSurface.isGlassGuiPipeline(pipeline)) {
            BlurredRoundedSurface.bindGlassComposite(pass, pipeline);
        }
    }
}