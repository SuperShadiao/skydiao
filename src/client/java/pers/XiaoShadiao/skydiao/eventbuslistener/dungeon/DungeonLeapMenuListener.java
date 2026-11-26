package pers.XiaoShadiao.skydiao.eventbuslistener.dungeon;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.screen.LeapOverlayScreen;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

import java.util.Locale;

/** 把地牢里服务端打开的 Spirit Leap 箱子界面替换成玩家头像菜单。 */
public class DungeonLeapMenuListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "DungeonLeapMenu";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.REPLACE_SCREEN.register(this::replaceScreen);
    }

    private Screen replaceScreen(Screen screen) {
        if (!ConfigManager.dungeonLeapMenu.getValue()) return null;
        if (!(screen instanceof ContainerScreen container)) return null;
        if (!StatusManager.get().isInDungeon()) return null;
        String title = container.getTitle().getString().trim().toLowerCase(Locale.ROOT);
        if (!title.contains("spirit leap")) return null;
        return new LeapOverlayScreen(container.getMenu(), container.getTitle());
    }
}
