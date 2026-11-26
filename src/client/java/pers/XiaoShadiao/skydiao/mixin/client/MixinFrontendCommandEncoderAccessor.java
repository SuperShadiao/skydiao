package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FrontendCommandEncoder.class)
public interface MixinFrontendCommandEncoderAccessor {

    @Accessor("isInRenderPass")
    boolean skydiao$isInRenderPass();

    @Accessor("isInRenderPass")
    void skydiao$setInRenderPass(boolean value);

    @Invoker("submitRenderPass")
    void skydiao$submitRenderPass();
}
