package pers.XiaoShadiao.skydiao.eventbuslistener;

import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

public class DungeonTrashTPSListener extends AbstractListener implements IDungeonListener {

    @Override
    public String getListenerName() {
        return "DungeonTrashTPSListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.ON_TPS_UPDATE.register(this::tps);
    }

    private void tps(double tps, String formattedTPS) {
        if(!StatusManager.get().isInDungeon()) return;

        if(tps <= 10) sendDungeonF7ChatMessage(ConfigManager.dungeonTrashTPS.getValue().replace("[tps]", formattedTPS));
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
