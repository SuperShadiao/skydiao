package pers.XiaoShadiao.skydiao.utils.crystalhollows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static pers.XiaoShadiao.skydiao.utils.crystalhollows.StructureType.*;

class StructureScanManagerTest {
    private final ManualExecutor executor = new ManualExecutor();
    private final StructureScanManager<String> scans = new StructureScanManager<>(executor);

    @Test
    void disablingOneStructurePreservesOtherScansAndDropsQueuedResults() {
        scans.update(EnumSet.of(BLUE, GREEN), type -> List.of(cancelled -> type.name()));
        executor.runNext(); // BLUE has found a result, but the client has not consumed it yet.

        scans.update(EnumSet.of(GREEN), type -> fail("Existing scans must not restart"));
        executor.runNext();

        assertEquals(List.of(new StructureScanManager.Result<>(GREEN, "GREEN", null)), scans.pollResults());
        assertFalse(scans.isRunning(BLUE));
    }

    @Test
    void rapidOffOnDoesNotAcceptThePreviousGroupsResult() {
        scans.update(EnumSet.of(CORLEONE), type -> List.of(cancelled -> "old"));
        executor.runNext();
        scans.update(EnumSet.noneOf(StructureType.class), type -> fail("All scans are disabled"));
        scans.update(EnumSet.of(CORLEONE), type -> List.of(cancelled -> "new"));

        assertTrue(scans.pollResults().isEmpty());
        assertTrue(scans.isRunning(CORLEONE));
        executor.runNext();
        assertEquals("new", scans.pollResults().getFirst().value());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completedScansStayIdleUntilExplicitlyReenabled(boolean found) {
        scans.update(EnumSet.of(BEAR3), type -> List.of(cancelled -> found ? "position" : null));
        executor.runNext();
        assertEquals(1, scans.pollResults().size());

        scans.update(EnumSet.of(BEAR3), type -> fail("Completed targets must not restart each tick"));
        assertTrue(scans.pollResults().isEmpty());
        assertFalse(scans.isRunning(BEAR3));

        scans.update(EnumSet.noneOf(StructureType.class), type -> fail("All scans are disabled"));
        scans.update(EnumSet.of(BEAR3), type -> List.of(cancelled -> "rescan"));
        assertTrue(scans.isRunning(BEAR3));
        executor.runNext();
        assertEquals("rescan", scans.pollResults().getFirst().value());
    }

    @Test
    void fairyGrottoReportsOneDiscoveryAcrossAllFourRegions() {
        AtomicInteger started = new AtomicInteger();
        StructureScanManager.ScanTask<String> task = cancelled -> "region-" + started.incrementAndGet();
        scans.update(EnumSet.of(FAIRY_GROTTO), type -> List.of(task, task, task, task));
        // Two workers can discover the structure before the next client tick.
        executor.runNext();
        executor.runNext();
        assertEquals(List.of(new StructureScanManager.Result<>(FAIRY_GROTTO, "region-1", null)), scans.pollResults());

        executor.runAll(); // The other two queued regions were cancelled.
        assertEquals(2, started.get());
        assertTrue(scans.pollResults().isEmpty());
    }

    @Test
    void notFoundIsReportedOnlyAfterEveryRegionFinishes() {
        StructureScanManager.ScanTask<String> task = cancelled -> null;
        scans.update(EnumSet.of(FAIRY_GROTTO), type -> List.of(task, task, task, task));
        for (int i = 0; i < 3; i++) {
            executor.runNext();
            assertTrue(scans.pollResults().isEmpty());
            assertTrue(scans.isRunning(FAIRY_GROTTO));
        }
        executor.runNext();
        assertEquals(List.of(new StructureScanManager.Result<String>(FAIRY_GROTTO, null, null)), scans.pollResults());
    }

    @Test
    void masterOffCancelsAllQueuedRegionsWithoutRunningThem() {
        AtomicInteger started = new AtomicInteger();
        scans.update(EnumSet.allOf(StructureType.class), type -> List.of(cancelled -> {
            started.incrementAndGet();
            return "position";
        }));
        scans.update(EnumSet.noneOf(StructureType.class), type -> fail("All scans are disabled"));
        executor.runAll();
        assertEquals(0, started.get());
        assertTrue(scans.pollResults().isEmpty());
    }

    @Test
    void worldResetDiscardsOldResultsAndAllowsScanningTheSameTypesAgain() {
        scans.update(EnumSet.of(BLUE, WORM_FISH_SPOT), type -> List.of(cancelled -> "old world"));
        executor.runNext();
        scans.cancelAll();
        scans.update(EnumSet.of(BLUE), type -> List.of(cancelled -> "new world"));
        executor.runAll();
        assertEquals(List.of(new StructureScanManager.Result<>(BLUE, "new world", null)), scans.pollResults());
    }

    @Test
    void changingThreadLimitRestartsOnlyUnfinishedStructures() {
        scans.update(EnumSet.of(BLUE, GREEN), type -> List.of(cancelled -> type.name()));
        executor.runNext();
        assertEquals(BLUE, scans.pollResults().getFirst().type());

        ManualExecutor replacement = new ManualExecutor();
        scans.replaceExecutor(replacement);
        assertTrue(executor.isShutdown());
        scans.update(EnumSet.of(BLUE, GREEN), type -> {
            assertEquals(GREEN, type);
            return List.of(cancelled -> "restarted");
        });
        replacement.runAll();
        assertEquals(List.of(new StructureScanManager.Result<>(GREEN, "restarted", null)), scans.pollResults());
    }

    @Test
    void workerFailuresAreDistinguishedFromNotFound() {
        IllegalStateException failure = new IllegalStateException("unavailable chunk");
        scans.update(EnumSet.of(FAIRY_GROTTO), type -> List.of(
                cancelled -> { throw failure; }, cancelled -> null));
        executor.runAll();
        StructureScanManager.Result<String> result = scans.pollResults().getFirst();
        assertNull(result.value());
        assertSame(failure, result.failure());
    }

    @Test
    void aSuccessfulRegionCanCompleteAGroupAfterAnotherRegionFails() {
        scans.update(EnumSet.of(FAIRY_GROTTO), type -> List.of(
                cancelled -> { throw new IllegalStateException("unavailable chunk"); },
                cancelled -> "found"));
        executor.runAll();
        assertEquals(List.of(new StructureScanManager.Result<>(FAIRY_GROTTO, "found", null)), scans.pollResults());
    }

    @Test
    void allRunningFairyRegionsObserveCancellationOnTheWorkStealingPool() throws Exception {
        ExecutorService workers = Executors.newWorkStealingPool(4);
        StructureScanManager<String> threaded = new StructureScanManager<>(workers);
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch stopped = new CountDownLatch(4);
        StructureScanManager.ScanTask<String> task = cancelled -> {
            started.countDown();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!cancelled.getAsBoolean() && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                // A late discovery from a cancelled worker must never be announced.
                return "late result";
            } finally {
                stopped.countDown();
            }
        };
        try {
            threaded.update(EnumSet.of(FAIRY_GROTTO), type -> List.of(task, task, task, task));
            assertTrue(started.await(5, TimeUnit.SECONDS), "All four regions should start");
            threaded.update(EnumSet.noneOf(StructureType.class), type -> fail("All scans are disabled"));
            assertTrue(stopped.await(5, TimeUnit.SECONDS), "Cancellation must not depend on Future interrupts");
            assertTrue(threaded.pollResults().isEmpty());
        } finally {
            threaded.cancelAll();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        private void runNext() {
            tasks.removeFirst().run();
        }

        private void runAll() {
            while (!tasks.isEmpty()) runNext();
        }

        @Override
        public void execute(Runnable task) {
            tasks.addLast(task);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
    }
}
