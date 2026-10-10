package pers.XiaoShadiao.skydiao.hud;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
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

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.WeakHashMap;

/**
 * Same-frame frosted glass for the island. Extraction only records its geometry;
 * the GUI renderer captures the completed world before text or items are drawn.
 * All filtering stays on the GPU and only the rounded island is composited back.
 */
public final class DynamicSpotSurface {
    private static final float BLUR_SIGMA = 2F;
    private static final float GLOW_EXTENT = 8F;
    private static final int DOWNSAMPLE = 4;
    private static final int UNIFORM_FLOATS = 148;
    private static final Map<GuiRenderState, Panel> PENDING = new WeakHashMap<>();
    private static final RenderPipeline BLUR = pipeline("dynamic_spot_blur", false);
    private static final RenderPipeline GLASS = pipeline("dynamic_spot_glass", true);
    private static final GpuTexture[] TEXTURES = new GpuTexture[3];
    private static final GpuTextureView[] VIEWS = new GpuTextureView[3];
    private static final GpuBuffer[] UNIFORMS = new GpuBuffer[4];
    private static GpuSampler sampler;
    private static int width;
    private static int height;
    private static int sampleWidth;
    private static int sampleHeight;
    private static boolean failed;

    private DynamicSpotSurface() { }

    /** Coordinates use the current GUI pose, including the user's HUD scale/offset. */
    public static void draw(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
        if (w <= 0 || h <= 0) return;
        if (failed) {
            fallback(graphics, x, y, w, h, radius);
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
                graphics.guiWidth(), graphics.guiHeight(), graphics.scissorStack.peek());
        if (!Float.isFinite(panel.x + panel.y + panel.width + panel.height + panel.radius)) return;
        synchronized (PENDING) {
            // There is one island per GUI state. Re-extraction while minimized replaces it.
            PENDING.put(graphics.guiRenderState, panel);
        }
    }

    /** Called at GuiRenderer.render HEAD, after the world and before native GUI draws. */
    public static void renderPending(GuiRenderState state) {
        Panel panel;
        synchronized (PENDING) {
            panel = PENDING.remove(state);
        }
        if (panel == null || failed || panel.width <= 0 || panel.height <= 0) return;
        Minecraft client = Minecraft.getInstance();
        var target = client.getMainRenderTarget();
        if (target == null || target.getColorTextureView() == null || panel.guiWidth <= 0 || panel.guiHeight <= 0) return;
        // GuiRenderer's projection uses physical size / integer GUI scale, not
        // the rounded-up GUI dimensions returned during extraction.
        var window = client.gameRenderer.getGameRenderState().windowRenderState;
        if (window.guiScale <= 0 || window.width <= 0 || window.height <= 0) return;
        panel = new Panel(panel.x, panel.y, panel.width, panel.height, panel.radius,
                window.width / (float) window.guiScale, window.height / (float) window.guiScale, panel.scissor);
        try {
            var device = RenderSystem.getDevice();
            // These are cached by the device and recompiled normally after resource reloads.
            if (!device.precompilePipeline(BLUR).isValid() || !device.precompilePipeline(GLASS).isValid()) {
                throw new IllegalStateException("DynamicSpot glass shader did not compile");
            }
            resize(target.width, target.height);
            var encoder = device.createCommandEncoder();
            float[] copy = new float[UNIFORM_FLOATS];
            copy[16] = 1F;
            pass(encoder, target.getColorTextureView(), VIEWS[0], copy, 0, false, null, 0);
            float[] kernel = kernel(BLUR_SIGMA * sampleWidth / panel.guiWidth);
            kernel[0] = 1F / sampleWidth;
            pass(encoder, VIEWS[0], VIEWS[1], kernel, 1, false, panel, BLUR_SIGMA * 3 + 2);
            kernel[0] = 0;
            kernel[1] = 1F / sampleHeight;
            pass(encoder, VIEWS[1], VIEWS[2], kernel, 2, false, panel, 2);

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
            glass[13] = 152F / 255F; // Samsara's 0x98000000 neutral glass.
            glass[14] = Math.min(sx, sy);
            pass(encoder, VIEWS[2], target.getColorTextureView(), glass, 3, true, panel, GLOW_EXTENT);
        } catch (RuntimeException failure) {
            failed = true;
            LoggerFactory.getLogger("skydiao").warn("DynamicSpot backdrop blur unavailable; using translucent glass", failure);
        }
    }

    private static RenderPipeline pipeline(String name, boolean translucent) {
        return RenderPipelines.register(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("skydiao", "pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("skydiao", "core/dynamic_spot"))
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
                UNIFORMS[i] = device.createBuffer(() -> "skydiao/dynamic-spot-uniform",
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
            TEXTURES[i] = device.createTexture("skydiao/dynamic-spot-" + i,
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
        try (var pass = encoder.createRenderPass(() -> "skydiao/dynamic-spot-glass", target, OptionalInt.empty())) {
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

    private static void fallback(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float r) {
        r = Math.clamp(r, 0, Math.min(w, h) / 2);
        for (int row = 0; row < Math.ceil(h); row++) {
            float dy = Math.max(0, Math.abs(row + .5F - h / 2) - (h / 2 - r));
            float inset = r - (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill((int) Math.ceil(x + inset), (int) (y + row),
                    (int) Math.floor(x + w - inset), (int) (y + row + 1), 0x98000000);
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
                         float guiWidth, float guiHeight, ScreenRectangle scissor) { }
}
