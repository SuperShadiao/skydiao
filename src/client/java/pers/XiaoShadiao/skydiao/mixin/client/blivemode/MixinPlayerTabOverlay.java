package pers.XiaoShadiao.skydiao.mixin.client.blivemode;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.ComponentHelper;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerTabOverlay {

    @WrapMethod(method = "getNameForDisplay")
    public Component get(PlayerInfo playerInfo, Operation<Component> original) {
        Component call = original.call(playerInfo);
        if(ConfigManager.blivemodetab.getValue()) call = ComponentHelper.wrapAsSensitive(call, true, false);
        if(ConfigManager.blivemodehideserverid.getValue()) call = ComponentHelper.wrapAsServerIdSpoof(call);
        return call;
    }

}
