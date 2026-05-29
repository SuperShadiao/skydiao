package pers.XiaoShadiao.skydiao.eventbuslistener;

import pers.XiaoShadiao.skydiao.utils.StatusManager;

public abstract class AbstractFishingListener extends AbstractListener {

    public boolean isInLotusAtoll() {
        return "lotus_atoll".equals(StatusManager.get().getMode());
    }

    public boolean isInBayou() {
        return "fishing_1".equals(StatusManager.get().getMode());
    }

    public boolean isInWaterFishingArea() {
        return isInLotusAtoll() || isInBayou();
    }

}
