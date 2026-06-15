package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
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
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if (ConfigManager.dungeonRenderDangerousEnemy.getValue() && mc.player != null && mc.level != null && StatusManager.get().isInDungeon()) {
            RenderUtils.WorldRender worldRender = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.WorldRender worldRender2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            for(Entity entity : mc.level.entitiesForRendering()) {
                if(entity.distanceTo(mc.player) > 30) continue;
                ArmorStand armorStand = e2AMappingListener.getArmorStand(entity.asLivingEntity());
                if (armorStand != null) {
                    String asName = ToolList.getInstance().deleteColorCode(armorStand.getName().getString());
                    boolean isStarMob = StatusManager.get().isInDungeon() && asName.contains("✯");
                    for (MobType type : MobType.values()) {
                        if(asName.contains(type.name)) {
                            RenderUtils.renderESP(worldRender, entity, type.r, type.g, type.b, 1, true);
                            if(!isStarMob) RenderUtils.renderESP(worldRender2, entity, type.r, type.g, type.b, 1, false);
                            RenderUtils.renderTrace(worldRender2, entity, type.r, type.g, type.b, 1);
                        }
                    }
                    if(isStarMob) {
                        RenderUtils.renderESP(worldRender2, entity, 1, 0.5f, 0, 1, false);
                    }
                } else if(entity instanceof RemotePlayer player) {
                    if(ToolList.getInstance().deleteColorCode(player.getName().getString()).equals("Shadow Assassin")) {
                        RenderUtils.renderESP(worldRender, entity, MobType.Shadow.r, MobType.Shadow.g, MobType.Shadow.b, 1, true);
                        RenderUtils.renderESP(worldRender2, entity, MobType.Shadow.r, MobType.Shadow.g, MobType.Shadow.b, 1, false);
                        RenderUtils.renderTrace(worldRender2, entity, MobType.Shadow.r, MobType.Shadow.g, MobType.Shadow.b, 1);
                    }
                }
                if (entity instanceof Bat && !entity.isInvisible() && XSDHUD.customBossbar.getStarRailBossBar() == null) {
                    RenderUtils.renderESP(worldRender, entity, 1, 0.5f, 0, 1, true);
                    RenderUtils.renderESP(worldRender2, entity, 1, 0.5f, 0, 1, false);
                    RenderUtils.renderTrace(worldRender2, entity, 1, 0.5f, 0, 1);
                }
            }

            worldRender.finishDraw();
            worldRender2.finishDraw();
        }
    }

    private enum MobType {
        Sniper(1, 0, 0, "Sniper"),
        Prime(0.5f, 1f, 0, "Prime"),
        Shadow(1, 1, 1, "Shadow"),
        Angry(1, 1, 0, "Angry"),
        Lost(1, 1, 0, "Lost"),
        Frozen(1, 0, 1, "Frozen"),
        Bear(1, 0, 1, "Bear");

        public final float r;
        public final float g;
        public final float b;
        public final String name;

        MobType(float r, float g, float b, String name) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.name = name;
        }
    }

}
