package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ImageTexture extends SimpleTexture {

    private final byte[] bytes;
    private TextureContents cache;

    public ImageTexture(Identifier Identifier, byte[] bytes) {
        super(Objects.requireNonNull(Identifier));
        this.bytes = bytes;
    }

    @Override
    public @NotNull TextureContents loadContents(ResourceManager resourceManager) throws IOException {
        if (cache == null || cache.image().getPointer() == 0) {
            cache = load(bytes);
        }
        return cache;
    }

    public static TextureContents load(byte[] bytes) throws IOException {

        NativeImage nativeImage = NativeImage.read(bytes);

        return new TextureContents(nativeImage, null);
    }
}
