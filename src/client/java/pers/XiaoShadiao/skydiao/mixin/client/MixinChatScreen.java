package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.commands.XSDChatCommand;
import pers.XiaoShadiao.skydiao.irc.XSDIRCConfig;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    @WrapMethod(method = "normalizeChatMessage")
    public String sendChatToXSDChat(String string, Operation<String> original) {
        if(XSDChatCommand.inXSDChatChannel && !String.valueOf(string).startsWith("/")) {
            XSDIRCConfig.IRC.trySendMessage(string, true);
            return "";
        }
        return original.call(string);
    }

}
