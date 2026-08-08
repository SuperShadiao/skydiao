package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class ResurrectionItemListener extends AbstractListener implements IDungeonListener {

    private int spiritCD = 0;
    private int bonzoCD = 0;
    private int phoenixCD = 0;

    @Override
    public String getListenerName() {
        return "ResurrectionItemListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register((client) -> {
            if (!StatusManager.get().isInDungeon()) return;
            if (spiritCD > 0) spiritCD--;
            if (bonzoCD > 0) bonzoCD--;
            if (phoenixCD > 0) phoenixCD--;
        });
    }

    private void onChat(Component component, boolean b) {
        if (!StatusManager.get().isInDungeon()) return;

        String msg = ToolList.getInstance().deleteColorCode(component.getString());

        if (msg.matches("^Second Wind Activated! Your Spirit Mask saved your life!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonSpiritMaskTriggered.getValue());
            if (!displayTitleStarRail("Spirit复活甲已触发!")) displayTitle("Spirit");
            spiritCD = 30 * 20;
        } else if (msg.matches("^Your (?:. )?Bonzo's Mask saved your life!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonBonzoTriggered.getValue());
            if (!displayTitleStarRail("Bonzo复活甲已触发!")) displayTitle("Bonzo");
            bonzoCD = 300 * 20;
        } else if (msg.matches("^Your Phoenix Pet saved you from certain death!$")) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonPhoenixTriggered.getValue());
            if (!displayTitleStarRail("Phoenix复活甲已触发!")) displayTitle("Phoenix");
            phoenixCD = 60 * 20;
        }
    }

    private void displayTitle(String title) {
        XSDHUD.bigTitle.updateTitleMsg(title, 1000);
    }

    private boolean displayTitleStarRail(String title) {
        return addStarRailNotification(title, StarRailNotification.Type.warning);
    }

    @Override
    public int getFloor() {
        return 7;
    }

    public int getSpiritCD() {
        return spiritCD;
    }

    public int getBonzoCD() {
        return bonzoCD;
    }

    public int getPhoenixCD() {
        return phoenixCD;
    }

}
