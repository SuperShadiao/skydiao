package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 价格 / 博物馆数据接口的连续失败计数。
 *
 * <p>每个 endpoint 独立计数，成功一次（含 HTTP 304）即清零；
 * 连续失败超过 {@link #FAILURE_THRESHOLD} 次时在聊天栏提示一次，
 * 之后最多每 {@link #MESSAGE_COOLDOWN_MS} 毫秒再提示一次，避免刷屏。</p>
 *
 * <p>只能由后台刷新线程调用，聊天消息通过 {@link ToolList#printChatMessage(Component)}
 * 调度回客户端线程执行。</p>
 */
public final class FetchFailureNotifier {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long MESSAGE_COOLDOWN_MS = 5 * 60 * 1000L;

    private static final String MESSAGE =
            "§a[小沙雕] §c无法拉取Bazaar/Auction House/Museum数据, 详情请检查日志, 若问题频繁出现, 请检查网络状态或寻找小沙雕解决";

    /** endpoint -> 连续失败次数 */
    private static final Map<String, Integer> CONSECUTIVE_FAILURES = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_MESSAGE_AT = new AtomicLong(0L);

    private FetchFailureNotifier() {
    }

    public static void recordSuccess(String endpoint) {
        CONSECUTIVE_FAILURES.remove(endpoint);
    }

    public static void recordFailure(String endpoint) {
        int failures = CONSECUTIVE_FAILURES.merge(endpoint, 1, Integer::sum);
        if (failures > FAILURE_THRESHOLD) {
            notifyOnce();
        }
    }

    private static void notifyOnce() {
        long now = System.currentTimeMillis();
        long last = LAST_MESSAGE_AT.get();
        if (now - last < MESSAGE_COOLDOWN_MS) return;
        if (!LAST_MESSAGE_AT.compareAndSet(last, now)) return;

        ToolList.printChatMessage(Component.literal(MESSAGE));
    }
}
