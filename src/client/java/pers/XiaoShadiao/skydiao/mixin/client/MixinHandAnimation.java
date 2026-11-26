package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class MixinHandAnimation {

    @Inject(method = "applyItemArmTransform", at = @At("RETURN"))
    private void applyCustomItemArmTransform(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
        if(HumanoidArm.RIGHT == arm) {
            poseStack.translate(
                    ConfigManager.handOffsetX.getValue(),
                    ConfigManager.handOffsetY.getValue(),
                    ConfigManager.handOffsetZ.getValue());
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"))
    private void applyCustomItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float attackValue, CallbackInfo ci) {
        if(HumanoidArm.RIGHT == arm) {
            poseStack.translate(
                    ConfigManager.handSwingPointOffsetX.getValue(),
                    ConfigManager.handSwingPointOffsetY.getValue(),
                    ConfigManager.handSwingPointOffsetZ.getValue());
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("RETURN"))
    private void pivotEnd(PoseStack poseStack, HumanoidArm arm, float attackValue, CallbackInfo ci) {
        if (arm == HumanoidArm.RIGHT) {
            poseStack.translate(
                    -ConfigManager.handSwingPointOffsetX.getValue(),
                    -ConfigManager.handSwingPointOffsetY.getValue(),
                    -ConfigManager.handSwingPointOffsetZ.getValue());
        }
    }

}
