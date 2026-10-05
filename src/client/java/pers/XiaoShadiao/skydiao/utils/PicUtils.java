package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.util.ARGB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class PicUtils {

    public static void copyNativeImageToBufferedImage(NativeImage nativeImage, BufferedImage bufferedImage) {
        int w = nativeImage.getWidth();
        int h = nativeImage.getHeight();

        // 构造 BufferedImage (TYPE_INT_ARGB)
        // BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = nativeImage.getPixel(x, y);
                bufferedImage.setRGB(x, y, pixel);
            }
        }
    }

    public static BufferedImage copyNativeImageToBufferedImage(NativeImage nativeImage) {
        int w = nativeImage.getWidth();
        int h = nativeImage.getHeight();

        // 构造 BufferedImage (TYPE_INT_ARGB)
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] pixels = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            int base = y * w;
            for (int x = 0; x < w; x++) {
                pixels[base + x] = nativeImage.getPixel(x, y);
            }
        });
        img.setRGB(0, 0, w, h, pixels, 0, w);
        return img;
    }

    /**
     * ✅ NativeImage → PNG 字节（内存中直接生成，无需临时文件）
     */
    public static byte[] nativeImageToPngBytes(NativeImage ni) throws Exception {

        // 构造 BufferedImage (TYPE_INT_ARGB)
        BufferedImage img = copyNativeImageToBufferedImage(ni);

        // 直接写到 ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    public static BufferedImage cloneMappedViewToBufferedImage(RenderTarget target, GpuBuffer.MappedView mappedView) {
        int width = target.width;
        int height = target.height;
        GpuTexture sourceTexture = target.getColorTexture();

        if (sourceTexture == null) throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);

        ByteBuffer data = cloneByteBuffer(mappedView.data()).join();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = data.getInt((x + y * width) * sourceTexture.getFormat().pixelSize());
                image.setRGB(x, height - y - 1, (ARGB.blue(argb) << 16) | (ARGB.green(argb) << 8) | (ARGB.red(argb)) | 0xFF000000);
            }
        }
        return image;
    }

    public static CompletableFuture<ByteBuffer> cloneByteBuffer(ByteBuffer buffer) {
        CompletableFuture<ByteBuffer> future = new CompletableFuture<>();
        ToolList.mc.execute(() -> {
            ByteBuffer heapDst = ByteBuffer.allocate(buffer.remaining());
            heapDst.order(buffer.order());
            heapDst.put(buffer);
            heapDst.position(0);
            future.complete(heapDst);
        });
        return future;
    }

}
