package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.google.common.io.ByteStreams;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class ImageTexture extends SimpleTexture {

    private final byte[] bytes;
    private TextureContents cache;

    public ImageTexture(Identifier Identifier, byte[] bytes) {
        super(Objects.requireNonNull(Identifier));
        this.bytes = getRealBytes(bytes);
    }

    @Override
    public @NotNull TextureContents loadContents(ResourceManager resourceManager) throws IOException {
        if (cache == null || cache.image().getPointer() == 0) {
            cache = load(bytes);
        }
        return cache;
    }

    public static TextureContents load(byte[] bytes) throws IOException {
        NativeImage nativeImage;
        try {
            nativeImage = NativeImage.read(bytes);
        } catch (IOException e) {
            List<Throwable> errors = new ArrayList<>();
            try {
                ImageInputStream inputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
                Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);

                if (!readers.hasNext()) {
                    throw new RuntimeException("不支持的图像格式");
                }

                a:{
                    while(readers.hasNext()) {
                        try {
                            ImageReader reader = readers.next();
                            if(reader.toString().contains(".ysm.")) {
                                continue;
                            }
                            reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)));
                            BufferedImage src = reader.read(0);

                            NativeImage ni = new NativeImage(
                                    NativeImage.Format.RGBA,
                                    src.getWidth(),
                                    src.getHeight(),
                                    false
                            );
                            for (int y = 0; y < src.getHeight(); y++) {
                                for (int x = 0; x < src.getWidth(); x++) {
                                    ni.setPixel(x, y, src.getRGB(x, y));
                                }
                            }
                            nativeImage = ni;
                            break a;
                        } catch (Throwable t) {
                            errors.add(t);
                        }
                    }

                    if(errors.isEmpty()) {
                        throw e;
                    } else {
                        throw errors.getLast();
                    }
                }

            } catch (Throwable e1) {
                e1.addSuppressed(e);
                try {
                    errors.forEach(e1::addSuppressed);
                } catch (Exception _) {}
                throw new IOException("Fail to decode picture file: " + Arrays.toString(ByteStreams.limit(new ByteArrayInputStream(bytes), 64).readAllBytes()) + "...", e1);
            }
        }

        return new TextureContents(nativeImage, null);
    }

    /**
     * ✅ 核心方法：检测图片格式，如果不是 PNG 则转换为 PNG
     */
    private byte[] getRealBytes(byte[] bytes) {
        // 1. 快速检查 PNG 文件头（8字节签名）
        if (isPng(bytes)) {
            return bytes; // 已经是 PNG，直接返回
        }

        // 2. 尝试用 ImageIO 读取并转换
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {

            ImageInputStream inputStream = ImageIO.createImageInputStream(bais);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(inputStream);

            if (!readers.hasNext()) {
                throw new RuntimeException("不支持的图像格式");
            }

            Throwable lastError = null;
            a:{
                while (readers.hasNext()) {
                    try {
                        ImageReader reader = readers.next();
                        if(reader.toString().contains(".ysm.")) {
                            continue;
                        }
                        reader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)));
                        BufferedImage src = reader.read(0);

                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            ImageIO.write(src, "png", baos);
                            return baos.toByteArray();
                        }
                    } catch (Throwable t1) {
                        lastError = t1;
                    }
                }
            }
            throw lastError;
        } catch (Throwable e) {
            ToolList.getInstance().log.error("Fail to turn Picture to PNG");
            ToolList.getInstance().log.catching(e);
            return bytes;
        }
    }

    /**
     * ✅ 检查字节数组是否为 PNG 格式
     * PNG 文件头签名：89 50 4E 47 0D 0A 1A 0A
     */
    private static boolean isPng(byte[] bytes) {
        if (bytes.length < 8) {
            return false;
        }

        return (bytes[0] & 0xFF) == 0x89 &&
                (bytes[1] & 0xFF) == 0x50 && // P
                (bytes[2] & 0xFF) == 0x4E && // N
                (bytes[3] & 0xFF) == 0x47 && // G
                (bytes[4] & 0xFF) == 0x0D &&
                (bytes[5] & 0xFF) == 0x0A &&
                (bytes[6] & 0xFF) == 0x1A &&
                (bytes[7] & 0xFF) == 0x0A;
    }

}
