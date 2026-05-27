package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class DungeonMiscMessageListener extends AbstractListener implements IDungeonListener {

    private long dungeonStartTime = -1;

    @Override
    public String getListenerName() {
        return "DungeonMiscMessageListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onUnload);
    }

    private void onClientStartTick(Minecraft mc) {
        if(!StatusManager.get().isInDungeon()) return;
        if(mc.player == null) return;

        // System.out.println(mc.player.getInventory().getItem(8).getItem());
        if(dungeonStartTime == -1 && mc.player.getInventory().getItem(8).getItem() == Items.FILLED_MAP) {
            dungeonStartTime = System.currentTimeMillis();
        }
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        dungeonStartTime = -1;
    }

    private void onChat(Component component, boolean b) {
        if(!StatusManager.get().isInDungeon()) return;

        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if(dungeonStartTime != -1 && msg.equals("A shiver runs down your spine...")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonBloodRoomTime.getValue().replace("[time]", String.valueOf((System.currentTimeMillis() - dungeonStartTime) / 1000f)));
        } else if(msg.equals("You can no longer consume or splash any potions during the remainder of this Dungeon run!")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonDrinkPotion.getValue());
        }
        // sendDungeonF7ChatMessage(ConfigManager.dungeonBloodRoomTime.getValue());;
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
