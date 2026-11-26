package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.BlurredRoundedSurface;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "close", at = @At("RETURN"))
    private void onGameRendererClose(CallbackInfo ci) {
        BlurredRoundedSurface.close();
        CustomRenderPipeline.closeAll();
        ToolList.getInstance().log.info("XSD Pipeline closed");
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void noHurtcam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if(ConfigManager.noHurtcam.getValue()) ci.cancel();
    }

}