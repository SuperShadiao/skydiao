package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;

public class GalateaShulkerESPListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "GalateaShulkerESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.galateashulker.getValue() || mc.level == null) return;
        Color color = getColor();
        if(color == null) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Shulker) {
                RenderUtils.renderESP(wr, entity, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 1, false);
            }
        }
        wr.finishDraw();
    }

    private Color getColor() {
        return switch (String.valueOf(StatusManager.get().getMode())) {
            case "foraging_2" -> Color.GREEN;
            case "foraging_3" -> Color.PINK;
            default -> null;
        };
    }

}
