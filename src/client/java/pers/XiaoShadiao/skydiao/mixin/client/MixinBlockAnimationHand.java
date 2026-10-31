package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinBlockAnimationHand {

    @Invoker("applyItemArmTransform")
    protected abstract void skydiao$applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress);

    @Invoker("applyItemArmAttackTransform")
    protected abstract void skydiao$applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void skydiao$renderBlockingSword(AbstractClientPlayer player, float partialTick, float pitch,
                                             InteractionHand hand, float swingProgress, ItemStack stack,
                                             float equipProgress, PoseStack poseStack,
                                             SubmitNodeCollector collector, int light, CallbackInfo callbackInfo) {
        if (!BlockAnimation.active() || player == null) return;
        if (!BlockAnimation.blocking(player)) return;
        if (BlockAnimation.usingConsumable(player)) return;
        if (BlockAnimation.blockingHand(player) != hand) return;
        if (!BlockAnimation.isSword(stack)) return;

        float size = BlockAnimation.itemSize();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;

        poseStack.pushPose();
        poseStack.scale(size, size, size);
        skydiao$applyItemArmTransform(poseStack, arm, equipProgress);
        skydiao$applyItemArmAttackTransform(poseStack, arm, swingProgress);
        poseStack.translate(direction * -0.14142136F, 0.08F, 0.14142136F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 13.365F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 78.05F));
        ((ItemInHandRenderer) (Object) this).renderItem(player, stack,
                arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                poseStack, collector, light);
        poseStack.popPose();
        callbackInfo.cancel();
    }
}