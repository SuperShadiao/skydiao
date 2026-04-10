package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameModeDestroyBlockDelayFix {

    @Shadow
    private int destroyDelay;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(at = @At("HEAD"), method = "startDestroyBlock", cancellable = true)
    public void startDestroyBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if(!this.minecraft.player.getAbilities().instabuild && destroyDelay > 1) {
            cir.setReturnValue(false);
        }
    }

}
