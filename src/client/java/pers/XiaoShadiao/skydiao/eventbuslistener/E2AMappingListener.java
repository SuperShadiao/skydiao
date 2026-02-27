package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.Map;

public class E2AMappingListener extends AbstractListener {

    public final E2AMapping e2aMapping = new E2AMapping();

    @Override
    public String getListenerName() {
        return "E2AMappingListener";
    }

    @Override
    protected void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        if(mc.level != null) {
            e2aMapping.selfCleaning();
            for (Entity entity : mc.level.entitiesForRendering()) {

            }
        }
    }

    public ArmorStand getArmorStand(LivingEntity e) {
        return e2aMapping.getArmorStand(e);
    }

    public LivingEntity getLivingEntity(ArmorStand a) {
        return e2aMapping.getLivingEntity(a);
    }

    public class E2AMapping {
        private final Map<LivingEntity, ArmorStand> e2a = new HashMap<>();
        private final Map<ArmorStand, LivingEntity> a2e = new HashMap<>();

        public void addMapping(LivingEntity e, ArmorStand a) {
            if(e == null) return;
            e2a.put(e, a);
            a2e.put(a, e);
        }

        public void clear() {
            e2a.clear();
            a2e.clear();
        }

        public void selfCleaning() {
            e2a.entrySet().removeIf(entry -> mc.level != null && (mc.level.getEntity(entry.getKey().getId()) == null || mc.level.getEntity(entry.getValue().getId()) == null));
            a2e.entrySet().removeIf(entry -> mc.level != null && (mc.level.getEntity(entry.getKey().getId()) == null || mc.level.getEntity(entry.getValue().getId()) == null));
        }

        public ArmorStand getArmorStand(LivingEntity e) {
            return e2a.get(e);
        }

        public LivingEntity getLivingEntity(ArmorStand a) {
            return a2e.get(a);
        }
    }

}