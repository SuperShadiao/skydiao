package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionInstance.class)
public interface MixinOptionInstanceAccessor {

    @Accessor("value")
    void skydiao$setRawValue(Object value);
}
