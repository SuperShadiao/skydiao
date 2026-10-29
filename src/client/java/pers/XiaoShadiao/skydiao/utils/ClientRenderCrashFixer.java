package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import pers.XiaoShadiao.skydiao.mixin.client.MixinGameRendererAccessor;
import pers.XiaoShadiao.skydiao.mixin.client.MixinGuiRendererAccessor;

public class ClientRenderCrashFixer {

    public static LevelRenderContext wrc;
    public static GuiGraphicsExtractor gg;

    public static void fix() {
        if (wrc != null) {
            if(wrc.poseStack() != null) {
                while(!wrc.poseStack().isEmpty()) wrc.poseStack().popPose();
            }
        }
        if (gg != null) {
            while (true) {
                try {
                    gg.pose().popMatrix();
                } catch (Throwable e) {
                    break;
                }
            }
        }
        fixGuiRenderer();
        fixRenderBuffers();
        fixWindowSurface();
    }

    /**
     * GuiRenderer.draw() 抛异常时，GuiRenderer 帧尾的收尾逻辑（endDraw/endFrame/draws.clear/renderState.reset）
     * 全部被跳过，会导致 currentVertexBuffer 保持非空、上一帧的 GUI 元素残留在 renderState 中。
     * 之后每一帧都会在 prepare() 的 appendDraw 处抛出 "Cannot append draw after upload"，
     * 于是永远走不到 Minecraft.runTick 末尾的上屏逻辑，画面就冻结在最后一帧。
     * 这里手工补做一次帧尾收尾，让下一帧从干净的渲染状态开始。
     */
    private static void fixGuiRenderer() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;

            GuiRenderer guiRenderer = ((MixinGameRendererAccessor) (Object) mc.gameRenderer).skydiao$getGuiRenderer();
            if (guiRenderer == null) return;

            MixinGuiRendererAccessor accessor = (MixinGuiRendererAccessor) (Object) guiRenderer;
            accessor.skydiao$getVertexBuffer().endFrame();
            accessor.skydiao$getDraws().clear();
            accessor.skydiao$getRenderState().reset();
            accessor.skydiao$setFirstDrawIndexAfterBlur(Integer.MAX_VALUE);
        } catch (Throwable ignored) {
        }
    }

    /**
     * GameRenderer.render() 末尾的 renderBuffers.endFrame()（GameRenderer.java:509）在异常时同样被跳过。
     * 世界/实体/粒子共用的那个 StagedVertexBuffer 在本帧 renderLevel 阶段已经 upload()，
     * 于是下一帧 FeatureRenderDispatcher.renderAllFeatures() 调 upload() 时会抛 "Already uploaded"。
     * 这个异常发生在 GUI 之前，所以只修 GuiRenderer 没用，画面会一直冻结在最后一帧。
     */
    private static void fixRenderBuffers() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;
            mc.gameRenderer.renderBuffers().endFrame();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 最致命的一处：Minecraft.renderFrame 整个函数体包在 if (!windowSurface.isAcquired()) 里（Minecraft.java:1293）。
     * GpuSurface 的 hasImageAcquired 只在 present() 里才会被复位（FrontendGpuSurface.java:114-125），
     * 所以只要异常发生在 acquireNextTexture()（1321）和 present()（1418）之间，这一帧就永远不会 present，
     * 之后每一帧 renderFrame 都直接整段跳过：不渲染、不出画、也不再抛异常 → FPS 0、画面冻结但游戏继续 tick。
     * present() 又要求先 blit，所以这里补一次 blit + present 把表面解锁，让后续帧恢复正常。
     */
    private static void fixWindowSurface() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;

            GpuSurface surface = mc.windowSurface();
            if (surface == null || !surface.isAcquired()) return;

            GpuTextureView colorTexture = mc.gameRenderer.mainRenderTarget().getColorTextureView();
            if (colorTexture != null) {
                try {
                    surface.blitFromTexture(RenderSystem.getDevice().createCommandEncoder(), colorTexture);
                } catch (Throwable ignored) {
                }
                RenderSystem.getDevice().createCommandEncoder().submit();
            }
            surface.present();
        } catch (Throwable ignored) {
        }
    }
}
