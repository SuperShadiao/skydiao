package pers.XiaoShadiao.skydiao.utils.pathfinderv2;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * A1生成的
 */

public class PathFinderV2 {

    /** 起始节点默认使用的移动方式 */
    private static final PathNodeType DEFAULT_START_TYPE = PathNodeType.MOVE;

    private final Executor executor;

    // 搜索过程中的上下文快照，供外部渲染 / 调试
    // 注意：搜索进行中 cameFrom 会变化，跨线程读取请在 future 完成后
    private volatile PathContext currentContext;
    private volatile PathContext lowestCostContext;
    private volatile PathContext lowestDistanceContext;

    // 与上面两个 context 配套的度量值，便于比较
    private volatile double lowestCostF = Double.MAX_VALUE;
    private volatile double lowestDistanceH = Double.MAX_VALUE;

    public PathFinderV2(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public PathFinderV2() {
        this(ForkJoinPool.commonPool());
    }

    // ------------------------------------------------------------------
    // 公开 API
    // ------------------------------------------------------------------

    /**
     * 异步寻路。start 使用默认类型 MOVE。
     */
    public CompletableFuture<PathNodes> findPathSync(BlockPos start, BlockPos goalPos) {
        return findPathSync(new SearchKey(start, DEFAULT_START_TYPE), goalPos);
    }

    /**
     * 异步寻路，允许指定起始 SearchKey。
     */
    public CompletableFuture<PathNodes> findPathSync(SearchKey start, BlockPos goalPos) {
        return CompletableFuture.supplyAsync(
                 () -> {
                     try {
                         return findPath(start, goalPos);
                     } catch (Throwable t) {
                         // 记录日志后返回空路径，避免吞异常
                         t.printStackTrace();
                         return new PathNodes(List.of(), 0.0, PathFinderV2State.EXCEPTION);
                     }
                 },
                 executor
         );
    }

    /**
     * 同步寻路，核心逻辑。
     */
    public PathNodes findPath(SearchKey start, BlockPos goalPos) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goalPos, "goalPos");

        // 每次搜索重置
        currentContext = null;
        lowestCostContext = null;
        lowestDistanceContext = null;
        lowestCostF = Double.MAX_VALUE;
        lowestDistanceH = Double.MAX_VALUE;

        Object2DoubleMap<SearchKey> gScore = new Object2DoubleOpenHashMap<>();
        Map<SearchKey, SearchKey> cameFrom = new HashMap<>();
        Set<SearchKey> closed = new HashSet<>();

        PriorityQueue<SearchKey> open = new PriorityQueue<>(
                Comparator.comparingDouble(k ->
                        gScore.getOrDefault(k, Double.MAX_VALUE) + heuristic(k, goalPos))
        );

        gScore.put(start, 0.0);
        open.add(start);

        SearchKey goalKey = null;

        while (!open.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                return new PathNodes(List.of(), 0.0, PathFinderV2State.CANCELLED);
            }

            SearchKey cur = open.poll();

            if (closed.contains(cur)) {
                continue;
            }
            closed.add(cur);

            // ---- 构造并记录 currentContext ----
            PathContext ctx = new PathContext(cameFrom, cur);
            currentContext = ctx;

            double g = gScore.getDouble(cur);
            double h = heuristic(cur, goalPos);

            // ---- 记录 lowestCostContext（f = g + h 最小）----
            double f = g + h;
            if (f < lowestCostF) {
                lowestCostF = f;
                lowestCostContext = ctx;
            }

            // ---- 记录 lowestDistanceContext（h 最小，离目标最近）----
            if (h < lowestDistanceH) {
                lowestDistanceH = h;
                lowestDistanceContext = ctx;
            }

            // 到达目标
            if (cur.pos().equals(goalPos)) {
                goalKey = cur;
                break;
            }

            for (SearchKey next : neighbors(cur)) {
                if (closed.contains(next)) continue;
                if (!canMove(ctx, next)) continue;

                double tentative = g + moveCost(ctx, next);

                if (tentative < gScore.getOrDefault(next, Double.MAX_VALUE)) {
                    gScore.put(next, tentative);
                    cameFrom.put(next, cur);
                    open.add(next);
                }
            }
        }

        if (goalKey == null) {
            return new PathNodes(List.of(), 0.0, PathFinderV2State.NOT_FOUND);
        }

        List<PathNode> path = reconstruct(cameFrom, goalKey);
        return new PathNodes(path, gScore.getDouble(goalKey), PathFinderV2State.DONE);
    }

    // ------------------------------------------------------------------
    // 规则方法（按你的世界 / 移动规则实现）
    // ------------------------------------------------------------------

    /**
     * 生成候选邻居（只做几何 / 类型枚举，不做合法性判断）。
     * 合法性统一交给 canMove。
     */
    private List<SearchKey> neighbors(SearchKey cur) {
        // TODO: 根据 cur.pos() 和 cur.type() 枚举候选的 (pos, type)
        return List.of();
    }

    /**
     * 能否从 ctx.current() 移动到 next。
     * ctx 提供反向路径历史，可用 ctx.reversed(maxSteps) 惰性遍历。
     */
    private boolean canMove(PathContext ctx, SearchKey next) {
        // TODO: 按你的规则实现
        // 示例：最近 3 步内连续 FLY 次数 >= 3 则不允许再 FLY
        // if (next.type() == PathNodeType.FLY) {
        //     int streak = 0;
        //     Iterator<SearchKey> it = ctx.reversed(3);
        //     while (it.hasNext()) {
        //         if (it.next().type() == PathNodeType.FLY) streak++;
        //         else break;
        //     }
        //     if (streak >= 3) return false;
        // }
        return true;
    }

    /**
     * 从 ctx.current() 到 next 的实际代价。
     * 只在 canMove 返回 true 后调用。
     */
    private double moveCost(PathContext ctx, SearchKey next) {
        // TODO: 按移动方式算代价
        return 1.0;
    }

    /**
     * 启发式：只依赖 pos，忽略 type，不要高估，否则 A* 不再最优。
     */
    private double heuristic(SearchKey from, BlockPos goal) {
        BlockPos p = from.pos();
        return Math.abs(p.getX() - goal.getX())
                + Math.abs(p.getY() - goal.getY())
                + Math.abs(p.getZ() - goal.getZ());
    }

    // ------------------------------------------------------------------
    // 路径重建
    // ------------------------------------------------------------------

    /**
     * 从终点回溯到起点，返回起点 -> 终点顺序的路径。
     */
    private List<PathNode> reconstruct(Map<SearchKey, SearchKey> cameFrom, SearchKey goal) {
        Deque<PathNode> deque = new ArrayDeque<>();
        SearchKey cur = goal;

        while (cur != null) {
            deque.addFirst(new PathNode(cur.pos(), cur.type()));
            cur = cameFrom.get(cur);
        }

        return new ArrayList<>(deque);
    }

    /**
     * 当前正在计算的节点上下文。
     * 搜索进行中会不断变化；搜索结束后停在最后一个被弹出的节点。
     */
    public PathContext getCurrentContext() {
        return currentContext;
    }

    /**
     * 搜索过程中 f = g + h 最小的节点上下文。
     * 不一定是最优路径终点，但通常是"目前看来最接近最优"的搜索前沿。
     */
    public PathContext getLowestCostContext() {
        return lowestCostContext;
    }

    /**
     * 搜索过程中 h（到目标的启发式距离）最小的节点上下文。
     * 即"目前离目标最近"的节点，不保证代价最优。
     * 适合用来渲染"搜索正在往目标靠拢"。
     */
    public PathContext getLowestDistanceContext() {
        return lowestDistanceContext;
    }

    // ==================================================================
    // 内部类：SearchKey 语义 = 搜索状态
    // ==================================================================

    public record SearchKey(BlockPos pos, PathNodeType type) {
    }

    // ==================================================================
    // 内部类：PathContext 提供反向惰性路径历史
    // ==================================================================

    @FunctionalInterface
    public interface PathIteratorFactory {
        @NotNull Iterator<SearchKey> create(int maxSteps);
    }

    /**
     * 搜索过程中传给 canMove / moveCost 的上下文。
     * 构造 O(1)；路径历史通过 reversed(maxSteps) 惰性获取。
     *
     * 迭代顺序：current -> parent -> grandparent -> ... -> start
     * 即第一个 next() 就是当前节点，第二个是它的父节点。
     */
    public static final class PathContext {

        private final SearchKey current;
        private final PathIteratorFactory factory;

        public PathContext(Map<SearchKey, SearchKey> cameFrom, SearchKey current) {
            this.current = Objects.requireNonNull(current, "current");
            Objects.requireNonNull(cameFrom, "cameFrom");
            this.factory = maxSteps -> new ReversePathIterator(cameFrom, current, maxSteps);
        }

        public PathContext(SearchKey current, PathIteratorFactory factory) {
            this.current = Objects.requireNonNull(current, "current");
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        public SearchKey current() {
            return current;
        }

        public BlockPos currentPos() {
            return current.pos();
        }

        public PathNodeType currentType() {
            return current.type();
        }

        public Iterator<SearchKey> reversed() {
            return factory.create(Integer.MAX_VALUE);
        }

        public Iterator<SearchKey> reversed(int maxSteps) {
            if (maxSteps <= 0) {
                return Collections.emptyIterator();
            }
            return factory.create(maxSteps);
        }

        public int consecutiveTypeCount(PathNodeType type, int maxSteps) {
            Iterator<SearchKey> it = reversed(maxSteps);
            int count = 0;
            while (it.hasNext()) {
                if (it.next().type() == type) {
                    count++;
                } else {
                    break;
                }
            }
            return count;
        }

        // ------------------------------------------------------------------
        // 新增：正向完整路径
        // ------------------------------------------------------------------

        /**
         * 正向 SearchKey 序列：start -> ... -> current。
         * 每次调用都是 O(depth)，会新建 ArrayList。
         * 不要在搜索主循环里频繁调用，用于渲染 / 调试。
         */
        public List<SearchKey> getForwardKeys() {
            List<SearchKey> keys = new ArrayList<>();
            Iterator<SearchKey> it = factory.create(Integer.MAX_VALUE);
            while (it.hasNext()) {
                keys.add(it.next());
            }
            Collections.reverse(keys);
            return keys;
        }

        /**
         * 正向 PathNode 序列：start -> ... -> current。
         * 每次调用都是 O(depth)，会新建两个 List。
         * 用于渲染 / 外部消费。
         */
        public List<PathNode> getForwardPath() {
            List<SearchKey> keys = new ArrayList<>();
            Iterator<SearchKey> it = factory.create(Integer.MAX_VALUE);
            while (it.hasNext()) {
                keys.add(it.next());
            }
            Collections.reverse(keys);

            List<PathNode> path = new ArrayList<>(keys.size());
            for (SearchKey k : keys) {
                path.add(new PathNode(k.pos(), k.type()));
            }
            return path;
        }

        /**
         * 正向 PathNode 序列，附带深度限制（只取离 current 最近的 maxSteps 步）。
         * 返回顺序仍是 start -> ... -> current。
         */
        public List<PathNode> getForwardPath(int maxSteps) {
            if (maxSteps <= 0) {
                return List.of();
            }

            List<SearchKey> keys = new ArrayList<>(Math.min(maxSteps, 16));
            Iterator<SearchKey> it = factory.create(maxSteps);
            while (it.hasNext()) {
                keys.add(it.next());
            }
            Collections.reverse(keys);

            List<PathNode> path = new ArrayList<>(keys.size());
            for (SearchKey k : keys) {
                path.add(new PathNode(k.pos(), k.type()));
            }
            return path;
        }
    }

    /**
     * 反向迭代器：current -> ... -> start。
     * 单向消费，用完重新创建。
     */
    public record ReversePathIterable(ReversePathIterator iterator) implements Iterable<SearchKey> {}
    public static final class ReversePathIterator implements Iterator<SearchKey> {

        private final Map<SearchKey, SearchKey> cameFrom;
        private final int maxSteps;
        private SearchKey cursor;
        private int step;

        ReversePathIterator(Map<SearchKey, SearchKey> cameFrom, SearchKey current, int maxSteps) {
            this.cameFrom = cameFrom;
            this.cursor = current;
            this.maxSteps = maxSteps;
        }

        @Override
        public boolean hasNext() {
            return cursor != null && step < maxSteps;
        }

        @Override
        public SearchKey next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            SearchKey result = cursor;
            cursor = cameFrom.get(cursor);
            step++;
            return result;
        }
    }

    // ==================================================================
    // 可选：从外部注入迭代器工厂，方便单元测试
    // ==================================================================

    /**
     * 举例：用固定路径构造 PathContext，脱离 cameFrom 测试 canMove。
     */
    @SuppressWarnings("unused")
    public static PathContext contextFromList(List<SearchKey> currentToStart) {
        Objects.requireNonNull(currentToStart, "currentToStart");
        if (currentToStart.isEmpty()) {
            throw new IllegalArgumentException("empty path");
        }
        SearchKey current = currentToStart.get(0);
        return new PathContext(current, maxSteps -> new Iterator<>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < currentToStart.size() && i < maxSteps;
            }

            @Override
            public SearchKey next() {
                if (!hasNext()) throw new NoSuchElementException();
                return currentToStart.get(i++);
            }
        });
    }

}