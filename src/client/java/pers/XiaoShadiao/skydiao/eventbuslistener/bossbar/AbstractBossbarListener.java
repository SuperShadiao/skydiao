package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar;

import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public abstract class AbstractBossbarListener extends AbstractListener implements CustomBossbar.IStarRailBossBar {

    protected final boolean addStarRailNotification(String msg, StarRailNotification.Type type) {
        if(!ConfigManager.bossbar.getValue()) return false;
        XSDHUD.starRailNotification.updateMessage(msg, type);
        return true;
    }

    protected final void setCurrentStarRailBossBar(CustomBossbar.IStarRailBossBar bossbar) {
        XSDHUD.customBossbar.loadStarRailBossBar(bossbar);
    }

    public static boolean isInMasterDungeonFloor() {
        return (StatusManager.get().isInDungeon() && ToolList.getInstance().fetchScoreboardLinesNoColor().stream().anyMatch(line -> line.contains("The Catacombs (M")));
    }

}