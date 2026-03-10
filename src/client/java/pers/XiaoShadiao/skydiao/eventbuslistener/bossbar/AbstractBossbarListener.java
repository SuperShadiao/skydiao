package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar;

import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;

public abstract class AbstractBossbarListener extends AbstractListener implements CustomBossbar.IStarRailBossBar {

    protected final boolean addStarRailNotification(String msg, StarRailNotification.Type type) {
        if(!ConfigManager.bossbar.getValue()) return false;
        XSDHUD.starRailNotification.updateMessage(msg, type);
        return true;
    }

    protected final void setCurrentStarRailBossBar(CustomBossbar.IStarRailBossBar bossbar) {
        XSDHUD.customBossbar.loadStarRailBossBar(bossbar);
    }

}