package pers.XiaoShadiao.skydiao.utils.renderutils;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

public class MissingImageTexture extends SimpleTexture {

    public static final MissingImageTexture INSTANCE = new MissingImageTexture(Identifier.fromNamespaceAndPath("skydiao", "missing_texture"));

    private MissingImageTexture(Identifier Identifier) {
        super(Objects.requireNonNull(Identifier));
    }

    @Override
    public @NonNull TextureContents loadContents(@NonNull ResourceManager resourceManager) {
        return TextureContents.createMissing();
    }

}
