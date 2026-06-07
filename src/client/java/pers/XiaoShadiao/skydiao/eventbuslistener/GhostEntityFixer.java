package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Optional;

public class GhostEntityFixer extends AbstractListener {

    private final IntPriorityQueue tryRemoveAgain = IntPriorityQueues.synchronize(new IntArrayFIFOQueue());
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
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::worldUnload);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.level == null) {
            return;
        }
        IntSet processed = new IntOpenHashSet();
        for (int i = 0; i < 50 && !tryRemoveAgain.isEmpty(); i++) {
            int entityId = tryRemoveAgain.dequeueInt();
            Entity entity = mc.level.getEntity(entityId);
            if(entity == null) {
                processed.add(entityId);
            } else {
                boolean remove = false;
                if(entity instanceof RemotePlayer player) {
                    if(player.getTeam() == null) {
                        log("Empty team, don't remove it id " + entityId + " " + entity);
                    } else {
                        String name = player.getTeam().getName();
                        if(name.startsWith("fkt_")) {
                            log("Team name: " + name);
                            remove = true;
                        } else {
                            log("Team name: " + name + ", don't remove it id " + entityId + " " + entity);
                        }
                    }
                } else {
                    remove = true;
                }
                if(remove) {
                    removeQueue.put(entityId, new UpdateCounter());
                }
            }
        }
        if(tryRemoveAgain.size() < 1000) processed.forEach(tryRemoveAgain::enqueue);
        synchronized (removeQueue) {
            ObjectIterator<Int2ObjectMap.Entry<UpdateCounter>> it = removeQueue.int2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                Int2ObjectMap.Entry<UpdateCounter> next = it.next();
                UpdateCounter value = next.getValue();
                if (value != null && System.currentTimeMillis() - value.startTime > 10000) {
                    int entityId = next.getIntKey();
                    Entity entity = mc.level.getEntity(entityId);
                    if (entity == null) {
                        tryRemoveAgain.enqueue(entityId);
                    } else {
                        log("Remain: " + tryRemoveAgain.size() + ", " + removeQueue.size() + " | Delayed removed entity id " + entityId + " " + entity);
                        mc.level.removeEntity(entityId, Entity.RemovalReason.DISCARDED);
                    }
                    it.remove();
                }
            }
        }
    }

    private void worldUnload(Minecraft mc, ClientLevel level) {
        tryRemoveAgain.clear();
    }

    private boolean onPacket(Packet<?> packet, PacketListener listener, PacketProcessor packetProcessor) {
        if(mc.level != null) {
            if(packet instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
                IntIterator it = removeEntitiesPacket.getEntityIds().intIterator();
                while(it.hasNext()) {
                    int i = it.nextInt();
                    // log("id " + i);
                    Entity entity = mc.level.getEntity(i);
                    if(entity == null) {
                        // log("Can't find entity id " + i + ", add it to remove queue");
                        tryRemoveAgain.enqueue(i);
                    }
                }
            }
            Integer entityId = null;
            int increaseCount = 1;
            if(packet instanceof ClientboundMoveEntityPacket moveEntityPacket) {
                entityId = Optional.ofNullable(moveEntityPacket.getEntity(mc.level)).map(Entity::getId).orElse(null);
            } else if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket) {
                entityId = entityDataPacket.id();
            } else if (packet instanceof ClientboundAddEntityPacket addEntityPacket) {
                entityId = addEntityPacket.getId();
                increaseCount = 2;
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
