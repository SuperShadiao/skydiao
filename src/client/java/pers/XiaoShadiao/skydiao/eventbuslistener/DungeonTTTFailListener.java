package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.Banned;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class DungeonTTTFailListener extends AbstractListener {
    @Override
    public String getListenerName() {
        return "DungeonTTTFailListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        if (("PUZZLE FAIL! " + mc.getUser().getName() + " lost Tic Tac Toe! Yikes!").equals(ToolList.getInstance().deleteColorCode(component.getString()))) {
            Banned.ban(Banned.BanReason.LOSTTTT, Banned.BanTime.PERMANENT, 2000);
        }
    }
}
