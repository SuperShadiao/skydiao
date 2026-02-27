package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
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
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("XSDDungeonMobESPListener");
        if (ConfigManager.dungeonRenderDangerousEnemy.getValue() && mc.level != null) {
            profilerFiller.push("createWR");
            RenderUtils.WorldRender worldRender = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.WorldRender worldRender2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            profilerFiller.popPush("forEach");
            for(Entity entity : mc.level.entitiesForRendering()) {
                ArmorStand armorStand = e2AMappingListener.getArmorStand(entity.asLivingEntity());
                if (armorStand != null) {
                    String asName = ToolList.getInstance().deleteColorCode(armorStand.getName().getString());
                    boolean isStarMob = StatusManager.get().isInDungeon() && asName.contains("✯");
                    if(asName.contains("Sniper")) {
                        profilerFiller.push("render");
                        RenderUtils.renderESP(worldRender, entity, 1, 0, 0, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 0, 0, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 1, 0, 0, 1);
                        profilerFiller.pop();
                    }
                    if(asName.contains("Prime")) {
                        profilerFiller.push("render");
                        RenderUtils.renderESP(worldRender, entity, 0.5f, 1f, 0, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 0.5f, 0, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 0.5f, 1f, 0, 1);
                        profilerFiller.pop();
                    }
                    if(asName.contains("Shadow")) {
                        profilerFiller.push("render");
                        RenderUtils.renderESP(worldRender, entity, 1, 1, 1, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 1, 1, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 1, 1, 1, 1);
                        profilerFiller.pop();
                    }
                    if(asName.contains("Angry") || asName.contains("Lost")) {
                        profilerFiller.push("render");
                        RenderUtils.renderESP(worldRender, entity, 1, 1, 0, 1, true);
                        if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, 1, 1, 0, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, 1, 1, 0, 1);
                        profilerFiller.pop();
                    }
                    if(isStarMob) {
                        profilerFiller.push("render");
                        RenderUtils.renderESP(worldRender2, entity, 1, 0.5f, 0, 1, false);
                        profilerFiller.pop();
                    }
                }
            }

            profilerFiller.popPush("finishDraw1");
            worldRender.finishDraw();
            profilerFiller.popPush("finishDraw2");
            worldRender2.finishDraw();
            profilerFiller.pop();
        }
        profilerFiller.pop();
    }

}
