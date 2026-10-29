package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(ScreenEffectRenderer.class)
public class MixinDeleteWallEffect {
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void submitBlockSprite(Identifier atlasLocation, float u0, float v0, float u1, float v1, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int color, CallbackInfo ci) {
        if (ConfigManager.nosuffoverlay.getValue()) ci.cancel();
    }
}
