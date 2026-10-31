package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinBlockAnimationHand {

    @Invoker("applyItemArmTransform")
    protected abstract void skydiao$applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress);

    @Invoker("applyItemArmAttackTransform")
    protected abstract void skydiao$applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress);

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void skydiao$renderBlockingSword(PlayerRenderState renderState,
                                             FirstPersonHandsAndItemsRenderState handState,
                                             float partialTick, float pitch, InteractionHand hand,
                                             float swingProgress, ItemStack stack, float equipProgress,
                                             PoseStack poseStack, SubmitNodeCollector collector, int light,
                                             CallbackInfo callbackInfo) {
        if (!BlockAnimation.active()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BlockAnimation.blocking(client.player)) return;
        if (BlockAnimation.usingConsumable(client.player)) return;
        if (BlockAnimation.blockingHand(client.player) != hand) return;
        if (!BlockAnimation.isSword(stack)) return;

        float size = BlockAnimation.itemSize();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? client.player.getMainArm() : client.player.getMainArm().getOpposite();
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        poseStack.pushPose();
        poseStack.scale(size, size, size);
        skydiao$applyItemArmTransform(poseStack, arm, equipProgress);
        skydiao$applyItemArmAttackTransform(poseStack, arm, swingProgress);
        poseStack.translate(direction * -0.14142136F, 0.08F, 0.14142136F);
        poseStack.rotateDegrees(Axis.XP, -102.25F);
        poseStack.rotateDegrees(Axis.YP, direction * 13.365F);
        poseStack.rotateDegrees(Axis.ZP, direction * 78.05F);

        ItemStackRenderState itemState = hand == InteractionHand.MAIN_HAND
                ? handState.mainHandRenderState : handState.offHandRenderState;
        itemState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        callbackInfo.cancel();
    }
}
