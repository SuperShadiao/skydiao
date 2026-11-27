package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.dungeon.DungeonDoorESPListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.dungeon.DungeonMapScanListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonCheckmark;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonClass;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonMapUtils;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonRoomType;

import java.util.*;
import java.util.regex.Matcher;

/**
 * 地牢地图 HUD，显示风格对齐 Skyblocker：每个房间格上直接写房间名和秘密进度。
 *
 * <p>地图永远按 128x128 的完整大小绘制并整体放大 {@value #MAP_SCALE} 倍，不会因为多识别到房间
 * 就变大。文字再缩小到 {@value #TEXT_SCALE}，一格 16 像素的房间放大后是 48 像素，刚好放下两行
 * 房间名。文字颜色表示清关状态：灰=未清、白=已清、绿=秘密找齐、红=解密失败。
 */
public class DungeonMapHUD extends XSDHUD {

    public static final int MAP_SIZE = 128;
    /** 地图整体放大的倍数。 */
    private static final float MAP_SCALE = 1f;
    /** 文字相对地图的缩放，最终约为原版字号的 1.5 倍。 */
    private static final float TEXT_SCALE = 0.5f;
    /** 允许房间名横向溢出格子的比例，和 Skyblocker 的 MAX_WIDTH_SCALAR 思路一致。 */
    private static final float LABEL_WIDTH_SCALAR = 1.6f;
    /** 地图底色是黑色，透明度由配置控制（0~1）。 */
    private static int surfaceColor() {
        double alpha = ConfigManager.dungeonMapHudBackgroundAlpha.getValue();
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24;
    }

    private static final int COLOR_BORDER = 0xFF9A9A9A;
    private static final int COLOR_UNKNOWN_TEXT = 0xFFAAAAAA;
    /** 房间之间普通门的连接色。 */
    private static final int COLOR_DOOR = 0xFF888888;
    /** 凋零门的连接色（黄偏绿）。 */
    private static final int COLOR_WITHER_DOOR = 0xFFAEEA00;
    /** 血房门的连接色。 */
    private static final int COLOR_BLOOD_DOOR = 0xFFFF4D4D;
    /** 仙女房门的连接色（粉色）。 */
    private static final int COLOR_FAIRY_DOOR = 0xFFFF69B4;
    /** 玩家名 -> 职业色，缓存一段时间，队友死亡后 tab 行消失也能继续用最后一次的职业色。 */
    private static final Map<String, CachedClass> CLASS_CACHE = new HashMap<>();
    private static final long CLASS_TTL = 1200;
    /** 职业缓存刷新间隔（tick）：tab 行几十条，每帧解析是真的贵。 */
    private static final long CLASS_REFRESH_INTERVAL = 20;
    private static long lastClassRefreshTick = -CLASS_REFRESH_INTERVAL;
    /** 房间名换行结果缓存：key = 名字 + 换行宽度。 */
    private static final Map<String, List<String>> WRAP_CACHE = new HashMap<>();

    private record CachedClass(int color, long tick) {
    }

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "dungeon_map"), this);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> {
            PLAYER_MARKERS.clear();
            CLASS_CACHE.clear();
            WRAP_CACHE.clear();
            lastClassRefreshTick = -CLASS_REFRESH_INTERVAL;
        });
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
        if (snapshot == null) {
            // 不在可扫描状态时给 HUD 配置界面留个占位，否则没法拖动定位
            if (force) context.text(mc.font, "DungeonMap", 0, 0, 0xFFFFFFFF, true);
            return;
        }
        if (!force && (!ConfigManager.dungeonMapHudRender.getValue() || customBossbar.getStarRailBossBar() != null)) return;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.scale(MAP_SCALE);
        drawMap(context, snapshot, null);
        pose.popMatrix();
    }

    /**
     * 地图当前是否可渲染：不看 HUD 配置开关，只受扫描结果和“进入 Boss 房隐藏地图”这类状态影响。
     * 给 Leap 菜单等自己画地图的地方用。
     */
    public static boolean canRenderMap() {
        return DungeonMapScanListener.getCurrent() != null && customBossbar.getStarRailBossBar() == null;
    }

    /**
     * 在屏幕的 (x, y) 处按 {@code scale} 绘制地图，并返回鼠标悬停到的玩家 UUID。
     *
     * @param buttonHovered 鼠标悬停在菜单按钮上的玩家；该玩家的地图标记会放大显示
     * @param mouseX / mouseY 屏幕坐标
     * @return 悬停到的玩家 UUID；没有命中玩家标记时返回 {@code null}
     */
    public UUID renderMap(GuiGraphicsExtractor context, float x, float y, float scale,
                          int mouseX, int mouseY, UUID buttonHovered) {
        DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
        if (snapshot == null) return null;

        // 用上一帧缓存的标记位置算悬停，绘制时就知道该放大哪个标记
        UUID hovered = hoveredPlayer((mouseX - x) / scale, (mouseY - y) / scale);
        UUID highlight = buttonHovered != null ? buttonHovered : hovered;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        drawMap(context, snapshot, highlight);
        pose.popMatrix();

        return hovered;
    }

    /** 按地图局部坐标找鼠标下面的玩家标记；多个重叠时取最近的。 */
    private static UUID hoveredPlayer(float mapX, float mapZ) {
        UUID hovered = null;
        double best = 8 * 8;
        for (Map.Entry<UUID, Marker> entry : PLAYER_MARKERS.entrySet()) {
            Marker marker = entry.getValue();
            double dx = marker.mapX() - mapX;
            double dz = marker.mapZ() - mapZ;
            double distance = dx * dx + dz * dz;
            if (distance <= best) {
                best = distance;
                hovered = entry.getKey();
            }
        }
        return hovered;
    }

    /** 绘制整张地图；坐标是地图像素（0~128），由调用方的 pose 决定最终位置和缩放。 */
    private void drawMap(GuiGraphicsExtractor context, DungeonMapScanListener.Snapshot snapshot, UUID highlightedPlayer) {
        MapItemSavedData map = snapshot.map();
        int size = snapshot.mapRoomSize();
        DungeonMapUtils.MapGrid grid = DungeonMapUtils.roomGrid(snapshot.mapEntrance(), size);

        // 地图尺寸固定，不随已识别的房间数量变化
        context.fill(-3, -3, MAP_SIZE + 3, MAP_SIZE + 3, surfaceColor());
        context.fill(-3, -3, MAP_SIZE + 3, -2, COLOR_BORDER);
        context.fill(-3, MAP_SIZE + 2, MAP_SIZE + 3, MAP_SIZE + 3, COLOR_BORDER);
        context.fill(-3, -3, -2, MAP_SIZE + 3, COLOR_BORDER);
        context.fill(MAP_SIZE + 2, -3, MAP_SIZE + 3, MAP_SIZE + 3, COLOR_BORDER);

        drawCells(context, map, grid);
        drawGaps(context, map, grid);
        drawUnknown(context, map, grid);
        drawHiddenRooms(context, snapshot, size);
        drawDoors(context, snapshot);

        for (DungeonMapScanListener.Snapshot.RoomView room : snapshot.rooms()) drawRoom(context, room, map, size);

        drawPlayers(context, snapshot, grid.pitch(), highlightedPlayer);
    }

    /**
     * 逐格填充房间色块。格子之间 4 像素的缝隙由 {@link #drawGaps} 按照地图像素原样补齐，这里只额外
     * 补上四条缝交叉处的 4x4 小方块：2x2 房间中间的洞在这里，而 L 形房间的凹角在地图上是暗的，
     * 采样到 0 就跳过，不会把凹角涂满。
     */
    private void drawCells(GuiGraphicsExtractor context, MapItemSavedData map, DungeonMapUtils.MapGrid grid) {
        for (int cx = 0; cx < grid.cells(); cx++) {
            for (int cz = 0; cz < grid.cells(); cz++) {
                int x = grid.x(cx);
                int z = grid.z(cz);
                if (x >= MAP_SIZE || z >= MAP_SIZE) continue;
                byte color = DungeonMapUtils.color(map, x, z);
                if (color <= 0) continue;
                context.fill(x, z, Math.min(x + grid.size(), MAP_SIZE), Math.min(z + grid.size(), MAP_SIZE), mapColor(color));

                byte corner = DungeonMapUtils.color(map, x + grid.size(), z + grid.size());
                if (corner > 0) {
                    context.fill(x + grid.size(), z + grid.size(), Math.min(x + grid.pitch(), MAP_SIZE),
                            Math.min(z + grid.pitch(), MAP_SIZE), mapColor(corner));
                }
            }
        }
    }

    /** 把房间之间 4 像素缝隙里的门（普通门/凋零门/血房门）按原版地图色画出来。 */
    private void drawGaps(GuiGraphicsExtractor context, MapItemSavedData map, DungeonMapUtils.MapGrid grid) {
        for (int cx = 0; cx < grid.cells(); cx++) {
            for (int cz = 0; cz < grid.cells(); cz++) {
                int x = grid.x(cx);
                int z = grid.z(cz);
                if (x >= MAP_SIZE || z >= MAP_SIZE) continue;
                for (int dz = 0; dz < grid.size(); dz++) {
                    drawGapLine(context, map, x + grid.size(), z + dz, grid.pitch() - grid.size(), true);
                }
                for (int dx = 0; dx < grid.size(); dx++) {
                    drawGapLine(context, map, x + dx, z + grid.size(), grid.pitch() - grid.size(), false);
                }
            }
        }
    }

    /** 画一条 4 像素宽/高的缝隙；同色连通段合并成一次 fill。 */
    private void drawGapLine(GuiGraphicsExtractor context, MapItemSavedData map, int x, int z, int gap, boolean horizontal) {
        int runStart = -1;
        byte runColor = 0;
        for (int i = 0; i <= gap; i++) {
            byte color = i == gap ? 0 : DungeonMapUtils.color(map, horizontal ? x + i : x, horizontal ? z : z + i);
            if (color < 0) color = 0;
            if (color == runColor) continue;
            if (runColor > 0) {
                if (horizontal) {
                    context.fill(x + runStart, z, x + i, z + 1, mapColor(runColor));
                } else {
                    context.fill(x, z + runStart, x + 1, z + i, mapColor(runColor));
                }
            }
            runStart = i;
            runColor = color;
        }
    }

    private void drawUnknown(GuiGraphicsExtractor context, MapItemSavedData map, DungeonMapUtils.MapGrid grid) {
        int half = grid.size() / 2;
        for (int cx = 0; cx < grid.cells(); cx++) {
            for (int cz = 0; cz < grid.cells(); cz++) {
                int x = grid.x(cx);
                int z = grid.z(cz);
                if (x + grid.size() > MAP_SIZE || z + grid.size() > MAP_SIZE) continue;
                if (DungeonMapUtils.color(map, x, z) != DungeonMapUtils.COLOR_UNKNOWN) continue;
                drawLines(context, x + half, z + half, List.of("?"), COLOR_UNKNOWN_TEXT);
            }
        }
    }

    /**
     * 透视扫描识别出来、但地图上还没探索到的房间：用房间类型色打底，再盖一层 alpha 180 的黑色，
     * 和已经探索过的房间区分开。地图上本来就有真实房间色的格子保持原样。
     */
    private void drawHiddenRooms(GuiGraphicsExtractor context, DungeonMapScanListener.Snapshot snapshot, int size) {
        MapItemSavedData map = snapshot.map();
        int pitch = DungeonMapUtils.mapPitch(size);
        int gap = pitch - size;
        for (DungeonMapScanListener.Snapshot.RoomView room : snapshot.rooms()) {
            if (room.type() == null || room.type() == DungeonRoomType.UNKNOWN
                    || room.type() == DungeonRoomType.ENTRANCE) continue;
            int fill = mapColor(room.type().color);
            for (int[] segment : room.segments()) {
                // 地图上已经是真实房间色（已探索）就保持原样，未探索/未知的色块才压暗
                if (DungeonMapUtils.isRevealed(map, segment[0], segment[1])) continue;
                fillHidden(context, segment[0], segment[1], size, size, fill);
                // 同一间房相邻两格之间的缝也补上，否则 1x4/2x2 房间看起来还是几个小方块
                for (int[] neighbor : room.segments()) {
                    if (neighbor[1] == segment[1] && neighbor[0] == segment[0] + pitch) {
                        fillHidden(context, segment[0] + size, segment[1], gap, size, fill);
                    } else if (neighbor[0] == segment[0] && neighbor[1] == segment[1] + pitch) {
                        fillHidden(context, segment[0], segment[1] + size, size, gap, fill);
                    }
                }
            }
        }
    }

    /** 先铺房间色再盖一层半透明黑。 */
    private void fillHidden(GuiGraphicsExtractor context, int x, int z, int width, int height, int color) {
        if (x >= MAP_SIZE || z >= MAP_SIZE) return;
        int x1 = Math.min(x + width, MAP_SIZE);
        int z1 = Math.min(z + height, MAP_SIZE);
        context.fill(x, z, x1, z1, color);
        context.fill(x, z, x1, z1, 0xB4000000);
    }

    /** 房间之间的门：凋零门黄绿色、血房门红色、仙女房门粉色，其余灰色。 */
    private void drawDoors(GuiGraphicsExtractor context, DungeonMapScanListener.Snapshot snapshot) {
        // 这些门属于透视功能，透视关闭时不画
        if (!ConfigManager.dungeonMapXray.getValue()) return;
        // 持有钥匙时，特殊门每秒在白色和原色之间闪一次
        boolean blinkWhite = DungeonDoorESPListener.hasKey() && (System.currentTimeMillis() / 1000) % 2 == 0;
        for (DungeonMapScanListener.Snapshot.DoorView door : snapshot.doors()) {
            int color = switch (door.kind()) {
                case DungeonMapScanListener.DOOR_WITHER -> COLOR_WITHER_DOOR;
                case DungeonMapScanListener.DOOR_BLOOD -> COLOR_BLOOD_DOOR;
                case DungeonMapScanListener.DOOR_FAIRY -> COLOR_FAIRY_DOOR;
                default -> COLOR_DOOR;
            };
            // 只有 ESP 标记的那道门（以及仙女房门）才闪烁
            boolean espMarked = DungeonDoorESPListener.isMarked(door)
                    || door.kind() == DungeonMapScanListener.DOOR_FAIRY;
            if (blinkWhite && espMarked) color = 0xFFFFFFFF;
            // 对齐原版地图的门像素：横向的门（6x4）右移 2，纵向的门（4x6）下移 2
            int x = door.x() + (door.eastWest() ? 0 : 2);
            int z = door.z() + (door.eastWest() ? 2 : 0);
            context.fill(x, z, Math.min(x + door.width(), MAP_SIZE),
                    Math.min(z + door.height(), MAP_SIZE), color);
        }
    }

    /** 在房间文字框中心画房间名和秘密进度，清关状态决定文字颜色。 */
    private void drawRoom(GuiGraphicsExtractor context, DungeonMapScanListener.Snapshot.RoomView room,
                          MapItemSavedData map, int size) {
        if (room.width() <= 0 || room.height() <= 0) return;
        int color = stateColor(room.checkmark());

        // 没识别出房间名（或关掉了房间名）时退回 Skyblocker 风格：只在最左上角那一格的中心画勾叉。
        // 灰色勾不画，未清状态的灰色只通过文字模式下的灰色文字表达。
        if (room.name().isEmpty() || !ConfigManager.dungeonMapHudShowName.getValue()) {
            if (room.checkmark() == DungeonCheckmark.NONE) return;
            int[] icon = topLeftCell(room, size);
            drawStateIcon(context, icon[0], icon[1], iconColor(room.checkmark()),
                    room.checkmark() == DungeonCheckmark.RED);
            return;
        }

        float centerX = room.mapX() + room.width() / 2f;
        float centerY = room.mapZ() + room.height() / 2f;
        float wrapWidth = room.width() * LABEL_WIDTH_SCALAR;

        int[][] segments = room.segments();
        if (segments.length == 3) {
            // L 形：文字放在横着的那 2x1 部分所在行，避免落进缺角
            int barZ = barRowZ(segments);
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (int[] segment : segments) {
                if (segment[1] != barZ) continue;
                minX = Math.min(minX, segment[0]);
                maxX = Math.max(maxX, segment[0]);
            }
            centerX = (minX + maxX + size) / 2f;
            centerY = barZ + size / 2f;
            wrapWidth = (maxX - minX + size) * LABEL_WIDTH_SCALAR;
        }

        List<String> lines = new ArrayList<>(wrap(room.name(), wrapWidth / TEXT_SCALE));
        // 还没探索到的房间只写名字，秘密数要等真正进去才准
        boolean explored = DungeonMapUtils.isRevealed(map, room.segments()[0][0], room.segments()[0][1]);
        if (explored && (room.type() == DungeonRoomType.ROOM || room.type() == DungeonRoomType.TRAP
                || (room.type() == DungeonRoomType.PUZZLE && room.total() > 0))) {
            lines.add(room.found() + "/" + (room.total() > 0 ? room.total() : "?"));
        }
        drawLines(context, centerX, centerY, lines, color);
    }

    /** 房间最左上角那一格的中心，勾叉画在这里最贴近原版地图。 */
    private static int[] topLeftCell(DungeonMapScanListener.Snapshot.RoomView room, int size) {
        int[] best = null;
        for (int[] segment : room.segments()) {
            if (best == null || segment[0] < best[0] || (segment[0] == best[0] && segment[1] < best[1])) {
                best = segment;
            }
        }
        if (best == null) return new int[]{room.mapX() + size / 2, room.mapZ() + size / 2};
        return new int[]{best[0] + size / 2, best[1] + size / 2};
    }

    /** 找出 L 形房间横着的 2x1 部分所在的行（3 个格子里有 2 个共用同一个 z）。 */
    private static int barRowZ(int[][] segments) {
        int z0 = segments[0][1];
        int z1 = segments[1][1];
        int z2 = segments[2][1];
        if (z0 == z1 || z0 == z2) return z0;
        return z1;
    }

    /** 头像缓存：玩家 UUID -> 最近一次在地图箭头/实体上的位置，实体走出渲染距离后继续用缓存渲染。 */
    private static final Map<UUID, Marker> PLAYER_MARKERS = new HashMap<>();
    /** 箭头和实体都消失后缓存还能保留多久（tick）。 */
    private static final long MARKER_TTL = 400;

    /** 一名队友在地图上的位置、朝向、职业色和皮肤，来源可以是实体，也可以是地图箭头。 */
    private record Marker(float mapX, float mapZ, float yaw, int classColor, PlayerSkin skin, long tick) {
    }

    /**
     * 用地图上的玩家箭头（{@link MapItemSavedData#getDecorations()}）定位队友，而不是遍历 {@code level.players()}：
     * 队友走出渲染距离后实体就没了，但服务器仍然在更新地图箭头，所以把玩家和箭头绑在一起缓存，
     * 实体消失时继续用缓存渲染，头像不会跟着一起消失。
     */
    private void drawPlayers(GuiGraphicsExtractor context, DungeonMapScanListener.Snapshot snapshot, int pitch,
                             UUID highlightedPlayer) {
        if (mc.level == null || mc.getConnection() == null || mc.player == null) return;
        long now = mc.level.getGameTime();
        refreshClassCache(mc, now);

        PlayerInfo self = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        // tab 里有职业信息的才是队友（顺便过滤掉 Hypixel 用玩家实体实现的 NPC），self 放第一个
        List<PlayerInfo> party = new ArrayList<>();
        if (self != null) party.add(self);
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info != self && classColor(info.getProfile().name()) != 0) party.add(info);
        }

        // 箭头顺序和队伍顺序一致：FRAME 是 self，其余箭头按顺序对应剩下的队友
        List<PlayerInfo> unassigned = new ArrayList<>(party);
        if (!unassigned.isEmpty() && unassigned.get(0) == self) unassigned.remove(0);
        for (MapDecoration decoration : snapshot.map().getDecorations()) {
            PlayerInfo info = decoration.type().value() == MapDecorationTypes.FRAME.value()
                    ? self : takeNearest(unassigned, decoration);
            if (info == null) continue;
            UUID uuid = info.getProfile().id();

            // 实体还在渲染距离内就用实时位置，被卸载后改用箭头位置和朝向
            Player entity = mc.level.getPlayerByUUID(uuid);
            float mapX;
            float mapZ;
            float yaw;
            if (entity != null) {
                mapX = snapshot.mapEntrance()[0] + (float) (entity.getX() - snapshot.physicalEntrance()[0]) / 32f * pitch;
                mapZ = snapshot.mapEntrance()[1] + (float) (entity.getZ() - snapshot.physicalEntrance()[1]) / 32f * pitch;
                yaw = entity.getYRot();
            } else {
                mapX = decoration.x() / 2f + 64f;
                mapZ = decoration.y() / 2f + 64f;
                yaw = decoration.rot() * 360f / 16f;
            }
            PLAYER_MARKERS.put(uuid, new Marker(mapX, mapZ, yaw, classColor(info.getProfile().name()),
                    info.getSkin(), now));
        }
        PLAYER_MARKERS.values().removeIf(marker -> now - marker.tick() > MARKER_TTL);

        for (Map.Entry<UUID, Marker> entry : PLAYER_MARKERS.entrySet()) {
            Marker marker = entry.getValue();
            if (marker.mapX() < 0 || marker.mapZ() < 0 || marker.mapX() > MAP_SIZE || marker.mapZ() > MAP_SIZE) continue;

            // Leap 菜单里选中的目标放大约 2 倍
            boolean highlighted = entry.getKey().equals(highlightedPlayer);
            int half = highlighted ? 8 : 4;

            Matrix3x2fStack pose = context.pose();
            pose.pushMatrix();
            pose.translate(marker.mapX() + 0.5f, marker.mapZ() + 0.5f);
            pose.rotate((float) Math.toRadians(marker.yaw() + 180f));
            PlayerFaceExtractor.extractRenderState(context, marker.skin(), -half, -half, half * 2);

            int color = marker.classColor();
            if (color != 0) {
                int outer = half + 1;
                context.fill(-outer, -outer, outer, -half, color);
                context.fill(-outer, half, outer, outer, color);
                context.fill(-outer, -half, -half, half, color);
                context.fill(half, -half, outer, half, color);
                context.fill(-1, -outer - 2, 1, -outer, color);
            }
            pose.popMatrix();
        }
    }

    /** 从还没分配的队友里挑一个箭头：优先挑缓存位置离箭头最近的，都没缓存就按队伍顺序取第一个。 */
    private static PlayerInfo takeNearest(List<PlayerInfo> unassigned, MapDecoration decoration) {
        if (unassigned.isEmpty()) return null;
        float x = decoration.x() / 2f + 64f;
        float z = decoration.y() / 2f + 64f;
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < unassigned.size(); i++) {
            Marker marker = PLAYER_MARKERS.get(unassigned.get(i).getProfile().id());
            if (marker == null) continue;
            double distance = (marker.mapX() - x) * (marker.mapX() - x) + (marker.mapZ() - z) * (marker.mapZ() - z);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return unassigned.remove(bestIndex);
    }

    /**
     * 地牢里的玩家行并不在真实 {@link PlayerInfo} 上：Hypixel 把它们塞进名字形如 {@code !A-b} 的假 tab
     * 条目（显示名里有职业），真实条目的显示名是 null。所以这里遍历所有 tab 行，靠
     * {@link DungeonClass#TAB_PATTERN} 反查出「玩家名 -> 职业」。
     */
    private static void refreshClassCache(Minecraft mc, long now) {
        if (mc.level == null || mc.getConnection() == null) return;
        // 每帧解析整张 tab 太贵，按固定间隔刷新即可；死亡后颜色靠 TTL 缓存保留
        if (now - lastClassRefreshTick < CLASS_REFRESH_INTERVAL) return;
        lastClassRefreshTick = now;
        ToolList tools = ToolList.getInstance();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display != null) addClassLine(tools.deleteColorCode(display.getString()), now);
            // 老式 tab 行（比如 self）职业可能写在队伍前缀/后缀里
            PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(info.getProfile().name());
            if (team != null) {
                addClassLine(tools.deleteColorCode(team.getPlayerPrefix().getString()
                        + info.getProfile().name() + team.getPlayerSuffix().getString()), now);
            }
        }
        CLASS_CACHE.values().removeIf(cached -> now - cached.tick() > CLASS_TTL);
    }

    private static void addClassLine(String line, long now) {
        Matcher matcher = DungeonClass.TAB_PATTERN.matcher(line);
        if (!matcher.find()) return;
        DungeonClass dungeonClass = DungeonClass.from(matcher.group("class"));
        if (dungeonClass != DungeonClass.UNKNOWN) {
            CLASS_CACHE.put(matcher.group("name"), new CachedClass(dungeonClass.color(), now));
        }
    }

    private static int classColor(String playerName) {
        CachedClass cached = CLASS_CACHE.get(playerName);
        return cached == null ? 0 : cached.color();
    }

    /** 在地图坐标里按 {@value #TEXT_SCALE} 缩放绘制多行居中文字。 */
    private void drawLines(GuiGraphicsExtractor context, float centerX, float centerY, List<String> lines, int color) {
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.scale(TEXT_SCALE);
        float y = -lines.size() * mc.font.lineHeight / 2f;
        for (String line : lines) {
            float width = mc.font.width(line);
            context.text(mc.font, line, Math.round(-width / 2f), Math.round(y), color, true);
            y += mc.font.lineHeight;
        }
        pose.popMatrix();
    }

    /** 按空格把房间名拆成能放进格子宽度的若干行；房间名和宽度整局不变，按 key 缓存结果。 */
    private static List<String> wrap(String text, float maxWidth) {
        String key = text + '\u0000' + Float.floatToIntBits(maxWidth);
        List<String> cached = WRAP_CACHE.get(key);
        if (cached != null) return cached;

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() == 0 || mc.font.width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        List<String> result = List.copyOf(lines);
        WRAP_CACHE.put(key, result);
        return result;
    }

    /** 勾叉图标：不显示房间名时画在房间左上角那一格的中心。 */
    private void drawStateIcon(GuiGraphicsExtractor context, float x, float y, int color, boolean cross) {
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        if (cross) {
            pose.rotate((float) Math.toRadians(45));
            context.fill(-1, -4, 1, 4, color);
            pose.rotate((float) Math.toRadians(90));
            context.fill(-1, -4, 1, 4, color);
        } else {
            // 再逆时针转 90°，让勾的尖角朝下、长臂朝右上（y 轴向下，所以逆时针是负角度）
            pose.rotate((float) Math.toRadians(-135));
            // 之前用 pose.scale(1, -1) 翻转，矩阵行列式变成负数，这批填充会被背面剔除掉直接看不见；
            // 改成直接把 y 坐标镜像，效果一样但不会丢渲染。左臂短、右臂长才像勾，长度一样会变成 V。
            context.fill(-1, -2, 1, 6, color);
            context.fill(-1, -2, 3, 0, color);
        }
        pose.popMatrix();
    }

    private static int stateColor(DungeonCheckmark checkmark) {
        return switch (checkmark) {
            case GREEN -> 0xFF55FF55;
            case WHITE -> 0xFFFFFFFF;
            case RED -> 0xFFFF5555;
            case NONE -> 0xFF999999;
        };
    }

    /** 勾叉图标的颜色：绿色用深绿，在黄色小 Boss 格、浅色房间背景上更清楚。 */
    private static int iconColor(DungeonCheckmark checkmark) {
        return checkmark == DungeonCheckmark.GREEN ? 0xFF008A00 : stateColor(checkmark);
    }

    /** 地图像素值 -> 原版地图实际颜色，这样门、未探索格、勾叉的颜色都和地图物品一致。 */
    private static int mapColor(byte color) {
        return MapColor.getColorFromPackedId(color & 0xFF) | 0xFF000000;
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {
    }

    @Override
    public @Nullable String getHudName() {
        return "DungeonMap";
    }
}
