package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.ComponentHelper;

public class BliveModeChatHandler extends AbstractListener {
    @Override
    public String getListenerName() {
        return "BliveModeChatHandler";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.MODIFY_GAME.register(this::modify);
    }

    private Component modify(Component component, boolean b) {
        if(!ConfigManager.blivemodechat.getValue()) return component;
        return ComponentHelper.wrapAsSensitive(component, true, true);
    }
}
