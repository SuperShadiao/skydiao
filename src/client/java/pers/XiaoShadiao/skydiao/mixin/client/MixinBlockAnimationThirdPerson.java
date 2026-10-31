package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(PlayerItemInHandLayer.class)
public abstract class MixinBlockAnimationThirdPerson {


    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void skydiao$renderThirdPersonBlock(AvatarRenderState state, ItemStackRenderState itemState,
                                                ItemStack stack, HumanoidArm arm, PoseStack poseStack,
                                                SubmitNodeCollector collector, int light,
                                                CallbackInfo callbackInfo) {
        if (!BlockAnimation.active()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || state.id != client.player.getId()) return;
        if (BlockAnimation.usingConsumable(client.player)) return;
        if (!BlockAnimation.blocking(client.player)) return;

        InteractionHand hand = arm == state.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (BlockAnimation.blockingHand(client.player) != hand) return;

        boolean leftHand = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
                Object parent = ((net.minecraft.client.renderer.entity.layers.RenderLayer<?, ?>) (Object) this).getParentModel();
        ((ArmedModel<AvatarRenderState>) parent).translateToHand(state, arm, poseStack);
        applyLegacyTransform(poseStack, leftHand);
        revertItemTransform(itemState, leftHand, poseStack);
        itemState.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        callbackInfo.cancel();
    }

    private static void applyLegacyTransform(PoseStack poseStack, boolean leftHand) {
        poseStack.translate((leftHand ? 1.0F : -1.0F) / 16.0F, 0.4375F, 0.0625F);
        poseStack.translate(leftHand ? -0.035F : 0.05F, leftHand ? 0.045F : 0.0F, leftHand ? -0.135F : -0.1F);
        poseStack.rotateDegrees(Axis.YP, (leftHand ? -1.0F : 1.0F) * -50.0F);
        poseStack.rotateDegrees(Axis.XP, -10.0F);
        poseStack.rotateDegrees(Axis.ZP, (leftHand ? -1.0F : 1.0F) * -60.0F);
        poseStack.translate(0.0F, 0.1875F, 0.0F);

        poseStack.scale(0.625F, 0.625F, 0.625F);
        poseStack.rotateDegrees(Axis.XP, 180.0F);
        poseStack.rotateDegrees(Axis.XN, -100.0F);
        poseStack.rotateDegrees(Axis.YN, leftHand ? 35.0F : 45.0F);
        poseStack.translate(0.0F, -0.3F, 0.0F);
        poseStack.scale(1.5F, 1.5F, 1.5F);
        poseStack.rotateDegrees(Axis.YN, 50.0F);
        poseStack.rotateDegrees(Axis.ZP, 335.0F);
        poseStack.translate(-0.9375F, -0.0625F, 0.0F);
        poseStack.translate(0.5F, 0.5F, 0.25F);
        poseStack.rotateDegrees(Axis.YN, 180.0F);
        poseStack.translate(0.0F, 0.0F, 0.28125F);
    }

    private static void revertItemTransform(ItemStackRenderState itemState, boolean leftHand, PoseStack poseStack) {
        ItemStackRenderState.LayerRenderState layer =
                ((MixinBlockAnimationAccessors.StackState) (Object) itemState).skydiao$firstLayer();
        if (layer == null) return;
        ItemTransform transform = ((MixinBlockAnimationAccessors.LayerState) layer).skydiao$itemTransform();
        if (transform == null) return;
        float angleX = transform.rotation().x();
        float angleY = leftHand ? -transform.rotation().y() : transform.rotation().y();
        float angleZ = leftHand ? -transform.rotation().z() : transform.rotation().z();
        Quaternionf quaternion = new Quaternionf().rotationXYZ(angleX * 0.017453292F,
                angleY * 0.017453292F, angleZ * 0.017453292F);
        quaternion.conjugate();
        poseStack.scale(1.0F / transform.scale().x(), 1.0F / transform.scale().y(),
                1.0F / transform.scale().z());
        poseStack.rotate(quaternion);
        poseStack.translate((leftHand ? -1.0F : 1.0F) * -transform.translation().x(),
                -transform.translation().y(), -transform.translation().z());
    }
}