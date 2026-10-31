package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.LegacyChat;

@Mixin(ChatListener.class)
public class MixinChatListenerIrc {

    @Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
    private void skydiao$keepIrcColors(Component message, boolean overlay, CallbackInfo callbackInfo) {
        if (message == null) return;
        String text = message.getString();
        if (!text.startsWith("[XSDChat]")) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null || client.gui.hud == null) return;
        client.gui.hud.getChat().addClientSystemMessage(LegacyChat.parse(message));
        callbackInfo.cancel();
    }
}
