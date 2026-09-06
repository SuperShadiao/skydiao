package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.util.ARGB;

import java.util.function.Consumer;

public class ScreenshotUtils {

    public static void takeScreenshot(final RenderTarget target, final Consumer<NativeImage> callback) {
        takeScreenshot(target, 1, callback);
    }

    public static void takeScreenshot(final RenderTarget target, final int downscaleFactor, final Consumer<NativeImage> callback) {
        int width = target.width;
        int height = target.height;
        GpuTexture sourceTexture = target.getColorTexture();
        if (sourceTexture == null) {
            throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
        } else if (width % downscaleFactor == 0 && height % downscaleFactor == 0) {
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Screenshot buffer", 9, (long)width * height * sourceTexture.getFormat().pixelSize());
            CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(sourceTexture, buffer, 0L, () -> {
                GpuBuffer.MappedView read0 = commandEncoder.mapBuffer(buffer, true, false);
                ToolList.addThreadedTask(() -> {
                    try (GpuBuffer.MappedView read = read0) {
                        int outputHeight = height / downscaleFactor;
                        int outputWidth = width / downscaleFactor;
                        NativeImage image = new NativeImage(outputWidth, outputHeight, false);

                        for (int y = 0; y < outputHeight; y++) {
                            for (int x = 0; x < outputWidth; x++) {
                                if (downscaleFactor == 1) {
                                    int argb = read.data().getInt((x + y * width) * sourceTexture.getFormat().pixelSize());
                                    image.setPixelABGR(x, height - y - 1, argb | 0xFF000000);
                                } else {
                                    int red = 0;
                                    int green = 0;
                                    int blue = 0;

                                    for (int i = 0; i < downscaleFactor; i++) {
                                        for (int j = 0; j < downscaleFactor; j++) {
                                            int argb = read.data().getInt((x * downscaleFactor + i + (y * downscaleFactor + j) * width) * sourceTexture.getFormat().pixelSize());
                                            red += ARGB.red(argb);
                                            green += ARGB.green(argb);
                                            blue += ARGB.blue(argb);
                                        }
                                    }

                                    int sampleCount = downscaleFactor * downscaleFactor;
                                    image.setPixelABGR(x, outputHeight - y - 1, ARGB.color(255, red / sampleCount, green / sampleCount, blue / sampleCount));
                                }
                            }
                        }

                        callback.accept(image);
                    }

                    return null;
                });
                buffer.close();
            }, 0);
        } else {
            throw new IllegalArgumentException("Image size is not divisible by downscale factor");
        }
    }

}
