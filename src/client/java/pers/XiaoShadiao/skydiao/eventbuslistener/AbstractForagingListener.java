package pers.XiaoShadiao.skydiao.eventbuslistener;

import pers.XiaoShadiao.skydiao.utils.StatusManager;

public abstract class AbstractForagingListener extends AbstractListener {

    public boolean isInGalgame() {
        return "foraging_2".equals(StatusManager.get().getMode());
    }

    public boolean isInTorrhusCanyon() {
        return "foraging_3".equals(StatusManager.get().getMode());
    }

    public boolean isInSafari() {
        return StatusManager.get().isInSafari();
    }

    public boolean isInForagingOrSafariArea() {
        return isInGalgame() || isInTorrhusCanyon() || isInSafari();
    }

    public boolean isInForagingArea() {
        return isInGalgame() || isInTorrhusCanyon();
    }

}
