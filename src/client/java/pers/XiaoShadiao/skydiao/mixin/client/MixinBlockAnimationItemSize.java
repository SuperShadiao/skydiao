package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinBlockAnimationItemSize {

    @Unique
    private static final String SKYDIAO$RENDER_ITEM =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Unique
    private boolean skydiao$sizePushed;

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = SKYDIAO$RENDER_ITEM, shift = At.Shift.BEFORE))
    private void skydiao$pushItemSize(AbstractClientPlayer player, float partialTick, float pitch,
                                      InteractionHand hand, float swingProgress, ItemStack stack,
                                      float equipProgress, PoseStack poseStack,
                                      SubmitNodeCollector collector, int light, CallbackInfo callbackInfo) {
        skydiao$sizePushed = false;
        if (!BlockAnimation.active()) return;
        float size = BlockAnimation.itemSize();
        if (size == 1.0F) return;
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        skydiao$sizePushed = true;
    }

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = SKYDIAO$RENDER_ITEM, shift = At.Shift.AFTER))
    private void skydiao$popItemSize(AbstractClientPlayer player, float partialTick, float pitch,
                                     InteractionHand hand, float swingProgress, ItemStack stack,
                                     float equipProgress, PoseStack poseStack,
                                     SubmitNodeCollector collector, int light, CallbackInfo callbackInfo) {
        if (!skydiao$sizePushed) return;
        skydiao$sizePushed = false;
        poseStack.popPose();
    }
}