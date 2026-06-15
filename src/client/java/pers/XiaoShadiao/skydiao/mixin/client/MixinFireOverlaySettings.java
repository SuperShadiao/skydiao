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
public class MixinFireOverlaySettings {

    @Inject(at = @At("HEAD"), method = "renderFire", cancellable = true)
    private static void removeFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        if(ConfigManager.fireOverlay.getValue() == 2) ci.cancel();
    }

    @Inject(at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"), method = "renderFire")
    private static void lowerFire(PoseStack poseStack, MultiBufferSource multiBufferSource, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        if(ConfigManager.fireOverlay.getValue() == 1) poseStack.translate(0, -0.24f, 0);
    }

}
