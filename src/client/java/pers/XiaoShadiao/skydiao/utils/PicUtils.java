package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
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

    /**
     * 把 {@link ScreenshotUtils.CapturedFrame} 还原成 BufferedImage.
     */
    public static BufferedImage heapBufferToBufferedImage(ScreenshotUtils.CapturedFrame frame) {
        return heapBufferToBufferedImage(frame.data(), frame.width(), frame.height(), frame.blockSize());
    }

    /**
     * 把帧的堆内存数据还原成 BufferedImage, 纯 Java 堆操作, 可在任意线程调用.
     */
    public static BufferedImage heapBufferToBufferedImage(ByteBuffer data, int width, int height, int blockSize) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int srcRow = y * width;
            int dstRow = (height - y - 1) * width;
            for (int x = 0; x < width; x++) {
                int argb = data.getInt((srcRow + x) * blockSize);
                pixels[dstRow + x] = (ARGB.blue(argb) << 16) | (ARGB.green(argb) << 8) | (ARGB.red(argb)) | 0xFF000000;
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

}
