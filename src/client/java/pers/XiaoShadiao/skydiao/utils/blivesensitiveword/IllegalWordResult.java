package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class IllegalWordResult {
    // 标记是否扫描中文/英文敏感词（volatile保证可见性）
    public volatile boolean C, E;
    // 是否包含敏感词（volatile保证可见性）
    public volatile boolean isIllegal;
    // 原始字符串和过滤后字符串（String本身不可变，无需额外保护）
    public String beforeTransfer;
    public String transfered;
    // 检测到的敏感词集合（线程安全的ConcurrentHashMap）
    public final Set<String> words = ConcurrentHashMap.newKeySet();
    // 替换次数统计（AtomicInteger保证原子性）
    public final AtomicInteger replaceCount = new AtomicInteger(0);

    public IllegalWordResult() {}

    @Override
    public String toString() {
        return "IllegalWordResult{" +
                "C=" + C +
                ", E=" + E +
                ", isIllegal=" + isIllegal +
                ", beforeTransfer='" + beforeTransfer + '\'' +
                ", transfered='" + transfered + '\'' +
                ", words=" + words +
                ", replaceCount=" + replaceCount +
                '}';
    }
}
