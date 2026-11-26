package pers.XiaoShadiao.skydiao.eventbuslistener.mining;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

public class ShaftHOTMSwitchListener extends AbstractMiningListener {

    private boolean isInShaft;

    @Override
    public String getListenerName() {
        return "ShaftHOTMSwitchListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        boolean temp = isInMineshaft();
        if(temp != isInShaft) {
            isInShaft = temp;
            delayTickExecutor.delayExec(() -> {
                int index;
                if (temp) {
                    index = ConfigManager.mineshaftEnterLoadout.getValue();
                } else {
                    index = ConfigManager.mineshaftExitLoadout.getValue();
                }
                if (index > 0) autoLoadoutListener.switchLoadout(index, null);
            }, 20);
        }
    }

}
