package pers.XiaoShadiao.skydiao.utils.pathfinder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Miner extends Thread {

    private volatile boolean stopped;

    public void stop0() {
        stopped = true;
    }

    public interface ScoreGenerator {
        public double getScore();
    }

    public static final int[][] offsets = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private int currentToken;

    public int noTargetCount;
    public BlockPos nextPos;

    public double distance;
    public int noBlockFlag = 0;
    public Vec3 currentPosition1;
    public volatile BlockPos currentPosition2;
    public List<AbstractMap.SimpleEntry<BlockPos, ScoreGenerator>> goals = Collections.emptyList();

    public final Map<BlockPos, Integer> count = new HashMap<>();

    public final List<Map.Entry<Block, Integer>> targets;
    private double distanceToBlock;
    private final long startTime = System.currentTimeMillis();

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public Miner(List<Map.Entry<Block, Integer>> targets) {
        this.targets = targets;
        printInfo();
    }

    public Miner(Map.Entry<Block, Integer>... targets) {
        this.targets = Arrays.asList(targets);
        printInfo();
    }

    private void printInfo() {
        setName("Miner");
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e启动挖掘任务中: "));

        targets.removeIf(target -> {
            boolean b = target.getKey() == Blocks.AIR || target.getKey() == Blocks.WATER || target.getKey() == Blocks.LAVA;
            if(b) ToolList.printChatMessage(Component.literal("§a[小沙雕] §c非法方块: " + target.getKey().getDescriptionId() + ", 已移除"));
            return b;
        });


        for (Map.Entry<Block, Integer> target : targets) {
            ToolList.getInstance().log.info(target.getKey().getDescriptionId() + ": " + target.getValue());
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §6方块: " + target.getKey().getDescriptionId() + " ->  " + target.getKey().getName().getString() + ", 优先级: " + target.getValue()));
        }
    }

    private final Queue<Long> queue = new ConcurrentLinkedQueue<>();
    private long lastUpdateTime;
    public List<AbstractMap.SimpleEntry<BlockPos, ScoreGenerator>> updateGoals() {
        if (System.currentTimeMillis() - lastUpdateTime > 2000) {
            lastUpdateTime = System.currentTimeMillis();
            // 1. 初始化参数和数据结构
            BlockPos startPos = BlockPos.containing(ToolList.mc.player.getPosition(1));
            final int searchRadius = 50;
            ChunkPos chunkPos = ToolList.mc.player.chunkPosition();
            // 边界预计算（优化位置检查）
            final int minX = startPos.getX() - searchRadius;
            final int maxX = startPos.getX() + searchRadius;
            final int minZ = startPos.getZ() - searchRadius;
            final int maxZ = startPos.getZ() + searchRadius;

            // 使用更高效的并发集合
            queue.clear();
            final Queue<AbstractMap.SimpleEntry<BlockPos, ScoreGenerator>> resultQueue = new ConcurrentLinkedQueue<>();

            final AtomicBoolean isEnough = new AtomicBoolean(false);

            // 2. 初始化队列
            queue.add(startPos.asLong());
            if (currentPosition2 != null) {
                queue.add(currentPosition2.asLong());
            }

            // 3. 并行处理
            int processors = Math.max(Runtime.getRuntime().availableProcessors(), 1);
            List<Future<?>> futures = new ArrayList<>(processors);

            for (int i = 0; i < processors; i++) {
                futures.add(executor.submit(() -> {

                    Int2ObjectMap<BlockPos.MutableBlockPos> muteableBlockPosMap1 = new Int2ObjectOpenHashMap<>();
                    for (int i5 = 0; i5 < 27; i5++) {
                        muteableBlockPosMap1.put(i5, new BlockPos.MutableBlockPos());
                    }

                    Int2ObjectMap<BlockPos.MutableBlockPos> muteableBlockPosMap2 = new Int2ObjectOpenHashMap<>();
                    for (int i5 = 0; i5 < 27; i5++) {
                        muteableBlockPosMap2.put(i5, new BlockPos.MutableBlockPos());
                    }

                    Int2ObjectMap<BlockPos.MutableBlockPos> muteableBlockPosMap3 = new Int2ObjectOpenHashMap<>();
                    for (int i5 = 0; i5 < 27; i5++) {
                        muteableBlockPosMap3.put(i5, new BlockPos.MutableBlockPos());
                    }

                    Long currentPos;
                    while (!isEnough.get() && (currentPos = queue.poll()) != null && !stopped) {
                        // 处理26个相邻方向（优化并行流）
                        long finalCurrentPos = currentPos;
                        IntStream.range(0, 27).parallel().forEach(idx -> {
                            int x = (idx % 3) - 1; // -1, 0, 1
                            int y = ((idx / 3) % 3) - 1;
                            int z = (idx / 9) - 1;
                            if(x == 0 && y == 0 && z == 0) return;
                            if(!(x == 0 && y == 0 || x == 0 && z == 0 || y == 0 && z == 0)) return;

                            BlockPos.MutableBlockPos reusablePos = muteableBlockPosMap1.get(idx);
                            BlockPos.MutableBlockPos reusablePos2 = muteableBlockPosMap2.get(idx);
                            BlockPos.MutableBlockPos reusablePos3 = muteableBlockPosMap3.get(idx);

                            BlockPos blockPos = reusablePos2.set(finalCurrentPos);
                            BlockPos blockPos3 = reusablePos3.set(finalCurrentPos);
                            reusablePos2.set(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);

                            BlockPos neighborPos = reusablePos2;

                            // 优化后的位置检查
                            if (neighborPos.getX() >= minX && neighborPos.getX() <= maxX &&
                                    neighborPos.getY() > -64 && neighborPos.getY() < 320 &&
                                    neighborPos.getZ() >= minZ && neighborPos.getZ() <= maxZ &&
                                    neighborPos.distManhattan(startPos) >= blockPos3.distManhattan(startPos)) {

                                long l = neighborPos.asLong();
                                if(!queue.contains(l)) queue.add(l);

                                // 处理方块逻辑
                                Block currentBlock = ToolList.mc.level.getBlockState(neighborPos).getBlock();
                                boolean flag = false;

                                // 检查是否靠近空气
                                if (currentPosition2 != null && currentPosition2.distSqr(neighborPos) < 10 * 10) {

                                    for (int[] offset : offsets) {
                                        reusablePos.set(
                                                neighborPos.getX() + offset[0],
                                                neighborPos.getY() + offset[1],
                                                neighborPos.getZ() + offset[2]
                                        );
                                        if (ToolList.mc.level.getBlockState(reusablePos).getBlock() == Blocks.AIR) {
                                            flag = true;
                                            break;
                                        }
                                    }
                                }

                                for (int i1 = 0; i1 < targets.size(); i1++) {
                                    Map.Entry<Block, Integer> target = targets.get(i1);
                                    
                                    if (currentBlock == target.getKey()) {

                                        if (count.getOrDefault(neighborPos, 0) < 5 &&
                                                PathFinder.isBreakable(neighborPos)) {

                                            BlockPos immutablePos = neighborPos.immutable();
                                            boolean finalFlag = flag;
                                            ScoreGenerator scoreGenerator = () -> {

                                                boolean aimable =
                                                        Math.abs(ToolList.mc.player.getY() - immutablePos.getY()) < 2.3 &&
                                                                getBreakBlockYawAndPitch(immutablePos) != null;

                                                double score = (getSpecialDistance(startPos, immutablePos) / target.getValue()) *
                                                        (finalFlag ? 1 : 8) * (aimable ? 1 : 30);

                                                return score;
                                            };

                                            resultQueue.add(new AbstractMap.SimpleEntry<>(immutablePos, scoreGenerator));
                                            if (resultQueue.size() > 50) {
                                                isEnough.set(true);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        });
                    }
                }));
            }

            // 4. 等待所有任务完成
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException("扫描方块时发生错误!", e);
                }
            }

            // 5. 处理结果
            goals = Collections.synchronizedList(resultQueue.stream()
                    .sorted(Comparator.comparingDouble(e -> e.getValue().getScore()))
                    .limit(100)
                    .collect(Collectors.toList()));
        } else {
            goals.sort(Comparator.comparingDouble(e -> e.getValue().getScore()));
        }

        // System.out.println(visited.size());

        return goals;
    }

    private double getSpecialDistance(Vec3i pos1, Vec3i pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = (pos1.getY() - pos2.getY()) * 2;
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public List<AbstractMap.SimpleEntry<BlockPos, ScoreGenerator>> getGoals() {
        return goals;
    }

    @Override
    public void run() {

        int runningToken;
        currentToken = runningToken = ToolList.getInstance().random.nextInt();

        PathFinder.registerConfig(new PathFinder.ICustomPathfinderConfig() {
            @Override
            public boolean isAllowBreak() {
                return true;
            }

            @Override
            public boolean isAllowPlace() {
                return PathFinder.isConfigAllowPlace();
            }

            @Override
            public boolean shouldUnregister() {
                return !Miner.this.isAlive();
            }

            @Override
            public int getDepth() {
                return 10000;
            }

            @Override
            public long getTimeout() {
                if(distanceToBlock < 2.6) return 5;
                long i = distanceToBlock > 10 ? PathFinder.getConfigTimeout() : 75;
                return Math.max(Math.min(PathFinder.getConfigTimeout(), i), 5);
            }

            @Override
            public boolean shouldPause() {
                return false;
            }

            @Override
            public boolean shouldStopWhenRecieveS08() {
                return ConfigManager.pfStopWhenTP.getValue();
            }
        });

        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e正在扫描世界目标方块, 请稍等片刻..."));
        updateGoals();

        Thread thread = new Thread(() -> {

            while (Miner.this.isAlive()) {
                try {
                    updateGoals();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {

                }
            }

            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c挖掘任务已结束"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c本次挖掘任务运行了§6" + ToolList.getInstance().timeToString(System.currentTimeMillis() - startTime)));

        });
        thread.setName("Miner $ Searcher");
        thread.start();

        while(!stopped) {
            if(currentToken != runningToken) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c挖掘线程已变更, 挖掘任务结束"));
                break;
            }

            try {
                if(!ConfigManager.pfAllowBreak.getValue() && !PathFinder.allowBreak) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §cAllow Break is False!"));
                    break;
                }

                if(MacroManagerListener.pathFinderExecutor.isSleeping()) {

                    List<AbstractMap.SimpleEntry<BlockPos, ScoreGenerator>> goalss = goals;

                    if(goalss.isEmpty()) {
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c这个世界上好像没有你想要的东西了..."));
                        break;
                    } else noTargetCount = 0;

                    if(count.size() >= 100) {
                        count.clear();
                    }

                    if(currentPosition2 != null) {
                        count.put(currentPosition2, count.getOrDefault(currentPosition2, 0) + 1);
                    }

                    for(BlockPos blockPos : new ArrayList<>(count.keySet())) {
                        if(targets.stream().noneMatch(a -> ToolList.mc.level.getBlockState(blockPos).getBlock().equals(a.getKey()))) {
                            count.remove(blockPos);
                        }
                    }

                    for(AbstractMap.SimpleEntry<BlockPos, ScoreGenerator> entry : goalss) {
                        if(count.getOrDefault(entry.getKey(), 0) > 3) continue;

                        if(targets.stream().anyMatch(a -> ToolList.mc.level.getBlockState(entry.getKey()).getBlock().equals(a.getKey()))) {
                            BlockPos pos = currentPosition2 = entry.getKey();

                            distanceToBlock = Math.sqrt(ToolList.mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                            MacroManagerListener.pathFinderExecutor.startExecution(currentPosition1 = new Vec3(pos.getX(), pos.getY(), pos.getZ()), false);
                            break;
                        }
                    }

                } else if(currentPosition2 != null) {
                    BlockPos nextPos = null;

                    List<AbstractMap.SimpleEntry<BlockPos, ScoreGenerator>> goalss = goals;

                    for(AbstractMap.SimpleEntry<BlockPos, ScoreGenerator> entry : goalss) {
                        if(count.getOrDefault(entry.getKey(), 0) > 3 || Objects.equals(entry.getKey(), currentPosition2)) continue;

                        if(targets.stream().anyMatch(a -> ToolList.mc.level.getBlockState(entry.getKey()).getBlock().equals(a.getKey()))) {
                            nextPos = this.nextPos = entry.getKey();
                            break;
                        }
                    }

                    if(targets.stream().noneMatch(a -> ToolList.mc.level.getBlockState(currentPosition2).getBlock().equals(a.getKey()))) {
                        if(nextPos != null && nextPos.distSqr(currentPosition2) < 25) {
                            count.remove(currentPosition2);
                            goalss.removeIf(a -> a.getKey().equals(currentPosition2));

                            MacroManagerListener.pathFinderExecutor.finishTaskQuickly();
                            continue;
                        }

                        if(noBlockFlag++ > 20) {
                            count.remove(currentPosition2);
                            goalss.removeIf(a -> a.getKey().equals(currentPosition2));

                            MacroManagerListener.pathFinderExecutor.finishTaskQuickly();
                            continue;
                        }
                    } else noBlockFlag = 0;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c挖掘任务已结束"));
        executor.shutdown();
    }

    private double[] getBreakBlockYawAndPitch(BlockPos blockPos) {
        // 获取玩家坐标
        double playerX = ToolList.mc.player.getX();
        double playerY = ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight();
        double playerZ = ToolList.mc.player.getZ();

        // 计算玩家与方块的距离
        double distance = Math.sqrt(Math.pow(blockPos.getX() - playerX, 2) +
                Math.pow(blockPos.getY() - playerY, 2) +
                Math.pow(blockPos.getZ() - playerZ, 2));

        // 如果距离大于3，返回null
        if (distance > 3) {
            return null;
        }

        // 获取方块类型
        BlockState blockState = ToolList.mc.level.getBlockState(blockPos);
        Block block = blockState.getBlock();

        // 检查方块是否是水、岩浆或空气
        if (block == Blocks.WATER || block == Blocks.LAVA || block == Blocks.AIR) {
            return null; // 如果方块不可选中，返回null
        }

        // 计算方块中心坐标
        double blockCenterX = blockPos.getX() + 0.5;
        double blockCenterY = blockPos.getY() + 0.5;
        double blockCenterZ = blockPos.getZ() + 0.5;

        Vec3[] edges1 = new Vec3[] {
                new Vec3(blockCenterX, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX - 0.51, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX + 0.51, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY - 0.51, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY + 0.51, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY, blockCenterZ - 0.51),
                new Vec3(blockCenterX, blockCenterY, blockCenterZ + 0.51),
                new Vec3(blockCenterX - 0.49, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX + 0.49, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY - 0.49, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY + 0.49, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY, blockCenterZ - 0.49),
                new Vec3(blockCenterX, blockCenterY, blockCenterZ + 0.49)
        };
        List<Vec3> edges = Arrays.asList(edges1);
        Vec3 v = new Vec3(playerX, playerY, playerZ);
        edges.sort(Comparator.comparingDouble(vec3 -> ((Vec3) vec3).distanceToSqr(v)).reversed());

        for(Vec3 edge : edges) {
            HitResult movingObjectPosition = ToolList.getInstance().predictPlayerAimBlock(v, edge);
            BlockPos pos3 = movingObjectPosition instanceof BlockHitResult ? ((BlockHitResult) movingObjectPosition).getBlockPos() : null;
            if (movingObjectPosition != null && blockPos.equals(pos3)) {

                // 计算目标位置的中心
                double targetCenterX = edge.x();
                double targetCenterY = edge.y();
                double targetCenterZ = edge.z();

                // 计算目标位置的偏移量
                double deltaX = targetCenterX - playerX;
                double deltaY = targetCenterY - playerY;
                double deltaZ = targetCenterZ - playerZ;

                // 计算 yaw 和 pitch
                float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Yaw
                float pitch = (float) -(Math.atan2(deltaY, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)) * (180 / Math.PI)); // Pitch
                // 返回yaw和pitchw
                return new double[] {yaw, pitch};
            }
        }

        return null;
    }
}
