package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class RiftTimeGunRightClickHolderListener extends AbstractListener {

    private boolean isRightClicking = false;

    @Override
    public String getListenerName() {
        return "RiftTimeGunRightClickHolderListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null || !ConfigManager.rifttimegunhelper.getValue()) return;

        boolean temp = false;
        if (mc.player.getMainHandItem().getHoverName().getString().contains("Time Gun")) {
            if (mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
                Block block = mc.level.getBlockState(blockHitResult.getBlockPos()).getBlock();
                if (block == Blocks.LIGHT_BLUE_STAINED_GLASS_PANE || block == Blocks.BLUE_STAINED_GLASS_PANE) {
                    InputSimulator.pressRightClick();
                    isRightClicking = temp = true;
                }
            }
        }
        if(isRightClicking && !temp) {
            InputSimulator.releaseRightClick();
            isRightClicking = false;
        }
    }

}
