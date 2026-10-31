package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinBlockAnimationBowSwing {

    @Invoker("applyItemArmAttackTransform")
    protected abstract void skydiao$applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress);

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    shift = At.Shift.BEFORE))
    private void skydiao$keepSwingWhileUsingBow(AbstractClientPlayer player, float partialTick, float pitch,
                                                InteractionHand hand, float swingProgress, ItemStack stack,
                                                float equipProgress, PoseStack poseStack,
                                                SubmitNodeCollector collector, int light, CallbackInfo callbackInfo) {
        if (!BlockAnimation.active() || player == null) return;
        if (swingProgress <= 0.0F) return;
        if (!BlockAnimation.usingBow(player)) return;
        if (player.getUsedItemHand() != hand) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        skydiao$applyItemArmAttackTransform(poseStack, arm, swingProgress);
    }
}