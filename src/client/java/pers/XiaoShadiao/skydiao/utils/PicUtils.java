package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

}
