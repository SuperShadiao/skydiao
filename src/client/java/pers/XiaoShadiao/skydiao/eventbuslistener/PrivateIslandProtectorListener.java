package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Optional;

public class PrivateIslandProtectorListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "PrivateIslandProtectorListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseButton);
    }

    private boolean onMouseButton(long l, MouseButtonInfo mouseButtonInfo, int i) {
        int button = mouseButtonInfo.button();
        if(ConfigManager.skyblockSafeIsland.getValue() && i == 1 && button == 1 && Optional.ofNullable(mc.player).map(Player::getMainHandItem).map(a -> (a.getItem().getDescriptionId().endsWith("pickaxe") || a.getDisplayName().getString().contains("Drill")) && !ToolList.getInstance().deleteColorCode(a.getHoverName().getString()).equals("Dungeonbreaker")).orElse(false)) {
            StatusManager statusManager = StatusManager.get();
            if("dynamic".equals(statusManager.getMode())) {
               ToolList.printChatMessage(Component.literal("§a[小沙雕] §c已阻止你在私人岛屿中使用镐子技能"));
                return true;
            }
        }
        return false;
    }

}
