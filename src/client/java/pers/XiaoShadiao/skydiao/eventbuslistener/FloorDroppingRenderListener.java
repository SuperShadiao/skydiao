package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.HashSet;
import java.util.Set;

public class FloorDroppingRenderListener extends AbstractForagingListener {

    @Override
    public String getListenerName() {
        return "FloorDroppingRenderListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.floorDroppingRender.getValue() || mc.level == null || mc.player == null || !isInForagingOrSafariArea()) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

        Set<BlockPos> positions = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Display.ItemDisplay display) {
                Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
                if(state != null && state.itemStack().getItem() == Items.STRING) {
                    Vec3 position = entity.position();
                    positions.add(BlockPos.containing(position));
                }
            }
        }
        for (BlockPos position : positions) {
            if (ToolList.getInstance().isFullBlock(mc.level.getBlockState(position.above()))) continue;
            RenderUtils.renderESP(wr1, position, isInGalgame() ? 1 : 0, 1, 0, 1, false);
            RenderUtils.renderESP(wr2, position, isInGalgame() ? 1 : 0, 1, 0, 1, true);
        }

        wr1.finishDraw();
        wr2.finishDraw();
    }

}
