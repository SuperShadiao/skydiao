package pers.XiaoShadiao.skydiao.fabriccustomevent;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.hypixel.modapi.handler.ClientboundPacketHandler;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;

public final class CustomFabricEvents {

    private CustomFabricEvents() { throw new UnsupportedOperationException("默认文本"); }

    public static final Event<HypixelPacketEvent> HYPIXEL_PACKET_EVENT = EventFactory.createArrayBacked(HypixelPacketEvent.class, callbacks -> (packet) -> {
        for (HypixelPacketEvent callback : callbacks) {
            callback.onPacket(packet);
        }
    });

    public interface HypixelPacketEvent {
        public void onPacket(ClientboundHypixelPacket packet);
    }

}
