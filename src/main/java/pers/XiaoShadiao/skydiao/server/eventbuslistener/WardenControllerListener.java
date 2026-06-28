package pers.XiaoShadiao.skydiao.server.eventbuslistener;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.pathfinder.Path;
import pers.XiaoShadiao.skydiao.server.customfabricevents.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.server.utils.ToolList;

import java.util.ArrayList;
import java.util.List;

public class WardenControllerListener extends AbstractListener {
    @Override
    public String getListenerName() {
        return "WardenControllerListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.ON_HURT_SERVER_POST.register(this::onHurt);
        ServerTickEvents.START_LEVEL_TICK.register(this::onLevelTick);
    }

    private int tickCount;

    private void onLevelTick(ServerLevel serverLevel) {
        tickCount++;
        if(tickCount < 20) return; else tickCount = 0;

        List<Warden> wardenList = new ArrayList<>();
        List<Warden> nonCombatingWardenList = new ArrayList<>();
        LivingEntity xiaoshadiao = null;
        for (Entity entity : serverLevel.getAllEntities()) {
            if(entity instanceof Warden warden) {
                wardenList.add(warden);
                if(warden.getTarget() == null && !warden.hasPose(Pose.ROARING)) {
                    nonCombatingWardenList.add(warden);
                }
            }
            if(ToolList.getInstance().isXiaoShadiaoPlayer(entity)) {
                xiaoshadiao = entity.asLivingEntity();
            }
        }

        if(xiaoshadiao != null) {
            for (Warden warden : nonCombatingWardenList) {
                Path path = warden.getNavigation().createPath(xiaoshadiao, 5);
                warden.getNavigation().moveTo(path, 1.34);
            }
            for (Warden warden : wardenList) {
                warden.heal(10);
            }
        }
    }

    private void onHurt(LivingEntity target, ServerLevel serverLevel, DamageSource damageSource, float dmg) {
        LivingEntity attacker = damageSource.getEntity() instanceof LivingEntity ? (LivingEntity) damageSource.getEntity() : null;
        if(ToolList.getInstance().isXiaoShadiaoPlayer(target)) {
            if(attacker != null) {
                serverLevel.getEntitiesOfClass(Warden.class, target.getBoundingBox().inflate(30)).forEach(e -> {
                    if(e == attacker) return;
                    logger.info("应用仇恨对象" + attacker + "到" + e);
                    e.increaseAngerAt(attacker, AngerLevel.ANGRY.getMinimumAnger() + 120, true);
                    LivingEntity target1 = e.getTarget();
                    if(target1 != attacker && !attacker.isDeadOrDying()) {
                        e.clearAnger(target1);
                        logger.info("原来的实体" + target1);
                        e.setAttackTarget(attacker);
                    }
                });
            }
        } else if(ToolList.getInstance().isXiaoShadiaoPlayer(attacker)) {
            if(target != null) {
                serverLevel.getEntitiesOfClass(Warden.class, target.getBoundingBox().inflate(30)).forEach(e -> {
                    if(e == target) return;
                    logger.info("应用仇恨对象" + target + "到" + e);
                    e.increaseAngerAt(target, AngerLevel.ANGRY.getMinimumAnger() + 120, true);
                    LivingEntity target1 = e.getTarget();
                    if(target1 != target && !target.isDeadOrDying()) {
                        e.clearAnger(target1);
                        logger.info("原来的实体" + target1);
                        e.setAttackTarget(target);
                    }
                });
            }
        }
    }
}
