package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class DungeonNoSwapItemInEndListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "DungeonNoSwapItemInEndListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(ConfigManager.dungeonautoswitchbackitem.getValue() && StatusManager.get().isInDungeon()) {
            if (packet instanceof ClientboundSetHeldSlotPacket(int slot)) {
                if (slot == 8) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 已自动切换回原物品。"));
                    int originalSlot = mc.player.getInventory().getSelectedSlot();
                    delayTickExecutor.delayExec(() -> {
                        // mc.player.getInventory().setSelectedSlot(originalSlot);
                        InputSimulator.switchItem(originalSlot);
                    }, 0);
                }
            }
        }
        return false;
    }

}
