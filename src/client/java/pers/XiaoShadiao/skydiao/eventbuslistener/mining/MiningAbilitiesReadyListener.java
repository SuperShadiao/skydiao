package pers.XiaoShadiao.skydiao.eventbuslistener.mining;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiningAbilitiesReadyListener extends AbstractMiningListener {

    @Override
    public String getListenerName() {
        return "MiningAbilitiesReadyListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onMessage);
    }

    private final Pattern pattern = Pattern.compile("(.*) is now available!");

    private void onMessage(Component component, boolean b) {
        if(ConfigManager.miningAbilitiesAvaliableTitle.getValue() && isInMiningIsland()) {
            Matcher matcher = pattern.matcher(ToolList.getInstance().deleteColorCode(component.getString()));
            if (matcher.find()) {
                XSDHUD.bigTitle.updateTitleMsg("§6" + matcher.group(1) + "§a技能就绪!", 3000);
            }
        }
    }

}
