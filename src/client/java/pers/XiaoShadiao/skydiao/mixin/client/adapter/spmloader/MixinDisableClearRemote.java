package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import com.micaftic.morpher.cloud.client.CloudPlayerModelSync;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

@Mixin(CloudPlayerModelSync.class)
public class MixinDisableClearRemote {

    @Inject(method = "clearRemote", at = @At("HEAD"), cancellable = true)
    private static void clearRemote(Player player, CallbackInfo ci) {
        if(StatusManager.get().hasStatus()) ci.cancel();
    }


}
