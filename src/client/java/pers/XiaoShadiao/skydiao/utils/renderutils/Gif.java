package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.madgag.gif.fmsware.GifDecoder;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.function.IOSupplier;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Gif implements AutoCloseable {

    private static final Map<Identifier, Gif> cache = new HashMap<>();

    public static Gif create(Identifier identifier) {
        return cache.computeIfAbsent(identifier, _ -> new Gif(identifier));
    }

    public static Gif create(Identifier identifier, IOSupplier<InputStream> ioSupplier) {
        return cache.computeIfAbsent(identifier, _ -> new Gif(identifier, ioSupplier));
    }

    public static void clearCache() {
        cache.values().removeIf(gif -> {
            try {
                gif.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        });
    }

    private final LongList frameDelaysMs = new LongArrayList();
    private final java.util.List<SimpleTexture> textures = new java.util.ArrayList<>();
    private int currentFrame;
    private long startTimeMs;
    private long totalDurationMs;

    private final boolean missingTexture;

    public Gif(Identifier identifier) {
        this(identifier, () -> ToolList.mc.getResourceManager().getResource(identifier).orElseThrow(() -> new IOException("No resource: " + identifier)).open());
    }

    public Gif(Identifier identifier, IOSupplier<InputStream> ioSupplier) {
        boolean missingTexture1 = false;

        a: try {

            try (InputStream is2 = ioSupplier.get()) {
                GifDecoder decoder = new GifDecoder();
                decoder.read(is2);
                for (int i = 0; i < decoder.getFrameCount(); i++) {
                    BufferedImage frame = decoder.getFrame(i);
                    int delay = decoder.getDelay(i);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(frame, "png", baos);

                    byte[] pngBytes = baos.toByteArray();
                    textures.add(new ImageTexture(
                            identifier.withSuffix("____" + i),
                            pngBytes
                    ));
                    frameDelaysMs.add(delay == 0 ? 100 : delay);
                }
                if (!textures.isEmpty() && !frameDelaysMs.isEmpty()) break a;
            } catch (Throwable e) {

            }

            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(ImageIO.createImageInputStream(ioSupplier.get()));
            com.mojang.blaze3d.platform.NativeImage canvas = null;
            com.mojang.blaze3d.platform.NativeImage previousCanvas = null;
            ImageReader reader = null;
            Throwable lastError = null;
            while(imageReaders.hasNext()) {
                try(ImageInputStream inputStream = ImageIO.createImageInputStream(ioSupplier.get())) {
                    reader = imageReaders.next();

                    if(reader.toString().contains(".ysm.")) {
                        continue;
                    }

                    reader.setInput(inputStream);

                    int numFrames = reader.getNumImages(true);


                    // ✅ 主画布（最终输出）
                    canvas = null;
                    // ✅ 快照画布（用于 disposalMethod=3）
                    previousCanvas = null;

                    int canvasWidth = reader.getWidth(0);
                    int canvasHeight = reader.getHeight(0);

                    // ✅ 记录上一帧的处置方法和区域（第一帧默认为0）
                    int lastDisposalMethod = 0;
                    int lastFrameLeft = 0, lastFrameTop = 0;
                    int lastFrameWidth = canvasWidth, lastFrameHeight = canvasHeight;

                    for (int i = 0; i < numFrames; i++) {
                        // ✅ 先解析元数据（在读取帧之前）
                        int disposalMethod = 0;
                        int transparentColorIndex = -1;
                        int delayCs = 10;
                        int frameLeft = 0;
                        int frameTop = 0;

                        try {
                            IIOMetadata metadata = reader.getImageMetadata(i);
                            if (metadata != null) {
                                String format = metadata.getNativeMetadataFormatName();
                                if ("javax_imageio_gif_image_1.0".equals(format)) {
                                    var root = (javax.imageio.metadata.IIOMetadataNode)
                                            metadata.getAsTree(format);

                                    // 解析 GraphicControlExtension
                                    var gce = (javax.imageio.metadata.IIOMetadataNode)
                                            root.getElementsByTagName("GraphicControlExtension").item(0);
                                    if (gce != null) {
                                        disposalMethod = parseDisposalMethod(
                                                gce.getAttribute("disposalMethod"));
                                        delayCs = Integer.parseInt(
                                                gce.getAttribute("delayTime"));
                                        // 只有 transparentColorFlag 为 true 时才解析透明色索引
                                        String tcf = gce.getAttribute("transparentColorFlag");
                                        if ("true".equalsIgnoreCase(tcf)) {
                                            String tci = gce.getAttribute("transparentColorIndex");
                                            if (!tci.isEmpty()) {
                                                transparentColorIndex = Integer.parseInt(tci);
                                            }
                                        }
                                    }

                                    // 解析 ImageDescriptor（获取偏移和尺寸）
                                    var imgDesc = (javax.imageio.metadata.IIOMetadataNode)
                                            root.getElementsByTagName("ImageDescriptor").item(0);
                                    if (imgDesc != null) {
                                        frameLeft = Integer.parseInt(
                                                imgDesc.getAttribute("imageLeftPosition"));
                                        frameTop = Integer.parseInt(
                                                imgDesc.getAttribute("imageTopPosition"));
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        // 读取原始帧
                        BufferedImage rawFrame = reader.read(i);
                        int frameWidth = rawFrame.getWidth();
                        int frameHeight = rawFrame.getHeight();

                        // 初始化画布
                        if (canvas == null) {
                            canvas = new com.mojang.blaze3d.platform.NativeImage(
                                    com.mojang.blaze3d.platform.NativeImage.Format.RGBA,
                                    canvasWidth,
                                    canvasHeight,
                                    false
                            );
                            canvas.fillRect(0, 0, canvasWidth, canvasHeight, 0x00000000);
                        }

                        // ✅ 根据【上一帧】的处置方法处理画布
                        if (i > 0) {
                            switch (lastDisposalMethod) {
                                case 2: // 恢复为背景（透明）
                                    fillRectAlpha(
                                            canvas,
                                            lastFrameLeft, lastFrameTop,
                                            lastFrameWidth, lastFrameHeight,
                                            0x00000000
                                    );
                                    break;

                                case 3: // 恢复为前一帧
                                    if (previousCanvas != null) {
                                        canvas.copyFrom(previousCanvas);
                                    }
                                    break;
                            }
                        }

                        // ✅ 保存快照（用于下一帧的 disposalMethod=3）
                        if (disposalMethod == 3) {
                            if (previousCanvas == null) {
                                previousCanvas = new com.mojang.blaze3d.platform.NativeImage(
                                        com.mojang.blaze3d.platform.NativeImage.Format.RGBA,
                                        canvasWidth,
                                        canvasHeight,
                                        false
                                );
                            }
                            previousCanvas.copyFrom(canvas);
                        }

                        // ✅ 将当前帧绘制到画布
                        try (NativeImage frameNative = bufferedImageToNativeImage(rawFrame, transparentColorIndex)) {
                            drawNativeImage(frameNative, canvas, frameLeft, frameTop);
                        }

                        // ✅ 导出 PNG（Minecraft 兼容）
                        byte[] pngBytes = nativeImageToPngBytes(canvas);
                        textures.add(new ImageTexture(
                                identifier.withSuffix("____" + i),
                                pngBytes
                        ));

                        frameDelaysMs.add(delayCs * 10L);

                        // ✅ 更新上一帧的处置方法和区域
                        lastDisposalMethod = disposalMethod;
                        lastFrameLeft = frameLeft;
                        lastFrameTop = frameTop;
                        lastFrameWidth = frameWidth;
                        lastFrameHeight = frameHeight;
                    }
                    break a;
                } catch (Throwable e) {
                    lastError = e;
                    frameDelaysMs.clear();
                    textures.clear();
                } finally {
                    if (canvas != null) canvas.close();
                    if (previousCanvas != null) previousCanvas.close();
                    reader.dispose();
                }
            }
            if(lastError != null) {
                throw lastError;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            textures.clear();
            textures.add(MissingImageTexture.INSTANCE);
            missingTexture1 = true;
        }

        if (textures.isEmpty()) {
            textures.add(MissingImageTexture.INSTANCE);
            missingTexture1 = true;
        }

        missingTexture = missingTexture1;
        for (SimpleTexture texture : textures) {
            ToolList.mc.getTextureManager().registerAndLoad(texture.resourceId(), texture);
        }

        this.totalDurationMs = frameDelaysMs.longStream().sum();
        if (this.totalDurationMs <= 0) {
            this.totalDurationMs = frameDelaysMs.size() * 100L;
        }

        resetFrame();
    }

    public boolean isMissingTexture() {
        return missingTexture;
    }

    /**
     * ✅ 安全版：BufferedImage → NativeImage
     */
    private static com.mojang.blaze3d.platform.NativeImage bufferedImageToNativeImage(
            BufferedImage src, int transparentIndex) {

        int w = src.getWidth();
        int h = src.getHeight();

        com.mojang.blaze3d.platform.NativeImage ni =
                new com.mojang.blaze3d.platform.NativeImage(
                        com.mojang.blaze3d.platform.NativeImage.Format.RGBA,
                        w, h, false
                );

        // ✅ 索引图像（GIF）
        if (src.getColorModel() instanceof IndexColorModel icm) {
            var raster = src.getRaster();

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixelIndex = raster.getSample(x, y, 0);

                    if (pixelIndex == transparentIndex) {
                        ni.setPixel(x, y, 0x00000000);
                    } else {
                        int rgb = icm.getRGB(pixelIndex);
                        ni.setPixel(x, y, 0xFF000000 | rgb);
                    }
                }
            }

            // ✅ 非索引图像
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    ni.setPixel(x, y, src.getRGB(x, y));
                }
            }
        }

        return ni;
    }

    /**
     * ✅ 绘制 NativeImage（支持偏移）
     */
    private static void drawNativeImage(
            com.mojang.blaze3d.platform.NativeImage src,
            com.mojang.blaze3d.platform.NativeImage dst,
            int offsetX, int offsetY) {

        int w = src.getWidth();
        int h = src.getHeight();

        for (int y = 0; y < h; y++) {
            int dstY = y + offsetY;
            if (dstY < 0 || dstY >= dst.getHeight()) continue;

            for (int x = 0; x < w; x++) {
                int dstX = x + offsetX;
                if (dstX < 0 || dstX >= dst.getWidth()) continue;

                int pixel = src.getPixel(x, y);
                // 跳过透明像素，保留画布原有内容
                if ((pixel >>> 24) != 0) {
                    dst.setPixel(dstX, dstY, pixel);
                }
            }
        }
    }

    /**
     * ✅ 填充矩形（带 Alpha）
     */
    private static void fillRectAlpha(
            com.mojang.blaze3d.platform.NativeImage img,
            int x, int y, int width, int height,
            int color) {

        for (int py = y; py < y + height; py++) {
            if (py < 0 || py >= img.getHeight()) continue;
            for (int px = x; px < x + width; px++) {
                if (px < 0 || px >= img.getWidth()) continue;
                img.setPixel(px, py, color);
            }
        }
    }

    /**
     * ✅ Disposal Method 解析
     */
    private static int parseDisposalMethod(String disposalStr) {
        return switch (disposalStr) {
            case "doNotDispose" -> 0;
            case "disposeToBackgroundColor" -> 2;
            case "restoreToPrevious" -> 3;
            default -> 0;
        };
    }

    /**
     * ✅ NativeImage → PNG 字节（内存中直接生成，无需临时文件）
     */
    private static byte[] nativeImageToPngBytes(
            com.mojang.blaze3d.platform.NativeImage ni) throws Exception {

        int w = ni.getWidth();
        int h = ni.getHeight();

        // 构造 BufferedImage (TYPE_INT_ARGB)
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = ni.getPixel(x, y);
                img.setRGB(x, y, pixel);
            }
        }
        // 直接写到 ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    @Override
    public void close() throws Exception {
        textures.forEach(SimpleTexture::close);
        textures.clear();
    }

    public SimpleTexture updateAndGetFrame() {
        if (textures.isEmpty()) return MissingImageTexture.INSTANCE;
        if (frameDelaysMs.isEmpty()) return textures.getFirst();

        long timeInCycle = (System.currentTimeMillis() - startTimeMs) % totalDurationMs;
        long accumulated = 0;

        for (int i = 0; i < frameDelaysMs.size(); i++) {
            accumulated += frameDelaysMs.getLong(i);
            if (timeInCycle < accumulated) {
                currentFrame = i;
                break;
            }
        }

        return textures.get(Math.min(currentFrame, textures.size() - 1));
    }

    public void resetFrame() {
        startTimeMs = System.currentTimeMillis();
    }
}