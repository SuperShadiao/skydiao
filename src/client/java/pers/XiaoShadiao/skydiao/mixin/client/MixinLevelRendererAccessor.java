package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface MixinLevelRendererAccessor {

    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher skydiao$getFeatureRenderDispatcher();
}
