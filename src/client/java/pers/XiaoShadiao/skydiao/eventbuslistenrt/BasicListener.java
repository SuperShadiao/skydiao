package pers.XiaoShadiao.skydiao.eventbuslistenrt;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

import java.util.Arrays;

public class BasicListener extends AbstractListener {

    public long lastOperationTime = System.currentTimeMillis();
    private boolean isAFK = false;

    @Override
    public String getListenerName() {
        return "BasicListener";
    }

    @Override
    protected void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldChange);
        CustomFabricEvents.HYPIXEL_PACKET_EVENT.register(this::onHypixelPacket);
    }

    private void onHypixelPacket(ClientboundHypixelPacket packet) {
        if(packet instanceof ClientboundLocationPacket) StatusManager.updateStatusByHypixelPacket((ClientboundLocationPacket) packet);
    }

    private void onWorldChange(Minecraft mc, ClientLevel clientLevel) {
        ChatClientManager.getChatClient();
    }

    private void onStartClientTick(Minecraft mc) {
        if((!(mc.screen instanceof ChatScreen) || mc.screen.getClass().getName().startsWith("pers.XiaoShadiao")) && Arrays.stream(mc.options.keyMappings).anyMatch(KeyMapping::isDown))  {
            if(isAFK) {
                isAFK = false;
                if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendAFK(false);
            }
            lastOperationTime = System.currentTimeMillis();
        }
        if(System.currentTimeMillis() - lastOperationTime > 120000 && !isAFK) {
            isAFK = true;
            if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendAFK(true);
        }
    }

    public boolean isAFK() {
        return System.currentTimeMillis() - lastOperationTime > 120000;
    }

}
