package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

@Mixin(targets = "com.micaftic.morpher.client.entity.PlayerEntityFrameState")
public class FlyingStateChange extends FlyingStateChangeParent<Player> {

    @Inject(method = "isFlying", at = @At("HEAD"), cancellable = true)
    public void isFlying(CallbackInfoReturnable<Boolean> cir) {
        if(StatusManager.get().hasStatus()) {
            cir.setReturnValue(entity.getAbilities().flying);
        }
    }

}
