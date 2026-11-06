package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class RiftAutoDigMushroom extends AbstractListener {

    private int cdTick = 0;

    @Override
    public String getListenerName() {
        return "RiftAutoDigMushroom";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null) return;

        if(!ConfigManager.riftAutoDigMushroom.getValue()) return;

        if(cdTick > 0) {
            cdTick--;
            return;
        }

        if("FARMING_WAND".equals(ToolList.getInstance().tryGetSkyblockItemId(mc.player.getMainHandItem()))) {
            if(mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
                if(mc.level.getBlockState(blockHitResult.getBlockPos()).is(Blocks.RED_MUSHROOM)) {
                    InputSimulator.singleLeftClick();
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 小沙雕自动帮你进行了点击。"));
                    cdTick = 10;
                }
            }
        }
    }
}
