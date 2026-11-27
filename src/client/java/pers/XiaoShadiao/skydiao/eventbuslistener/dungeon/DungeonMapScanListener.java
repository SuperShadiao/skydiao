package pers.XiaoShadiao.skydiao.eventbuslistener.dungeon;

import it.unimi.dsi.fastutil.longs.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.*;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 地牢地图扫描：从快捷栏的地图物品解出入口、房间尺寸、玩家所在格、当前房间模板和勾叉状态。
 *
 * <p>每 {@value #MAX_SCAN_INTERVAL} tick 跑一次。房间识别只在玩家换了物理格时执行（失败则每
 * {@value #RETRY_INTERVAL} tick 重试一次），识别结果按物理格缓存，所以同一个房间只会算一次。
 *
 * <p>结果通过 {@link #getCurrent()} 暴露给 HUD 使用。
 */
public class DungeonMapScanListener extends AbstractListener {

    /** 空闲/没识别到房间时的扫描间隔上限（tick）。 */
    private static final int MAX_SCAN_INTERVAL = 20;
    /** 识别到房间后的快速扫描间隔。 */
    private static final int FAST_SCAN_INTERVAL = 5;
    /** 没识别到房间时每次把间隔加这么多，直到上限。 */
    private static final int SCAN_INTERVAL_STEP = 5;
    private static final int RETRY_INTERVAL = 40;
    /** 地图像素里房间色块的边长下限，用来把入口色块和 9x9 的绿勾区分开。 */
    private static final int MIN_ROOM_SIZE = 12;
    /** 和 Skyblocker 一致：地牢动作栏里当前房间的秘密进度形如 "3/6 Secrets"。 */
    private static final Pattern SECRETS_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2}) Secrets");
    /** 刚换房间时动作栏还显示上一个房间的秘密数，这段时间内不记录，避免秘密串房。 */
    private static final int SECRET_PAUSE_TICKS = 40;
    /** 进本后等这么久（tick）再开始透视扫描。 */
    private static final int XRAY_START_DELAY = 100;
    /** 透视要用的其他形状模板，每隔这么多 tick 解压一种。 */
    private static final int SHAPE_INTERVAL = 40;
    /** 特殊房（解密/陷阱/小 Boss）识别失败后的重试间隔，等模板解压时别干等 2 秒。 */
    private static final int SPECIAL_RETRY_INTERVAL = 10;
    /** 透视扫失败的格子隔这么久（tick）再试，避免一次失败就永久放弃（区块没加载好、玩家高度不对等）。 */
    private static final int XRAY_RETRY_TICKS = 400;

    private static volatile Snapshot current;

    /** 抓包线程也会读这几个字段，所以用 volatile。 */
    private volatile long tick;
    /** 下一次唤醒扫描线程的 tick，以及当前使用的扫描间隔（扫描线程会根据结果动态调整）。 */
    private long nextScanTick;
    private volatile int scanInterval = MAX_SCAN_INTERVAL;
    /** 上一轮扫描是否识别到了当前房间，用来决定下一轮的间隔。 */
    private boolean scanMatched;
    /** 弱引用当前世界，避免 mod 单例长期持有 ClientLevel 导致旧世界无法回收。 */
    private WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
    /** 客户端 tick 找好的地图物品，扫描线程读取；换层时在 reset 里清空。 */
    private volatile MapItemSavedData map;
    /** 客户端 tick 顺手算好的玩家地图像素（地图装饰物只能在客户端线程读）。 */
    private volatile int[] playerPixel;
    /** 换层 reset 和整轮扫描互斥，避免清缓存时扫描线程还在用。 */
    private final Object scanLock = new Object();
    private int[] physicalEntrance;
    private int[] mapEntrance;
    private int mapRoomSize;
    /** 房间覆盖的每个物理格 -> 识别结果；大房间的每一格都指向同一个实例，避免走进去重新识别。 */
    private final Long2ObjectMap<Identified> identified = new Long2ObjectOpenHashMap<>();
    /** 陷阱/解密/小 Boss 房即使模板没匹配上，也先按类型占位，避免地图上什么都不显示。 */
    private final Long2ObjectMap<DungeonRoomType> placeholders = new Long2ObjectOpenHashMap<>();
    /** 物理格 -> {已找到秘密数, 总数}，不同格子指向同一个数组；抓包线程会写，所以用并发表。 */
    private final Map<Long, int[]> secrets = new ConcurrentHashMap<>();
    private long lastAttemptCell = Long.MIN_VALUE;
    private long lastAttemptTick = Long.MIN_VALUE;
    /** 透视扫描识别出的房间（玩家还没走到、地图上也还没探索到的那部分）。 */
    private final Long2ObjectMap<Identified> xray = new Long2ObjectOpenHashMap<>();
    /** 透视扫描试过但没匹配上的格子 -> 上次尝试的 tick，过一段时间再重试。 */
    private final Long2LongMap xrayTried = new Long2LongOpenHashMap();
    /** 透视扫出来的仙女房/血房：它们没有模板，靠招牌方块认。 */
    private final Long2ObjectMap<DungeonRoomType> xraySpecial = new Long2ObjectOpenHashMap<>();
    /** 透视扫描的轮询游标。 */
    private int xrayCursor;
    /** 全层门共用的 Y：进本时（Mort 所在房间）的脚底高度，之后一直用它扫门。 */
    private volatile int doorY = Integer.MIN_VALUE;
    /** Mort 所在的物理格（地牢入口）：主线程每 tick 检测并缓存，扫描线程只读。 */
    private volatile int[] mortCell;
    /** 两格之间的门类型缓存：{@link #DOOR_NORMAL} / {@link #DOOR_WITHER} / {@link #DOOR_BLOOD} / {@link #NO_DOOR}。 */
    private final Long2ObjectMap<int[]> doorKinds = new Long2ObjectOpenHashMap<>();
    /** 本轮还能新扫几道门。 */
    private int doorBudget;
    /** 玩家上一次所在的物理格，用来判断是否刚换房间。 */
    private volatile long lastPlayerCell = Long.MIN_VALUE;
    /** 在换房间后的这段时间里，动作栏的秘密数还不属于新房间。 */
    private volatile long secretPauseUntil = Long.MIN_VALUE;
    private boolean prefetched;
    /** 进本后先攒这么多 tick 再开始透视，避开开局区块加载和卡顿。 */
    private long xrayStartTick = Long.MAX_VALUE;
    /** 透视要用的非普通房间模板，按形状一个一个慢慢解压。 */
    private final Deque<DungeonRoomType> shapeQueue = new ArrayDeque<>(List.of(
            DungeonRoomType.TRAP, DungeonRoomType.MINIBOSS));
    private long nextShapeTick = Long.MAX_VALUE;

    /** 普通门。 */
    public static final int DOOR_NORMAL = 0;
    /** 凋零门，门口那圈是煤炭块。 */
    public static final int DOOR_WITHER = 1;
    /** 血房门，门口那圈是红色硬化黏土。 */
    public static final int DOOR_BLOOD = 2;
    /** 仙女房门：仙女房还没点亮、与它相连的房间已点亮时，用粉色单独标出来。 */
    public static final int DOOR_FAIRY = 3;
    /** 这两格之间没有门。 */
    private static final int NO_DOOR = -1;
    /** 每轮最多新扫几道门，防止一次扫太多方块卡住客户端。 */
    private static final int DOOR_BUDGET = 10;
    /** 当前扫描结果，未初始化或不在可用状态时为 {@code null}。 */
    public static Snapshot getCurrent() {
        return current;
    }

    @Override
    public String getListenerName() {
        return "DungeonMapScan";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        reset(level);
    }

    /**
     * 客户端 tick 只做轻量调度：换层检测、取地图物品和玩家像素，然后唤醒扫描线程。
     * 房间匹配、透视和门推断都在 {@link #run()} 的扫描线程里跑，避免卡主线程。
     */
    private void onTick(Minecraft mc) {
        if (mc.level != lastLevel.get()) reset(mc.level);
        if (mc.level == null || mc.player == null) {
            current = null;
            return;
        }
        if (!StatusManager.get().isInDungeon()) return;
        // Mort 只在进本瞬间可能加载出来一下，主线程每 tick 看一眼并缓存；
        // 放到扫描线程里会受 5~20 tick 的扫描间隔影响，极端情况下会错过。
        // 开局 400 tick 内每 tick 找，之后降为每 20 tick，避免整局遍历渲染实体
        if ((doorY == Integer.MIN_VALUE || mortCell == null) && (tick < 400 || tick % 20 == 0)) {
            Entity mort = findMort(mc.level);
            if (mort != null) {
                doorY = mort.blockPosition().getY();
                mortCell = DungeonMapUtils.physicalRoomPos(mort.getX(), mort.getZ());
            }
        }

        tick++;
        if (tick < nextScanTick) return;
        nextScanTick = tick + scanInterval;

        // 背包和地图装饰物都只能在客户端线程读，先准备好交给扫描线程
        if (map == null) map = findMap(mc);
        if (map == null) return;
        int[] pixel = DungeonMapUtils.playerPixel(map);
        if (pixel == null) return;
        playerPixel = pixel;
        ensureScanThread();
    }

    /** 扫描循环：睡到被客户端 tick 唤醒，然后跑一轮完整扫描。 */
    private void scanLoop() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
                // 客户端 tick 唤醒
            }
            try {
                scanTick();
            } catch (Throwable throwable) {
                logger.warn("地牢地图扫描失败", throwable);
                scanMatched = false;
            }
            // 识别到房间说明玩家在移动，加快扫描；一直没变化就慢慢退避到 20 tick
            scanInterval = scanMatched
                    ? FAST_SCAN_INTERVAL
                    : Math.min(MAX_SCAN_INTERVAL, scanInterval + SCAN_INTERVAL_STEP);
        }
    }

    /**
     * 优先用监听器自己的线程（{@link AbstractListener} 构造时就 start 了）。
     * 热重载时旧线程已经结束、无法重新 start，就退回一个自建线程，保证不用重启也能继续扫描。
     */
    private void ensureScanThread() {
        if (isAlive()) {
            interrupt();
        }
    }

    @Override
    public void run() {
        scanLoop();
    }

    /** 一轮完整扫描：房间识别、透视、门推断和快照生成。 */
    private void scanTick() {
        synchronized (scanLock) {
            scanMatched = false;
            Minecraft mc = Minecraft.getInstance();
            int[] playerPixel = this.playerPixel;
            if (mc.level == null || mc.player == null || map == null || playerPixel == null) return;

            if (!prefetched) {
                // 房间模板首次解压要 1 秒多，丢到后台线程，别在客户端主线程上卡住；
                // 透视要用的其他形状模板不急着全解压，隔一段时间一个，避免开局 CPU 打满
                prefetched = true;
                // 解密房最影响体验，解压完普通房后紧接着预热解密房；陷阱/小 Boss 按间隔慢慢来
                CompletableFuture.runAsync(() -> DungeonRoomData.roomsFor(DungeonRoomType.ROOM))
                        .thenRunAsync(() -> DungeonRoomData.roomsFor(DungeonRoomType.PUZZLE));
                xrayStartTick = tick + XRAY_START_DELAY;
                nextShapeTick = tick + SHAPE_INTERVAL;
            }
            if (!shapeQueue.isEmpty() && tick >= nextShapeTick) {
                nextShapeTick = tick + SHAPE_INTERVAL;
                DungeonRoomType shape = shapeQueue.poll();
                CompletableFuture.runAsync(() -> DungeonRoomData.roomsFor(shape));
            }

            if (mapEntrance == null) {
                int[] entrance = findEntrance(map, playerPixel);
                if (entrance == null) return;
                mapEntrance = new int[]{entrance[0], entrance[1]};
                mapRoomSize = entrance[2];
            }

            int[] playerCell = DungeonMapUtils.physicalRoomPos(mc.player.getX(), mc.player.getZ());
            int[] playerMapCell = DungeonMapUtils.mapCellOf(playerPixel, mapEntrance, mapRoomSize);
            DungeonRoomType type = DungeonRoomType.byColor(
                    DungeonMapUtils.color(map, playerMapCell[0], playerMapCell[1]));
            if (type == null) {
                current = null;
                return;
            }

            // 入口物理格和门基准 Y 都用主线程缓存的 Mort 结果
            int[] mortCell = this.mortCell;
            if (mortCell != null) {
                if (physicalEntrance == null) {
                    physicalEntrance = mortCell;
                } else if (physicalEntrance[0] != mortCell[0] || physicalEntrance[1] != mortCell[1]) {
                    // 之前锚点偏了（例如热重载前算出来的），用 Mort 纠正并清掉所有依赖锚点的缓存
                    logger.info("入口锚点从 [{}, {}] 修正为 [{}, {}]", physicalEntrance[0], physicalEntrance[1],
                            mortCell[0], mortCell[1]);
                    physicalEntrance = mortCell;
                    identified.clear();
                    placeholders.clear();
                    xray.clear();
                    xrayTried.clear();
                    xraySpecial.clear();
                    doorKinds.clear();
                    lastAttemptCell = Long.MIN_VALUE;
                }
            }
            // 还没找到 Mort 时不猜入口位置，等下一轮
            if (physicalEntrance == null) return;

            long playerCellKey = cellKey(playerCell);
            if (playerCellKey != lastPlayerCell) {
                // 同一房间内部的格子共享同一个 Identified，只有真的换了房间才暂停秘密检测
                Identified previous = identified.get(lastPlayerCell);
                Identified now = identified.get(playerCellKey);
                if (previous == null || now == null || previous != now) secretPauseUntil = tick + SECRET_PAUSE_TICKS;
                lastPlayerCell = playerCellKey;
            }

            Identified room = identified.get(playerCellKey);
            if (room == null && type.hasTemplate()
                    && shouldAttempt(playerCellKey, type == DungeonRoomType.ROOM ? RETRY_INTERVAL : SPECIAL_RETRY_INTERVAL)) {
                room = identify(mc, playerMapCell, type, mc.player.blockPosition());
                if (room != null) {
                    for (int[] segment : room.match().segments()) {
                        long key = cellKey(segment);
                        identified.put(key, room);
                        placeholders.remove(key);
                        // 玩家实地识别比透视准，把透视结果让位
                        xray.remove(key);
                    }
                } else if (type != DungeonRoomType.ROOM) {
                    // 特殊房即使模板没匹配上，也先按类型占位，至少能显示 Trap/Puzzle/Miniboss
                    placeholders.put(playerCellKey, type);
                }
            }

            if (ConfigManager.dungeonMapXray.getValue()) {
                // 刚进本时区块还在加载、客户端也卡，先缓一缓再开始透视，免得开局顿一下
                if (tick > xrayStartTick) xrayScan(mc);
            } else if (!xray.isEmpty() || !xrayTried.isEmpty() || !xraySpecial.isEmpty()) {
                xray.clear();
                xrayTried.clear();
                xraySpecial.clear();
            }

            List<Snapshot.RoomView> views = buildRooms();
            scanMatched = room != null;
            current = new Snapshot(map, type, playerPixel, playerMapCell, playerCell,
                    room == null ? null : room.match(),
                    room == null ? DungeonCheckmark.NONE : scanCheckmark(room.match().segments(), type),
                    physicalEntrance.clone(), mapEntrance.clone(), mapRoomSize, views, buildDoors(views),
                    room == null ? List.of() : buildSecretViews(room));
        }
    }

    /**
     * 当前房间还没找完的秘密点，换算成世界坐标交给渲染层。
     * 房间已经全清（秘密数和总数相等）时返回空表，不再显示 waypoint。
     */
    private List<Snapshot.SecretView> buildSecretViews(Identified room) {
        int[] progress = bestSecret(room.match().segments());
        if (progress != null && progress[1] > 0 && progress[0] >= progress[1]) return List.of();
        List<DungeonRoom.Secret> roomSecrets = room.match().room().secrets();
        if (roomSecrets.isEmpty()) return List.of();
        List<Snapshot.SecretView> views = new ArrayList<>(roomSecrets.size());
        for (DungeonRoom.Secret secret : roomSecrets) {
            BlockPos pos = room.match().secretPos(secret);
            views.add(new Snapshot.SecretView(pos.getX(), pos.getY(), pos.getZ(), secret.name(), secret.category()));
        }
        return views;
    }

    /**
     * 动作栏里的 "N/M Secrets" 只对玩家当前所在的房间有效，和 Skyblocker 的
     * {@code Room#updateSecretCount} 一样按物理格记录；识别完成后再由 {@link #buildRooms()} 取用。
     *
     * <p>直接抓包而不是监听聊天栏：部分 mod 会把动作栏消息替换或吞掉，监听消息事件会漏掉。
     */
    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if (packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay) && overlay) {
            String message = ToolList.getInstance().deleteColorCode(content.getString());
            Matcher matcher = SECRETS_PATTERN.matcher(message);
            if (matcher.find()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    int[] playerCell = DungeonMapUtils.physicalRoomPos(mc.player.getX(), mc.player.getZ());
                    long key = cellKey(playerCell);
                    // 刚踏进新房间时动作栏还是上一个房间的秘密数，先别记到新格子上
                    if (key != lastPlayerCell || tick < secretPauseUntil) return false;
                    secrets.put(key, new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))});
                }
            }
        } else if(packet instanceof ClientboundContainerSetSlotPacket packet1) {
            if (map == null && packet1.getContainerId() == 0 && packet1.getSlot() == 44) {
                ItemStack itemStack = packet1.getItem();
                MapId id = itemStack.get(DataComponents.MAP_ID);
                if (id != null) {
                    MapItemSavedData data = MapItem.getSavedData(id, mc.level);
                    if (data != null) {
                        map = data;
                    }
                }
            }
        }
        return false;
    }

    /** 把所有已识别房间换算成地图上的文字框，供 HUD 直接绘制。 */
    private List<Snapshot.RoomView> buildRooms() {
        if (physicalEntrance == null || mapEntrance == null) return List.of();
        List<Snapshot.RoomView> views = new ArrayList<>();
        LongSet covered = new LongOpenHashSet();
        Set<Identified> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Identified room : identified.values()) addRoomView(views, seen, covered, room);
        // 透视扫描出来的房间：只要有任何一格已经被实地识别结果占了，就整间让位
        for (Identified room : xray.values()) {
            boolean overlaps = false;
            for (int[] segment : room.match().segments()) {
                long key = cellKey(segment);
                // 仙女房/血房的格子不允许被形似的普通房间盖掉
                if (covered.contains(key) || xraySpecial.containsKey(key)) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) continue;
            if (!insideMap(room.match().segments())) continue;
            addRoomView(views, seen, covered, room);
        }
        addSpecialRooms(views, covered);
        // 透视扫到的仙女房/血房：地图上还没揭开颜色，只能靠招牌方块认
        for (Long2ObjectMap.Entry<DungeonRoomType> entry : xraySpecial.long2ObjectEntrySet()) {
            long cell = entry.getLongKey();
            if (covered.contains(cell)) continue;
            int[] physical = DungeonMapUtils.cellPos(cell);
            int[] topLeft = DungeonMapUtils.mapPosFromPhysical(physicalEntrance, mapEntrance, mapRoomSize, physical);
            if (topLeft[0] < 0 || topLeft[1] < 0
                    || topLeft[0] + mapRoomSize > DungeonMapUtils.MAP_SIZE
                    || topLeft[1] + mapRoomSize > DungeonMapUtils.MAP_SIZE) continue;
            views.add(new Snapshot.RoomView(topLeft[0], topLeft[1], mapRoomSize, mapRoomSize,
                    placeholderName(entry.getValue()), 0, 0, DungeonCheckmark.NONE, entry.getValue(),
                    new int[][]{topLeft}));
            covered.add(cell);
        }
        for (Long2ObjectMap.Entry<DungeonRoomType> entry : placeholders.long2ObjectEntrySet()) {
            long cell = entry.getLongKey();
            if (covered.contains(cell)) continue;
            int[] physical = DungeonMapUtils.cellPos(cell);
            int[] topLeft = DungeonMapUtils.mapPosFromPhysical(physicalEntrance, mapEntrance, mapRoomSize, physical);
            if (topLeft[0] < 0 || topLeft[1] < 0
                    || topLeft[0] + mapRoomSize > DungeonMapUtils.MAP_SIZE
                    || topLeft[1] + mapRoomSize > DungeonMapUtils.MAP_SIZE) continue;
            int[] secret = secrets.get(cell);
            views.add(new Snapshot.RoomView(topLeft[0], topLeft[1], mapRoomSize, mapRoomSize,
                    placeholderName(entry.getValue()),
                    secret == null ? 0 : secret[0], secret == null ? 0 : secret[1],
                    scanCheckmark(new int[][]{physical}, entry.getValue()), entry.getValue(), new int[][]{topLeft}));
            covered.add(cell);
        }
        addFallbackRooms(views, covered);
        return mergeRooms(views);
    }

    /** 把一个识别结果换算成地图上的文字框，并登记它占用的格子。 */
    private void addRoomView(List<Snapshot.RoomView> views, Set<Identified> seen, LongSet covered, Identified room) {
        if (!seen.add(room)) return;
        RoomMatcher.Match match = room.match();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int[] secret;
        int[][] segments = match.segments();
        int[][] segmentPositions = new int[segments.length][];
        for (int i = 0; i < segments.length; i++) {
            int[] topLeft = DungeonMapUtils.mapPosFromPhysical(physicalEntrance, mapEntrance, mapRoomSize, segments[i]);
            segmentPositions[i] = topLeft;
            minX = Math.min(minX, topLeft[0]);
            minZ = Math.min(minZ, topLeft[1]);
            maxX = Math.max(maxX, topLeft[0] + mapRoomSize);
            maxZ = Math.max(maxZ, topLeft[1] + mapRoomSize);
        }
        secret = bestSecret(segments);
        // 仙女房/血房的格子一律交给 addSpecialRooms 处理，避免被形似的普通房间占掉
        for (int[] position : segmentPositions) {
            if (isSpecialCell(position[0], position[1])) return;
        }
        for (int[] segment : segments) covered.add(cellKey(segment));
        DungeonCheckmark checkmark = scanCheckmark(segments, typeOf(match.room()));
        int found = secret == null ? 0 : secret[0];
        int total = secret == null ? secretTotal(match.room()) : secret[1];
        // 房间已经被别人清完（原版地图绿勾）时，秘密数直接按找满显示
        if (checkmark == DungeonCheckmark.GREEN) found = total;
        views.add(new Snapshot.RoomView(minX, minZ, maxX - minX, maxZ - minZ, match.room().name(),
                found, total, checkmark, typeOf(match.room()), segmentPositions));
    }

    /**
     * 多格房间的动作栏进度是按物理格分开存的，玩家只在站过的格子上下发更新，
     * 所以同一房间的格子里可能是 3/7、4/7 这样的旧值/新值混杂；取已找到数最大的那个。
     */
    private int[] bestSecret(int[][] segments) {
        int[] best = null;
        for (int[] segment : segments) {
            int[] progress = secrets.get(cellKey(segment));
            if (progress == null) continue;
            if (best == null || progress[0] > best[0]) best = progress;
        }
        return best;
    }

    /**
     * 房间的秘密总数：按名字前缀里的编号去重统计，一个秘密可能由多个节点组成
     * （例如 "1 - Superboom" 和 "1 - Item" 合起来才算秘密 1）；没有编号时返回 0。
     */
    private static int secretTotal(DungeonRoom room) {
        Set<String> groups = new HashSet<>();
        for (DungeonRoom.Secret secret : room.secrets()) groups.addAll(DungeonRoom.secretGroups(secret.name()));
        return groups.size();
    }

    /** 这一格在地图上已经是仙女房/血房，专门处理，不允许被形似的普通房间占掉。 */
    private boolean isSpecialCell(int x, int z) {
        DungeonRoomType type = DungeonRoomType.byColor(DungeonMapUtils.color(map, x, z));
        return type == DungeonRoomType.FAIRY || type == DungeonRoomType.BLOOD;
    }

    /** 房间的所有格子都要落在地图内，否则透视出来的房间会画到图外。 */
    private boolean insideMap(int[][] segments) {
        for (int[] segment : segments) {
            int[] topLeft = DungeonMapUtils.mapPosFromPhysical(physicalEntrance, mapEntrance, mapRoomSize, segment);
            if (topLeft[0] < 0 || topLeft[1] < 0
                    || topLeft[0] + mapRoomSize > 128 || topLeft[1] + mapRoomSize > 128) return false;
        }
        return true;
    }

    /**
     * 盲扫时 1x4 这类房间可能被逐格识别成四个 1x1，这里把「同名、同类型且格子相邻」的房间并成一间，
     * 免得地图上出现一串互不相连的小方块和重复文字。
     */
    private List<Snapshot.RoomView> mergeRooms(List<Snapshot.RoomView> views) {
        int pitch = DungeonMapUtils.mapPitch(mapRoomSize);
        List<Snapshot.RoomView> current = new ArrayList<>(views);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < current.size() && !changed; i++) {
                for (int j = i + 1; j < current.size() && !changed; j++) {
                    Snapshot.RoomView a = current.get(i);
                    Snapshot.RoomView b = current.get(j);
                    if (a.name().isEmpty() || !a.name().equals(b.name()) || a.type() != b.type()) continue;
                    if (!adjacent(a.segments(), b.segments(), pitch)) continue;
                    current.set(i, combine(a, b));
                    current.remove(j);
                    changed = true;
                }
            }
        }
        return current;
    }

    /** 两组格子是否有共边的相邻格。 */
    private static boolean adjacent(int[][] a, int[][] b, int pitch) {
        for (int[] x : a) {
            for (int[] y : b) {
                if (Math.abs(x[0] - y[0]) == pitch && x[1] == y[1]) return true;
                if (Math.abs(x[1] - y[1]) == pitch && x[0] == y[0]) return true;
            }
        }
        return false;
    }

    /** 合并两个同名房间：格子取并集，包围盒重新算。 */
    private Snapshot.RoomView combine(Snapshot.RoomView a, Snapshot.RoomView b) {
        List<int[]> segments = new ArrayList<>(a.segments().length + b.segments().length);
        LongSet seen = new LongOpenHashSet();
        for (Snapshot.RoomView view : new Snapshot.RoomView[]{a, b}) {
            for (int[] segment : view.segments()) {
                if (seen.add(DungeonMapUtils.cellKey(segment[0], segment[1]))) segments.add(segment);
            }
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] segment : segments) {
            minX = Math.min(minX, segment[0]);
            minZ = Math.min(minZ, segment[1]);
            maxX = Math.max(maxX, segment[0] + mapRoomSize);
            maxZ = Math.max(maxZ, segment[1] + mapRoomSize);
        }
        return new Snapshot.RoomView(minX, minZ, maxX - minX, maxZ - minZ, a.name(),
                Math.max(a.found(), b.found()), Math.max(a.total(), b.total()),
                a.checkmark(), a.type(), segments.toArray(new int[0][]));
    }

    /**
     * 推断房间与房间之间的门。相邻两格之间如果墙上有 2 格以上的开口就是普通门；门口那圈是煤炭块就是
     * 凋零门，是红色硬化黏土就是血房门。
     *
     * <p>不要求格子在地图上已经有颜色：未探索的房间在地图上还是黑的，但门框方块已经能扫到。
     * 血房门正是这种情况——旁边的房间点亮之前整片地图是黑的，门其实早就在那里了。
     * 没加载的区块由 {@link #doorInfo} 跳过，不会把“没加载”当成“没门”。
     */
    private List<Snapshot.DoorView> buildDoors(List<Snapshot.RoomView> views) {
        // 这些门是按方块推断出来的，属于透视功能的一部分；透视关闭时只保留原版地图自己画的门
        if (!ConfigManager.dungeonMapXray.getValue()) return List.of();
        DungeonMapUtils.MapGrid grid = DungeonMapUtils.roomGrid(mapEntrance, mapRoomSize);
        int pitch = grid.pitch();
        // 每个格子归属哪个房间视图：同一个视图里的相邻两格是大房间（2x2/1x4）的内部连接，不是门
        Long2IntMap owners = new Long2IntOpenHashMap();
        for (int i = 0; i < views.size(); i++) {
            for (int[] segment : views.get(i).segments()) {
                owners.put(DungeonMapUtils.cellKey(segment[0], segment[1]), i);
            }
        }
        doorBudget = DOOR_BUDGET;
        // 透视认出来、但地图上还没点亮的仙女房，它连出去的门单独用粉色
        LongSet hiddenFairies = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<DungeonRoomType> entry : xraySpecial.long2ObjectEntrySet()) {
            if (entry.getValue() != DungeonRoomType.FAIRY) continue;
            long cell = entry.getLongKey();
            int[] physical = DungeonMapUtils.cellPos(cell);
            int[] mapCell = DungeonMapUtils.mapPosFromPhysical(physicalEntrance, mapEntrance, mapRoomSize, physical);
            if (!DungeonMapUtils.isRevealed(map, mapCell[0], mapCell[1])) {
                hiddenFairies.add(DungeonMapUtils.cellKey(mapCell[0], mapCell[1]));
            }
        }
        List<Snapshot.DoorView> doors = new ArrayList<>();
        // 用固定的地图格做锚点（不跟玩家移动），逐格东西/南北配对；物理坐标由地图格反算，
        // 整局的房间格都落在同一套 32 格网格上，所以位置永远稳定
        for (int cx = 0; cx < grid.cells(); cx++) {
            int x = grid.x(cx);
            for (int cz = 0; cz < grid.cells(); cz++) {
                int z = grid.z(cz);
                if (!grid.fits(x, z)) continue;
                // 两边都已经点亮才交给原版地图画门；只要有一边没点亮，门口原版是不画的，我们自己补
                boolean revealedHere = DungeonMapUtils.isRevealed(map, x, z);
                for (int orientation = 0; orientation < 2; orientation++) {
                    boolean eastWest = orientation == 0;
                    int nx = eastWest ? x + pitch : x;
                    int nz = eastWest ? z : z + pitch;
                    if (!grid.fits(nx, nz)) continue;
                    // 同一间房的内部连接（2x2、1x4 这类大房间）不画门
                    if (sameRoom(owners, x, z, nx, nz)) continue;
                    int[] cell = DungeonMapUtils.physicalFromMap(physicalEntrance, mapEntrance, mapRoomSize,
                            new int[]{x, z});
                    int[] info = doorInfo(cell, eastWest);
                    if (info == null || info[0] == NO_DOOR) continue;
                    int kind = info[0];
                    boolean revealedNext = DungeonMapUtils.isRevealed(map, nx, nz);
                    // 两边都点亮时原版地图自己会画门（含凋零门/血房门），不重复渲染
                    if (revealedHere && revealedNext) continue;
                    // 仙女房还没点亮、与它相连的房间已点亮：这道门用粉色标出来
                    if ((hiddenFairies.contains(DungeonMapUtils.cellKey(x, z)) && revealedNext)
                            || (hiddenFairies.contains(DungeonMapUtils.cellKey(nx, nz)) && revealedHere)) {
                        kind = DOOR_FAIRY;
                    }
                    int half = Snapshot.DoorView.LENGTH / 2;
                    int along = Math.max(half, Math.min(mapRoomSize - half, info[1]));
                    int doorX = eastWest ? x + mapRoomSize : x + along - half;
                    int doorZ = eastWest ? z + along - half : z + mapRoomSize;
                    doors.add(new Snapshot.DoorView(doorX, doorZ, kind, eastWest));
                }
            }
        }
        return doors;
    }

    /**
     * 相邻两格是不是同一个房间的内部两格。
     *
     * <p>大房间（2x2、1x4）的内部是完全打通的，物理上墙那一带全是空气、头上又压着天花板，
     * 单看方块会被误判成普通门。这里用两个更可靠的口径排除：
     * 两边都已点亮时看原版地图在两格缝隙里填的是不是和格子一样的房间色；
     * 其余情况看两格是否属于同一个房间视图（实体识别和透视识别都会把整间房的格子放在一起）。
     */
    private boolean sameRoom(Long2IntMap owners, int x, int z, int nx, int nz) {
        if (DungeonMapUtils.isRevealed(map, x, z) && DungeonMapUtils.isRevealed(map, nx, nz)) {
            int seamX = nx != x ? x + mapRoomSize : x;
            int seamZ = nz != z ? z + mapRoomSize : z;
            if (DungeonMapUtils.color(map, seamX, seamZ) == DungeonMapUtils.color(map, x, z)) return true;
        }
        long a = DungeonMapUtils.cellKey(x, z);
        long b = DungeonMapUtils.cellKey(nx, nz);
        return owners.containsKey(a) && owners.containsKey(b) && owners.get(a) == owners.get(b);
    }

    /** 扫过的门按物理格缓存；返回 null 表示这一轮预算用完了，下一轮再看。 */
    private int[] doorInfo(int[] cell, boolean eastWest) {
        // 还没找到 Mort 时不猜门的高度，直接不动门
        if (doorY == Integer.MIN_VALUE) return null;
        // 缓存带上脚底 Y：玩家在不同高度的房间时门位置不同，换层要重扫
        long key = (DungeonMapUtils.cellKey(cell[0], cell[1]) * 2 + (eastWest ? 1 : 0)) * 256 + (doorBase() & 255);
        int[] cached = doorKinds.get(key);
        // 玩家身边的“无门”结果不可信：它可能是区块还没加载、或门还没被打开/打掉时缓存的，
        // 靠近时重新扫一次；真正的门一旦扫到就会换成正面结果
        if (cached != null && (cached[0] != NO_DOOR || !nearPlayer(cell))) return cached;
        // 区块根本没加载的格子不占预算：否则每轮预算都被最靠前（常常是地图角落、没加载）的
        // 那几对吃掉，玩家身边真正加载好的门永远轮不到
        if (!DungeonMapUtils.hasChunk(mc.level, cell[0] + 16, cell[1] + 16)) return null;
        // 每轮只扫几道新门，避免一次扫太多方块把客户端卡住
        if (doorBudget <= 0) return null;
        doorBudget--;

        int[] info = scanDoor(cell, eastWest);
        doorKinds.put(key, info);
        return info;
    }

    /** 这一格是不是就在玩家附近（用于重新验证缓存的“无门”结果）。 */
    private boolean nearPlayer(int[] cell) {
        if (mc.player == null) return false;
        double dx = mc.player.getX() - (cell[0] + 16);
        double dz = mc.player.getZ() - (cell[1] + 16);
        return dx * dx + dz * dz < 48 * 48;
    }

    /** 扫两格之间那道墙：先认门框方块，再看有没有开口。 */
    private int[] scanDoor(int[] cell, boolean eastWest) {
        // 门框就嵌在两格之间那道墙里（格子原点 +30 ~ +33），高度固定是 Mort 的高度往上 4 层。
        // 不用上下偏移：房间里常有煤炭块装饰，偏移就会误判。网格是固定步长、位置唯一，扫到即判定
        int blackMin = 32;
        int blackMax = -1;
        int redMin = 32;
        int redMax = -1;
        // 同一列（along）上红色硬化黏土最多占到几层墙厚。真血房门是贯穿整道墙的门，
        // 而普通房间墙上贴的一层红陶瓦装饰（比如 Midas 房）只有 1 层厚，不能当门
        int redLayers = 0;
        for (int y = doorBase() + 1; y <= doorBase() + 1; y++) {
            for (int along = 0; along < 32; along++) {
                int layers = 0;
                for (int across = 30; across <= 33; across++) {
                    int x = eastWest ? cell[0] + across : cell[0] + along;
                    int z = eastWest ? cell[1] + along : cell[1] + across;
                    if (!DungeonMapUtils.hasChunk(mc.level, x, z)) continue;
                    BlockState state = mc.level.getBlockState(new BlockPos(x, y, z));
                    // 只有煤炭块是真凋零门；黑色硬化黏土是墙上的假门框装饰，永远不开
                    if (state.is(Blocks.COAL_BLOCK)) {
                        blackMin = Math.min(blackMin, along);
                        blackMax = Math.max(blackMax, along);
                    } else if (state.is(Blocks.DYED_TERRACOTTA.red())) {
                        layers++;
                        redMin = Math.min(redMin, along);
                        redMax = Math.max(redMax, along);
                    }
                }
                redLayers = Math.max(redLayers, layers);
            }
        }
        if (redMax >= 0 && redLayers >= 2) return new int[]{DOOR_BLOOD, (redMin + redMax) / 4};
        if (blackMax >= 0) return new int[]{DOOR_WITHER, (blackMin + blackMax) / 4};
        // 普通门：墙上一个 2 格以上的开口。开口最下面 2 格必须是空气（有些门的第 3 格会压一排
        // 铁栏杆或装饰，不影响通过），正上方要有实心墙。房间里开放空间的上方是空气，不会被当成门。
        // 高度严格用 Mort 的高度，不做上下偏移
        int run = 0;
        for (int along = 0; along < 32; along++) {
            boolean clear = true;
            boolean solidAbove = false;
            for (int across = 30; across <= 33; across++) {
                int x = eastWest ? cell[0] + across : cell[0] + along;
                int z = eastWest ? cell[1] + along : cell[1] + across;
                for (int y = doorBase(); y <= doorBase() + 1 && clear; y++) {
                    if (!mc.level.getBlockState(new BlockPos(x, y, z)).isAir()) clear = false;
                }
                boolean solid = true;
                for (int y = doorBase() + 4; y <= doorBase() + 6 && solid; y++) {
                    if (mc.level.getBlockState(new BlockPos(x, y, z)).isAir()) solid = false;
                }
                solidAbove |= solid;
            }
            run = clear && solidAbove ? run + 1 : 0;
            if (run >= 2) return new int[]{DOOR_NORMAL, (along - (run - 1) / 2) / 2};
        }
        return new int[]{NO_DOOR, 0};
    }

    /** 门扫描用的脚底 Y：全层统一用 Mort 的脚底高度；还没找到 Mort 时不动门。 */
    private int doorBase() {
        return doorY;
    }

    /**
     * 找地牢开始处 Mort 的盔甲架；没加载到他时返回 {@code null}。
     * 他的脚底 Y 是全层门的基准高度，他所在的物理格就是地牢入口房间（用来锚定整张地图的换算）。
     */
    private static Entity findMort(ClientLevel level) {
        for (Entity entity : level.entitiesForRendering()) {
            String name = entity.getName().getString();
            if (name != null && name.toLowerCase(Locale.ROOT).contains("mort")) {
                return entity;
            }
        }
        return null;
    }

    /**
     * 没有识别出房间名的格子（包括玩家还没进去过的特殊房，比如还没清完的小 Boss 房）一律补一个
     * 兜底视图：地图上已经有勾叉就跟随原版颜色，没有勾叉也画灰色勾，用勾代替看不见的名字。
     */
    private void addFallbackRooms(List<Snapshot.RoomView> views, LongSet covered) {
        DungeonMapUtils.MapGrid grid = DungeonMapUtils.roomGrid(mapEntrance, mapRoomSize);
        for (int cx = 0; cx < grid.cells(); cx++) {
            for (int cz = 0; cz < grid.cells(); cz++) {
                int x = grid.x(cx);
                int z = grid.z(cz);
                if (!grid.fits(x, z)) continue;
                DungeonRoomType type = DungeonRoomType.byColor(DungeonMapUtils.color(map, x, z));
                // 入口格本身就是绿色，不能当成绿勾；未探索格也不画
                if (type == null || type == DungeonRoomType.UNKNOWN || type == DungeonRoomType.ENTRANCE) continue;
                int[] physical = DungeonMapUtils.physicalFromMap(physicalEntrance, mapEntrance, mapRoomSize,
                        new int[]{x, z});
                if (!covered.add(cellKey(physical))) continue;
                DungeonCheckmark checkmark = scanCheckmark(new int[][]{physical}, type);
                views.add(new Snapshot.RoomView(x, z, mapRoomSize, mapRoomSize, "", 0, 0,
                        checkmark, type, new int[][]{{x, z}}));
            }
        }
    }

    /**
     * 仙女房和血房没有模板，地图颜色本身就决定了房间，所以直接按格子补上名字和勾。
     *
     * <p>两者在原版地图里都是“没通关不画勾、通关画绿勾”：血房打完全部小怪变绿勾，
     * 仙女房拿完仙女之魂变绿勾。这里完全跟随地图上的实际勾叉，没有勾就显示灰色。
     */
    private void addSpecialRooms(List<Snapshot.RoomView> views, LongSet covered) {
        DungeonMapUtils.MapGrid grid = DungeonMapUtils.roomGrid(mapEntrance, mapRoomSize);
        for (int cx = 0; cx < grid.cells(); cx++) {
            for (int cz = 0; cz < grid.cells(); cz++) {
                int x = grid.x(cx);
                int z = grid.z(cz);
                if (!grid.fits(x, z)) continue;
                DungeonRoomType type = DungeonRoomType.byColor(DungeonMapUtils.color(map, x, z));
                if (type != DungeonRoomType.FAIRY && type != DungeonRoomType.BLOOD) continue;
                int[] physical = DungeonMapUtils.physicalFromMap(physicalEntrance, mapEntrance, mapRoomSize,
                        new int[]{x, z});
                if (!covered.add(cellKey(physical))) continue;
                placeholders.remove(cellKey(physical));
                views.add(new Snapshot.RoomView(x, z, mapRoomSize, mapRoomSize,
                        type == DungeonRoomType.FAIRY ? "Fairy" : "Blood", 0, 0,
                        scanCheckmark(new int[][]{physical}, type), type, new int[][]{{x, z}}));
            }
        }
    }

    private static String placeholderName(DungeonRoomType type) {
        return switch (type) {
            case TRAP -> "Trap";
            case PUZZLE -> "Puzzle";
            case MINIBOSS -> "Miniboss";
            default -> "";
        };
    }

    private static DungeonRoomType typeOf(DungeonRoom room) {
        return switch (room.shape()) {
            case PUZZLE -> DungeonRoomType.PUZZLE;
            case TRAP -> DungeonRoomType.TRAP;
            case MINIBOSS -> DungeonRoomType.MINIBOSS;
            default -> DungeonRoomType.ROOM;
        };
    }

    /**
     * 房间格子直接取自地图上同色连通块，模板匹配只负责“这是哪个房间 + 朝向”。
     *
     * <p>玩家脚部 y 有一格误差时匹配结果会完全不同（陷阱房尤其明显），所以 0、-1、+1 三个高度都试一次。
     * 只有最佳候选严格优于第二名才承认，避免像以前那样把相邻房间的模板套到当前格上。
     */
    private Identified identify(Minecraft mc, int[] playerMapCell, DungeonRoomType type, BlockPos center) {
        // 模板还没解压好时不要在这里同步解压（会卡扫描线程 1 秒多），转后台加载后快速重试
        if (!DungeonRoomData.isLoaded(type)) {
            DungeonRoomData.loadAsync(type);
            return null;
        }
        int[][] mapSegments = DungeonMapUtils.roomSegments(map, playerMapCell, mapRoomSize, type.color);
        if (mapSegments.length == 0) return null;
        int[][] segments = new int[mapSegments.length][];
        for (int i = 0; i < mapSegments.length; i++) {
            segments[i] = DungeonMapUtils.physicalFromMap(physicalEntrance, mapEntrance, mapRoomSize, mapSegments[i]);
        }

        List<RoomMatcher.Match> matches = List.of();
        for (int dy : new int[]{0, -1, 1}) {
            matches = RoomMatcher.match(mc.level, center.offset(0, dy, 0),
                    segments, DungeonRoomData.roomsFor(type));
            if (!matches.isEmpty()) break;
        }
        if (matches.isEmpty()) return null;

        RoomMatcher.Match match = matches.get(0);
        if (matches.size() > 1) {
            // RoomMatcher 只留下错块数和最佳相近的候选，还有并列就说明现在证据不够
            logger.debug("房间识别暂不唯一：{} 与 {}（错块 {} / {}，验证 {} 块）", match.room().id(),
                    matches.get(1).room().id(), match.mismatched(), matches.get(1).mismatched(), match.checked());
            return null;
        }
        logger.info("识别房间：{}（{}，{}，西北角 [{}, {}]，验证 {} 块，错块 {}）", match.room().name(),
                match.room().shape().folder(), match.direction(), match.minX(), match.minZ(),
                match.checked(), match.mismatched());
        return new Identified(match);
    }

    /**
     * 透视扫描：地牢开始后把地图网格挨个走一遍，只处理“区块已经加载、但还没有识别结果”的格子。
     *
     * <p>已探索的格子有地图颜色，可以照常先用颜色挑出同色连通格再匹配模板；未探索的格子地图上
     * 什么都没有，只能拿这一格的世界方块把所有模板盲试一遍。每 tick 只处理一格，避免卡主线程，
     * 没加载的格子直接跳过、等下一轮。
     */
    private void xrayScan(Minecraft mc) {
        if (physicalEntrance == null || mapEntrance == null || mapRoomSize <= 0) return;
        DungeonMapUtils.MapGrid grid = DungeonMapUtils.roomGrid(mapEntrance, mapRoomSize);
        int cells = grid.cells();
        int total = cells * cells;
        for (int step = 0; step < total; step++) {
            int index = (xrayCursor + step) % total;
            int x = grid.x(index % cells);
            int z = grid.z(index / cells);
            if (!grid.fits(x, z)) continue;
            int[] physical = DungeonMapUtils.physicalFromMap(physicalEntrance, mapEntrance, mapRoomSize,
                    new int[]{x, z});
            long key = cellKey(physical);
            if (identified.containsKey(key) || xray.containsKey(key)) continue;
            if (xrayTried.containsKey(key) && tick - xrayTried.get(key) < XRAY_RETRY_TICKS) continue;
            if (!DungeonMapUtils.isLoaded(mc.level, physical[0], physical[1],
                    physical[0] + DungeonMapUtils.ROOM_PITCH - 1,
                    physical[1] + DungeonMapUtils.ROOM_PITCH - 1)) continue;
            xrayCursor = (index + 1) % total;
            scanCell(mc, new int[]{x, z}, physical);
            return;
        }
    }

    /** 识别一个格子：已探索的走地图连通格，未探索的走盲匹配。 */
    private void scanCell(Minecraft mc, int[] mapCell, int[] physical) {
        // 透视识别远处的房间时不能用玩家脚底 Y：玩家可能在别的楼层/台阶上。
        // 全层地面都以 Mort 的高度为基准，优先用它
        int sampleY = doorY == Integer.MIN_VALUE ? mc.player.blockPosition().getY() : doorBase();
        DungeonRoomType type = DungeonRoomType.byColor(DungeonMapUtils.color(map, mapCell[0], mapCell[1]));
        // 模板还在后台解压：这一格先不记入 xrayTried，等加载完成后马上再扫
        if (type != null && type.hasTemplate() && !DungeonRoomData.isLoaded(type)) {
            DungeonRoomData.loadAsync(type);
            return;
        }
        Identified found = null;
        if (type == null || type == DungeonRoomType.UNKNOWN) {
            // 仙女房和血房没有结构文件，先用招牌方块认一下
            DungeonRoomType special = signatureRoom(mc, physical, sampleY);
            if (special != null) {
                if (xraySpecial.putIfAbsent(cellKey(physical), special) == null) {
                    logger.info("透视识别特殊房间：{}（物理格 [{}, {}]）", special.name(), physical[0], physical[1]);
                }
                return;
            }
        }
        if (type != null && type.hasTemplate()) {
            found = identify(mc, mapCell, type, new BlockPos(physical[0] + DungeonMapUtils.ROOM_PITCH / 2,
                    sampleY, physical[1] + DungeonMapUtils.ROOM_PITCH / 2));
        } else if (type == null || type == DungeonRoomType.UNKNOWN) {
            found = identifyBlind(mc, physical);
            // 盲匹配出来的多格房间可能越界压到别的房间上（比如把 Slime 的两格也划进 Gravel），
            // 只要和已有识别结果（不同房间）抢格子就放弃这次匹配
            if (found != null && overlapsOtherRoom(found)) {
                xrayTried.put(cellKey(physical), tick);
                return;
            }
        }
        if (found == null) {
            xrayTried.put(cellKey(physical), tick);
            return;
        }
        for (int[] segment : found.match().segments()) {
            long key = cellKey(segment);
            xray.put(key, found);
            placeholders.remove(key);
        }
    }

    /** 盲匹配的占地格是否和已有识别结果（不同房间）重叠。 */
    private boolean overlapsOtherRoom(Identified found) {
        for (int[] segment : found.match().segments()) {
            long key = cellKey(segment);
            if (xraySpecial.containsKey(key)) return true;
            Identified owner = identified.get(key);
            if (owner == null) owner = xray.get(key);
            if (owner == null) continue;
            if (!owner.match().room().id().equals(found.match().room().id())) return true;
        }
        return false;
    }

    /**
     * 仙女房和血房没有结构文件，用招牌方块认：仙女房满屋粉色玻璃/羊毛/黏土；
     * 血房是房间内圈（贴墙 1~3 格）的一整圈红色硬化黏土。
     */
    private DungeonRoomType signatureRoom(Minecraft mc, int[] physical, int y) {
        int size = DungeonMapUtils.ROOM_PITCH;
        // 仙女房：满屋粉色玻璃/羊毛/黏土，数量要够多才算。
        // 品红色羊毛/陶瓦在一些普通房间（传送迷宫之类）里只是装饰，不能当成仙女房
        int pink = 0;
        for (int dy = -2; dy <= 12; dy++) {
            for (int x = physical[0]; x < physical[0] + size; x += 2) {
                for (int z = physical[1]; z < physical[1] + size; z += 2) {
                    if (!DungeonMapUtils.hasChunk(mc.level, x, z)) continue;
                    Block block = mc.level.getBlockState(new BlockPos(x, y + dy, z)).getBlock();
                    if (block == Blocks.STAINED_GLASS.pink() || block == Blocks.STAINED_GLASS_PANE.pink()
                            || block == Blocks.WOOL.pink() || block == Blocks.DYED_TERRACOTTA.pink()) {
                        pink++;
                    }
                }
            }
        }
        if (pink >= 8) return DungeonRoomType.FAIRY;
        // 血房：内圈红色硬化黏土从门框上方（脚底 +4 层）一直铺到高处。
        // 门框本身只有脚底 0~3 层高，紧挨血房的普通房间也会在门框位置扫到十几块红黏土，
        // 所以必须跳过门框高度、只看内圈，并按数量判定，否则邻居房间会被误认成第二个血房
        int red = 0;
        for (int dy = 4; dy <= 10; dy++) {
            int py = y + dy;
            for (int along = 1; along < size - 1; along++) {
                for (int depth = 1; depth <= 3; depth++) {
                    if (isRed(mc, physical[0] + along, py, physical[1] + depth)) red++;
                    if (isRed(mc, physical[0] + along, py, physical[1] + size - 1 - depth)) red++;
                    if (isRed(mc, physical[0] + depth, py, physical[1] + along)) red++;
                    if (isRed(mc, physical[0] + size - 1 - depth, py, physical[1] + along)) red++;
                }
            }
        }
        return red >= 64 ? DungeonRoomType.BLOOD : null;
    }

    /** 取样点是不是红色硬化黏土；区块没加载就当作不是。 */
    private static boolean isRed(Minecraft mc, int x, int y, int z) {
        if (!DungeonMapUtils.hasChunk(mc.level, x, z)) return false;
        return mc.level.getBlockState(new BlockPos(x, y, z)).is(Blocks.DYED_TERRACOTTA.red());
    }

    /** 未探索格子的盲匹配：这一格 32x32 的地皮上到底长着什么房间。 */
    private Identified identifyBlind(Minecraft mc, int[] physical) {
        int y = doorY == Integer.MIN_VALUE ? mc.player.blockPosition().getY() : doorBase();
        List<DungeonRoom> rooms = DungeonRoomData.allRooms();
        for (int dy : new int[]{0, -1, 1}) {
            RoomMatcher.Match match = uniqueMatch(RoomMatcher.matchBlind(mc.level, physical[0], physical[1], y + dy, rooms));
            if (match == null) continue;
            logger.info("透视识别房间：{}（{}，西北角 [{}, {}]，验证 {} 块，错块 {}）", match.room().name(),
                    match.room().shape().folder(), match.minX(), match.minZ(), match.checked(), match.mismatched());
            return new Identified(match);
        }
        return null;
    }

    /** 只有一个模板通过，或者并列的候选其实都是同一个房间（只是朝向不同）时才认。 */
    private static RoomMatcher.Match uniqueMatch(List<RoomMatcher.Match> matches) {
        if (matches.isEmpty()) return null;
        RoomMatcher.Match best = matches.get(0);
        for (int i = 1; i < matches.size(); i++) {
            if (!matches.get(i).room().id().equals(best.room().id())) return null;
        }
        return best;
    }

    private boolean shouldAttempt(long cell, int retryInterval) {
        if (cell != lastAttemptCell || tick - lastAttemptTick >= retryInterval) {
            lastAttemptCell = cell;
            lastAttemptTick = tick;
            return true;
        }
        return false;
    }

    /**
     * 和 Skyblocker 的 {@code getRoomCheckmarkColour} 一致：每个 segment 取中心，向下扫半格找勾叉颜色。
     * 2x2 这类多格房间的勾只画在其中一格，所以必须遍历全部 segment。
     *
     * <p>要跳过房间本身的底色：血房的底色就是 18（红叉色），不跳过的话整间血房都会被当成红叉。
     */
    private DungeonCheckmark scanCheckmark(int[][] segments, DungeonRoomType type) {
        int half = mapRoomSize / 2;
        for (int[] segment : segments) {
            int[] topLeft = DungeonMapUtils.mapPosFromPhysical(physicalEntrance, mapEntrance, mapRoomSize, segment);
            for (int offset = 0; offset < half; offset++) {
                byte color = DungeonMapUtils.color(map, topLeft[0] + half, topLeft[1] + half + offset);
                if (type != null && color == type.color) continue;
                DungeonCheckmark checkmark = DungeonCheckmark.byColor(color);
                if (checkmark != DungeonCheckmark.NONE) return checkmark;
            }
        }
        return DungeonCheckmark.NONE;
    }

    /** 从玩家所在像素出发，按 10 像素步长广度搜索入口的绿色块。 */
    private static int[] findEntrance(MapItemSavedData map, int[] start) {
        Deque<int[]> queue = new ArrayDeque<>();
        LongSet seen = new LongOpenHashSet();
        queue.add(start);
        seen.add(cellKey(start));
        int[][] steps = {{-10, 0}, {0, -10}, {10, 0}, {0, 10}};
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            if (DungeonMapUtils.color(map, pos[0], pos[1]) == DungeonMapUtils.COLOR_ENTRANCE) {
                int[] found = entranceAt(map, pos[0], pos[1]);
                if (found != null) return found;
            }
            for (int[] step : steps) {
                int nx = pos[0] + step[0];
                int nz = pos[1] + step[1];
                if (nx < 0 || nz < 0 || nx >= 128 || nz >= 128) continue;
                if (seen.add(DungeonMapUtils.cellKey(nx, nz))) queue.add(new int[]{nx, nz});
            }
        }
        return null;
    }

    /** 把绿色块走到左上角再量宽度，得到入口像素与地图房间尺寸。 */
    private static int[] entranceAt(MapItemSavedData map, int x, int z) {
        while (DungeonMapUtils.color(map, x - 1, z) == DungeonMapUtils.COLOR_ENTRANCE) x--;
        while (DungeonMapUtils.color(map, x, z - 1) == DungeonMapUtils.COLOR_ENTRANCE) z--;
        int size = 0;
        while (size < 32 && DungeonMapUtils.color(map, x + size, z) == DungeonMapUtils.COLOR_ENTRANCE) size++;
        return size >= MIN_ROOM_SIZE ? new int[]{x, z, size} : null;
    }

    private static MapItemSavedData findMap(Minecraft mc) {
        for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            MapId id = stack.get(DataComponents.MAP_ID);
            if (id == null) continue;
            MapItemSavedData data = MapItem.getSavedData(id, mc.level);
            if (data != null) return data;
        }
        return null;
    }

    private void reset(ClientLevel level) {
        synchronized (scanLock) {
            lastLevel = new WeakReference<>(level);
            map = null;
            playerPixel = null;
            physicalEntrance = null;
            mapEntrance = null;
            mapRoomSize = 0;
            identified.clear();
            placeholders.clear();
            secrets.clear();
            xray.clear();
            xrayTried.clear();
            xraySpecial.clear();
            xrayCursor = 0;
            doorKinds.clear();
            lastAttemptCell = Long.MIN_VALUE;
            lastAttemptTick = Long.MIN_VALUE;
            lastPlayerCell = Long.MIN_VALUE;
            secretPauseUntil = Long.MIN_VALUE;
            xrayStartTick = tick + XRAY_START_DELAY;
            nextShapeTick = tick + SHAPE_INTERVAL;
            nextScanTick = 0;
            scanInterval = MAX_SCAN_INTERVAL;
            scanMatched = false;
            doorY = Integer.MIN_VALUE;
            mortCell = null;
            shapeQueue.clear();
            shapeQueue.addAll(List.of(DungeonRoomType.TRAP, DungeonRoomType.MINIBOSS));
            current = null;
        }
    }

    private static long cellKey(int[] cell) {
        return DungeonMapUtils.cellKey(cell[0], cell[1]);
    }

    private record Identified(RoomMatcher.Match match) {
    }

    /**
     * 一次扫描的结果快照，字段都是当次 tick 的即时值，可以直接交给 HUD 渲染。
     *
     * @param room 识别出的房间，{@code null} 表示还没识别出来（入口/仙女/血门本来就没有模板）
     * @param rooms 所有已识别房间在地图上的文字框
     */
    public record Snapshot(MapItemSavedData map, DungeonRoomType type, int[] playerPixel, int[] playerMapCell,
                           int[] playerCell, RoomMatcher.Match room, DungeonCheckmark checkmark,
                           int[] physicalEntrance, int[] mapEntrance, int mapRoomSize, List<RoomView> rooms,
                           List<DoorView> doors, List<SecretView> secrets) {

        /** 当前房间名，未识别时返回类型名。 */
        public String label() {
            if (room != null) return room.room().name();
            return type == null ? "?" : type.name();
        }

        public DungeonRoom dungeonRoom() {
            return room == null ? null : room.room();
        }

        /**
         * 一个已识别房间在地图上的显示信息。
         *
         * @param mapX / mapZ 房间文字框左上角（地图像素）
         * @param width / height 文字框尺寸（地图像素）
         * @param found / total 已找到秘密数 / 总数，{@code total} 为 0 表示未知
         * @param segments 房间每一格左上角的地图像素坐标，用来合并房间内部缝隙和定位 L 形文字
         */
        public record RoomView(int mapX, int mapZ, int width, int height, String name,
                               int found, int total, DungeonCheckmark checkmark, DungeonRoomType type,
                               int[][] segments) {
        }

        /**
         * 两个房间之间的一道门。
         *
         * @param x / z    门标记左上角的地图像素坐标
         * @param kind     {@link #DOOR_NORMAL} / {@link #DOOR_WITHER} / {@link #DOOR_BLOOD} / {@link #DOOR_FAIRY}
         * @param eastWest true 表示东西向连接：门嵌在竖直的墙缝里，标记 4 宽 6 高；
         *                 false 表示南北向连接，标记 6 宽 4 高
         */
        public record DoorView(int x, int z, int kind, boolean eastWest) {

            /** 门标记垂直方向的厚度，正好等于房间之间的 4 像素缝隙。 */
            public static final int THICKNESS = 4;
            /** 门标记沿墙方向的长度。 */
            public static final int LENGTH = 6;

            public int width() {
                return eastWest ? THICKNESS : LENGTH;
            }

            public int height() {
                return eastWest ? LENGTH : THICKNESS;
            }
        }

        /**
         * 当前房间里还没找完的一个秘密点，坐标已经换算成世界坐标。
         *
         * @param name     秘密名，比如 "1 - Wither Essence"
         * @param category 秘密类别，比如 chest / item / lever / wither
         */
        public record SecretView(int x, int y, int z, String name, String category) {
        }
    }
}
