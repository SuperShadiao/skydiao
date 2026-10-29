package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 同帧磨砂玻璃效果：绘制带圆角的矩形，并在其周围做 GPU 模糊与辉光/阴影。
 *
 * <p>提取阶段（{@link #draw}）只记录一块圆角面板的几何信息，并往 {@link GuiRenderState} 里放一个
 * 「哨兵」GUI 元素，让它像普通 GUI 元素一样参与 z-order 排序，因此在正确的图层位置被绘制。
 * 真正的绘制发生在 GUI 渲染器执行该哨兵元素时：{@code GuiRenderer.executeDraw} 绑定我们的管线时，
 * 会顺带绑定模糊结果与面板参数（见 {@link #bindGlassComposite}）。世界渲染完成、GUI 绘制之前的
 * {@link #renderPending} 负责把当前画面拷贝并按 {@link #DOWNSAMPLE} 降采样后做两趟可分离高斯模糊。
 *
 * <p>调用方需保证在 {@link GuiRenderState} 对应的一帧内先 {@link #draw}，并在渲染器关闭时调用
 * {@link #close} 释放资源。
 */
public final class BlurredRoundedSurface {
    private static final float BLUR_SIGMA = 2F;
    private static final float GLOW_EXTENT = 8F;
    private static final int DOWNSAMPLE = 4;
    private static final int UNIFORM_FLOATS = 148;
    /** 哨兵元素与预模糊纹理是每面板一份，这里给出每帧可支持的面板上限。 */
    private static final int MAX_PANELS = 32;
    /** 默认玻璃底色：0x98000000（中性黑，不透明度 152/255），保持原有观感。 */
    private static final float DEFAULT_R = 0F;
    private static final float DEFAULT_G = 0F;
    private static final float DEFAULT_B = 0F;
    private static final float DEFAULT_A = 152F / 255F;
    /** 默认辉光颜色（原硬编码冷蓝）与强度，保持原有观感。 */
    private static final float DEFAULT_GLOW_R = 0.43F;
    private static final float DEFAULT_GLOW_G = 0.55F;
    private static final float DEFAULT_GLOW_B = 0.74F;
    private static final float DEFAULT_GLOW_A = 1F;
    private static final float DEFAULT_GLOW_RADIUS = 1.8F;
    /** 每个 GUI 状态每帧待处理的面板；顺序即哨兵管线槽位顺序。 */
    private static final Map<GuiRenderState, List<Panel>> PENDING = new WeakHashMap<>();
    private static final RenderPipeline BLUR = RenderPipelines.registerOptional(pipeline("blurred_rounded_blur", false));
    /**
     * 供哨兵元素使用的管线。每个槽位一条独立管线，这样 {@code executeDraw} 里只凭管线就能定位到
     * 对应面板的参数；同时也避免 {@code GuiRenderer} 把多个面板合批进同一次绘制。
     */
    private static final List<RenderPipeline> GUI_PIPELINES = new ArrayList<>();
    private static final GpuTexture[] TEXTURES = new GpuTexture[3];
    private static final GpuTextureView[] VIEWS = new GpuTextureView[3];
    /** 0=降采样拷贝，1=水平高斯，2=垂直高斯。 */
    private static final GpuBuffer[] BLUR_UNIFORMS = new GpuBuffer[3];
    /** 每个槽位一份玻璃合成参数（面板位置、圆角、颜色、辉光等）。 */
    private static final List<GpuBuffer> GLASS_UNIFORMS = new ArrayList<>();
    private static GpuSampler sampler;
    private static int width;
    private static int height;
    private static int sampleWidth;
    private static int sampleHeight;
    private static boolean failed;
    private static boolean warnedTooManyPanels;

    private BlurredRoundedSurface() { }

    /** Coordinates use the current GUI pose, including the user's HUD scale/offset. */
    public static void draw(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
        draw(graphics, x, y, w, h, radius, DEFAULT_R, DEFAULT_G, DEFAULT_B, DEFAULT_A);
    }

    /**
     * Coordinates use the current GUI pose, including the user's HUD scale/offset.
     * {@code r/g/b/a} 为 0~1 的玻璃底色与不透明度，用于给圆角模糊区域着色（{@code a} 同时作为底色混合强度）。
     */
    public static void draw(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius,
                            float r, float g, float b, float a) {
        drawInternal(graphics, x, y, w, h, true, true, radius, r, g, b, a,
                DEFAULT_GLOW_R, DEFAULT_GLOW_G, DEFAULT_GLOW_B, DEFAULT_GLOW_A, DEFAULT_GLOW_RADIUS, false);
    }

    /**
     * 自定义辉光：{@code useLiquidGlassblur} 位于 {@code radius} 之前，控制当底色 alpha 非 1 时是否预模糊该区域；
     * {@code overwriteBeforeColors} 紧随其后，控制是否覆盖此前已绘制的颜色（false 时与之混合）；
     * {@code glowRadius} 控制辉光距离，{@code glowR/g/b/a}（0~1）为辉光颜色与强度。
     */
    public static void drawWithCustomGlow(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                          boolean useLiquidGlassblur, boolean overwriteBeforeColors, float radius,
                                          float glowRadius, float r, float g, float b, float a,
                                          float glowR, float glowG, float glowB, float glowA) {
        drawInternal(graphics, x, y, w, h, useLiquidGlassblur, overwriteBeforeColors, radius, r, g, b, a, glowR, glowG, glowB, glowA, glowRadius, true);
    }

    /** 自定义辉光：未指定辉光颜色时辉光跟随内部颜色，强度取默认 1。 */
    public static void drawWithCustomGlow(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                          boolean useLiquidGlassblur, boolean overwriteBeforeColors, float radius,
                                          float glowRadius, float r, float g, float b, float a) {
        drawWithCustomGlow(graphics, x, y, w, h, useLiquidGlassblur, overwriteBeforeColors, radius, glowRadius, r, g, b, a, r, g, b, a);
    }

    private static void drawInternal(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                     boolean useLiquidGlassblur, boolean overwriteBeforeColors, float radius,
                                     float r, float g, float b, float a,
                                     float glowR, float glowG, float glowB, float glowA,
                                     float glowRadius, boolean customGlow) {
        if (w <= 0 || h <= 0) return;
        if (failed) {
            fallback(graphics, x, y, w, h, radius, r, g, b, a);
            return;
        }
        var pose = graphics.pose();
        Vector2f start = pose.transformPosition(x, y, new Vector2f());
        Vector2f end = pose.transformPosition(x + w, y + h, new Vector2f());
        float sx = Math.abs(pose.m00());
        float sy = Math.abs(pose.m11());
        Panel panel = new Panel(Math.min(start.x, end.x), Math.min(start.y, end.y),
                Math.abs(end.x - start.x), Math.abs(end.y - start.y),
                Math.max(0, Math.min(radius, Math.min(w, h) / 2)) * Math.min(sx, sy),
                graphics.guiWidth(), graphics.guiHeight(), r, g, b, a,
                useLiquidGlassblur, overwriteBeforeColors, glowR, glowG, glowB, glowA, glowRadius, customGlow);
        if (!Float.isFinite(panel.x + panel.y + panel.width + panel.height + panel.radius)) return;
        synchronized (PENDING) {
            // There is one surface per GUI state. Re-extraction while minimized replaces it.
            List<Panel> panels = PENDING.computeIfAbsent(graphics.guiRenderState, _ -> new ArrayList<>());
            if (panels.size() >= MAX_PANELS) {
                if (!warnedTooManyPanels) {
                    warnedTooManyPanels = true;
                    LoggerFactory.getLogger("skydiao").warn(
                            "BlurredRoundedSurface: more than {} panels in one frame; extra panels are skipped", MAX_PANELS);
                }
                return;
            }
            int slot = panels.size();
            ScreenRectangle clip = graphics.scissorStack.peek();
            int left = (int) Math.floor(panel.x - GLOW_EXTENT);
            int top = (int) Math.floor(panel.y - GLOW_EXTENT);
            int right = (int) Math.ceil(panel.x + panel.width + GLOW_EXTENT);
            int bottom = (int) Math.ceil(panel.y + panel.height + GLOW_EXTENT);
            ScreenRectangle region = new ScreenRectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
            ScreenRectangle scissor = clip == null ? region : clip.intersection(region);
            if (scissor == null) return;
            panels.add(panel);
            // 哨兵元素：几何本身没有意义（着色器只依赖 gl_VertexID 画全屏三角形），
            // 它存在的意义是把玻璃合成排进 GUI 的 z-order，并在 executeDraw 时提供管线标识。
            graphics.guiRenderState.addGuiElement(new GlassPanelElement(
                    guiPipeline(slot), TextureSetup.noTexture(), scissor, region));
        }
    }

    /** Called at GuiRenderer.render HEAD, after the world and before native GUI draws. */
    public static void renderPending(GuiRenderState state) {
        List<Panel> panelList;
        synchronized (PENDING) {
            panelList = PENDING.remove(state);
        }
        if (panelList == null || panelList.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        var target = client.gameRenderer.mainRenderTarget();
        if (target == null || target.getColorTextureView() == null) return;
        // GuiRenderer's projection uses physical size / integer GUI scale, not
        // the rounded-up GUI dimensions returned during extraction.
        var window = client.gameRenderer.gameRenderState().windowRenderState;
        if (window.guiScale <= 0 || window.width <= 0 || window.height <= 0) return;
        float guiWidth = window.width / (float) window.guiScale;
        float guiHeight = window.height / (float) window.guiScale;
        try {
            var device = RenderSystem.getDevice();
            // 管线在使用时惰性编译；编译失败会抛出异常并走下方的回退逻辑
            RenderSystem.getCompiledPipeline(BLUR);
            resize(target.width, target.height);
            var encoder = device.createCommandEncoder();
            // 全屏拷贝（顺便 4x 降采样）后做两趟可分离高斯；面板在合成时按需采样结果。
            boolean anyBlur = false;
            for (Panel panel : panelList) {
                if (panel.useLiquidGlassblur && panel.a < 1F) { anyBlur = true; break; }
            }
            if (anyBlur) {
                float[] copy = new float[UNIFORM_FLOATS];
                copy[16] = 1F;
                pass(encoder, target.getColorTextureView(), VIEWS[0], copy, 0);
                float[] kernel = kernel(BLUR_SIGMA * sampleWidth / guiWidth);
                kernel[0] = 1F / sampleWidth;
                pass(encoder, VIEWS[0], VIEWS[1], kernel, 1);
                kernel[0] = 0;
                kernel[1] = 1F / sampleHeight;
                pass(encoder, VIEWS[1], VIEWS[2], kernel, 2);
            }

            for (int i = 0; i < panelList.size(); i++) {
                Panel original = panelList.get(i);
                Panel panel = new Panel(original.x, original.y, original.width, original.height, original.radius,
                        guiWidth, guiHeight, original.r, original.g, original.b, original.a,
                        original.useLiquidGlassblur, original.overwriteBeforeColors, original.glowR, original.glowG, original.glowB, original.glowA,
                        original.glowRadius, original.customGlow);
                float sx = width / panel.guiWidth;
                float sy = height / panel.guiHeight;
                float[] glass = new float[UNIFORM_FLOATS];
                glass[3] = 1;
                glass[4] = width;
                glass[5] = height;
                glass[8] = panel.x * sx;
                glass[9] = panel.y * sy;
                glass[10] = panel.width * sx;
                glass[11] = panel.height * sy;
                glass[12] = panel.radius * Math.min(sx, sy);
                glass[14] = Math.min(sx, sy);
                // data[5]：0~1 的玻璃底色与不透明度，供着色器给模糊区域着色。
                glass[20] = panel.r;
                glass[21] = panel.g;
                glass[22] = panel.b;
                glass[23] = panel.a;
                // data[6]：辉光颜色 rgb 与强度 a（0~1）。
                glass[24] = panel.glowR;
                glass[25] = panel.glowG;
                glass[26] = panel.glowB;
                glass[27] = panel.glowA;
                // data[7]：x=辉光距离，y=1 使用自定义彩色辉光（0 保持原中性阴影+冷辉光）。
                glass[28] = panel.glowRadius;
                glass[29] = panel.customGlow ? 1F : 0F;
                // data[7].z：1 表示采样预模糊场景（液态玻璃），0 表示直接以半透明底色叠加到现有画面。
                glass[30] = panel.useLiquidGlassblur && panel.a < 1F ? 1F : 0F;
                // data[7].w：1 表示覆盖此前已绘制的颜色（玻璃体不透明），0 表示与之混合。
                glass[31] = panel.overwriteBeforeColors ? 1F : 0F;
                writeUniform(encoder, glassUniform(i), glass);
            }
        } catch (RuntimeException failure) {
            failed = true;
            LoggerFactory.getLogger("skydiao").warn("BlurredRounded backdrop blur unavailable; using translucent glass", failure);
        }
    }

    /** True 表示该管线是 {@link BlurredRoundedSurface} 为哨兵元素创建的玻璃合成管线。 */
    public static boolean isGlassGuiPipeline(RenderPipeline pipeline) {
        return GUI_PIPELINES.contains(pipeline);
    }

    /**
     * 在 {@code GuiRenderer.executeDraw} 绑定哨兵管线后调用：补上玻璃合成所需的采样器与参数。
     * 任一资源不可用时退回 {@link RenderPipelines#GUI}（哨兵顶点全为退化三角形，因此不可见）。
     */
    public static void bindGlassComposite(RenderPass pass, CompiledRenderPipeline compiled) {
        int slot = -1;
        for (int i = 0; i < GUI_PIPELINES.size(); i++) {
            if (RenderSystem.getCompiledPipelineNullable(GUI_PIPELINES.get(i)) == compiled) {
                slot = i;
                break;
            }
        }
        GpuTextureView scene = VIEWS[2] != null ? VIEWS[2] : VIEWS[0];
        if (slot < 0) return;
        if (failed || slot >= GLASS_UNIFORMS.size() || GLASS_UNIFORMS.get(slot) == null
                || scene == null || scene.isClosed() || sampler == null) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.GUI));
            return;
        }
        pass.setUniform("Scene", scene, sampler);
        pass.setUniform("Blur", GLASS_UNIFORMS.get(slot));
    }

    private static RenderPipeline pipeline(String name, boolean translucent) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("skydiao", "core/blurred_rounded"))
                .withBindGroupLayout(glassBindGroupLayout())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(translucent ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
                .build();
    }

    private static BindGroupLayout glassBindGroupLayout() {
        return BindGroupLayout.builder()
                .withUniform("Scene", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("Blur", UniformType.UNIFORM_BUFFER)
                .build();
    }

    private static RenderPipeline guiPipeline(int slot) {
        while (GUI_PIPELINES.size() <= slot) {
            int index = GUI_PIPELINES.size();
            GUI_PIPELINES.add(RenderPipelines.registerOptional(RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/blurred_rounded_gui_" + index))
                    // 不能用 core/screenquad：它靠 gl_VertexID 生成三角形，而 GuiRenderer 会用非零
                    // baseVertex 绘制哨兵网格，导致 gl_VertexID 整体偏移、三角形沿屏幕对角线被切掉。
                    .withVertexShader(Identifier.fromNamespaceAndPath("skydiao", "core/blurred_rounded_gui"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("skydiao", "core/blurred_rounded"))
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(glassBindGroupLayout())
                    // 顶点着色器直接消费 Position（当作 NDC），哨兵写入一个覆盖全屏的三角形。
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withCull(false)
                    .withDepthStencilState(Optional.empty())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build()));
        }
        return GUI_PIPELINES.get(slot);
    }

    private static void resize(int w, int h) {
        var device = RenderSystem.getDevice();
        if (sampler == null) {
            sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        }
        for (int i = 0; i < BLUR_UNIFORMS.length; i++) {
            if (BLUR_UNIFORMS[i] == null) {
                BLUR_UNIFORMS[i] = device.createBuffer(() -> "skydiao/blurred-rounded-uniform",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_FLOATS * Float.BYTES);
            }
        }
        if (w == width && h == height && VIEWS[0] != null) return;
        closeTextures();
        width = w;
        height = h;
        sampleWidth = Math.max(1, (w + DOWNSAMPLE - 1) / DOWNSAMPLE);
        sampleHeight = Math.max(1, (h + DOWNSAMPLE - 1) / DOWNSAMPLE);
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = device.createTexture("skydiao/blurred-rounded-" + i,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    GpuFormat.RGBA8_UNORM, sampleWidth, sampleHeight, 1, 1);
            VIEWS[i] = device.createTextureView(TEXTURES[i]);
        }
    }

    private static GpuBuffer glassUniform(int slot) {
        while (GLASS_UNIFORMS.size() <= slot) GLASS_UNIFORMS.add(null);
        GpuBuffer buffer = GLASS_UNIFORMS.get(slot);
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "skydiao/blurred-rounded-glass-uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_FLOATS * Float.BYTES);
            GLASS_UNIFORMS.set(slot, buffer);
        }
        return buffer;
    }

    private static void writeUniform(CommandEncoder encoder, GpuBuffer buffer, float[] values) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var bytes = stack.malloc(UNIFORM_FLOATS * Float.BYTES);
            bytes.asFloatBuffer().put(values);
            encoder.writeToBuffer(buffer.slice(), bytes);
        }
    }

    private static void pass(CommandEncoder encoder, GpuTextureView source, GpuTextureView target,
                             float[] values, int uniformIndex) {
        writeUniform(encoder, BLUR_UNIFORMS[uniformIndex], values);
        try (var pass = encoder.createRenderPass(() -> "skydiao/blurred-rounded-blur", target, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(BLUR));
            pass.setUniform("Scene", source, sampler);
            pass.setUniform("Blur", BLUR_UNIFORMS[uniformIndex]);
            pass.draw(3, 1, 0, 0);
        }
    }

    /** Adjacent Gaussian taps share a linear sample; weights remain normalized. */
    private static float[] kernel(float sigma) {
        sigma = Math.max(.5F, sigma);
        int radius = Math.min(64, (int) Math.ceil(sigma * 3));
        float[] values = new float[UNIFORM_FLOATS];
        values[16] = 1;
        float total = 1;
        int taps = 0;
        for (int i = 1; i <= radius; i += 2) {
            float first = (float) Math.exp(-i * i / (2F * sigma * sigma));
            float second = i + 1 <= radius ? (float) Math.exp(-(i + 1) * (i + 1) / (2F * sigma * sigma)) : 0;
            float weight = first + second;
            taps++;
            values[16 + taps * 4] = weight;
            values[17 + taps * 4] = i + second / weight;
            total += 2 * weight;
        }
        for (int i = 0; i <= taps; i++) values[16 + i * 4] /= total;
        values[2] = taps;
        return values;
    }

    private static void fallback(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r,
                                 float cr, float cg, float cb, float ca) {
        r = Math.clamp(r, 0, Math.min(w, h) / 2);
        int color = (Math.round(ca * 255F) << 24) | (Math.round(cr * 255F) << 16)
                | (Math.round(cg * 255F) << 8) | Math.round(cb * 255F);
        for (int row = 0; row < Math.ceil(h); row++) {
            float dy = Math.max(0, Math.abs(row + .5F - h / 2) - (h / 2 - r));
            float inset = r - (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill((int) Math.ceil(x + inset), (int) (y + row),
                    (int) Math.floor(x + w - inset), (int) (y + row + 1), color);
        }
    }

    private static void closeTextures() {
        for (int i = 0; i < TEXTURES.length; i++) {
            if (VIEWS[i] != null) { VIEWS[i].close(); VIEWS[i] = null; }
            if (TEXTURES[i] != null) { TEXTURES[i].close(); TEXTURES[i] = null; }
        }
    }

    public static void close() {
        synchronized (PENDING) { PENDING.clear(); }
        closeTextures();
        for (int i = 0; i < BLUR_UNIFORMS.length; i++) {
            if (BLUR_UNIFORMS[i] != null) { BLUR_UNIFORMS[i].close(); BLUR_UNIFORMS[i] = null; }
        }
        for (int i = 0; i < GLASS_UNIFORMS.size(); i++) {
            if (GLASS_UNIFORMS.get(i) != null) { GLASS_UNIFORMS.get(i).close(); GLASS_UNIFORMS.set(i, null); }
        }
        if (sampler != null) { sampler.close(); sampler = null; }
        width = height = 0;
        failed = false;
    }

    private record Panel(float x, float y, float width, float height, float radius,
                         float guiWidth, float guiHeight,
                         float r, float g, float b, float a,
                         boolean useLiquidGlassblur, boolean overwriteBeforeColors,
                         float glowR, float glowG, float glowB, float glowA,
                         float glowRadius, boolean customGlow) { }

    /**
     * 只用于占位的 GUI 元素：三个顶点坐标直接作为 NDC 组成覆盖整个屏幕的三角形（着色器只依赖
     * Position，不依赖 gl_VertexID，因此不受 GuiRenderer 传入的 baseVertex 影响）。它把玻璃合成排进
     * GUI 的 z-order，并让 {@link BlurredRoundedSurface#bindGlassComposite} 能在绘制时认出对应槽位。
     */
    private record GlassPanelElement(RenderPipeline pipeline, TextureSetup textureSetup,
                                     ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            Matrix3x2f pose = new Matrix3x2f();
            writeVertex(vertexConsumer, pose, -1F, -1F);
            writeVertex(vertexConsumer, pose, 3F, -1F);
            writeVertex(vertexConsumer, pose, -1F, 3F);
        }

        private static void writeVertex(VertexConsumer vertexConsumer, Matrix3x2f pose, float x, float y) {
            vertexConsumer.addVertexWith2DPose(pose, x, y).setColor(0);
        }
    }
}