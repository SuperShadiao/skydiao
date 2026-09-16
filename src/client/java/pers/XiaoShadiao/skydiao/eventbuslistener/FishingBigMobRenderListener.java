package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class FishingBigMobRenderListener extends AbstractFishingListener {

    public IntSet bigMobEntities = new IntOpenHashSet();
    public IntSet notBigMobEntities = new IntOpenHashSet();

    @Override
    public String getListenerName() {
        return "FishingBigMobRenderListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.fishingBigFishRender.getValue() || mc.level == null || mc.player == null || (!isInWaterFishingArea() && !isInLavaFishingArea())) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);

        IntIterator it = bigMobEntities.intIterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            Entity entity = mc.level.getEntity(id);
            if(entity != null) {
                RenderUtils.renderTrace(wr1, entity, 1, 0, 0, 1);
                RenderUtils.renderESP(wr1, entity, 1, 0, 0, 1, false);
            }
        }

        wr1.finishDraw();
    }

    private void onStartTick(Minecraft mc) {
        if(!ConfigManager.fishingBigFishRender.getValue() || mc.level == null || mc.player == null || (!isInWaterFishingArea() && !isInLavaFishingArea())) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(isBigMob(entity)) {
                if (isInLavaFishingArea()) {
                    if(mc.player.fishing == null) continue;
                    if(entity.distanceTo(mc.player) > 20 || notBigMobEntities.contains(entity.getId())) {
                        notBigMobEntities.add(entity.getId());
                        continue;
                    }
                }
                if (!bigMobEntities.contains(entity.getId())) {
                    String value = ConfigManager.fishingBigFishTip.getValue().trim();
                    if(!value.isEmpty()) XSDHUD.bigTitle.updateTitleMsg(value.replace("&", "§"), 3000, SoundEvents.ANVIL_USE);
                    bigMobEntities.add(entity.getId());
                }
            }
        }
        bigMobEntities.removeIf(isInLavaFishingArea() ? e -> mc.level.getEntity(e) == null : this::isNotBigMob);
        notBigMobEntities.removeIf(e -> mc.level.getEntity(e) == null);
    }

    private boolean isNotBigMob(int entityId) {
        return !isBigMob(entityId);
    }

    public boolean isBigMob(int entityId) {
        if(mc.level == null) return false;
        return isBigMob(mc.level.getEntity(entityId));
    }

    public boolean isBigMob(Entity entity) {
        if(entity instanceof LivingEntity livingEntity) {
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(livingEntity);
            if(mobInfo != null) {
                String name = mobInfo.armorStand.getName().getString();
                if(isInWaterFishingArea()) {
                    if (name.contains("/") && (name.contains("M❤") || name.contains("k❤"))) {
                        return true;
                    }
                } else if(isInLavaFishingArea()) {
                    if (livingEntity.getMaxHealth() >= 10_000_000 && livingEntity.getMaxHealth() <= 500_000_000 && !(livingEntity instanceof RemotePlayer)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
