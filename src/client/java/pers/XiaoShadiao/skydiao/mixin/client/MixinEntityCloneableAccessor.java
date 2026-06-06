package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface MixinEntityCloneableAccessor extends Cloneable {

    @Invoker("clone")
    public Entity clone();

}
