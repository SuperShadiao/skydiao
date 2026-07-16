package pers.XiaoShadiao.skydiao.server.customfabricevents;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class CustomFabricEvents {

    public static final Event<@NotNull PreHurtServerEvent> ON_HURT_SERVER_PRE = EventFactory.createArrayBacked(PreHurtServerEvent.class, callbacks -> (LivingEntity target, ServerLevel level, DamageSource source, float damage) -> {
        boolean cancel = false;
        for (PreHurtServerEvent callback : callbacks) {
            cancel |= callback.onHurt(target, level, source, damage);
        }
        return cancel;
    });

    public interface PreHurtServerEvent {
        public boolean onHurt(LivingEntity target, ServerLevel level, DamageSource source, float damage);
    }

    public static final Event<@NotNull PostHurtServerEvent> ON_HURT_SERVER_POST = EventFactory.createArrayBacked(PostHurtServerEvent.class, callbacks -> (LivingEntity target, ServerLevel level, DamageSource source, float damage) -> {
        for (PostHurtServerEvent callback : callbacks) {
            callback.onHurt(target, level, source, damage);
        }
    });

    public interface PostHurtServerEvent {
        public void onHurt(LivingEntity target, ServerLevel level, DamageSource source, float damage);
    }

}
