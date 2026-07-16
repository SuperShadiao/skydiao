package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar;

import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;

public abstract class AbstractBossbarListener extends AbstractListener implements CustomBossbar.IStarRailBossBar {

    protected final void setCurrentStarRailBossBar(CustomBossbar.IStarRailBossBar bossbar) {
        XSDHUD.customBossbar.loadStarRailBossBar(bossbar);
    }

}