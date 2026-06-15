package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class FishingHotSpotListener extends AbstractFishingListener {

    public IntSet hotspots = new IntOpenHashSet();

    public int currentHotSpot = -100;
    public Vec3 currentHotSpotPos = Vec3.ZERO;

    @Override
    public String getListenerName() {
        return "FishingHotSpotListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.hotspotrender.getValue() || mc.level == null || mc.player == null) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

        IntIterator it = hotspots.intIterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            Entity entity = mc.level.getEntity(id);
            if(entity != null) {
                float distance = entity.distanceTo(mc.player);
                float alpha = (Mth.clamp(distance, 8, 20) - 8) / 12;
                RenderUtils.renderTrace(wr1, entity.position(), 1, 0, 1, alpha);
                RenderUtils.renderESP(wr1, entity.position(), 1, 0, 1, alpha, false);
                RenderUtils.renderESP(wr2, entity.position(), 1, 0, 1, alpha, true);
            }
        }
    }

    private void onStartTick(Minecraft mc) {
        if(!ConfigManager.hotspotrender.getValue() || mc.level == null || mc.player == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof ArmorStand armorStand) {
                if(armorStand.hasCustomName() && ToolList.getInstance().deleteColorCode(armorStand.getName().getString()).equals("HOTSPOT")) {
                    hotspots.add(armorStand.getId());
                }
            }
        }
        hotspots.removeIf(id -> mc.level.getEntity(id) == null);

        if(mc.player.fishing != null) {
            Optional<Entity> entity = hotspots.intStream()
                    .mapToObj(id -> mc.level.getEntity(id))
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingDouble(e -> e.distanceTo(mc.player.fishing)));

            if(entity.isPresent()) {
                Entity entity1 = entity.get();
                if(entity1.distanceTo(mc.player.fishing) < 8) {
                    currentHotSpot = entity1.getId();
                    currentHotSpotPos = entity1.position();
                }
            }
        }
        if(currentHotSpot != -100) {
            if(!hotspots.contains(currentHotSpot)) {
                if(mc.player.distanceToSqr(currentHotSpotPos) < 20 * 20) {
                    XSDHUD.bigTitle.updateTitleMsg("§dHOT SPOT位置改变!", 7500, SoundEvents.WITHER_SPAWN);
                    currentHotSpot = -100;
                }
            }
        }
    }

}
