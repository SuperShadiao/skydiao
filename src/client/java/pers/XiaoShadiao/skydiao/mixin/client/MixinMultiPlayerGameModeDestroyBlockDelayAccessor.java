package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MixinMultiPlayerGameModeDestroyBlockDelayAccessor {

    @Accessor("destroyDelay")
    public int getDestroyDelay();

    @Accessor("destroyDelay")
    public void setDestroyDelay(int destroyDelay);

    @Accessor("destroyBlockPos")
    public BlockPos getDestroyBlockPos();

    @Accessor("destroyBlockPos")
    public void setDestroyBlockPos(BlockPos destroyBlockPos);

}
