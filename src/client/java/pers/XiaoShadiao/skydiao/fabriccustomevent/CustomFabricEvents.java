package pers.XiaoShadiao.skydiao.fabriccustomevent;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.NotNull;

public final class CustomFabricEvents {

    private CustomFabricEvents() { throw new UnsupportedOperationException("默认文本"); }

    public static final Event<@NotNull HypixelPacketEvent> HYPIXEL_PACKET_EVENT = EventFactory.createArrayBacked(HypixelPacketEvent.class, callbacks -> (packet) -> {
        for (HypixelPacketEvent callback : callbacks) {
            callback.onPacket(packet);
        }
    });

    public interface HypixelPacketEvent {
        public void onPacket(ClientboundHypixelPacket packet);
    }

    /**
    * mouseButtonInfo 0 left 1 right 2 mid
    */
    public static final Event<@NotNull MouseButtonEvent> MOUSE_BUTTON_EVENT = EventFactory.createArrayBacked(MouseButtonEvent.class, callbacks -> (windowsHandle, mouseButtonInfo, pressState) -> {
        boolean cancelled = false;
        for (MouseButtonEvent callback : callbacks) {
            if (callback.onMouseButton(windowsHandle, mouseButtonInfo, pressState)) {
                cancelled = true;
            }
        }
        return cancelled;
    });

    public interface MouseButtonEvent {
        /**
         * @param mouseButtonInfo 0 left 1 right 2 mid
         */
        public boolean onMouseButton(long windowsHandle, MouseButtonInfo mouseButtonInfo, int pressState);
    }

    public static final Event<@NotNull MouseScrollEvent> MOUSE_SCROLL_EVENT = EventFactory.createArrayBacked(MouseScrollEvent.class, callbacks -> (windowsHandle, idk, scrollCount) -> {
        boolean cancelled = false;
        for (MouseScrollEvent callback : callbacks) {
            if (callback.onMouseScroll(windowsHandle, idk, scrollCount)) {
                cancelled = true;
            }
        }
        return cancelled;
    });

    public interface MouseScrollEvent {
        public boolean onMouseScroll(long windowsHandle, double idk, double scrollCount);
    }

    public static final Event<@NotNull PacketEvent<PacketListener>> CLIENT_PACKET_EVENT = EventFactory.createArrayBacked(PacketEvent.class, callbacks -> (packet, packetListener, packetProcessor) -> {
        boolean cancel = false;
        for (PacketEvent<PacketListener> callback : callbacks) {
            cancel |= callback.onPacket(packet, packetListener, packetProcessor);
        }
        return cancel;
    });

    public interface PacketEvent<T extends PacketListener> {
        public boolean onPacket(Packet<T> packet, T packetListener, PacketProcessor packetProcessor);
    }

    public static final Event<@NotNull SendPacketEvent> CLIENT_SEND_PACKET_EVENT = EventFactory.createArrayBacked(SendPacketEvent.class, callbacks -> (packet) -> {
        for (SendPacketEvent callback : callbacks) {
            callback.onSendPacket(packet);
        }
    });

    public interface SendPacketEvent {
        public void onSendPacket(Packet<?> packet);
    }

    public static final Event<@NotNull TPSUpdate> ON_TPS_UPDATE = EventFactory.createArrayBacked(TPSUpdate.class, callbacks -> (tps, formattedTPS) -> {
        for (TPSUpdate callback : callbacks) {
            callback.update(tps, formattedTPS);
        }
    });

    public interface TPSUpdate {
        public void update(double tps, String formattedTPS);
    }

}
