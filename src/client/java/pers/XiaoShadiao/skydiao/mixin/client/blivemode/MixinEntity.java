package pers.XiaoShadiao.skydiao.mixin.client.blivemode;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.ComponentHelper;

@Mixin(Entity.class)
public class MixinEntity {

    @WrapMethod(method = "getDisplayName")
    public Component getDisplayName(Operation<Component> original) {
        Component call = original.call();
        if(!ConfigManager.blivemodeentityname.getValue()) return call;
        return ComponentHelper.wrapAsSensitive(call, true, false);
    }

}
