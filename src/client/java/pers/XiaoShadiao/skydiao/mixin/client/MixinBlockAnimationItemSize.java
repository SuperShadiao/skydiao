package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinBlockAnimationItemSize {

    @Unique
    private static final String SKYDIAO$SUBMIT_ITEM =
            "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    @Unique
    private boolean skydiao$sizePushed;

    @Invoker("applyItemArmAttackTransform")
    protected abstract void skydiao$applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress);

    @Inject(method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = SKYDIAO$SUBMIT_ITEM, shift = At.Shift.BEFORE))
    private void skydiao$pushItemSize(PlayerRenderState renderState,
                                      FirstPersonHandsAndItemsRenderState handState,
                                      float partialTick, float pitch, InteractionHand hand,
                                      float swingProgress, ItemStack stack, float equipProgress,
                                      PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      CallbackInfo callbackInfo) {
        skydiao$sizePushed = false;
        if (!BlockAnimation.active()) return;

        float size = BlockAnimation.itemSize();
        if (size != 1.0F) {
            poseStack.pushPose();
            poseStack.scale(size, size, size);
            skydiao$sizePushed = true;
        }

        if (swingProgress > 0.0F && BlockAnimation.usingBow(net.minecraft.client.Minecraft.getInstance().player)) {
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                    ? net.minecraft.client.Minecraft.getInstance().player.getMainArm()
                    : net.minecraft.client.Minecraft.getInstance().player.getMainArm().getOpposite();
            skydiao$applyItemArmAttackTransform(poseStack, arm, swingProgress);
        }
    }

    @Inject(method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = SKYDIAO$SUBMIT_ITEM, shift = At.Shift.AFTER))
    private void skydiao$popItemSize(PlayerRenderState renderState,
                                     FirstPersonHandsAndItemsRenderState handState,
                                     float partialTick, float pitch, InteractionHand hand,
                                     float swingProgress, ItemStack stack, float equipProgress,
                                     PoseStack poseStack, SubmitNodeCollector collector, int light,
                                     CallbackInfo callbackInfo) {
        if (!skydiao$sizePushed) return;
        skydiao$sizePushed = false;
        poseStack.popPose();
    }
}
