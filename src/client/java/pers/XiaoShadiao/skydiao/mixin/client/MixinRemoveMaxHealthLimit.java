package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(RangedAttribute.class)
public class MixinRemoveMaxHealthLimit {

    @Final
    @Shadow
    private double minValue;

    @Inject(at = @At("HEAD"), method = "getMaxValue", cancellable = true)
    public void getMaxValue(CallbackInfoReturnable<Double> cir) {
        if(Objects.equals((RangedAttribute) (Object) this, Attributes.MAX_HEALTH.value())) {
            cir.setReturnValue(Double.MAX_VALUE);
        }
    }

    @Inject(at = @At("HEAD"), method = "sanitizeValue", cancellable = true)
    public void sanitizeValue(double d, CallbackInfoReturnable<Double> cir) {
        if(Objects.equals((RangedAttribute) (Object) this, Attributes.MAX_HEALTH.value())) {
            cir.setReturnValue(Double.isNaN(d) ? this.minValue : Mth.clamp(d, this.minValue, Double.MAX_VALUE));
        }
    }

}
