package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class AutoUseItemListener extends AbstractListener {

    private int progressTick = 0;

    private int lastItemIndex = -1;

    private String skyblockItemId = "";

    @Override
    public String getListenerName() {
        return "AutoUseItemListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    private void onStartClientTick(Minecraft mc) {

        if(progressTick > 0) {

            if(mc.player == null || mc.level == null) {
                progressTick = 0;
                return;
            }

            switch (progressTick) {
                case 5 -> {
                    lastItemIndex = mc.player.getInventory().getSelectedSlot();
                    a: {
                        for (int i = 0; i < 9; i++) {
                            if (isCorrectItem(mc.player.getInventory().getItem(i))) {
                                InputSimulator.switchItem(i);
                                break a;
                            }
                        }
                        progressTick = 0;
                    }
                }
                case 3 -> InputSimulator.singleRightClick();
                case 1 -> InputSimulator.switchItem(lastItemIndex);
            }

            progressTick--;
        }

    }

    public void startUseItem(String skyblockItemId) {
        if(progressTick <= 0) {
            this.skyblockItemId = skyblockItemId;
            progressTick = 5;
        }
    }

    private boolean isCorrectItem(ItemStack itemStack) {
        return skyblockItemId.equals(ToolList.getInstance().tryGetSkyblockItemId(itemStack))
                || (ToolList.getInstance().isDevEnvironment() && itemStack.is(Items.WOODEN_AXE));
    }

}
