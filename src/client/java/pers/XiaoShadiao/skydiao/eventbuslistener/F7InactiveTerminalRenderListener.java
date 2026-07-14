package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class F7InactiveTerminalRenderListener extends AbstractListener implements IDungeonListener {

    @Override
    public String getListenerName() {
        return "F7InactiveTerminalRenderListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::renderEntity);
    }

    private void renderEntity(LevelRenderContext context) {
        if(mc.player == null || mc.level == null || !ConfigManager.dungeonf7InactiveTerminalRender.getValue() || !isInCorrectDungeon()) return;
        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        for (Entity e : mc.level.entitiesForRendering()) {
            if(e instanceof ArmorStand) {
                if(e.getName().getString().contains("Inactive")) {
                    BlockPos pos = e.blockPosition().above(1);
                    RenderUtils.renderESP(wr, pos, 1, 0, 1, 1, false);
                    RenderUtils.renderTrace(wr, pos, 1, 0, 1, 1);
                }
            }
        }
        wr.finishDraw();
    }

    @Override
    public int getFloor() {
        return 7;
    }
}
