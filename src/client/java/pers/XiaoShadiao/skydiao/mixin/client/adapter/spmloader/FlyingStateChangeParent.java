package pers.XiaoShadiao.skydiao.mixin.client.adapter.spmloader;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.micaftic.morpher.geckolib3.core.EntityFrameStateTracker")
public class FlyingStateChangeParent<T extends Entity> {
    @Shadow
    public T entity;
}
