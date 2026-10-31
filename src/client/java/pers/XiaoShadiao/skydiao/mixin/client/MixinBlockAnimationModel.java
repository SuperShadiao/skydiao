package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(HumanoidModel.class)
public abstract class MixinBlockAnimationModel {

    @Shadow
    public ModelPart rightArm;

    @Shadow
    public ModelPart leftArm;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void skydiao$liftBlockingArm(HumanoidRenderState state, CallbackInfo callbackInfo) {
        if (!BlockAnimation.active()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!(state instanceof AvatarRenderState avatar) || avatar.id != client.player.getId()) return;
        if (BlockAnimation.usingConsumable(client.player)) return;
        if (!BlockAnimation.blocking(client.player)) return;

        HumanoidArm arm = BlockAnimation.blockingArm(client.player, avatar.mainArm);
        ModelPart part = arm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
        part.xRot -= (float) (Math.PI * 2.0F / 10.0F);
    }
}