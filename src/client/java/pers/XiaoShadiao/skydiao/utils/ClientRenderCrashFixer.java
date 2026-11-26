package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import pers.XiaoShadiao.skydiao.mixin.client.MixinFeatureRenderDispatcherAccessor;
import pers.XiaoShadiao.skydiao.mixin.client.MixinFrontendCommandEncoderAccessor;
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
        } catch (Throwable e) {
            ToolList.getInstance().log.catching(e);
        }
    }

    public static LevelRenderContext wrc;
    public static GuiGraphicsExtractor gg;

    public static void fix() {
        fixModelViewStack();
        if (wrc != null) {
            if(wrc.poseStack() != null) {
                try {
                    while(!wrc.poseStack().isEmpty()) wrc.poseStack().popPose();
                } catch (Throwable e) {
                    ToolList.getInstance().log.catching(e);
                }
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
        fixGuiState();
        fixRenderBuffers();
        fixWindowSurface();
    }

    /**
     * GuiRenderer.draw() 抛异常时，GuiRenderer 帧尾的收尾逻辑（endDraw/endFrame/draws.clear/renderState.reset）
     * 全部被跳过，会导致 currentVertexBuffer 保持非空、上一帧的 GUI 元素残留在 renderState 中。
     * 之后每一帧都会在 prepare() 的 appendDraw 处抛出 "Cannot append draw after upload"，
     * 于是永远走不到 Minecraft.runTick 末尾的上屏逻辑，画面就冻结在最后一帧。
     * 这里手工补做一次帧尾收尾，让下一帧从干净的渲染状态开始。
     *
     * GuiItemAtlas 里的 poseStack 同样是跨帧复用的单个实例：drawToSlot() 在 :106 pushPose、
     * :127 popPose，中间抛异常就让栈里多一层 translate(槽位中心) + scale(16,-16,16)，
     * 下一帧再叠加一次，箱子/背包里的物品图标就会位置、大小、朝向全错；
     * 同时 :111 开的 scissor 也因 :126 被跳过而残留。这里一并收尾。
     */
    private static void fixGuiState() {
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

            GuiItemAtlas atlas = accessor.skydiao$getItemAtlas();
            if (atlas == null) return;

            PoseStack poseStack = ((MixinGuiItemAtlasAccessor) (Object) atlas).skydiao$getPoseStack();
            while (!poseStack.isEmpty()) {
                poseStack.popPose();
            }
            RenderSystem.disableScissorForRenderTypeDraws();
        } catch (Throwable e) {
            ToolList.getInstance().log.catching(e);
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
        } catch (Throwable e) {
            if (!(e instanceof NullPointerException) || !"Frame not in use".equals(e.getMessage())) {
                ToolList.getInstance().log.catching(e);
            }
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
        } catch (Throwable e) {
            ToolList.getInstance().log.catching(e);
        }
    }

    /**
     * 最致命的一处：Minecraft.renderFrame 整个函数体包在 if (!windowSurface.isAcquired()) 里（Minecraft.java:1293）。
     * GpuSurface 的 hasImageAcquired 只在 present() 里才会被复位（FrontendGpuSurface.java:114-125），
     * 所以只要异常发生在 acquireNextTexture()（1321）和 present()（1418）之间，这一帧就永远不会 present，
     * 之后每一帧 renderFrame 都直接整段跳过：不渲染、不出画、也不再抛异常 → FPS 0、画面冻结但游戏继续 tick。
     * present() 又要求先 blit，所以这里补一次 blit + present 把表面解锁，让后续帧恢复正常。
     * 除了异常路径外，MixinMinecraft 在 renderFrame 入口发现表面仍处于 acquired 时也会调用本方法。
     */
    public static void fixWindowSurface() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;

            GpuSurface surface = mc.windowSurface();
            if (surface == null || !surface.isAcquired()) return;

            GpuTextureView colorTexture = mc.gameRenderer.mainRenderTarget().getColorTextureView();
            if (colorTexture != null) {
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                fixStuckRenderPass(encoder);
                try {
                    surface.blitFromTexture(encoder, colorTexture);
                } catch (Throwable e) {
                    ToolList.getInstance().log.catching(e);
                }
                // submit 可能被别的销毁回调弄挂（例如 ScreenshotUtils 关闭了仍处于 mapped 状态的 buffer），
                // 但 VulkanCommandEncoder 是先提交命令再 rotate 销毁队列，blit 已经进队列了，
                // 所以这里必须继续 present，否则表面会一直锁在 acquired 状态
                try {
                    encoder.submit();
                } catch (Throwable e) {
                    ToolList.getInstance().log.catching(e);
                }
            }
            surface.present();
        } catch (Throwable e) {
            ToolList.getInstance().log.catching(e);
        }
    }

    /**
     * FrontendCommandEncoder 是全局单例（FrontendGpuDevice.createCommandEncoder 返回同一个实例），
     * 一旦某个 render pass 没走完 close()（例如 close 时 debug group 未平衡抛错，onFinish 被跳过），
     * isInRenderPass 就会永久为 true，之后所有 blitFromTexture 都会抛
     * "Close the existing render pass before presenting with a command encoder"。
     * 这里强制把这个卡住的 pass 结束掉，后续帧才能恢复渲染。
     */
    private static void fixStuckRenderPass(CommandEncoder encoder) {
        if (!(encoder instanceof FrontendCommandEncoder frontendEncoder)) return;

        MixinFrontendCommandEncoderAccessor accessor = (MixinFrontendCommandEncoderAccessor) (Object) frontendEncoder;
        if (!accessor.skydiao$isInRenderPass()) return;

        try {
            accessor.skydiao$submitRenderPass();
        } catch (Throwable e) {
            ToolList.getInstance().log.catching(e);
        }
        accessor.skydiao$setInRenderPass(false);
    }
}
