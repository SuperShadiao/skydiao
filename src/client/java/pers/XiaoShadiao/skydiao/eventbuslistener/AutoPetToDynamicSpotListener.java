package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class AutoPetToDynamicSpotListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoPetToDynamicSpotListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        String message = component.getString();

        // §cAutopet §eequipped your §7[Lvl 100] §dGuardian§e! §a§lVIEW RULE
        String pet = ToolList.getInstance().getMidOfText(message, "§cAutopet §eequipped your ", "§e!");
        if (pet.isBlank()) {
            return;
        }

        DynamicSpotHUD.replaceIdlePart(
                DynamicSpotHUD.USERNAME_PART_ID,
                DynamicSpotHUD.iconPart(null, DynamicSpotHUD.statusIcon("autopet"), 0xFFFFFFFF, DynamicSpotHUD.textPart(null, "§cAutopet §r➜ " + pet, 0xFFFFFFFF, null)),
                5000L
        );
    }

}
