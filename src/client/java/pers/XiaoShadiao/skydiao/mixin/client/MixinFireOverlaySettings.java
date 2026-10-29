package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(ScreenEffectRenderer.class)
public class MixinFireOverlaySettings {

    @WrapMethod(method = "submitFire")
    private static void skydiao$submitFire(PoseStack poseStack, SubmitNodeCollector collector,
                                           TextureAtlasSprite sprite, Operation<Void> original) {
        int mode = ConfigManager.fireOverlay.getValue();
        if (mode == 2) return;
        poseStack.pushPose();
        if (mode == 1) poseStack.translate(0, -0.24f, 0);
        original.call(poseStack, collector, sprite);
        poseStack.popPose();
    }

}
