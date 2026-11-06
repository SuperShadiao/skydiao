package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.services.MinecraftServicesSessionService;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;

import java.util.UUID;

@Mixin(MinecraftServicesSessionService.class)
public class MixinMinecraftServicesSessionService {

    @WrapMethod(method = "joinServer")
    public void joinServer(UUID profileId, String authenticationToken, String serverId, Operation<Void> original) throws AuthenticationException {
        if(!(authenticationToken instanceof String) && ToolList.mc.getUser() instanceof XSDSafeSession xsdSafeSession) {
            xsdSafeSession.joinServer(serverId);
        } else {
            original.call(profileId, authenticationToken, serverId);
        }
    }

}
