package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class AutoTipListener extends AbstractListener {

    private int tickCount;
    private boolean autoTipFlag;

    @Override
    public String getListenerName() {
        return "AutoTipListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onChat);
    }

    private boolean onChat(Component component, boolean b) {
        if(autoTipFlag) {
            String string = component.getString();
            if(string.contains("打赏") ||
                    string.contains("tip") ||
                    string.contains("booster") ||
                    string.contains("nline")) {
                autoTipFlag = false;
                return false;
            }
        }
        return true;
    }

    private void onClientTick(Minecraft mc) {
        if(mc.player != null && StatusManager.get().hasStatus()) {
            tickCount++;
            if (tickCount % (20 * 60 * 10) == 0) {
                autoTipFlag = true;
                ToolList.sendChatMessage("/tip all");
                DynamicSpotHUD.replaceIdlePart(
                        DynamicSpotHUD.TPS_PART_ID,
                        DynamicSpotHUD.iconPart(Identifier.fromNamespaceAndPath("skydiao", "autotip"), DynamicSpotHUD.statusIcon("refresh"), 0xFFFFFFFF, DynamicSpotHUD.textPart(null, "AutoTip Triggered", 0xFFFFFFFF, null)),
                        3000);
            }
        }
    }

}
