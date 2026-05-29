package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class E2AMappingListener extends AbstractListener {

    public static final Pattern timeMatcher = Pattern.compile("[\\d]+:[\\d]+");

    private int tickCount = 0;
    private final E2AMapping e2aMapping = new E2AMapping();

    @Override
    public String getListenerName() {
        return "E2AMappingListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldUnload);
    }

    private void onWorldUnload(Minecraft minecraft, ClientLevel clientLevel) {
        e2aMapping.clear();
    }

    private void onClientTick(Minecraft mc) {
        e2aMapping.selfCleaningAndUpdate();
        if(mc.level != null) {

            // 获取所有盔甲架实体
            java.util.List<ArmorStand> armorStands = new java.util.ArrayList<>();
            java.util.List<ArmorStand> armorStandsForBoss = new java.util.ArrayList<>();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ArmorStand armorStand) {
                    // 跳过已被绑定的盔甲架
                    if (!armorStand.hasCustomName() || getLivingEntity(armorStand) != null) {
                        continue;
                    }
                    String name = armorStand.getName().getString();
                    if (name.contains("by:")) continue;
                    Matcher matcher = timeMatcher.matcher(name);
                    if (matcher.matches()) {
                        armorStandsForBoss.add(armorStand);
                        continue;
                    }
                    // System.out.println(armorStand.getName().getString());
                    armorStands.add(armorStand);
                }
            }
            // 为每个非盔甲架实体找到最近的盔甲架
            for (Entity entity : mc.level.entitiesForRendering()) {

                MobInfo info = null;
                // 跳过盔甲架本身
                infoLabel: {
                    if (entity instanceof ArmorStand || entity == mc.player) {
                        continue;
                    }

                    // 跳过已经是映射中的实体
                    if (entity instanceof LivingEntity livingEntity) {
                        info = getMobInfo(livingEntity);
                        if (info != null) {
                            break infoLabel;
                        }
                    }

                    // 查找距离0.5内最近的未绑定盔甲架
                    ArmorStand closestArmorStand = null;
                    double minDistance = 1;

                    double currentDistance = Integer.MAX_VALUE;
                    for (ArmorStand armorStand : armorStands) {
                        double distance = getXZDistance(entity, armorStand);
                        if (distance <= minDistance && armorStand.getY() + 2 >= entity.getY() &&
                                (closestArmorStand == null || distance < currentDistance)) {
                            currentDistance = distance;
                            closestArmorStand = armorStand;
                        }
                    }

                    // 如果找到了符合条件的盔甲架，建立绑定
                    if (closestArmorStand != null && entity instanceof LivingEntity livingEntity) {
                        if(livingEntity.getHealth() < 2E9 || Float.isFinite(livingEntity.getMaxHealth()) || livingEntity.isInvisible()) {
                            info = e2aMapping.addMapping(livingEntity, closestArmorStand);
                            armorStands.remove(closestArmorStand);
                        } else {
                            // logger.info(livingEntity.getHealth() + "/" + livingEntity.getMaxHealth());
                        }
                    }
                }

                if(info != null) {
                    double currentDistance = Integer.MAX_VALUE;
                    double minDistance = 1;
                    ArmorStand closestArmorStandForBoss = null;
                    for (ArmorStand armorStand : armorStandsForBoss) {
                        double distance = getXZDistance(entity, armorStand);
                        if (distance <= minDistance && armorStand.getY() + 1 >= entity.getY() &&
                                (closestArmorStandForBoss == null || distance < currentDistance)) {
                            currentDistance = distance;
                            closestArmorStandForBoss = armorStand;
                        }
                    }
                    if (closestArmorStandForBoss != null) {
                        info.armorStandForBoss = closestArmorStandForBoss;
                    }
                }

            }
        }
    }

    public ArmorStand getArmorStand(LivingEntity e) {
        return e2aMapping.getArmorStand(e);
    }

    public LivingEntity getLivingEntity(ArmorStand a) {
        return e2aMapping.getLivingEntity(a);
    }

    public MobInfo getMobInfo(LivingEntity e) {
        return e2aMapping.getMobInfo(e);
    }

    public static class E2AMapping {
        private final Map<LivingEntity, ArmorStand> e2a = new HashMap<>();
        private final Map<ArmorStand, LivingEntity> a2e = new HashMap<>();
        private final Map<LivingEntity, MobInfo> e2Info = new HashMap<>();

        public MobInfo addMapping(LivingEntity e, ArmorStand a) {
            if(e == null || a == null) return null;
            e2a.put(e, a);
            a2e.put(a, e);
            MobInfo mobInfo = new MobInfo();
            mobInfo.theEntity = e;
            mobInfo.armorStand = a;
            mobInfo.health = e.getHealth();
            mobInfo.maxHealth = Math.max(e.getMaxHealth(), Math.max(mobInfo.health, mobInfo.maxHealth));
            mobInfo.hasHittingAttr = false;
            mobInfo.hit = -1;
            mobInfo.maxHit = -1;
            mobInfo.isBoss = false;
            e2Info.put(e, mobInfo);
            return mobInfo;
        }

        public void clear() {
            e2a.clear();
            a2e.clear();
        }

        public void selfCleaningAndUpdate() {
            e2a.entrySet().removeIf(entry -> {
                boolean flag = mc.level == null || (!ToolList.getInstance().isEntityOnWorld(entry.getKey()) || !ToolList.getInstance().isEntityOnWorld(entry.getValue()) || getXZDistance(entry.getKey(), entry.getValue()) >= 2);
                if(flag) a2e.remove(entry.getValue());
                return flag;
            });
            // a2e.entrySet().removeIf(entry -> mc.level == null || (!ToolList.getInstance().isEntityOnWorld(entry.getKey()) || !ToolList.getInstance().isEntityOnWorld(entry.getValue()) || getXZDistance(entry.getKey(), entry.getValue()) >= 2));

            e2Info.entrySet().removeIf(entry -> mc.level == null || !e2a.containsKey(entry.getKey()) || !ToolList.getInstance().isEntityOnWorld(entry.getKey()));
            e2Info.forEach((key, mobInfo) -> {
                mobInfo.health = key.getHealth();
                mobInfo.maxHealth = Math.max(key.getMaxHealth(), Math.max(mobInfo.health, mobInfo.maxHealth));
                mobInfo.theEntity = key;
            });
        }

        public ArmorStand getArmorStand(LivingEntity e) {
            if(e == null) return null;
            return e2a.get(e);
        }

        public MobInfo getMobInfo(LivingEntity e) {
            if(e == null) return null;
            return e2Info.get(e);
        }

        public LivingEntity getLivingEntity(ArmorStand a) {
            if(a == null) return null;
            return a2e.get(a);
        }
    }

    private static double getXZDistance(Entity e1, Entity e2) {
        return Math.sqrt((e1.getX() - e2.getX()) * (e1.getX() - e2.getX()) + (e1.getZ() - e2.getZ()) * (e1.getZ() - e2.getZ()));
    }

    private double getYDistance(Entity e1, Entity e2) {
        return Math.abs(e1.getY() - e2.getY());
    }

    public static class MobInfo {
        public LivingEntity theEntity;
        public ArmorStand armorStand;
        public double maxHealth;
        public double health;
        public int hit;
        public int maxHit;
        public boolean isBoss;
        public ArmorStand armorStandForBoss;
        public boolean hasHittingAttr;

        public String toString() {
            return health + "/" + maxHealth;
        }

        public void replaceWith(MobInfo mobInfo) {
            this.theEntity = mobInfo.theEntity;
            this.armorStand = mobInfo.armorStand;
            this.maxHealth = mobInfo.maxHealth;
            this.health = mobInfo.health;
            this.hit = mobInfo.hit;
            this.maxHit = mobInfo.maxHit;
            this.isBoss = mobInfo.isBoss;
            this.armorStandForBoss = mobInfo.armorStandForBoss;
            this.hasHittingAttr = mobInfo.hasHittingAttr;
        }
    }

}