package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class AutoPickupPhoneRingListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoPickupPhoneRingListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        if(!ConfigManager.autoPickupPhoneRing.getValue()) return;

        String msg = ToolList.getInstance().deleteColorCode(component.getString());
        if("✆ RING... RING... RING...  [PICK UP]".equals(msg)) {
            String possibleCommand = null;

            for (Component component1 : component.toFlatList()) {
                ClickEvent clickEvent = component1.getStyle().getClickEvent();
                if (clickEvent instanceof ClickEvent.RunCommand(String command)) {
                    if(command.startsWith("/cb")) possibleCommand = command;
                }
            }

            if(possibleCommand != null) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] 自动接听骚扰电话..."));
                ToolList.sendChatMessage(possibleCommand);
            }
        }
    }

}
