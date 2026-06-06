package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FishingHook.class)
public interface MixinFishHookEntityHookedAccessor {

    @Accessor("hookedIn")
    public void setHookedEntity0(Entity entity);

}
