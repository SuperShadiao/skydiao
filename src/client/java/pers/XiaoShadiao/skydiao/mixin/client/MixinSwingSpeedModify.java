package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

@Mixin(LivingEntity.class)
public class MixinSwingSpeedModify {

    @WrapOperation(method = "getModifiedSwingDuration", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/SwingAnimation;duration()I"))
    private int modifySwingDuration(SwingAnimation instance, Operation<Integer> original) {
        return (int) Math.min(original.call(instance) / ConfigManager.handSwingSpeedMultiplier.getValue(), 100);
    }

}
