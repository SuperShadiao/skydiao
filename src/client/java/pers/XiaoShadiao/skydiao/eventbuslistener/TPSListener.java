package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

import java.util.ArrayList;
import java.util.List;

public class TPSListener extends AbstractListener {

    private final List<MutableLong> tps = Util.make(() -> {
        ArrayList<MutableLong> list = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            list.add(new MutableLong(0));
        }
        return list;
    });

    private final List<MutableDouble> tpss = Util.make(() -> {
        ArrayList<MutableDouble> list = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            list.add(new MutableDouble(0));
        }
        return list;
    });

    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

    private long lastWorldChange;

    private int arrayFlag;

    private boolean isTPSAvaliable;

    public boolean isTPSAvaliable() {
        return isTPSAvaliable;
    }

    public double getCurrentTPS() {
        return currentTPS;
    }

    public String getCurrentFormattedTPS() {
        return String.format("%.1f", currentTPS);
    }

    private double currentTPS;

    @Override
    public String getListenerName() {
        return "TPSListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((mc, level) -> {
            tps.forEach(e -> e.setValue(0));
            lastWorldChange = System.currentTimeMillis();
        });
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
    }

    private void onClientStartTick(Minecraft mc) {

        if(mc.level == null) {
            isTPSAvaliable = false;
            return;
        }

        arrayFlag++;
        isTPSAvaliable = true;
        double tempTPS = Mth.clamp(tps.stream().mapToDouble(Number::doubleValue).sum() / (50d * tps.size() / 20), 0, 20);
        try {
            tpss.get(arrayFlag % tpss.size()).setValue(tempTPS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        currentTPS = tpss.stream().mapToDouble(Number::doubleValue).average().orElse(0);

        if(arrayFlag % 40 == 0) {
            if(System.currentTimeMillis() - lastWorldChange > 10000) CustomFabricEvents.ON_TPS_UPDATE.invoker().update(getCurrentTPS(), getCurrentFormattedTPS());
        }

        try {
            tps.get(arrayFlag % tps.size()).setValue(queue.isEmpty() ? 0 : queue.dequeueLong());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private long lastReceivedPacketTime;

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(packet instanceof ClientboundSetTimePacket(long gameTime, long dayTime, boolean tickDayTime)) {
//            System.out.println(dayTime);
//            System.out.println(gameTime);
//            System.out.println(tickDayTime);
            queue.enqueue(Math.min(System.currentTimeMillis() - lastReceivedPacketTime, 1050));
            lastReceivedPacketTime = System.currentTimeMillis();
        }
        return false;
    }

}
