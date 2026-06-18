package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.Mth;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

import java.util.List;

public class TooltipScrollController extends AbstractListener {

    private int currentOffset = 0;
    private int currentTooltipHeight = 0;
    private long lastUpdateHeightTime;

    @Override
    public String getListenerName() {
        return "TooltipScrollController";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.MOUSE_SCROLL_EVENT.register(this::mouseButtonScroll);
    }

    private boolean mouseButtonScroll(long l, double idk, double scrollCount) {
        if (updateScroll(scrollCount) && System.currentTimeMillis() - lastUpdateHeightTime < 100) {
            if (mc.screen instanceof ChatScreen screen) {
                return true;
            }
        }
        return false;
    }

    private boolean updateScroll(double scrollCount) {
        int lastOffset = currentOffset;
        int maxOffset = Math.max(0, currentTooltipHeight - mc.getWindow().getGuiScaledHeight()) + 10;
        currentOffset = (int) Mth.clamp(currentOffset + scrollCount * 10, 0, maxOffset);
        return lastOffset != currentOffset;
    }

    public int getOffsetScroll(Font font, List<ClientTooltipComponent> list) {
        int l = list.size() == 1 ? -2 : 0;

        for (ClientTooltipComponent clientTooltipComponent : list) {
            l += clientTooltipComponent.getHeight(font);
        }

        currentTooltipHeight = l;
        if(l < mc.getWindow().getGuiScaledHeight()) currentOffset = 0;
        updateScroll(0);

        lastUpdateHeightTime = System.currentTimeMillis();
        return currentOffset;
    }
}
