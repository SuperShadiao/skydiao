package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class AutoClickerListener extends AbstractListener {

    private boolean leftAutoClickingEnabled = false;
    private boolean rightAutoClickingEnabled = false;

    private boolean isLeftAutoClicking = false;
    private boolean isRightAutoClicking = false;

    private int leftClickDelay = 0;
    private int rightClickDelay = 0;

    @Override
    public String getListenerName() {
        return "AutoClickerListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
    }

    private boolean onMouseClick(long windows, MouseButtonInfo mouseButtonInfo, int state) {
        if(mc.screen == null) {
            boolean flag = false;
            if (rightAutoClickingEnabled) {
                if (mouseButtonInfo.button() == 1) {
                    isRightAutoClicking = state == 1;
                    flag = true;
                }
            }
            if (leftAutoClickingEnabled) {
                if (mouseButtonInfo.button() == 0) {
                    isLeftAutoClicking = state == 1;
                    flag = true;
                }
            }
            return flag;
        }
        return false;
    }

    private void onClientStartTick(Minecraft mc) {
        while(KeyBindsManager.leftAutoClickerSwap.consumeClick()) {
            leftAutoClickingEnabled = !leftAutoClickingEnabled;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e左键连点已" + (leftAutoClickingEnabled ? "§a启用§e, 长按左键即可开始连点" : "§c禁用")));
        }
        while(KeyBindsManager.rightAutoClickerSwap.consumeClick()) {
            rightAutoClickingEnabled = !rightAutoClickingEnabled;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e右键连点已" + (rightAutoClickingEnabled ? "§a启用§e, 长按右键即可开始连点" : "§c禁用")));
        }
        if(rightAutoClickingEnabled) {
            if(isRightAutoClicking) {
                if (rightClickDelay > 0) {
                    rightClickDelay--;
                } else {
                    rightClickDelay = ToolList.getInstance().random.nextInt(3) + 1;
                    InputSimulator.singleRightClick();
                }
            }
        } else {
            rightClickDelay = 0;
            isRightAutoClicking = false;
        }
        if(leftAutoClickingEnabled) {
            if(isLeftAutoClicking) {
                if (leftClickDelay > 0) {
                    leftClickDelay--;
                } else {
                    leftClickDelay = ToolList.getInstance().random.nextInt(3) + 1;
                    InputSimulator.singleLeftClick();
                }
            }
        } else {
            leftClickDelay = 0;
            isLeftAutoClicking = false;
        }
    }
}
