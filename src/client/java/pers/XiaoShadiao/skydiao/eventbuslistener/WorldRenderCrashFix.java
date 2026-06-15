package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.level.LevelRenderContextImpl;
import pers.XiaoShadiao.skydiao.utils.ClientRenderCrashFixer;

public class WorldRenderCrashFix extends AbstractListener {
    @Override
    public String getListenerName() {
        return "WorldRenderCrashFix";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.START_MAIN.register((context) -> {
            if(context instanceof LevelRenderContextImpl w) ClientRenderCrashFixer.wrc = w;
        });
    }
}
