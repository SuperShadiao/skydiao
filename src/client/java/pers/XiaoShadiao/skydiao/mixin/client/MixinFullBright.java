package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinFullBright {

    @Unique
    private static final OptionInstance<@NotNull Double> FULL_BRIGHT = new OptionInstance<>("", null, null, null, null, 15d, null);

    @WrapOperation(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;"))
    public OptionInstance<@NotNull Double> wrapGamma(Options instance, Operation<OptionInstance<@NotNull Double>> original) {
        if (!ConfigManager.fullBright.getValue()) {
            return original.call(instance);
        }
        return FULL_BRIGHT;
    }

}
