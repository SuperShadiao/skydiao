package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class DungeonMobESPListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "DungeonMobESPListener";
    }

    @Override
    protected void registerListeners() {
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(WorldRenderContext context) {
        if (ConfigManager.dungeonRenderDangerousEnemy.getValue() && mc.level != null) {
            RenderUtils.WorldRender worldRender = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.WorldRender worldRender2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            for(Entity entity : mc.level.entitiesForRendering()) {
                ArmorStand armorStand = e2AMappingListener.getArmorStand(entity.asLivingEntity());
                if (armorStand != null) {
                    String asName = ToolList.getInstance().deleteColorCode(armorStand.getName().getString());
                    boolean isStarMob = StatusManager.get().isInDungeon() && asName.contains("✯");
                    if(asName.contains("Sniper")) {
                        RenderUtils.renderESP(worldRender, entity, 1, 0, 0, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 0, 0, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 1, 0, 0, 1);
                    }
                    if(asName.contains("Prime")) {
                        RenderUtils.renderESP(worldRender, entity, 0.5f, 1f, 0, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 0.5f, 0, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 0.5f, 1f, 0, 1);
                    }
                    if(asName.contains("Shadow")) {
                        RenderUtils.renderESP(worldRender, entity, 1, 1, 1, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 1, 1, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 1, 1, 1, 1);
                    }
                    if(asName.contains("Angry") || asName.contains("Lost")) {
                        RenderUtils.renderESP(worldRender, entity, 1, 1, 0, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 1, 0, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 1, 1, 0, 1);
                    }
                    if(isStarMob) {
                        RenderUtils.renderESP(worldRender2, entity, 1, 0.5f, 0, 1, false);
                    }
                }
            }
            worldRender.finishDraw();
            worldRender2.finishDraw();
        }
    }

}
