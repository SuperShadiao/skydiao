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
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;

public class CrystalHollowHelperListener extends AbstractListener {

    public boolean inCN;
    private int scannerToken;
    private boolean foundFairy;
    public static final Object2LongArrayMap<String> visitedServer = new Object2LongArrayMap<>();

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



    @Override
    public String getListenerName() {
        return "CrystalHollowHelperListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((m, l) -> {
            inCN = false;
            scannerToken = 0;
        });
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        return false;
    }

    private void onLastRender(LevelRenderContext context) {
        if(ConfigManager.crystalHollowHelperDebug.getValue() && inCN) {
            RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            for (ScannerInfo info : activeCrystalScanners) {
                RenderUtils.renderESP(wr, info.currentScanning, 1, 1, 1, 1, false);
            }
            for (ScannerInfo info : activeWormFishSpotScanners) {
                RenderUtils.renderESP(wr, info.currentScanning, 1, 1, 1, 1, false);
            }
            wr.finishDraw();
        }
    }

    private final List<CrystalScannerInfo> activeCrystalScanners = new ArrayList<>();
    private final List<ScannerInfo> activeWormFishSpotScanners = new ArrayList<>();
    // private boolean flag;

    private void onStartTick(Minecraft mc) {
        boolean tempInCN = "crystal_hollows".equals(StatusManager.get().getMode());
        if(!inCN && tempInCN) {
            inCN = true;
            long time = visitedServer.getOrDefault(StatusManager.get().getServerID(), -1);
            if(time != -1 && ConfigManager.crystalHollowDupServerTipper.getValue()) {
                XSDHUD.bigTitle.updateTitleMsg("§a你在§e" + ToolList.getInstance().timeToString(System.currentTimeMillis() - time) + "§a之前拜访过这个服务器!", 3000);
            }
            if(ConfigManager.crystalHollowHelper.getValue()) {
                scannerToken = ToolList.getInstance().random.nextInt();
                activeCrystalScanners.clear();
                activeWormFishSpotScanners.clear();
                foundFairy = false;
                activeCrystalScanners.add(startScanCrystal(CrystalType.BLUE, PRECURSOR_REMNANTS_BB));
                activeCrystalScanners.add(startScanCrystal(CrystalType.PURPLE, JUNGLE_BB));
                activeCrystalScanners.add(startScanCrystal(CrystalType.YELLOW, MAGMA_FIELDS_BB));
                activeCrystalScanners.add(startScanCrystal(CrystalType.ORANGE, GOBLIN_HOLDOUT_BB));
                activeCrystalScanners.add(startScanCrystal(CrystalType.GREEN, MITHRIL_DEPOSITS_BB));
                activeCrystalScanners.add(startScanGoblinKing(CrystalType.GOBLIN_KING, GOBLIN_HOLDOUT_BB));
                activeCrystalScanners.add(startScanDragonLair(CrystalType.DRAGON_LAIR, MITHRIL_DEPOSITS_BB));
                activeWormFishSpotScanners.add(startScanWormFishSpot(PRECURSOR_REMNANTS_BB));
                activeCrystalScanners.add(startScanCorleone(CrystalType.CORLEONE, MITHRIL_DEPOSITS_BB));
                activeCrystalScanners.add(startScanFairyGrotto(CrystalType.FAIRY_GROTTO, MITHRIL_DEPOSITS_BB));
                activeCrystalScanners.add(startScanFairyGrotto(CrystalType.FAIRY_GROTTO, JUNGLE_BB));
                activeCrystalScanners.add(startScanFairyGrotto(CrystalType.FAIRY_GROTTO, PRECURSOR_REMNANTS_BB));
                activeCrystalScanners.add(startScanFairyGrotto(CrystalType.FAIRY_GROTTO, GOBLIN_HOLDOUT_BB));
                activeCrystalScanners.add(startScanBear3(CrystalType.BEAR3, GOBLIN_HOLDOUT_BB));

                int cpu = Runtime.getRuntime().availableProcessors();
                if(cpu < 13 && ConfigManager.crystalHollowHelperDisableThreadLimit.getValue()) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c警告: 当前CPU核数小于13 (当前为" + cpu + "核), 如果客户端发生卡顿, 请前往设置中关闭线程限制绕过!"));
                }
            }
        } else if(inCN && !tempInCN) {
            inCN = false;
        }

//        if(!flag) {
//            flag = true;
//            activeCrystalScanners.add(startScanGoblinKing(CrystalType.GOBLIN_KING, GOBLIN_HOLDOUT_BB));
//        }

        // if(mc.hitResult instanceof BlockHitResult hit) System.out.println(mc.level.getBlockState(hit.getBlockPos()).getBlock());

        if(inCN) {
            visitedServer.put(StatusManager.get().getServerID(), System.currentTimeMillis());
        }
        if(!activeCrystalScanners.isEmpty()) {
            Iterator<CrystalScannerInfo> it1 = activeCrystalScanners.iterator();
            while (it1.hasNext()) {
                CrystalScannerInfo info = it1.next();
                if (info.result != null) {
                    String crystalName = info.crystalType.displayName;
                    String structureName = info.crystalType.internalName;

                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 在这个服务器发现了一个" + crystalName + "§a! §e(" + (int) Math.sqrt(info.result.distToCenterSqr(mc.player.position())) + "m) (" + info.result + ")"));
                    if(info.crystalType == CrystalType.FAIRY_GROTTO) {
                        foundFairy = true;
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已自动关闭其他区域的扫描器"));
                    }
                    if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
                        // /skyblocker crystalWaypoints add 92 226 -88 Xalx
                        ToolList.sendChatMessage(String.format("/skyblocker crystalWaypoints add %d %d %d %s", info.result.getX(), info.result.getY(), info.result.getZ(), structureName));
                    }
                    it1.remove();
                } else if (info.task.isDone()) {
                    String crystalName = info.crystalType.displayName;
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c没有在这个服务器找到" + crystalName + " §c:("));
                    it1.remove();
                }
            }
        }
        if(!activeWormFishSpotScanners.isEmpty()) {
            Iterator<ScannerInfo> it1 = activeWormFishSpotScanners.iterator();
            while (it1.hasNext()) {
                ScannerInfo info = it1.next();
                if (info.result != null) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 在这个服务器发现了一个§c可以烤鱼钩的地方! §e(" + (int) Math.sqrt(info.result.distToCenterSqr(mc.player.position())) + "m) (" + info.result + ")"));
                    if (FabricLoader.getInstance().isModLoaded("skyblocker")) {
                        // /skyblocker crystalWaypoints add 92 226 -88 Xalx
                        ToolList.sendChatMessage(String.format("/skyblocker crystalWaypoints add %d %d %d %s", info.result.getX(), info.result.getY(), info.result.getZ(), "Unknown"));
                    }
                    it1.remove();
                } else if (info.task.isDone()) {
                    it1.remove();
                }
            }
        }
    }

    public void updateThreadLimit() {
        ExecutorService temp = structureScannerExecutor;
        structureScannerExecutor = Executors.newWorkStealingPool(ConfigManager.crystalHollowHelperDisableThreadLimit.getValue() ? 13 : Math.min(13, Runtime.getRuntime().availableProcessors()));
        if(temp != null) temp.shutdownNow();
    }

    private ExecutorService structureScannerExecutor = Executors.newWorkStealingPool(Math.min(13, Runtime.getRuntime().availableProcessors()));
    private final BiPredicate<ClientLevel, BlockPos> barrierFinder = (level, bp) -> level.getBlockState(bp).getBlock() == Blocks.BARRIER;
    private final BiPredicate<ClientLevel, BlockPos> lavaFinder = (level, bp) -> level.getBlockState(bp).getBlock() == Blocks.LAVA;
    private final BiPredicate<ClientLevel, BlockPos> pinkGlassPaneFinder = (level, bp) -> {
        Block block = level.getBlockState(bp).getBlock();
        return block == Blocks.MAGENTA_STAINED_GLASS_PANE || block == Blocks.MAGENTA_STAINED_GLASS;
    };

    private CrystalScannerInfo startScanCrystal(CrystalType crystalType, BoundingBox area) {
        CrystalScannerInfo info = new CrystalScannerInfo(crystalType);
        info.scannerName = crystalType.displayName;
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX() - 32;
            int minY = area.minY() - 32;
            int minZ = area.minZ() - 32;
            int maxX = area.maxX() + 32;
            int maxY = area.maxY() + 32;
            int maxZ = area.maxZ() + 32;

            int barrierStep = 2;
            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }

            for(int x = minX; x <= maxX; x += barrierStep) {
                for(int y = minY; y <= maxY; y += barrierStep) {
                    if(!crystalType.isInRange(y)) continue;
                    for(int z = minZ; z <= maxZ; z += barrierStep) {
                        info.currentScanning.set(x, y, z);
                        if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
                        if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                            unloadedQueue.enqueue(info.currentScanning.immutable());
                            continue;
                        }

                        if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                        if(barrierFinder.test(currentLevel, info.currentScanning)) {
                            info.result = info.currentScanning.immutable();
                            return;
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + crystalType.displayName + "§e区域扫描, 开始轮询未加载区块..."));
            while(!unloadedQueue.isEmpty()) {
                if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
                BlockPos pos = unloadedQueue.dequeue();
                info.currentScanning.set(pos);
                if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                    if(barrierFinder.test(currentLevel, info.currentScanning)) {
                        info.result = pos.immutable();
                        return;
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
            info.result = null;
        });
        return info;
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

    private void queueWait() {
        try {
            Thread.sleep(751);
        } catch (InterruptedException e) {

        }
    }

    private CrystalScannerInfo startScanGoblinKing(CrystalType crystalType, BoundingBox area) {
        if(crystalType != CrystalType.GOBLIN_KING) throw new AssertionError("咕咕嘎嘎!!!!!");

        CrystalScannerInfo info = new CrystalScannerInfo(crystalType);
        info.scannerName = crystalType.displayName;
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX() - 32;
            int minY = area.minY() - 32;
            int minZ = area.minZ() - 32;
            int maxX = area.maxX() + 32;
            int maxY = area.maxY() + 32;
            int maxZ = area.maxZ() + 32;

            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
            for(int x = minX; x <= maxX; x += 2) {
                for(int y = minY; y <= maxY; y += 2) {
                    if(!crystalType.isInRange(y)) continue;
                    label_z:for(int z = minZ; z <= maxZ; z += 2) {
                        info.currentScanning.set(x, y, z);
                        if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                                info.result = info.currentScanning.immutable();
                                return;
                            }
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + crystalType.displayName + "§e区域扫描, 开始轮询未加载区块..."));
            label:while(!unloadedQueue.isEmpty()) {
                if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                            info.result = pos;
                            return;
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
            info.result = null;
        });
        return info;
    }

    private CrystalScannerInfo startScanBear3(CrystalType crystalType, BoundingBox area) {
        if(crystalType != CrystalType.BEAR3) throw new AssertionError("咕咕嘎嘎!!!!!");

        CrystalScannerInfo info = new CrystalScannerInfo(crystalType);
        info.scannerName = crystalType.displayName;
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX() - 32;
            int minY = area.minY() - 32;
            int minZ = area.minZ() - 32;
            int maxX = area.maxX() + 32;
            int maxY = area.maxY() + 32;
            int maxZ = area.maxZ() + 32;

            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
            for(int x = minX; x <= maxX; x += 2) {
                for(int y = minY; y <= maxY; y++) {
                    if(!crystalType.isInRange(y)) continue;
                    label_z:for(int z = minZ; z <= maxZ; z += 2) {
                        info.currentScanning.set(x, y, z);
                        if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                                info.result = info.currentScanning.immutable();
                                return;
                            }
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + crystalType.displayName + "§e区域扫描, 开始轮询未加载区块..."));
            label:while(!unloadedQueue.isEmpty()) {
                if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                            info.result = pos;
                            return;
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
            info.result = null;
        });
        return info;
    }

    private CrystalScannerInfo startScanDragonLair(CrystalType crystalType, BoundingBox area) {
        if(crystalType != CrystalType.DRAGON_LAIR) throw new AssertionError("咕咕嘎嘎!!!!!");

        CrystalScannerInfo info = new CrystalScannerInfo(crystalType);
        info.scannerName = crystalType.displayName;
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX() - 32;
            int minY = area.minY() - 32;
            int minZ = area.minZ() - 32;
            int maxX = area.maxX() + 32;
            int maxY = area.maxY() + 32;
            int maxZ = area.maxZ() + 32;

            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
            for(int x = minX; x <= maxX; x += 2) {
                for(int y = minY; y <= maxY; y += 2) {
                    if(!crystalType.isInRange(y)) continue;
                    label_z:for(int z = minZ; z <= maxZ; z += 2) {
                        info.currentScanning.set(x, y, z);
                        if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                                info.result = info.currentScanning.immutable();
                                return;
                            }
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + crystalType.displayName + "§e区域扫描, 开始轮询未加载区块..."));
            label:while(!unloadedQueue.isEmpty()) {
                if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                            info.result = pos;
                            return;
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
            info.result = null;
        });
        return info;
    }

    private CrystalScannerInfo startScanCorleone(CrystalType crystalType, BoundingBox area) {
        if(crystalType != CrystalType.CORLEONE) throw new AssertionError("咕咕嘎嘎!!!!!");

        CrystalScannerInfo info = new CrystalScannerInfo(crystalType);
        info.scannerName = crystalType.displayName;
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX() - 32;
            int minY = area.minY() - 32;
            int minZ = area.minZ() - 32;
            int maxX = area.maxX() + 32;
            int maxY = area.maxY() + 32;
            int maxZ = area.maxZ() + 32;

            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            BlockPos.MutableBlockPos temp = new BlockPos.MutableBlockPos();
            for(int x = minX; x <= maxX; x += 2) {
                for(int y = minY; y <= maxY; y++) {
                    if(!crystalType.isInRange(y)) continue;
                    label_z:for(int z = minZ; z <= maxZ; z += 2) {
                        info.currentScanning.set(x, y, z);
                        if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                                info.result = info.currentScanning.immutable();
                                return;
                            }
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + crystalType.displayName + "§e区域扫描, 开始轮询未加载区块..."));
            label:while(!unloadedQueue.isEmpty()) {
                if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
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
                                info.result = info.currentScanning.immutable();
                                return;
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
            info.result = null;
        });
        return info;
    }

    private CrystalScannerInfo startScanFairyGrotto(CrystalType crystalType, BoundingBox area) {
        if(crystalType != CrystalType.FAIRY_GROTTO) throw new AssertionError("咕咕嘎嘎!!!!!");

        CrystalScannerInfo info = new CrystalScannerInfo(crystalType);
        info.scannerName = crystalType.displayName;
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX() - 32;
            int minY = area.minY() - 32;
            int minZ = area.minZ() - 32;
            int maxX = area.maxX() + 32;
            int maxY = area.maxY() + 32;
            int maxZ = area.maxZ() + 32;

            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            for(int x = minX; x <= maxX; x++) {
                for(int y = minY; y <= maxY; y++) {
                    if(!crystalType.isInRange(y)) continue;
                    label_z:for(int z = minZ; z <= maxZ; z++) {
                        if(Math.abs(z + x) % 5 != 0) continue;
                        info.currentScanning.set(x, y, z);
                        if(foundFairy || currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
                        if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                            unloadedQueue.enqueue(info.currentScanning.immutable());
                            continue label_z;
                        }

                        if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;

                        if(pinkGlassPaneFinder.test(currentLevel, info.currentScanning)) {
                            info.result = info.currentScanning.immutable();
                            return;
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + crystalType.displayName + "§e区域扫描, 开始轮询未加载区块..."));
            while (!unloadedQueue.isEmpty()) {
                if (foundFairy || currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
                BlockPos pos = unloadedQueue.dequeue();
                info.currentScanning.set(pos);
                if (ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    if (NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                    if (pinkGlassPaneFinder.test(currentLevel, info.currentScanning)) {
                        info.result = info.currentScanning.immutable();
                        return;
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
            info.result = null;
        });
        return info;
    }

    private ScannerInfo startScanWormFishSpot(BoundingBox area) {
        ScannerInfo info = new ScannerInfo();
        info.scannerName = "worm_fish_spot";
        info.area = area;
        info.task = structureScannerExecutor.submit(() -> {
            int minX = area.minX();
            int minY = area.minY();
            int minZ = area.minZ();
            int maxX = area.maxX();
            int maxY = area.maxY();
            int maxZ = area.maxZ();

            ObjectHeapPriorityQueue<BlockPos> unloadedQueue = genUnloadedQueue();

            ClientLevel currentLevel = ToolList.mc.level;
            int currentToken = scannerToken;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }

            for(int x = minX; x <= maxX; x += 2) {
                for(int y = minY; y <= maxY; y += 1) {
                    for(int z = minZ; z <= maxZ; z += 2) {
                        info.currentScanning.set(x, y, z);
                        if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
                        if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                            unloadedQueue.enqueue(info.currentScanning.immutable());
                            continue;
                        }

                        if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                        if(lavaFinder.test(currentLevel, info.currentScanning)) {
                            info.result = info.currentScanning.immutable();
                            return;
                        }

                        if(!ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                            unloadedQueue.enqueue(info.currentScanning.immutable());
                            continue;
                        }
                    }
                }
            }
            if(ConfigManager.crystalHollowHelperDebug.getValue()) ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已完成" + info.scannerName + "§e区域扫描, 开始轮询未加载区块..."));
            while(!unloadedQueue.isEmpty()) {
                if(currentLevel != ToolList.mc.level || currentToken != scannerToken) return;
                BlockPos pos = unloadedQueue.dequeue();
                info.currentScanning.set(pos);
                if(ToolList.getInstance().isChunkLoaded(currentLevel, info.currentScanning)) {
                    if(NUCLEUS_BB.isInside(info.currentScanning) || !HOLLOWS_BB.isInside(info.currentScanning)) continue;
                    if(lavaFinder.test(currentLevel, info.currentScanning)) {
                        info.result = pos.immutable();
                        return;
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
            info.result = null;
        });
        return info;
    }

    static class ScannerInfo {
        public BlockPos.MutableBlockPos currentScanning = new BlockPos.MutableBlockPos(0, 0, 0);
        public BlockPos result = null;
        public Future<?> task;
        public String scannerName;
        public BoundingBox area;
    }

    static final class CrystalScannerInfo extends ScannerInfo {
        public CrystalType crystalType;

        CrystalScannerInfo(CrystalType crystalType) {
            this.crystalType = crystalType;
        }
    }

    enum CrystalType {
        BLUE("§b蓝色水晶", "Lost Precursor City", 121, 130),
        PURPLE("§5紫色水晶", "Jungle Temple", 72, 81),
        YELLOW("§e黄色水晶", "Khazad-dûm", 0, 63),
        ORANGE("§6橙色水晶", "Goblin Queen's Den", 125, 140),
        GREEN("§a绿色水晶", "Mines of Divan", 97, 102),
        GOBLIN_KING("§6王下一桶", "King Yolkar", 82, 168),
        DRAGON_LAIR("§c那位来客", "Dragon's Lair", 64, 189),
        CORLEONE("§a骷髅王", "Corleone", 64, 189),
        FAIRY_GROTTO("§d粉色小狗", "Fairy Grotto", 64, 189),
        BEAR3("§e熊出没", "Unknown", 64, 189),
        UNKNOWN("§c未知水晶", "Unknown", 0, 0);

        private final String displayName;
        private final String internalName;
        private final int minY;
        private final int maxY;

        CrystalType(String displayName, String internalName, int minY, int maxY) {
            this.displayName = displayName;
            this.internalName = internalName;
            this.minY = minY;
            this.maxY = maxY;
        }

        public boolean isInRange(int y) {
            return y >= minY && y <= maxY;
        }
    }

}
