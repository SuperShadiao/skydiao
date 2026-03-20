package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.AbstractBossbarListener;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public abstract class AbstractDungeonBossbar extends AbstractBossbarListener {

    public abstract int getFloor();

    public static boolean isInMasterDungeonFloor() {
        return (StatusManager.get().isInDungeon() && ToolList.getInstance().fetchScoreboardLinesNoColor().stream().anyMatch(line -> line.contains("The Catacombs (M")));
    }

    public boolean isInCorrectDungeon() {
        return (StatusManager.get().isInDungeon() && ToolList.getInstance().fetchScoreboardLinesNoColor().stream()
                .anyMatch(line -> line.contains("The Catacombs (M" + getFloor()) || line.contains("The Catacombs (F" + getFloor())));
    }

}
