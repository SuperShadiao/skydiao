package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class AutoUseFireVeilWandListener extends AbstractListener {

    private int cooldownTick = 0;
    private boolean autoUse = false;

    @Override
    public String getListenerName() {
        return "AutoUseFireVeilWandListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        cooldownTick = 0;
        autoUse = false;
    }

    private void onStartClientTick(Minecraft mc) {

        while(KeyBindsManager.useFireVeilWandItem.consumeClick()) {
            useWand();
        }
        while (KeyBindsManager.toggleAutoUseFireVeilWand.consumeClick()) {
            switchAutoMode();
        }

        if(cooldownTick > 0) {
            cooldownTick--;
            return;
        }

        if(autoUse) {
            useWand();
            cooldownTick = 100;
        }

    }

    public void useWand() {
        autoUseItemListener.startUseItem("FIRE_VEIL_WAND");
    }

    public void switchAutoMode() {
        autoUse = !autoUse;
        ToolList.printChatMessage(Component.literal("§a[小沙雕] " + (autoUse ? "已开启自动使用Fire Veil Wand" : "§c已关闭自动使用Fire Veil Wand")));
    }

}
