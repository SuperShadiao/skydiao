package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;

public class EndIslandDragonESPListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "EndIslandDragonESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if (ConfigManager.endIslandDragonESPListener.getValue() && mc.player != null && mc.level != null && "combat_3".equals(StatusManager.get().getMode())) {
            RenderUtils.WorldRender worldRender = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.WorldRender worldRender2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            for(Entity entity : mc.level.entitiesForRendering()) {
                if(entity instanceof EnderDragon dragon) {
                    // new Color(0x9E00FF);
                    RenderUtils.renderESP(worldRender, dragon, 0x9E / 255f, 0 / 255f, 0xFF / 255f, 1, true);
                    RenderUtils.renderESP(worldRender2, dragon, 0x9E / 255f, 0 / 255f, 0xFF / 255f, 1, false);
                    RenderUtils.renderTrace(worldRender2, dragon, 0x9E / 255f, 0 / 255f, 0xFF / 255f, 1);
                }
            }

            worldRender.finishDraw();
            worldRender2.finishDraw();
        }
    }

}
