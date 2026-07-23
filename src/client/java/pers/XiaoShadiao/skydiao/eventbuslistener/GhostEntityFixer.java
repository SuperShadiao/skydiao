package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

public class GhostEntityFixer extends AbstractListener {

    private final Int2ObjectArrayMap<UpdateCounter> removeQueue = new Int2ObjectArrayMap<>();

    public static class UpdateCounter {
        public int updateCount;
        public final long startTime = System.currentTimeMillis();
    }

    @Override
    public String getListenerName() {
        return "GhostEntityFixer";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::worldUnload);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        AttackEntityCallback.EVENT.register(this::onAttackEntity);
    }

    private InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult entityHitResult) {
        LivingEntity living = entity.asLivingEntity();
        if (living != null) enqueueRemoveEntity(entity);
        return InteractionResult.PASS;
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.level == null || !StatusManager.get().isInDungeon()) {
            return;
        }

        Set<Arrow> arrowList = new HashSet<>();
        Set<ArmorStand> blazeMobInfoList = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Arrow arrow) {
                if(arrow.getOwner() == mc.player) {
                    arrowList.add(arrow);
                }
            }
            E2AMappingListener.MobInfo mobInfo = entity instanceof Blaze ? e2AMappingListener.getMobInfo(entity.asLivingEntity()) : null;
            if(mobInfo != null) blazeMobInfoList.add(mobInfo.armorStand);
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(!(entity instanceof LivingEntity) || entity == mc.player) continue;
            AABB aabb = entity.getBoundingBox();
            aabb = aabb.inflate(entity instanceof ArmorStand ? 3 : 1);
            for (Arrow arrow : arrowList) {
                if(aabb.contains(arrow.position())) {
                    if(!blazeMobInfoList.contains(entity)) {
                        enqueueRemoveEntity(entity);
                        break;
                    }
                }
            }
        }

        synchronized (removeQueue) {
            ObjectIterator<Int2ObjectMap.Entry<UpdateCounter>> it = removeQueue.int2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                Int2ObjectMap.Entry<UpdateCounter> next = it.next();
                UpdateCounter value = next.getValue();
                int entityId = next.getIntKey();
                Entity entity = mc.level.getEntity(entityId);
                if (entity instanceof LivingEntity livingEntity) {
                    if(livingEntity.hurtTime > 0 || livingEntity.swinging) {
                        it.remove();
                        continue;
                    }
                }
                if (value != null && System.currentTimeMillis() - value.startTime > 5000) {
                    if (entity != null) {
                        log("Delayed removed entity id " + entityId + " " + entity);
                        mc.level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
                    }
                    it.remove();
                }
            }
        }
    }

    private void enqueueRemoveEntity(Entity entity) {
        if(entity instanceof ArmorStand && !entity.getName().getString().contains("❤")) return;
        if(entity instanceof Blaze) return;
        if(entity instanceof WitherBoss) return;
        if(entity instanceof RemotePlayer player) {
            if(player.getTeam() != null) {
                String name = player.getTeam().getName();
                if(!name.startsWith("fkt_")) {
                    return;
                }
            }
        }

        synchronized (removeQueue) {
            removeQueue.put(entity.getId(), new UpdateCounter());
        }
    }

    private void worldUnload(Minecraft mc, ClientLevel level) {
        removeQueue.clear();
    }

    private boolean onPacket(Packet<?> packet, PacketListener listener, PacketProcessor packetProcessor) {
        if(mc.level != null && StatusManager.get().isInDungeon()) {
            Integer entityId = null;
            int increaseCount = 1;
            if(packet instanceof ClientboundMoveEntityPacket moveEntityPacket) {
                entityId = Optional.ofNullable(moveEntityPacket.getEntity(mc.level)).map(Entity::getId).orElse(null);
            } else if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket) {
                entityId = entityDataPacket.id();
            } else if (packet instanceof ClientboundAddEntityPacket addEntityPacket) {
                entityId = addEntityPacket.getId();
                increaseCount = 2;
            } else if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket) {
                entityId = syncPacket.id();
            } else if (packet instanceof ClientboundTeleportEntityPacket entityPacket) {
                entityId = entityPacket.id();
            } else if (packet instanceof ClientboundSetEntityMotionPacket entityPacket) {
                entityId = entityPacket.id();
            } else if (packet instanceof ClientboundSetEntityLinkPacket entityPacket) {
                entityId = entityPacket.getSourceId();
            }
            if(entityId != null) {
                Entity entity = mc.level.getEntity(entityId);
                increaseFlagAndRemove(entityId, increaseCount);
                if(entity instanceof LivingEntity living) {
                    E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(living);
                    if(mobInfo != null) {
                        Optional.ofNullable(mobInfo.armorStand).ifPresent(e -> increaseFlagAndRemove(e.getId(), 1));
                        Optional.ofNullable(mobInfo.armorStandForBoss).ifPresent(e -> increaseFlagAndRemove(e.getId(), 1));
                    }
                }
            }
        }

        return false;
    }

    private void increaseFlagAndRemove(int entityId, int amount) {
        UpdateCounter counter = removeQueue.get(entityId);
        if(counter != null) {
            counter.updateCount += amount;
            if(counter.updateCount >= 5) {
                synchronized (removeQueue) {
                    removeQueue.remove(entityId);
                }
            }
        }
    }

    private void log(String msg) {
        if(ToolList.getInstance().isXiaoShadiao()) logger.info(msg);
    }
}
