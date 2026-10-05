package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.HeadTextures;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class HubRatESPListener extends AbstractListener {
    @Override
    public String getListenerName() {
        return "HubRatESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.hubratesp.getValue() || mc.level == null || !"hub".equals(StatusManager.get().getMode())) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Zombie) {
                if(mc.level.getEntitiesOfClass(Display.class, entity.getBoundingBox().inflate(1)).stream().anyMatch(e -> {
                    if(e instanceof Display.ItemDisplay itemDisplay) {
                        return HeadTextures.RAT_PEST.equals(ToolList.getInstance().getSkullBase64(itemDisplay.getItemStack()));
                    }
                    return false;
                })) {
                    RenderUtils.renderESP(wr, entity, 1, 1, 0, 1, false);
                    RenderUtils.renderESP(wr2, entity, 1, 1, 0, 1, true);
                }
            }
        }
        wr.finishDraw();
        wr2.finishDraw();
    }
}
