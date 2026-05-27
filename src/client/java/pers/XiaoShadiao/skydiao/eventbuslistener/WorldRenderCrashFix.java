package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.rendering.world.WorldRenderContextImpl;
import pers.XiaoShadiao.skydiao.utils.ClientRenderCrashFixer;

public class WorldRenderCrashFix extends AbstractListener {
    @Override
    public String getListenerName() {
        return "WorldRenderCrashFix";
    }

    @Override
    public void registerListeners() {
        WorldRenderEvents.START_MAIN.register((context) -> {
            if(context instanceof WorldRenderContextImpl w) ClientRenderCrashFixer.wrc = w;
        });
    }
}
