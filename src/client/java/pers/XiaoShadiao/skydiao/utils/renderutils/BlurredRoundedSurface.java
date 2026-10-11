package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 同帧磨砂玻璃效果：绘制带圆角的矩形，并在其周围做 GPU 模糊与辉光/阴影。
 *
 * <p>提取阶段（{@link #draw}）只记录一块圆角面板的几何信息；GUI 渲染器会在世界渲染完成、
 * 原生 GUI 绘制之前调用 {@link #renderPending} 捕获画面并合成模糊结果。所有滤波都在 GPU 上
 * 完成，并且只把圆角区域与其周围光晕合成回去。
 *
 * <p>调用方需保证在 {@link GuiRenderState} 对应的一帧内先 {@link #draw} 再 {@link #renderPending}，
 * 并在渲染器关闭时调用 {@link #close} 释放资源。
 */
public final class BlurredRoundedSurface {
    private static final float BLUR_SIGMA = 2F;
    private static final float GLOW_EXTENT = 8F;
    private static final int DOWNSAMPLE = 4;
    private static final int UNIFORM_FLOATS = 148;
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
    private static final Map<GuiRenderState, List<Panel>> PENDING = new WeakHashMap<>();
    private static final RenderPipeline BLUR = pipeline("blurred_rounded_blur", false);
    private static final RenderPipeline GLASS = pipeline("blurred_rounded_glass", true);
    private static final GpuTexture[] TEXTURES = new GpuTexture[3];
    private static final GpuTextureView[] VIEWS = new GpuTextureView[3];
    private static final GpuBuffer[] UNIFORMS = new GpuBuffer[4];
    private static GpuSampler sampler;
    private static int width;
    private static int height;
    private static int sampleWidth;
    private static int sampleHeight;
    private static boolean failed;

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
        drawInternal(graphics, x, y, w, h, true, radius, r, g, b, a,
                DEFAULT_GLOW_R, DEFAULT_GLOW_G, DEFAULT_GLOW_B, DEFAULT_GLOW_A, DEFAULT_GLOW_RADIUS, false);
    }

    /**
     * 自定义辉光：{@code useLiquidGlassblur} 位于 {@code radius} 之前，控制当底色 alpha 非 1 时是否预模糊该区域；
     * {@code glowRadius} 控制辉光距离，{@code glowR/g/b/a}（0~1）为辉光颜色与强度。
     */
    public static void drawWithCustomGlow(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                          boolean useLiquidGlassblur, float radius,
                                          float glowRadius, float r, float g, float b, float a,
                                          float glowR, float glowG, float glowB, float glowA) {
        drawInternal(graphics, x, y, w, h, useLiquidGlassblur, radius, r, g, b, a, glowR, glowG, glowB, glowA, glowRadius, true);
    }

    /** 自定义辉光：未指定辉光颜色时辉光跟随内部颜色，强度取默认 1。 */
    public static void drawWithCustomGlow(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                          boolean useLiquidGlassblur, float radius,
                                          float glowRadius, float r, float g, float b, float a) {
        drawWithCustomGlow(graphics, x, y, w, h, useLiquidGlassblur, radius, glowRadius, r, g, b, a, r, g, b, a);
    }

    private static void drawInternal(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                     boolean useLiquidGlassblur, float radius,
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
                graphics.guiWidth(), graphics.guiHeight(), graphics.scissorStack.peek(), r, g, b, a,
                useLiquidGlassblur, glowR, glowG, glowB, glowA, glowRadius, customGlow);
        if (!Float.isFinite(panel.x + panel.y + panel.width + panel.height + panel.radius)) return;
        synchronized (PENDING) {
            // There is one surface per GUI state. Re-extraction while minimized replaces it.
            PENDING.computeIfAbsent(graphics.guiRenderState, _ -> new ArrayList<>()).add(panel);
        }
    }

    /** Called at GuiRenderer.render HEAD, after the world and before native GUI draws. */
    public static void renderPending(GuiRenderState state) {
        List<Panel> panelList;
        synchronized (PENDING) {
            panelList = PENDING.remove(state);
        }
        if(panelList == null) return;
        for(Panel panel : panelList) {
            if (panel == null || failed || panel.width <= 0 || panel.height <= 0) return;
            Minecraft client = Minecraft.getInstance();
            var target = client.getMainRenderTarget();
            if (target == null || target.getColorTextureView() == null || panel.guiWidth <= 0 || panel.guiHeight <= 0)
                return;
            // GuiRenderer's projection uses physical size / integer GUI scale, not
            // the rounded-up GUI dimensions returned during extraction.
            var window = client.gameRenderer.getGameRenderState().windowRenderState;
            if (window.guiScale <= 0 || window.width <= 0 || window.height <= 0) return;
            panel = new Panel(panel.x, panel.y, panel.width, panel.height, panel.radius,
                    window.width / (float) window.guiScale, window.height / (float) window.guiScale, panel.scissor,
                    panel.r, panel.g, panel.b, panel.a, panel.useLiquidGlassblur,
                    panel.glowR, panel.glowG, panel.glowB, panel.glowA,
                panel.glowRadius, panel.customGlow);
            try {
                var device = RenderSystem.getDevice();
                // These are cached by the device and recompiled normally after resource reloads.
                if (!device.precompilePipeline(BLUR).isValid() || !device.precompilePipeline(GLASS).isValid()) {
                    throw new IllegalStateException("BlurredRounded glass shader did not compile");
                }
                resize(target.width, target.height);
                var encoder = device.createCommandEncoder();
                // 仅当底色半透明（alpha 非 1）且调用方要求液态玻璃模糊时，才预模糊该区域。
                boolean blur = panel.useLiquidGlassblur && panel.a < 1F;
                GpuTextureView glassScene = VIEWS[0];
                if (blur) {
                    float[] copy = new float[UNIFORM_FLOATS];
                    copy[16] = 1F;
                    pass(encoder, target.getColorTextureView(), VIEWS[0], copy, 0, false, null, 0);
                    float[] kernel = kernel(BLUR_SIGMA * sampleWidth / panel.guiWidth);
                    kernel[0] = 1F / sampleWidth;
                    pass(encoder, VIEWS[0], VIEWS[1], kernel, 1, false, panel, BLUR_SIGMA * 3 + 2);
                    kernel[0] = 0;
                    kernel[1] = 1F / sampleHeight;
                    pass(encoder, VIEWS[1], VIEWS[2], kernel, 2, false, panel, 2);
                    glassScene = VIEWS[2];
                }

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
            glass[30] = blur ? 1F : 0F;
                pass(encoder, glassScene, target.getColorTextureView(), glass, 3, true, panel, GLOW_EXTENT);
            } catch (RuntimeException failure) {
                failed = true;
                LoggerFactory.getLogger("skydiao").warn("BlurredRounded backdrop blur unavailable; using translucent glass", failure);
            }
        }
    }

    private static RenderPipeline pipeline(String name, boolean translucent) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("skydiao", "core/blurred_rounded"))
                .withSampler("Scene")
                .withUniform("Blur", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withCull(false)
                .withDepthStencilState(Optional.empty())
                .withColorTargetState(translucent ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
                .build());
    }

    private static void resize(int w, int h) {
        var device = RenderSystem.getDevice();
        if (sampler == null) {
            sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        }
        for (int i = 0; i < UNIFORMS.length; i++) {
            if (UNIFORMS[i] == null) {
                UNIFORMS[i] = device.createBuffer(() -> "skydiao/blurred-rounded-uniform",
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
                    TextureFormat.RGBA8, sampleWidth, sampleHeight, 1, 1);
            VIEWS[i] = device.createTextureView(TEXTURES[i]);
        }
    }

    private static void pass(CommandEncoder encoder, GpuTextureView source, GpuTextureView target,
                             float[] values, int uniformIndex, boolean composite, Panel panel, float margin) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var bytes = stack.malloc(UNIFORM_FLOATS * Float.BYTES);
            bytes.asFloatBuffer().put(values);
            encoder.writeToBuffer(UNIFORMS[uniformIndex].slice(), bytes);
        }
        try (var pass = encoder.createRenderPass(() -> "skydiao/blurred-rounded-glass", target, OptionalInt.empty())) {
            pass.setPipeline(composite ? GLASS : BLUR);
            pass.bindTexture("Scene", source, sampler);
            pass.setUniform("Blur", UNIFORMS[uniformIndex]);
            if (panel != null) {
                float sx = target.getWidth(0) / panel.guiWidth;
                float sy = target.getHeight(0) / panel.guiHeight;
                float left = panel.x - margin;
                float top = panel.y - margin;
                float right = panel.x + panel.width + margin;
                float bottom = panel.y + panel.height + margin;
                if (composite && panel.scissor != null) {
                    left = Math.max(left, panel.scissor.left());
                    top = Math.max(top, panel.scissor.top());
                    right = Math.min(right, panel.scissor.right());
                    bottom = Math.min(bottom, panel.scissor.bottom());
                }
                int l = Math.max(0, (int) Math.floor(left * sx));
                int t = Math.max(0, (int) Math.floor(top * sy));
                int r = Math.min(target.getWidth(0), (int) Math.ceil(right * sx));
                int b = Math.min(target.getHeight(0), (int) Math.ceil(bottom * sy));
                if (r <= l || b <= t) return;
                pass.enableScissor(l, target.getHeight(0) - b, r - l, b - t);
            }
            pass.draw(0, 3);
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
        for (int i = 0; i < UNIFORMS.length; i++) {
            if (UNIFORMS[i] != null) { UNIFORMS[i].close(); UNIFORMS[i] = null; }
        }
        if (sampler != null) { sampler.close(); sampler = null; }
        width = height = 0;
        failed = false;
    }

    private record Panel(float x, float y, float width, float height, float radius,
                         float guiWidth, float guiHeight, ScreenRectangle scissor,
                         float r, float g, float b, float a,
                         boolean useLiquidGlassblur,
                         float glowR, float glowG, float glowB, float glowA,
                         float glowRadius, boolean customGlow) { }
}