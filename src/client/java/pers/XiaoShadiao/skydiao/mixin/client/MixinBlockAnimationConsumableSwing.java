package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.BlockAnimation;

@Mixin(Minecraft.class)
public abstract class MixinBlockAnimationConsumableSwing {

    @Shadow
    @Final
    public Options options;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "tick", at = @At("TAIL"))
    private void skydiao$swingWhileEating(CallbackInfo callbackInfo) {
        if (!BlockAnimation.active() || player == null) return;
        if (!BlockAnimation.usingConsumable(player)) return;
        if (!options.keyAttack.isDown()) return;
        if (player.isSwinging()) return;
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
    }
}
