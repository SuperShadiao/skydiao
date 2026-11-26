package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import pers.XiaoShadiao.skydiao.utils.PageSwitchCallback;

import java.util.ArrayList;
import java.util.List;

public class SyncScreenListener extends AbstractListener {

    private final List<PageSwitchCallback> screenListeners = new ArrayList<>();

    @Override
    public String getListenerName() {
        return "SyncScreenListener";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::afterInit);
    }

    private void afterInit(Minecraft minecraft, Screen screen, int i, int i1) {
        synchronized (screenListeners) {
            screenListeners.forEach(pageSwitchCallback -> pageSwitchCallback.setScreen(screen));
            screenListeners.clear();
        }
    }

    public PageSwitchCallback createScreenListener() {
        PageSwitchCallback pageSwitchCallback = new PageSwitchCallback();
        synchronized (screenListeners) {
            screenListeners.add(pageSwitchCallback);
        }
        return pageSwitchCallback;
    }

}
