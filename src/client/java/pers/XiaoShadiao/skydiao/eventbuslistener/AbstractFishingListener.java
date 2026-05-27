package pers.XiaoShadiao.skydiao.eventbuslistener;

import pers.XiaoShadiao.skydiao.utils.StatusManager;

public abstract class AbstractFishingListener extends AbstractListener {

    public boolean isInLotusAtoll() {
        return "lotus_atoll".equals(StatusManager.get().getMode());
    }

}
