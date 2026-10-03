package pers.XiaoShadiao.skydiao.utils.crystalhollows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Owns one scan group per enabled structure. All public methods run on the client thread;
 * workers only read cancellation flags and publish immutable completion events.
 */
public final class StructureScanManager<T> {
    @FunctionalInterface
    public interface ScanTask<T> {
        T scan(BooleanSupplier cancelled) throws Exception;
    }

    public record Result<T>(StructureType type, T value, Exception failure) { }

    private record Completion<T>(ScanGroup group, T value, Exception failure) { }

    private static final class ScanGroup {
        private final StructureType type;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final List<Future<?>> tasks = new ArrayList<>();
        private int remaining;
        private boolean completed;
        private Exception failure;

        private ScanGroup(StructureType type, int remaining) {
            this.type = type;
            this.remaining = remaining;
        }

        private boolean shouldStop() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }

        private void cancel() {
            // ForkJoinTask.cancel(true) does not guarantee an interrupt. The token is authoritative.
            cancelled.set(true);
            tasks.forEach(task -> task.cancel(true));
            tasks.clear();
        }
    }

    private ExecutorService executor;
    private final EnumMap<StructureType, ScanGroup> groups = new EnumMap<>(StructureType.class);
    private final ConcurrentLinkedQueue<Completion<T>> completions = new ConcurrentLinkedQueue<>();

    public StructureScanManager(ExecutorService executor) {
        this.executor = executor;
    }

    public void update(Set<StructureType> enabled,
                       Function<StructureType, List<ScanTask<T>>> taskFactory) {
        groups.entrySet().removeIf(entry -> {
            if (enabled.contains(entry.getKey())) return false;
            entry.getValue().cancel();
            return true;
        });
        for (StructureType type : enabled) {
            // Completed groups remain registered until disabled or the world changes.
            if (groups.containsKey(type)) continue;
            List<ScanTask<T>> tasks = taskFactory.apply(type);
            if (tasks.isEmpty()) throw new IllegalArgumentException("No scanners for " + type);
            ScanGroup group = new ScanGroup(type, tasks.size());
            groups.put(type, group);
            for (ScanTask<T> task : tasks) {
                group.tasks.add(executor.submit(() -> runTask(group, task)));
            }
        }
    }

    private void runTask(ScanGroup group, ScanTask<T> task) {
        if (group.shouldStop()) return;
        T value = null;
        Exception failure = null;
        try {
            value = task.scan(group::shouldStop);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = e;
        } catch (Exception e) {
            failure = e;
        }
        if (!group.cancelled.get()) {
            completions.add(new Completion<>(group, value, failure));
        }
    }

    public List<Result<T>> pollResults() {
        List<Result<T>> results = new ArrayList<>();
        Completion<T> completion;
        while ((completion = completions.poll()) != null) {
            ScanGroup group = completion.group();
            // Group identity also rejects late results after rapid toggles and world changes.
            if (groups.get(group.type) != group || group.completed || group.cancelled.get()) continue;
            group.remaining--;
            if (completion.failure() != null) group.failure = completion.failure();
            if (completion.value() != null || group.remaining == 0) {
                group.completed = true;
                group.cancel();
                results.add(new Result<>(group.type, completion.value(),
                        completion.value() == null ? group.failure : null));
            }
        }
        return results;
    }

    public boolean isRunning(StructureType type) {
        ScanGroup group = groups.get(type);
        return group != null && !group.completed;
    }

    public void cancelAll() {
        groups.values().forEach(ScanGroup::cancel);
        groups.clear();
        completions.clear();
    }

    public void replaceExecutor(ExecutorService replacement) {
        // Restart unfinished scans on the next update without announcing completed targets again.
        groups.entrySet().removeIf(entry -> {
            if (entry.getValue().completed) return false;
            entry.getValue().cancel();
            return true;
        });
        ExecutorService previous = executor;
        executor = replacement;
        previous.shutdownNow();
    }
}
