package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;

import java.util.function.BiConsumer;

public class ScreenshotUtils {

    public static void takeScreenshot2(final RenderTarget target, final BiConsumer<GpuBufferSlice.MappedView, Runnable> callback) {
        int width = target.width;
        int height = target.height;
        GpuTexture sourceTexture = target.getColorTexture();
        if (sourceTexture == null) {
            throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
        } else {
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Screenshot buffer", 9, (long)width * height * sourceTexture.getFormat().blockSize());
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(sourceTexture, buffer, 0L, () -> {
                GpuBufferSlice.MappedView read0 = buffer.map(true, false);

                Runnable runnable = new Runnable() {

                    private boolean closed;

                    @Override
                    public void run() {
                        if (closed) {
                            return;
                        }
                        closed = true;
                        ToolList.mc.execute(() -> {
                                    try {
                                        read0.close();
                                    } catch (Exception _) {
                                    }
                                    try {
                                        buffer.close();
                                    } catch (Exception _) {
                                    }
                                }
                        );
                    }
                };
                callback.accept(read0, runnable);
            }, 0);
        }
    }

}
