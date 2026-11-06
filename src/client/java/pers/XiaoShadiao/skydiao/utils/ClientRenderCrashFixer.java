package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import pers.XiaoShadiao.skydiao.mixin.client.MixinFeatureRenderDispatcherAccessor;
import pers.XiaoShadiao.skydiao.mixin.client.MixinGameRendererAccessor;
import pers.XiaoShadiao.skydiao.mixin.client.MixinGuiItemAtlasAccessor;
import pers.XiaoShadiao.skydiao.mixin.client.MixinGuiRendererAccessor;
import pers.XiaoShadiao.skydiao.mixin.client.MixinLevelRendererAccessor;

public class ClientRenderCrashFixer {

    /**
     * RenderSystem 的 model view 栈是全局复用的（Matrix4fStack），帧末必须回到空栈。
     * LevelRenderer.render() 的 push 在 :199-201，pop 在 :295 —— 只要 frame.execute()（:282-292，
     * 自定义渲染/事件监听都在里面）抛异常，pop 就被跳过，栈里多一层崩溃时的 cameraState.viewRotationMatrix。
     * 之后每帧 push 再 mul 当前相机旋转，实体/手持物等走 RenderSystem.getModelViewMatrix() 的绘制
     * 就会叠加一层过期旋转（位置和角度都不对，而地形用的是 terrainMatrix，所以不受影响）。
     * 这里把栈弹回空栈即可恢复。
     */
    private static void fixModelViewStack() {
        try {
            RenderSystem.getModelViewStack().clear();
        } catch (Throwable ignored) {
        }
    }

    public static LevelRenderContext wrc;
    public static GuiGraphicsExtractor gg;

    public static void fix() {
        fixModelViewStack();
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
        fixFeatureFrame();
        fixGuiRenderer();
        fixGuiItemAtlas();
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
     * GuiItemAtlas 里的 poseStack 是跨帧复用的单个实例：drawToSlot() 在 :106 pushPose、
     * :127 popPose，中间（item.submit / prepareFrame / renderAllFeatures）抛异常就让栈里多一层
     * translate(槽位中心) + scale(16,-16,16)。下一帧再叠加同样的变换，所有 GUI 物品（箱子/背包里的
     * 物品图标）就会被二次变换，表现为位置/大小/朝向错误。
     * 同时 drawToSlot 在 :111 开的 scissor 也因 :126 被跳过而残留，这里一并关闭。
     */
    private static void fixGuiItemAtlas() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;

            GuiRenderer guiRenderer = ((MixinGameRendererAccessor) (Object) mc.gameRenderer).skydiao$getGuiRenderer();
            if (guiRenderer == null) return;

            GuiItemAtlas atlas = ((MixinGuiRendererAccessor) (Object) guiRenderer).skydiao$getItemAtlas();
            if (atlas == null) return;

            PoseStack poseStack = ((MixinGuiItemAtlasAccessor) (Object) atlas).skydiao$getPoseStack();
            while (!poseStack.isEmpty()) {
                poseStack.popPose();
            }
            RenderSystem.disableScissorForRenderTypeDraws();
        } catch (Throwable ignored) {
        }
    }

    /**
     * LevelRenderer.render() 里 prepareFrame() 之后、featureFrame.close()（LevelRenderer.java:296）之前抛异常时，
     * PreparedFrame 的 context 不会被置空。由于它是复用的单例对象，下一帧 begin() 会直接抛
     * "PreparedFrame already in use"（FeatureRenderDispatcher.java:186），之后每帧必现。
     * 这里补一次 close()：它内部先把 context 置空，再调各 renderer 的 finishExecute 和
     * stagedVertexBuffer.endDraw()，正好是中断帧缺失的收尾。
     */
    private static void fixFeatureFrame() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.levelRenderer == null) return;

            FeatureRenderDispatcher dispatcher = ((MixinLevelRendererAccessor) (Object) mc.levelRenderer).skydiao$getFeatureRenderDispatcher();
            if (dispatcher == null) return;

            // close() 在未使用时（context 为 null）会抛 NullPointerException("Frame not in use")，属于正常情况
            ((MixinFeatureRenderDispatcherAccessor) (Object) dispatcher).skydiao$getPreparedFrame().close();
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
                } catch (Throwable e) {
                    ToolList.getInstance().log.catching(e);
                }
                // submit 可能被别的销毁回调弄挂（例如 ScreenshotUtils 关闭了仍处于 mapped 状态的 buffer），
                // 但 VulkanCommandEncoder 是先提交命令再 rotate 销毁队列，blit 已经进队列了，
                // 所以这里必须继续 present，否则表面会一直锁在 acquired 状态
                try {
                    RenderSystem.getDevice().createCommandEncoder().submit();
                } catch (Throwable e) {
                    ToolList.getInstance().log.catching(e);
                }
            }
            surface.present();
        } catch (Throwable e) {
            ToolList.getInstance().log.catching(e);
        }
    }
}
