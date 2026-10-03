package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeaps;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class CrystalHollowHelperListener extends AbstractListener {

    public boolean inCN;
    public static final Object2LongArrayMap<String> visitedServer = new Object2LongArrayMap<>();
    private static final int MAX_SCAN_THREADS = 13;
    private ClientLevel scannerLevel;
    private boolean warnedThreadLimit;

    // =====================NotEnoughUpdate========================

    // There is a small set of breakable blocks above the nucleus at Y > 181. While this zone is reported
    // as the Crystal Nucleus by Hypixel, for wishing compass purposes it is in the appropriate quadrant.
    private static final BoundingBox NUCLEUS_BB = new BoundingBox(462, 63, 461, 564, 181, 565);
    // Bounding box around all breakable blocks in the crystal hollows, appears as bedrock in-game
    private static final BoundingBox HOLLOWS_BB = new BoundingBox(201, 30, 201, 824, 189, 824);

    // Zone bounding boxes
    private static final BoundingBox PRECURSOR_REMNANTS_BB = new BoundingBox(512, 64, 512, 824, 189, 824);
    private static final BoundingBox MITHRIL_DEPOSITS_BB = new BoundingBox(512, 64, 201, 824, 189, 513);
    private static final BoundingBox GOBLIN_HOLDOUT_BB = new BoundingBox(201, 64, 512, 513, 189, 824);
    private static final BoundingBox JUNGLE_BB = new BoundingBox(201, 64, 201, 513, 189, 513);
    private static final BoundingBox MAGMA_FIELDS_BB = new BoundingBox(201, 30, 201, 824, 63, 824);

    // =====================NotEnoughUpdate========================

    private final EnumMap<StructureType, ScanDefinition> definitions = createScanDefinitions();
    private final EnumMap<StructureType, List<ScannerInfo>> scannerInfos = new EnumMap<>(StructureType.class);
    private final StructureScanManager<BlockPos> scans = new StructureScanManager<>(
            Executors.newWorkStealingPool(Math.min(MAX_SCAN_THREADS, Runtime.getRuntime().availableProcessors())));

    private EnumMap<StructureType, ScanDefinition> createScanDefinitions() {
        EnumMap<StructureType, ScanDefinition> registry = new EnumMap<>(StructureType.class);
        registry.put(StructureType.BLUE, new ScanDefinition(this::scanCrystal, List.of(PRECURSOR_REMNANTS_BB.inflatedBy(-48))));
        registry.put(StructureType.PURPLE, new ScanDefinition(this::scanCrystal, List.of(JUNGLE_BB)));
        registry.put(StructureType.YELLOW, new ScanDefinition(this::scanCrystal, List.of(MAGMA_FIELDS_BB)));
        registry.put(StructureType.ORANGE, new ScanDefinition(this::scanCrystal, List.of(GOBLIN_HOLDOUT_BB)));
        registry.put(StructureType.GREEN, new ScanDefinition(this::scanCrystal, List.of(MITHRIL_DEPOSITS_BB)));
        registry.put(StructureType.GOBLIN_KING, new ScanDefinition(this::scanGoblinKing, List.of(GOBLIN_HOLDOUT_BB)));
        registry.put(StructureType.DRAGON_LAIR, new ScanDefinition(this::scanDragonLair, List.of(MITHRIL_DEPOSITS_BB)));
        registry.put(StructureType.WORM_FISH_SPOT, new ScanDefinition(this::scanWormFishSpot, List.of(PRECURSOR_REMNANTS_BB)));
        registry.put(StructureType.CORLEONE, new ScanDefinition(this::scanCorleone, List.of(MITHRIL_DEPOSITS_BB)));
        registry.put(StructureType.FAIRY_GROTTO, new ScanDefinition(this::scanFairyGrotto,
                List.of(MITHRIL_DEPOSITS_BB, JUNGLE_BB, PRECURSOR_REMNANTS_BB, GOBLIN_HOLDOUT_BB)));
        registry.put(StructureType.BEAR3, new ScanDefinition(this::scanBear3, List.of(GOBLIN_HOLDOUT_BB)));
        if (registry.size() != StructureType.values().length) {
            throw new IllegalStateException("Every Crystal Hollows structure needs a scanner");
        }
        return registry;
    }

    @Override
    public String getListenerName() {
        return "CrystalHollowHelperListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((m, l) -> {
            resetScans();
            inCN = false;
        });
    }

    private void resetScans() {
        scans.cancelAll();
        scannerInfos.clear();
        scannerLevel = null;
        warnedThreadLimit = false;
    }

    private void onLastRender(LevelRenderContext context) {
        if (ConfigManager.crystalHollowHelperDebug.getValue() && inCN && !scannerInfos.isEmpty()) {
            RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            for (List<ScannerInfo> group : scannerInfos.values()) {
                for (ScannerInfo info : group) {
                    RenderUtils.renderESP(wr, BlockPos.of(info.debugPosition), 1, 1, 1, 1, false);
                }
            }
            wr.finishDraw();
        }
    }

    private void onStartTick(Minecraft mc) {
        boolean inHollows = mc.level != null && mc.player != null
                && "crystal_hollows".equals(StatusManager.get().getMode());
        if (!inHollows) {
            if (inCN || scannerLevel != null) resetScans();
            inCN = false;
            return;
        }
        if (!inCN || scannerLevel != mc.level) {
            resetScans();
            inCN = true;
            scannerLevel = mc.level;
            long time = visitedServer.getOrDefault(StatusManager.get().getServerID(), -1);
            if (time != -1 && ConfigManager.crystalHollowDupServerTipper.getValue()) {
                XSDHUD.bigTitle.updateTitleMsg("§a你在§e" + ToolList.getInstance().timeToString(System.currentTimeMillis() - time) + "§a之前拜访过这个服务器!", 3000);
            }
        }
        visitedServer.put(StatusManager.get().getServerID(), System.currentTimeMillis());

        EnumSet<StructureType> enabled = EnumSet.noneOf(StructureType.class);
        if (ConfigManager.crystalHollowHelper.getValue()) {
            for (StructureType type : StructureType.values()) {
                if (type.isEnabled()) enabled.add(type);
            }
        }
        // Apply settings before consuming results, so disabling also discards queued discoveries.
        scans.update(enabled, this::createScanners);
        for (StructureScanManager.Result<BlockPos> result : scans.pollResults()) {
            reportResult(mc, result);
        }
        scannerInfos.entrySet().removeIf(entry -> !scans.isRunning(entry.getKey()));
        if (ConfigManager.crystalHollowHelperDebug.getValue()) {
            for (List<ScannerInfo> group : scannerInfos.values()) {
                for (ScannerInfo info : group) {
                    if (info.waitingForChunks && !info.reportedWaiting) {
                        info.reportedWaiting = true;
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + info.type.displayName() + "§e区域扫描, 开始轮询未加载区块..."));
                    }
                }
            }
        }
        int cpu = Runtime.getRuntime().availableProcessors();
        if (!scannerInfos.isEmpty() && !warnedThreadLimit && cpu < MAX_SCAN_THREADS
                && ConfigManager.crystalHollowHelperDisableThreadLimit.getValue()) {
            warnedThreadLimit = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c警告: 当前CPU核数小于" + MAX_SCAN_THREADS + " (当前为" + cpu + "核), 如果客户端发生卡顿, 请前往设置中关闭线程限制绕过!"));
        }
    }

    private List<StructureScanManager.ScanTask<BlockPos>> createScanners(StructureType type) {
        ScanDefinition definition = definitions.get(type);
        List<ScannerInfo> infos = new ArrayList<>();
        List<StructureScanManager.ScanTask<BlockPos>> tasks = new ArrayList<>();
        for (BoundingBox area : definition.areas()) {
            // Capture the world on the client thread, before a worker can be queued or cancelled.
            ScannerInfo info = new ScannerInfo(type, area, scannerLevel);
            infos.add(info);
            tasks.add(cancelled -> {
                Thread.sleep(1000);
                if (cancelled.getAsBoolean()) return null;
                return definition.scanner().scan(info, cancelled);
            });
        }
        scannerInfos.put(type, infos);
        return tasks;
    }

    private void reportResult(Minecraft mc, StructureScanManager.Result<BlockPos> result) {
        StructureType type = result.type();
        BlockPos position = result.value();
        if (result.failure() != null) {
            logger.error("Failed to scan Crystal Hollows structure {}", type, result.failure());
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c扫描" + type.displayName() + "§c时发生错误, 请查看日志"));
        } else if (position != null) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] 在这个服务器发现了一个" + type.displayName() + "§a! §e(" + (int) Math.sqrt(position.distToCenterSqr(mc.player.position())) + "m) (" + position + ")"));
            if (type == StructureType.FAIRY_GROTTO) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已自动关闭其他区域的扫描器"));
            }
            if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
                ToolList.sendChatMessage(String.format("/skyblocker crystalWaypoints add %d %d %d %s",
                        position.getX(), position.getY(), position.getZ(), type.waypointName()));
            }
        } else if (type != StructureType.WORM_FISH_SPOT) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c没有在这个服务器找到" + type.displayName() + " §c:("));
        }
    }

    public void updateThreadLimit() {
        scans.replaceExecutor(Executors.newWorkStealingPool(ConfigManager.crystalHollowHelperDisableThreadLimit.getValue()
                ? MAX_SCAN_THREADS : Math.min(MAX_SCAN_THREADS, Runtime.getRuntime().availableProcessors())));
        scannerInfos.entrySet().removeIf(entry -> !scans.isRunning(entry.getKey()));
    }

    private boolean shouldStop(ScannerInfo info, BooleanSupplier cancelled) {
        info.debugPosition = info.currentScanning.asLong();
        return cancelled.getAsBoolean();
    }

    private final BiPredicate<ClientLevel, BlockPos> barrierFinder = (level, bp) -> level.getBlockState(bp).getBlock() == Blocks.BARRIER;
    private final BiPredicate<ClientLevel, BlockPos> lavaFinder = (level, bp) -> level.getBlockState(bp).getBlock() == Blocks.LAVA;
    private final BiPredicate<ClientLevel, BlockPos> pinkGlassPaneFinder = (level, bp) -> {
        Block block = level.getBlockState(bp).getBlock();
        return block == Blocks.MAGENTA_STAINED_GLASS_PANE || block == Blocks.MAGENTA_STAINED_GLASS;
    };

    private BlockPos scanCrystal(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        StructureType structureType = info.type;
        int minX = area.minX() - 32;
        int minY = area.minY() - 32;
        int minZ = area.minZ() - 32;
        int maxX = area.maxX() + 32;
        int maxY = area.maxY() + 32;
        int maxZ = area.maxZ() + 32;

        int barrierStep = 2;
        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;

        for(int x = minX; x <= maxX; x += barrierStep) {
            for(int y = minY; y <= maxY; y += barrierStep) {
                if(!structureType.isInRange(y)) continue;
                for(int z = minZ; z <= maxZ; z += barrierStep) {
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                    if(barrierFinder.test(currentLevel, info.currentScanning)) {
                        return info.currentScanning.immutable();
                    }
                }
            }
        }
        info.waitingForChunks = true;
        while(!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                if(barrierFinder.test(currentLevel, info.currentScanning)) {
                    return pos.immutable();
                }
                if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(info.currentScanning.immutable());
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private @NotNull ObjectHeapPriorityQueue<BlockPos> genUnloadedQueue() {
        return new ObjectHeapPriorityQueue<>(Comparator.comparingDouble((pos) -> {
            if (mc.player == null) return 0;
            return pos.distToCenterSqr(mc.player.position());
        })) {
            boolean dequeued = false;
            int counter = 0;

            @Override
            public BlockPos dequeue() {
                dequeued = true;
                return super.dequeue();
            }

            @Override
            public void enqueue(BlockPos pos) {
                super.enqueue(pos);
                if (dequeued) {
                    counter++;
                    if(counter >= 2) {
                        counter = 0;
                        ObjectHeaps.makeHeap(heap, size, c);
                    }
                }
            }
        };
    }

    private void queueWait() throws InterruptedException {
        Thread.sleep(751);
    }

    private BlockPos scanGoblinKing(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        StructureType structureType = info.type;
        int minX = area.minX() - 32;
        int minY = area.minY() - 32;
        int minZ = area.minZ() - 32;
        int maxX = area.maxX() + 32;
        int maxY = area.maxY() + 32;
        int maxZ = area.maxZ() + 32;

        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;
        BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
        for(int x = minX; x <= maxX; x += 2) {
            for(int y = minY; y <= maxY; y += 2) {
                if(!structureType.isInRange(y)) continue;
                label_z:for(int z = minZ; z <= maxZ; z += 2) {
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue label_z;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;

                    Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                    if(block == Blocks.RED_WOOL) {
                        block = currentLevel.getBlockState(info.currentScanning.move(0, -1, 0)).getBlock();
                    }
                    if(block == Blocks.STONE_BRICKS) {
                        for (int xOff = -2; xOff <= 2; xOff++) {
                            for (int zOff = -2; zOff <= 2; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 0, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() != Blocks.STONE_BRICKS) continue label_z;
                            }
                        }
                        temp.set(info.currentScanning).move(0, 1, 0);
                        int redWoolCount = 0;
                        for (int xOff = -2; xOff <= 2; xOff++) {
                            for (int zOff = -2; zOff <= 2; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 1, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.RED_WOOL) redWoolCount++;
                            }
                        }
                        if(redWoolCount == 6) {
                            return info.currentScanning.immutable();
                        }
                    }
                }
            }
        }
        info.waitingForChunks = true;
        label:while(!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                if(block == Blocks.RED_WOOL) {
                    block = currentLevel.getBlockState(info.currentScanning.move(0, -1, 0)).getBlock();
                }
                if(block == Blocks.STONE_BRICKS) {
                    for (int xOff = -2; xOff <= 2; xOff++) {
                        for (int zOff = -2; zOff <= 2; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 0, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() != Blocks.STONE_BRICKS) continue label;
                        }
                    }
                    temp.set(info.currentScanning).move(0, 1, 0);
                    int redWoolCount = 0;
                    for (int xOff = -2; xOff <= 2; xOff++) {
                        for (int zOff = -2; zOff <= 2; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 1, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.RED_WOOL) redWoolCount++;
                        }
                    }
                    if(redWoolCount == 6) {
                        return pos;
                    }
                }
                if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(pos);
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private BlockPos scanBear3(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        StructureType structureType = info.type;
        int minX = area.minX() - 32;
        int minY = area.minY() - 32;
        int minZ = area.minZ() - 32;
        int maxX = area.maxX() + 32;
        int maxY = area.maxY() + 32;
        int maxZ = area.maxZ() + 32;

        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;
        BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
        for(int x = minX; x <= maxX; x += 2) {
            for(int y = minY; y <= maxY; y++) {
                if(!structureType.isInRange(y)) continue;
                label_z:for(int z = minZ; z <= maxZ; z += 2) {
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue label_z;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;

                    Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                    int blockCount = 0;
                    if(block == Blocks.OAK_PLANKS) {
                        for (int xOff = -2; xOff <= 2; xOff++) {
                            for (int zOff = -2; zOff <= 2; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 0, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.OAK_PLANKS) blockCount++;
                            }
                        }
                        if(blockCount < 15) continue label_z;
                        temp.set(info.currentScanning).move(0, 1, 0);
                        int fireCount = 0;
                        int wallCount = 0;
                        for (int xOff = -3; xOff <= 3; xOff++) {
                            for (int zOff = -3; zOff <= 3; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 1, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.FIRE) fireCount++;
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.COBBLESTONE_WALL) wallCount++;
                            }
                        }
                        if(fireCount == 2 && wallCount == 2) {
                            return info.currentScanning.immutable();
                        }
                    }
                }
            }
        }
        info.waitingForChunks = true;
        label:while(!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                int blockCount = 0;
                Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                if(block == Blocks.OAK_PLANKS) {
                    for (int xOff = -2; xOff <= 2; xOff++) {
                        for (int zOff = -2; zOff <= 2; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 0, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.OAK_PLANKS) blockCount++;
                        }
                    }
                    if(blockCount < 15) continue label;
                    temp.set(info.currentScanning).move(0, 1, 0);
                    int fireCount = 0;
                    int wallCount = 0;
                    for (int xOff = -3; xOff <= 3; xOff++) {
                        for (int zOff = -3; zOff <= 3; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 1, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.FIRE) fireCount++;
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.COBBLESTONE_WALL) wallCount++;
                        }
                    }
                    if(fireCount == 2 && wallCount == 2) {
                        return pos;
                    }
                }
                if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(pos);
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private BlockPos scanDragonLair(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        StructureType structureType = info.type;
        int minX = area.minX() - 32;
        int minY = area.minY() - 32;
        int minZ = area.minZ() - 32;
        int maxX = area.maxX() + 32;
        int maxY = area.maxY() + 32;
        int maxZ = area.maxZ() + 32;

        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;
        BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
        for(int x = minX; x <= maxX; x += 2) {
            for(int y = minY; y <= maxY; y += 2) {
                if(!structureType.isInRange(y)) continue;
                label_z:for(int z = minZ; z <= maxZ; z += 2) {
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue label_z;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;

                    Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                    if(block == Blocks.RED_TERRACOTTA) {
                        block = currentLevel.getBlockState(info.currentScanning.move(0, -1, 0)).getBlock();
                    }
                    if(block == Blocks.SMOOTH_SANDSTONE) {
                        int sandstoneCount = 0;
                        for (int xOff = -2; xOff <= 2; xOff++) {
                            for (int zOff = -2; zOff <= 2; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 0, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.SMOOTH_SANDSTONE) sandstoneCount++;
                            }
                        }
                        if(sandstoneCount < 8) continue label_z;
                        temp.set(info.currentScanning).move(0, 1, 0);
                        int redClayCount = 0;
                        for (int xOff = -2; xOff <= 2; xOff++) {
                            for (int zOff = -2; zOff <= 2; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 1, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.RED_TERRACOTTA) redClayCount++;
                            }
                        }
                        if(redClayCount >= 8) {
                            return info.currentScanning.immutable();
                        }
                    }
                }
            }
        }
        info.waitingForChunks = true;
        label:while(!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                if(block == Blocks.RED_TERRACOTTA) {
                    block = currentLevel.getBlockState(info.currentScanning.move(0, -1, 0)).getBlock();
                }
                if(block == Blocks.SMOOTH_SANDSTONE) {
                    int sandstoneCount = 0;
                    for (int xOff = -2; xOff <= 2; xOff++) {
                        for (int zOff = -2; zOff <= 2; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 0, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.SMOOTH_SANDSTONE) sandstoneCount++;
                        }
                    }
                    if(sandstoneCount < 8) continue label;
                    temp.set(info.currentScanning).move(0, 1, 0);
                    int redClayCount = 0;
                    for (int xOff = -2; xOff <= 2; xOff++) {
                        for (int zOff = -2; zOff <= 2; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 1, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.RED_TERRACOTTA) redClayCount++;
                        }
                    }
                    if(redClayCount >= 8) {
                        return pos;
                    }
                }
                if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(pos);
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private BlockPos scanCorleone(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        StructureType structureType = info.type;
        int minX = area.minX() - 32;
        int minY = area.minY() - 32;
        int minZ = area.minZ() - 32;
        int maxX = area.maxX() + 32;
        int maxY = area.maxY() + 32;
        int maxZ = area.maxZ() + 32;

        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;
        BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
        for(int x = minX; x <= maxX; x += 2) {
            for(int y = minY; y <= maxY; y++) {
                if(!structureType.isInRange(y)) continue;
                label_z:for(int z = minZ; z <= maxZ; z += 2) {
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue label_z;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;

                    Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                    if(block == Blocks.CYAN_TERRACOTTA) {
                        int count = 0;
                        for (int xOff = -2; xOff <= 2; xOff++) {
                            for (int zOff = -2; zOff <= 2; zOff++) {
                                temp.set(info.currentScanning).move(xOff, 0, zOff);
                                if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                    unloadedQueue.enqueue(info.currentScanning.immutable());
                                    continue label_z;
                                }
                                if(currentLevel.getBlockState(temp).getBlock() == Blocks.CYAN_TERRACOTTA) count++;
                            }
                        }
                        if(count < 20) continue label_z;

                        boolean flag = false;
                        int brickCount = 0;
                        for (int i = 1; i <= 4; i++) {
                            temp.set(info.currentScanning).move(0, 0, i);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(info.currentScanning.immutable());
                                continue label_z;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount += 100;
                            temp.set(info.currentScanning).move(0, 0, -i);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(info.currentScanning.immutable());
                                continue label_z;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount -= 1;
                        }
                        flag |= brickCount == 99;
                        brickCount = 0;
                        for (int i = 1; i <= 4; i++) {
                            temp.set(info.currentScanning).move(i, 0, 0);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(info.currentScanning.immutable());
                                continue label_z;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount += 100;
                            temp.set(info.currentScanning).move(-i, 0, 0);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(info.currentScanning.immutable());
                                continue label_z;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount -= 1;
                        }
                        flag |= brickCount == 99;
                        if(flag) {
                            return info.currentScanning.immutable();
                        }
                    }
                }
            }
        }
        info.waitingForChunks = true;
        label:while(!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                Block block = currentLevel.getBlockState(info.currentScanning).getBlock();
                if(block == Blocks.CYAN_TERRACOTTA) {
                    int count = 0;
                    for (int xOff = -2; xOff <= 2; xOff++) {
                        for (int zOff = -2; zOff <= 2; zOff++) {
                            temp.set(info.currentScanning).move(xOff, 0, zOff);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(info.currentScanning.immutable());
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.CYAN_TERRACOTTA) count++;
                        }
                    }
                    if(count >= 20) {
                        boolean flag = false;
                        int brickCount = 0;
                        for (int i = 1; i <= 4; i++) {
                            temp.set(info.currentScanning).move(0, 0, i);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount += 100;
                            temp.set(info.currentScanning).move(0, 0, -i);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount -= 1;
                        }
                        flag |= brickCount == 99;
                        brickCount = 0;
                        for (int i = 1; i <= 4; i++) {
                            temp.set(info.currentScanning).move(i, 0, 0);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount += 100;
                            temp.set(info.currentScanning).move(-i, 0, 0);
                            if(!ToolList.getInstance().isChunkLoaded(currentLevel, temp)) {
                                unloadedQueue.enqueue(pos);
                                queueWait();
                                continue label;
                            }
                            if(currentLevel.getBlockState(temp).getBlock() == Blocks.STONE_BRICKS) brickCount -= 1;
                        }
                        flag |= brickCount == 99;
                        if(flag) {
                            return info.currentScanning.immutable();
                        }
                    }
                }
                if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(pos);
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private BlockPos scanFairyGrotto(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        StructureType structureType = info.type;
        int minX = area.minX() - 32;
        int minY = area.minY() - 32;
        int minZ = area.minZ() - 32;
        int maxX = area.maxX() + 32;
        int maxY = area.maxY() + 32;
        int maxZ = area.maxZ() + 32;

        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;
        for(int x = minX; x <= maxX; x++) {
            for(int y = minY; y <= maxY; y++) {
                if(!structureType.isInRange(y)) continue;
                label_z:for(int z = minZ; z <= maxZ; z++) {
                    if(Math.abs(z + x) % 5 != 0) continue;
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue label_z;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;

                    if(pinkGlassPaneFinder.test(currentLevel, info.currentScanning)) {
                        return info.currentScanning.immutable();
                    }
                }
            }
        }
        info.waitingForChunks = true;
        while (!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if (ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if (NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                if (pinkGlassPaneFinder.test(currentLevel, info.currentScanning)) {
                    return info.currentScanning.immutable();
                }
                if (!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(pos);
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private BlockPos scanWormFishSpot(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException {
        BoundingBox area = info.area;
        int minX = area.minX();
        int minY = area.minY();
        int minZ = area.minZ();
        int maxX = area.maxX();
        int maxY = area.maxY();
        int maxZ = area.maxZ();

        ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

        ClientLevel currentLevel = info.level;

        for(int x = minX; x <= maxX; x += 2) {
            for(int y = minY; y <= maxY; y += 1) {
                for(int z = minZ; z <= maxZ; z += 2) {
                    info.currentScanning.set(x, y, z);
                    if (shouldStop(info, cancelled)) return null;
                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue;
                    }

                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                    if(lavaFinder.test(currentLevel, info.currentScanning)) {
                        return info.currentScanning.immutable();
                    }

                    if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                        unloadedQueue.enqueue(info.currentScanning.immutable());
                        continue;
                    }
                }
            }
        }
        info.waitingForChunks = true;
        while(!unloadedQueue.isEmpty()) {
            if (shouldStop(info, cancelled)) return null;
            BlockPos pos = unloadedQueue.dequeue();
            info.currentScanning.set(pos);
            if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                if(lavaFinder.test(currentLevel, info.currentScanning)) {
                    return pos.immutable();
                }
                if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    unloadedQueue.enqueue(pos);
                    queueWait();
                    continue;
                }
            } else {
                unloadedQueue.enqueue(pos);
                queueWait();
            }
        }
        return null;
    }

    private enum StructureType {
        BLUE("§b蓝色水晶", "Lost Precursor City", 121, 130),
        PURPLE("§5紫色水晶", "Jungle Temple", 72, 81),
        YELLOW("§e黄色水晶", "Khazad-dûm", 0, 63),
        ORANGE("§6橙色水晶", "Goblin Queen's Den", 125, 140),
        GREEN("§a绿色水晶", "Mines of Divan", 97, 102),
        GOBLIN_KING("§6王下一桶", "King Yolkar", 82, 168),
        DRAGON_LAIR("§c那位来客", "Dragon's Lair", 64, 189),
        WORM_FISH_SPOT("§c可以烤鱼钩的地方", "Unknown", 64, 189),
        CORLEONE("§a骷髅王", "Corleone", 64, 189),
        FAIRY_GROTTO("§d粉色小狗", "Fairy Grotto", 64, 189),
        BEAR3("§e熊出没", "Unknown", 64, 189);

        private final String displayName;
        private final String waypointName;
        private final int minY;
        private final int maxY;

        StructureType(String displayName, String waypointName, int minY, int maxY) {
            this.displayName = displayName;
            this.waypointName = waypointName;
            this.minY = minY;
            this.maxY = maxY;
        }

        public boolean isEnabled() {
            return switch (this) {
                case BLUE -> ConfigManager.crystalHollowScanBlue.getValue();
                case PURPLE -> ConfigManager.crystalHollowScanPurple.getValue();
                case YELLOW -> ConfigManager.crystalHollowScanYellow.getValue();
                case ORANGE -> ConfigManager.crystalHollowScanOrange.getValue();
                case GREEN -> ConfigManager.crystalHollowScanGreen.getValue();
                case GOBLIN_KING -> ConfigManager.crystalHollowScanGoblinKing.getValue();
                case DRAGON_LAIR -> ConfigManager.crystalHollowScanDragonLair.getValue();
                case WORM_FISH_SPOT -> ConfigManager.crystalHollowScanWormFishSpot.getValue();
                case CORLEONE -> ConfigManager.crystalHollowScanCorleone.getValue();
                case FAIRY_GROTTO -> ConfigManager.crystalHollowScanFairyGrotto.getValue();
                case BEAR3 -> ConfigManager.crystalHollowScanBear3.getValue();
            };
        }

        public String displayName() {
            return displayName;
        }

        public String waypointName() {
            return waypointName;
        }

        public boolean isInRange(int y) {
            return y >= minY && y <= maxY;
        }
    }

    /**
     * Owns one scan group per enabled structure. All public methods run on the client thread;
     * workers only read cancellation flags and publish immutable completion events.
     */
    private static final class StructureScanManager<T> {
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

    @FunctionalInterface
    private interface StructureScanner {
        BlockPos scan(ScannerInfo info, BooleanSupplier cancelled) throws InterruptedException;
    }

    private record ScanDefinition(StructureScanner scanner, List<BoundingBox> areas) { }

    private static final class ScannerInfo {
        private final StructureType type;
        private final BoundingBox area;
        private final ClientLevel level;
        private final BlockPos.MutableBlockPos currentScanning = new BlockPos.MutableBlockPos(0, 0, 0);
        private volatile long debugPosition;
        private volatile boolean waitingForChunks;
        private boolean reportedWaiting;

        private ScannerInfo(StructureType type, BoundingBox area, ClientLevel level) {
            this.type = type;
            this.area = area;
            this.level = level;
        }
    }
}
