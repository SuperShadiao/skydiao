package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FeatureRenderDispatcher.class)
public interface MixinFeatureRenderDispatcherAccessor {

    @Accessor("preparedFrame")
    FeatureRenderDispatcher.PreparedFrame skydiao$getPreparedFrame();
}
