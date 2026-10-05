package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.HeadTextures;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class GardenPestESPListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "GardenPestESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if (!ConfigManager.gardenPestESP.getValue() || mc.level == null || !"garden".equals(StatusManager.get().getMode())) return;

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        float partialTicks = ToolList.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof ArmorStand armorStand) {
                String skullBase64 = ToolList.getInstance().getSkullBase64(armorStand.getItemBySlot(EquipmentSlot.HEAD));
                if(skullBase64 != null && HeadTextures.PEST_HEADS.contains(skullBase64)) {
                    Vec3 add = armorStand.getEyePosition(partialTicks).add(0, -0.5, 0);
                    RenderUtils.renderESP(wrFill, add, 1, 0, 0.4f, 1, true);
                    RenderUtils.renderESP(wrLine, add, 1, 0, 0.4f, 1, false);
                    RenderUtils.renderTrace(wrLine, add, 1, 0, 0.4f, 1);
                }
            }
        }

        wrLine.finishDraw();
        wrFill.finishDraw();
    }

}
