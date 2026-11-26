package pers.XiaoShadiao.skydiao.eventbuslistener.mining;

import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

public abstract class AbstractMiningListener extends AbstractListener {

    public boolean isInCrystalHollows() {
        return "crystal_hollows".equals(StatusManager.get().getMode());
    }

    public boolean isInMineshaft() {
        return "mineshaft".equals(StatusManager.get().getMode());
    }

    public boolean isInMines() {
        return "mining_3".equals(StatusManager.get().getMode());
    }

    public boolean isInMiningIsland() {
        return isInMines() || isInCrystalHollows() || isInMineshaft();
    }

}
