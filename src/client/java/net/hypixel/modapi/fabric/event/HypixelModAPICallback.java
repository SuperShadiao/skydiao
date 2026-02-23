package net.hypixel.modapi.fabric.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;

/**
 * Callback for when a Hypixel Mod API packet is received.
 */
public interface HypixelModAPICallback {

    Event<HypixelModAPICallback> EVENT = EventFactory.createArrayBacked(HypixelModAPICallback.class, callbacks -> packet -> {
        for (HypixelModAPICallback callback : callbacks) {
            callback.onPacketReceived(packet);
        }
    });

    void onPacketReceived(ClientboundHypixelPacket packet);

}
