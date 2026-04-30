package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class ResurrectionItemListener extends AbstractListener implements IDungeonListener {

    @Override
    public String getListenerName() {
        return "ResurrectionItemListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        if(!StatusManager.get().isInDungeon()) return;

        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if(msg.matches("^Second Wind Activated! Your Spirit Mask saved your life!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonSpiritMaskTriggered.getValue());
        } else if(msg.matches("^Your (?:. )?Bonzo's Mask saved your life!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonBonzoTriggered.getValue());
        } else if(msg.matches("^Your Phoenix Pet saved you from certain death!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonPhoenixTriggered.getValue());
        }
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
