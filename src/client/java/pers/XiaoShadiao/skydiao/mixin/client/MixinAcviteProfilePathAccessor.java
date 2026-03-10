package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.util.profiling.ActiveProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ActiveProfiler.class)
public interface MixinAcviteProfilePathAccessor {

    @Accessor("path")
    public String getPath();

}
