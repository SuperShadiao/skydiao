package pers.XiaoShadiao.skydiao.server.mixin.fun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.server.customfabricevents.CustomFabricEvents;

@Mixin(LivingEntity.class)
public class MixinHurtEntityServer {

    @Inject(method = "hurtServer", at = @At(value = "HEAD"), cancellable = true)
    public void preHurtServer(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        boolean cancel = CustomFabricEvents.ON_HURT_SERVER_PRE.invoker().onHurt((LivingEntity) (Object) this, level, source, damage);
        if(cancel) cir.setReturnValue(false);
    }

    @Inject(method = "hurtServer", at = @At(value = "RETURN"))
    public void postHurtServer(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        CustomFabricEvents.ON_HURT_SERVER_POST.invoker().onHurt((LivingEntity) (Object) this, level, source, damage);
    }

}
