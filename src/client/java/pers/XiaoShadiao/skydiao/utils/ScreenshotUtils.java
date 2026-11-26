package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class ScreenshotUtils {

    /**
     * 采集到的帧数据, 全部为 Java 堆内存, 不含任何 native 句柄, 可安全跨线程传递.
     *
     * @param data      帧像素数据, 位置为 0
     * @param width     采集时的宽度
     * @param height    采集时的高度
     * @param blockSize 采集时的像素字节数
     */
    public record CapturedFrame(ByteBuffer data, int width, int height, int blockSize) {
        public BufferedImage toBufferedImage() {
            return PicUtils.heapBufferToBufferedImage(this);
        }
    }

    /**
     * 采集帧并在回调前把映射内存同步拷贝到 Java 堆, 回调与 native 句柄的关闭都在同一线程完成,
     * 调用方拿到的 {@link CapturedFrame} 不持有任何 native 资源.
     */
    public static void captureFramebuffer(final RenderTarget target, final Consumer<CapturedFrame> callback) {
        int width = target.width;
        int height = target.height;
        GpuTexture sourceTexture = target.getColorTexture();
        if (sourceTexture == null) {
            throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
        }
        int blockSize = sourceTexture.getFormat().blockSize();

        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Screenshot buffer", 9, (long)width * height * blockSize);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(sourceTexture, buffer, 0L, () -> {
            ByteBuffer copy;
            try(GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                ByteBuffer src = view.data();
                copy = ByteBuffer.allocate(src.remaining());
                copy.order(src.order());
                copy.put(src);
                copy.flip();
            } finally {
                buffer.close();
            }
            callback.accept(new CapturedFrame(copy, width, height, blockSize));
        }, 0);
    }

}
