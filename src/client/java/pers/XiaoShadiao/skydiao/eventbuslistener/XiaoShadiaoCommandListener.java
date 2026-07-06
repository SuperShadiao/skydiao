package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XiaoShadiaoCommandListener extends AbstractListener {

    private final Pattern constant = Pattern.compile("(组队|Party) > (\\[M?VI?P\\+{0,2}\\])? ?([\\w]+): !pt(.*)");

    @Override
    public String getListenerName() {
        return "XiaoShadiaoCommandListener";
    }

    @Override
    public void registerListeners() {
        if(ToolList.getInstance().isXiaoShadiao()) {
            ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
            ClientReceiveMessageEvents.GAME.register(this::onChat);
        }
    }

    private void onChat(Component component, boolean b) {
        String msg = ToolList.getInstance().deleteColorCode(component.getString());
        Matcher matcher = constant.matcher(msg);
        if(matcher.find()) {
            String group = matcher.group(3);
            ToolList.sendChatMessage("/p kick " + group);
        }
    }
}
