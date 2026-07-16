package pers.XiaoShadiao.skydiao.server.mixin.fun;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.server.utils.ToolList;

@Mixin(Warden.class)
public class MixinWarden {

    @WrapMethod(method = "canTargetEntity")
    public boolean canTargetEntity(Entity entity, Operation<Boolean> original) {
        return (original.call(entity) || entity instanceof Warden) && !ToolList.getInstance().isXiaoShadiaoPlayer(entity);
    }

}
