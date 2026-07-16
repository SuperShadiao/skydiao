package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MixinMultiPlayerGameModeDestroyBlockDelayAccessor {

    @Accessor("destroyDelay")
    public int getDestroyDelay();

    @Accessor("destroyDelay")
    public void setDestroyDelay(int destroyDelay);

}
