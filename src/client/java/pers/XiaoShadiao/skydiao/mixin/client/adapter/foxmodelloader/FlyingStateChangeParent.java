package pers.XiaoShadiao.skydiao.mixin.client.adapter.foxmodelloader;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.elfmcys.yesstevemodel.geckolib3.core.EntityFrameStateTracker")
public class FlyingStateChangeParent<T extends Entity> {
    @Shadow
    public T entity;
}
