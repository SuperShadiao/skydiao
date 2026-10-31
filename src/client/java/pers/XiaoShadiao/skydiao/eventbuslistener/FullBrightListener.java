package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

public class FullBrightListener extends AbstractListener {

    private Double previousGamma;

    @Override
    public String getListenerName() {
        return "FullBrightListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (client.options == null) return;
        if (ConfigManager.fullLight.getValue()) {
            Double current = client.options.gamma().get();
            if (current < 100.0D) {
                if (previousGamma == null) previousGamma = current;
                client.options.gamma().set(100.0D);
            }
        } else if (previousGamma != null) {
            client.options.gamma().set(previousGamma);
            previousGamma = null;
        }
    }
}
