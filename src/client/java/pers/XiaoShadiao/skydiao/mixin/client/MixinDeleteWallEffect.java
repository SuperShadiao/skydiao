package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(ScreenEffectRenderer.class)
public class MixinDeleteWallEffect {

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void renderTex(TextureAtlasSprite textureAtlasSprite, PoseStack poseStack, MultiBufferSource multiBufferSource, CallbackInfo ci) {
        if(ConfigManager.nosuffoverlay.getValue()) ci.cancel();
    }

}
