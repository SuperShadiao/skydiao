package pers.XiaoShadiao.skydiao.utils.pathfinderv2;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * A1生成的
 */

public class PathFinderV2 {

    public interface PathFinderConfig {

        public boolean isAllowBreak();
        public boolean isAllowPlace();

    }

    /** 起始节点默认使用的移动方式 */
    private static final PathNodeType DEFAULT_START_TYPE = PathNodeType.MOVE;

    private static final PathFinderConfig DEFAULT_CONFIG = new PathFinderConfig() {
        @Override
        public boolean isAllowBreak() {
            return ConfigManager.pfAllowBreak.getValue();
        }

        @Override
        public boolean isAllowPlace() {
            return ConfigManager.pfAllowPlace.getValue();
        }
    };

    private final Executor executor;

    private BlockPos startPos = BlockPos.ZERO;
    private BlockPos endPos = BlockPos.ZERO;

    public BlockPos getStartPos() {
        return startPos;
    }
    public BlockPos getEndPos() {
        return endPos;
    }

    // 搜索过程中的上下文快照，供外部渲染 / 调试
    // 注意：搜索进行中 cameFrom 会变化，跨线程读取请在 future 完成后
    private volatile PathContext currentContext = PathContext.EMPTY;
    private volatile PathContext lowestCostContext = PathContext.EMPTY;
    private volatile PathContext lowestDistanceContext = PathContext.EMPTY;

    // 与上面两个 context 配套的度量值，便于比较
    private volatile double lowestCostF = Double.MAX_VALUE;
    private volatile double lowestDistanceH = Double.MAX_VALUE;

    private final PathFinderConfig config;

    public PathFinderConfig getConfig() {
        return config;
    }

    public PathFinderV2() {
        this(DEFAULT_CONFIG);
    }

    public PathFinderV2(PathFinderConfig config) {
        this.executor = ForkJoinPool.commonPool();
        this.config = config;
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
                 () -> findPath(start, goalPos),
                 executor
        ).exceptionallyAsync((t) -> {
            t.printStackTrace();

            Throwable cause = (t instanceof CompletionException && t.getCause() != null)
                    ? t.getCause()
                    : t;

            PathFinderV2State state = (cause instanceof CancellationException)
                    ? PathFinderV2State.CANCELLED
                    : PathFinderV2State.EXCEPTION;

            return new PathNodes(List.of(), 0.0, state);
        });
    }

    /**
     * 同步寻路，核心逻辑。
     */
    public PathNodes findPath(SearchKey start, BlockPos goalPos) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goalPos, "goalPos");

        startPos = start.pos();
        endPos = goalPos;

        // 每次搜索重置
        currentContext = PathContext.EMPTY;
        lowestCostContext = PathContext.EMPTY;
        lowestDistanceContext = PathContext.EMPTY;
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

            if (!ToolList.mc.level.isLoaded(cur.pos())) {
                List<PathNode> path = reconstruct(cameFrom, lowestDistanceContext.current());
                return new PathNodes(path, gScore.getDouble(lowestDistanceContext.current()), PathFinderV2State.TOUCH_BORDER);
            }

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
            List<PathNode> path = reconstruct(cameFrom, lowestDistanceContext.current());
            return new PathNodes(path, gScore.getDouble(lowestDistanceContext.current()), PathFinderV2State.NOT_FOUND);
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

        List<SearchKey> move = List.of(
                new SearchKey(cur.pos().offset(1, 0, 0), PathNodeType.MOVE),
                new SearchKey(cur.pos().offset(-1, 0, 0), PathNodeType.MOVE),
                new SearchKey(cur.pos().offset(0, 0, 1), PathNodeType.MOVE),
                new SearchKey(cur.pos().offset(0, 0, -1), PathNodeType.MOVE)
        );
        if(cur.type == PathNodeType.FLY) {
            move = move.stream().map(k -> {
                if(!BlockHelper.canStepOn(k.pos().below())) {
                    return k.setType(PathNodeType.FLY);
                }
                return k;
            }).toList();
        }

        List<SearchKey> up = List.of(
                new SearchKey(cur.pos().offset(0, 1, 0), cur.type == PathNodeType.UP || cur.type == PathNodeType.FLY ? PathNodeType.FLY : PathNodeType.UP)
        );

        List<SearchKey> down = List.of(
                new SearchKey(cur.pos().offset(0, -1, 0), cur.type == PathNodeType.FLY && !BlockHelper.canStepOn(cur.pos().offset(0, -2, 0)) ? PathNodeType.FLY : PathNodeType.DOWN)
        );

        List<SearchKey> jumpMove = List.of(

                new SearchKey(cur.pos().offset(2, 0, 0), PathNodeType.JUMP1),
                new SearchKey(cur.pos().offset(-2, 0, 0), PathNodeType.JUMP1),
                new SearchKey(cur.pos().offset(0, 0, 2), PathNodeType.JUMP1),
                new SearchKey(cur.pos().offset(0, 0, -2), PathNodeType.JUMP1),

                new SearchKey(cur.pos().offset(3, 0, 0), PathNodeType.JUMP2),
                new SearchKey(cur.pos().offset(-3, 0, 0), PathNodeType.JUMP2),
                new SearchKey(cur.pos().offset(0, 0, 3), PathNodeType.JUMP2),
                new SearchKey(cur.pos().offset(0, 0, -3), PathNodeType.JUMP2),

                new SearchKey(cur.pos().offset(4, 0, 0), PathNodeType.JUMP3),
                new SearchKey(cur.pos().offset(-4, 0, 0), PathNodeType.JUMP3),
                new SearchKey(cur.pos().offset(0, 0, 4), PathNodeType.JUMP3),
                new SearchKey(cur.pos().offset(0, 0, -4), PathNodeType.JUMP3),

                new SearchKey(cur.pos().offset(2, 1, 0), PathNodeType.JUMP1_UP),
                new SearchKey(cur.pos().offset(-2, 1, 0), PathNodeType.JUMP1_UP),
                new SearchKey(cur.pos().offset(0, 1, 2), PathNodeType.JUMP1_UP),
                new SearchKey(cur.pos().offset(0, 1, -2), PathNodeType.JUMP1_UP),

                new SearchKey(cur.pos().offset(3, 1, 0), PathNodeType.JUMP2_UP),
                new SearchKey(cur.pos().offset(-3, 1, 0), PathNodeType.JUMP2_UP),
                new SearchKey(cur.pos().offset(0, 1, 3), PathNodeType.JUMP2_UP),
                new SearchKey(cur.pos().offset(0, 1, -3), PathNodeType.JUMP2_UP)

                );

        if(cur.type == PathNodeType.FLY) {
            jumpMove = List.of();
        }

        return Stream.of(move, up, down, jumpMove).flatMap(List::stream).toList();
    }

    /**
     * 能否从 ctx.current() 移动到 next。
     * ctx 提供反向路径历史，可用 ctx.reversed(maxSteps) 惰性遍历。
     */
    private boolean canMove(PathContext ctx, SearchKey next) {
        if (BlockHelper.hasCollision(next.pos()) || BlockHelper.hasCollision(next.pos().above())) {
            return false;
        }
        if (next.type().isJumpMove()) {
            if (
                    BlockHelper.areaHasCollision(ctx.current.pos().below(), next.pos().above(2), List.of(next.pos().below(), ctx.current.pos().below(), next.pos().below(2)))
            ) {
                return false;
            }

            if (!BlockHelper.canStepOn(ctx.current.pos().below())) {
                return false;
            }
        }
        if (next.type().isMove()) {
            if (
                    ((ctx.current.type().isMove() || ctx.current.type() == PathNodeType.DOWN) && !BlockHelper.canStepOn(ctx.current.pos().below())) ||
                    (ctx.current.type().isJump() && ctx.getCurrentOffset(1).type().isMove() && !BlockHelper.canStepOn(ctx.getCurrentOffset(1).pos().below()))
            ) {
                return false;
            }
        }
        if (!ToolList.mc.player.getAbilities().mayfly) {
            if(next.type() == PathNodeType.FLY) {
                return false;
            }
            if(next.type().isJump() && ctx.current.type().isJump() && !BlockHelper.canStepOn(ctx.current.pos().below())) {
                return false;
            }
            if(ctx.current.type() == PathNodeType.DOWN && (next.type().isJump() || next.type().isMove()) && !BlockHelper.canStepOn(next.pos().below())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 从 ctx.current() 到 next 的实际代价。
     * 只在 canMove 返回 true 后调用。
     */
    private double moveCost(PathContext ctx, SearchKey next) {
        double ret;
        if(next.type().isJumpMove()) {
            ret = 5;
        } else if(next.type() == PathNodeType.FLY) {
            ret = 0.05;
        } else {
            ret = 0.4;
        }

        if(BlockPos.betweenClosedStream(ctx.current.pos().offset(1, 1, 1), ctx.current.pos().offset(-1, 0, -1)).anyMatch(BlockHelper::hasCollision)) {
            ret += 1;
        }

        return ret;
    }

    /**
     * 启发式：只依赖 pos，忽略 type，不要高估，否则 A* 不再最优。
     */
    private double heuristic(SearchKey from, BlockPos goal) {
        BlockPos p = from.pos();
        double dx = p.getX() - goal.getX();
        double dy = p.getY() - goal.getY();
        double dz = p.getZ() - goal.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

        return optimizePath(new ArrayList<>(deque));
    }

    private List<PathNode> optimizePath(List<PathNode> path) {
        for(int i = path.size() - 1; i > 3; i--) {
            if(path.get(i - 1).type().isJumpMove()) continue;
            BlockPos vec1 = path.get(i).pos();
            BlockPos vec2 = path.get(i - 1).pos();
            BlockPos vec3 = path.get(i - 2).pos();

            if(
                    (Math.abs(vec1.getX() - vec3.getX()) == 1 || Math.abs(vec1.getZ() - vec3.getZ()) == 1) &&
                            (vec1.getY() - vec2.getY()) == 0 &&
                            (vec2.getY() - vec3.getY()) == 1
                            &&
                            (!isActuallyAllowBreak() || (!BlockHelper.hasCollision(vec2) && !BlockHelper.hasCollision(vec2.above()))) &&
                            (!isActuallyAllowPlace() || (BlockHelper.hasCollision(vec2.below())))
            ) {
                path.remove(i - 1);
            }
        }

        for(int i = path.size() - 1; i > 3; i--) {
            if(path.get(i - 1).type().isJumpMove()) continue;
            BlockPos vec1 = path.get(i).pos();
            BlockPos vec2 = path.get(i - 1).pos();
            BlockPos vec3 = path.get(i - 2).pos();
            if(
                    Math.abs(vec1.getX() - vec3.getX()) == 1 &&
                            Math.abs(vec1.getZ() - vec3.getZ()) == 1 &&
                            /*Math.abs*/(vec1.getY() - vec3.getY()) >= -2 && (vec1.getY() - vec3.getY()) <= 0 &&
                            vec2.getY() <= vec3.getY() && vec2.getY() <= vec1.getY() &&
                            (
                                    ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec1.below())) == ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec3.below())) ||
                                            !ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec1.below())) && ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec3.below()))
                            ) &&
                            (!isActuallyAllowBreak() || (!BlockHelper.hasCollision(vec2) && !BlockHelper.hasCollision(vec2.above()))) &&
                            (!isActuallyAllowPlace() || (BlockHelper.hasCollision(vec2.below())))
            ) {
                path.remove(i - 1);
            }
        }
        return path;
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
        public SearchKey setType(PathNodeType type) {
            return new SearchKey(pos, type);
        }
    }

    // ==================================================================
    // 内部类：PathContext 提供反向惰性路径历史
    // ==================================================================

    public boolean isActuallyAllowBreak() {
        return config.isAllowBreak() && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted();
    }

    public boolean isActuallyAllowPlace() {
        return config.isAllowPlace() && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted() && hasBlockInInventory();
    }

    private static boolean hasBlockInInventory() {
        LocalPlayer player = ToolList.mc.player;
        for (int i = 0; i < 9; i++) { // 检查物品栏的前 9 个槽
            ItemStack itemStack = player.getInventory().getItem(i);
            if (itemStack.getItem() instanceof BlockItem) {
                return true;
            }
        }
        return false; // 未找到方块，返回 false
    }

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

        private static final PathContext EMPTY = new PathContext(Map.of(), new SearchKey(BlockPos.ZERO, PathNodeType.MOVE));

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

        public SearchKey getCurrentOffset(int offset) {
            Iterator<SearchKey> it = reversed();
            SearchKey cur = it.next();
            for (int i = 0; i < offset && it.hasNext(); i++) {
                cur = it.next();
            }
            return cur;
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