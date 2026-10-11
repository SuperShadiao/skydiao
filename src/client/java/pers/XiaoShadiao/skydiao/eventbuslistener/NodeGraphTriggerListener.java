package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.NodeGraphManager;
import pers.XiaoShadiao.skydiao.utils.nodegraphexecutor.node.impl.mc.OnChatMessageNode;

import java.util.Map;

public class NodeGraphTriggerListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "NodeGraphTriggerListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        if(!b) {
            NodeGraphManager.INSTANCE.triggerAllStartNode(
                    OnChatMessageNode.FACTORY.getType(),
                    Map.of(OnChatMessageNode.PARAM_MSG, component.getString()));
        }
    }

    // /src/client/java/pers/XiaoShadiao/skydiao/utils/nodegraphexecutor/node/skydiao_extensions.json
    private void onStartTick(Minecraft mc) {
        NodeGraphManager.INSTANCE.triggerAllStartNode("on_tick_start");
    }

}
