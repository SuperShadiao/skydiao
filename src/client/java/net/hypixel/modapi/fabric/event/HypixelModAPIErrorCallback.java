package net.hypixel.modapi.fabric.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.hypixel.modapi.error.ErrorReason;

/**
 * Callback for when a Hypixel Mod API error reason is received.
 */
public interface HypixelModAPIErrorCallback {

    Event<HypixelModAPIErrorCallback> EVENT = EventFactory.createArrayBacked(HypixelModAPIErrorCallback.class, callbacks -> (identifier, reason) -> {
        for (HypixelModAPIErrorCallback callback : callbacks) {
            callback.onError(identifier, reason);
        }
    });

    void onError(String identifier, ErrorReason reason);

}
