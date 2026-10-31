package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.mixin.client.MixinOptionInstanceAccessor;

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
                ((MixinOptionInstanceAccessor) (Object) client.options.gamma()).skydiao$setRawValue(100.0D);
            }
        } else if (previousGamma != null) {
            ((MixinOptionInstanceAccessor) (Object) client.options.gamma()).skydiao$setRawValue(previousGamma);
            previousGamma = null;
        }
    }
}
