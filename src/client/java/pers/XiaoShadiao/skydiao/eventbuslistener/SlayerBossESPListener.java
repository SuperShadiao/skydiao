package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class SlayerBossESPListener extends AbstractListener {

    private final Int2LongMap ignoreEntities = new Int2LongOpenHashMap();

    @Override
    public String getListenerName() {
        return "SlayerBossESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null || mc.level == null || !ConfigManager.slayerBossESP.getValue()) return;

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);

        for (Entity entity : mc.level.entitiesForRendering()) {
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(entity.asLivingEntity());
            if(mobInfo == null) continue;
            if(mobInfo.armorStandForBossType == null) continue;
            if(ignoreEntities.containsKey(entity.getId())) continue;

            String string = mobInfo.armorStandForBossType.getName().getString();
            if(string.equals("SLAYER MINIBOSS")) {
                RenderUtils.renderESP(wrFill, entity, 1, 0.4f, 0.1f, 1, true);
                RenderUtils.renderESP(wrLine, entity, 1, 0.4f, 0.1f, 1, false);
            } else if(string.endsWith("SLAYER BOSS")) {
                RenderUtils.renderESP(wrFill, entity, 0.4f, 0.1f, 1f, 1, true);
                RenderUtils.renderESP(wrLine, entity, 0.4f, 0.1f, 1f, 1, false);
            }
        }

        ignoreEntities.values().removeIf(v -> v + 1000 < System.currentTimeMillis());

        wrLine.finishDraw();
        wrFill.finishDraw();
    }

    public void addIgnoreEntity(Entity entity) {
        ignoreEntities.put(entity.getId(), System.currentTimeMillis());
    }

}
