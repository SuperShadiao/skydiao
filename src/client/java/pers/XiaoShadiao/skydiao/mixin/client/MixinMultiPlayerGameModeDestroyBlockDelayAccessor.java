package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public interface MixinMultiPlayerGameModeDestroyBlockDelayAccessor {

    @Accessor("destroyDelay")
    public int getDestroyDelay();

    @Accessor("destroyDelay")
    public void setDestroyDelay(int destroyDelay);

}
