package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class GalateaShulkerESPListener extends AbstractListener {
    @Override
    public String getListenerName() {
        return "GalateaShulkerESPListener";
    }

    @Override
    public void registerListeners() {
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(WorldRenderContext context) {
        if(!ConfigManager.galateashulker.getValue() || mc.level == null || !"foraging_2".equals(StatusManager.get().getMode())) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Shulker) {
                RenderUtils.renderESP(wr, entity, 0, 1, 0, 1, false);
            }
        }
        wr.finishDraw();
    }
}
