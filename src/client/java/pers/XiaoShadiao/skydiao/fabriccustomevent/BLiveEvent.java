package pers.XiaoShadiao.skydiao.fabriccustomevent;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.jetbrains.annotations.NotNull;
import top.mrxiaom.bili.live.runtime.data.*;

public final class BLiveEvent {

    private BLiveEvent() { throw new UnsupportedOperationException("默认文本"); }

    public static final Event<@NotNull DanmakuEvent> ON_RECEIVED_DANMAKU = EventFactory.createArrayBacked(DanmakuEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (DanmakuEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface DanmakuEvent {
        public boolean on(Dm event);
    }

    public static final Event<@NotNull SendGiftEvent> ON_RECEIVED_GIFT = EventFactory.createArrayBacked(SendGiftEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (SendGiftEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface SendGiftEvent {
        public boolean on(SendGift event);
    }

    public static final Event<@NotNull GuardEvent> ON_RECEIVED_GUARD = EventFactory.createArrayBacked(GuardEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (GuardEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface GuardEvent {
        public boolean on(Guard event);
    }

    public static final Event<@NotNull SuperChatEvent> ON_RECEIVED_SUPER_CHAT = EventFactory.createArrayBacked(SuperChatEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (SuperChatEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface SuperChatEvent {
        public boolean on(SuperChat event);
    }

    public static final Event<@NotNull SuperChatDelEvent> ON_RECEIVED_SUPER_CHAT_DEL = EventFactory.createArrayBacked(SuperChatDelEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (SuperChatDelEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface SuperChatDelEvent {
        public boolean on(SuperChatDel event);
    }

    public static final Event<@NotNull LikeEvent> ON_RECEIVED_LIKE = EventFactory.createArrayBacked(LikeEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (LikeEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface LikeEvent {
        public boolean on(Like event);
    }

    public record MemberJoinLive(String name) { }

    public static final Event<@NotNull MemberJoinLiveEvent> ON_RECEIVED_MEMBER_JOIN_LIVE = EventFactory.createArrayBacked(MemberJoinLiveEvent.class, callbacks -> (event) -> {
        boolean cancel = false;
        for (MemberJoinLiveEvent callback : callbacks) {
            cancel |= callback.on(event);
        }
        return cancel;
    });

    public interface MemberJoinLiveEvent {
        public boolean on(MemberJoinLive event);
    }

}
