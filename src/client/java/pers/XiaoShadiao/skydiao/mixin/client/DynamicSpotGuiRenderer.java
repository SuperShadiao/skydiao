package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotSurface;

@Mixin(GuiRenderer.class)
public abstract class DynamicSpotGuiRenderer {
    @Shadow @Final private GuiRenderState renderState;

    @Inject(method = "render", at = @At("HEAD"))
    private void skydiao$renderGlassBeforeGui(GpuBufferSlice fog, CallbackInfo ci) {
        DynamicSpotSurface.renderPending(renderState);
    }
}
