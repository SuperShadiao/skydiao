package pers.XiaoShadiao.skydiao.utils.dungeon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 地牢地图与物理坐标之间的换算工具。
 *
 * <p>三套坐标系：
 * <ul>
 *   <li>物理格：以 32 格为间距，原点是 {@code v - floorMod(v, 32) - 8}，即始终 {@code ≡ 24 (mod 32)}；</li>
 *   <li>地图格：每格 {@code mapRoomSize} 像素（实测 F7 为 16），格间留 4 像素缝，节距 = 尺寸 + 4；</li>
 *   <li>地图像素：128x128 的 {@code MapItemSavedData#colors} 下标 {@code x + z * 128}。</li>
 * </ul>
 */
public final class DungeonMapUtils {

    /** 入口房间的绿色。 */
    public static final byte COLOR_ENTRANCE = 30;
    /** 普通房间。 */
    public static final byte COLOR_ROOM = 63;
    /** 陷阱房。 */
    public static final byte COLOR_TRAP = 62;
    /** 解密房。 */
    public static final byte COLOR_PUZZLE = 66;
    /** 小 Boss 房。 */
    public static final byte COLOR_MINIBOSS = 74;
    /** 仙女房。 */
    public static final byte COLOR_FAIRY = 82;
    /** 血门。 */
    public static final byte COLOR_BLOOD = 18;
    /** 未探索房间。 */
    public static final byte COLOR_UNKNOWN = 85;
    /** 白勾。 */
    public static final byte COLOR_WHITE_CHECK = 34;
    /** 绿勾（与入口同色）。 */
    public static final byte COLOR_GREEN_CHECK = 30;
    /** 红叉（与血门同色）。 */
    public static final byte COLOR_RED_CROSS = 18;

    /** 物理房间格边长。 */
    public static final int ROOM_PITCH = 32;
    /** 地图边长（像素）。 */
    public static final int MAP_SIZE = 128;
    /** 地图上房间之间的缝隙像素。 */
    public static final int MAP_ROOM_GAP = 4;

    private DungeonMapUtils() {
    }

    /** 物理格坐标打包成 long，方便做 map 的 key。 */
    public static long cellKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** 解包 {@link #cellKey(int, int)}。 */
    public static int[] cellPos(long key) {
        return new int[]{(int) (key >> 32), (int) (key & 0xFFFFFFFFL)};
    }

    /** 玩家/实体所在的物理房间格西北角。 */
    public static int[] physicalRoomPos(double x, double z) {
        int vx = (int) (x + 8.5);
        int vz = (int) (z + 8.5);
        return new int[]{
                vx - Math.floorMod(vx, ROOM_PITCH) - 8,
                vz - Math.floorMod(vz, ROOM_PITCH) - 8};
    }

    /** 读取地图像素颜色，越界返回 -1。 */
    public static byte color(MapItemSavedData map, int x, int z) {
        if (x < 0 || z < 0 || x >= 128 || z >= 128) return -1;
        return map.colors[x + (z << 7)];
    }

    /** 地图上代表玩家的那个装饰物（画框图标）的像素坐标。 */
    public static int[] playerPixel(MapItemSavedData map) {
        for (MapDecoration decoration : map.getDecorations()) {
            if (decoration.renderOnFrame()) {
                return new int[]{(decoration.x() >> 1) + 64, (decoration.y() >> 1) + 64};
            }
        }
        return null;
    }

    /** 地图节距（一格 + 一条缝）。 */
    public static int mapPitch(int mapRoomSize) {
        return mapRoomSize + MAP_ROOM_GAP;
    }

    /**
     * 玩家所处地图格的左上角像素。房间网格的相位由入口像素对节距取模决定。
     */
    public static int[] mapCellOf(int[] pixel, int[] mapEntrance, int mapRoomSize) {
        int pitch = mapPitch(mapRoomSize);
        return new int[]{
                pixel[0] - Math.floorMod(pixel[0] - Math.floorMod(mapEntrance[0], pitch), pitch),
                pixel[1] - Math.floorMod(pixel[1] - Math.floorMod(mapEntrance[1], pitch), pitch)};
    }

    /** 物理格 -> 地图格左上角像素。 */
    public static int[] mapPosFromPhysical(int[] physicalEntrance, int[] mapEntrance, int mapRoomSize, int[] physicalPos) {
        int pitch = mapPitch(mapRoomSize);
        return new int[]{
                mapEntrance[0] + (physicalPos[0] - physicalEntrance[0]) / ROOM_PITCH * pitch,
                mapEntrance[1] + (physicalPos[1] - physicalEntrance[1]) / ROOM_PITCH * pitch};
    }

    /** 地图格左上角像素 -> 物理格。 */
    public static int[] physicalFromMap(int[] physicalEntrance, int[] mapEntrance, int mapRoomSize, int[] mapPos) {
        int pitch = mapPitch(mapRoomSize);
        return new int[]{
                physicalEntrance[0] + (mapPos[0] - mapEntrance[0]) / pitch * ROOM_PITCH,
                physicalEntrance[1] + (mapPos[1] - mapEntrance[1]) / pitch * ROOM_PITCH};
    }

    /**
     * 从给定地图格出发，收集与它同属一个房间的所有格子左上角像素。
     *
     * <p>关键点是判断“两格之间有没有打通”：不能看相邻格的格子颜色（所有房间格都是同色，会把整片
     * 房间粘成一坨），而要看两格之间那条 4 像素缝隙的像素是不是也是房间色。Skyblocker 的
     * {@code DungeonMapUtils#getRoomSegments} 同样取缝隙上的单个像素来判断连通。
     */
    public static int[][] roomSegments(MapItemSavedData map, int[] mapCell, int mapRoomSize, byte color) {
        int pitch = mapPitch(mapRoomSize);
        List<int[]> cells = new ArrayList<>();
        Deque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(mapCell);
        seen.add(cellKey(mapCell[0], mapCell[1]));
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            if (color(map, pos[0], pos[1]) != color) continue;
            cells.add(pos);
            int[][] next = {
                    {pos[0] - pitch, pos[1], pos[0] - 1, pos[1]},
                    {pos[0], pos[1] - pitch, pos[0], pos[1] - 1},
                    {pos[0] + pitch, pos[1], pos[0] + mapRoomSize, pos[1]},
                    {pos[0], pos[1] + pitch, pos[0], pos[1] + mapRoomSize}};
            for (int[] step : next) {
                if (color(map, step[2], step[3]) != color) continue;
                if (step[0] < 0 || step[1] < 0
                        || step[0] + mapRoomSize > 128 || step[1] + mapRoomSize > 128) continue;
                if (seen.add(cellKey(step[0], step[1]))) queue.add(new int[]{step[0], step[1]});
            }
        }
        return cells.toArray(new int[0][]);
    }

    /**
     * 门口走廊在 y 66~73 会被挖空，不同房间的门口差异很大，匹配时要跳过。
     * 逻辑照抄 Skyblocker 的 {@code notInDoorway} 再取反。
     */
    public static boolean inDoorway(BlockPos pos) {
        int y = pos.getY();
        if (y < 66 || y > 73) return false;
        int x = Math.floorMod(pos.getX() - 8, ROOM_PITCH);
        int z = Math.floorMod(pos.getZ() - 8, ROOM_PITCH);
        boolean notInDoorway = (x < 13 || x > 17 || z > 2 && z < 28) && (z < 13 || z > 17 || x > 2 && x < 28);
        return !notInDoorway;
    }

    /** 这一格在地图上已经点亮（有真实房间色，而不是未知/未探索）。 */
    public static boolean isRevealed(MapItemSavedData map, int x, int z) {
        byte color = color(map, x, z);
        if (color == 0 || color == COLOR_UNKNOWN) return false;
        DungeonRoomType type = DungeonRoomType.byColor(color);
        return type != null && type != DungeonRoomType.UNKNOWN;
    }

    /** 这个方块所在的区块是否已经加载。 */
    public static boolean hasChunk(ClientLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    /** 矩形区域（含边界）四角的区块是否都加载好了。 */
    public static boolean isLoaded(ClientLevel level, int minX, int minZ, int maxX, int maxZ) {
        return hasChunk(level, minX, minZ) && hasChunk(level, maxX, minZ)
                && hasChunk(level, minX, maxZ) && hasChunk(level, maxX, maxZ);
    }

    /**
     * 地图像素里的房间网格：第 i 格的左上角是 {@code phase + i * pitch}。
     *
     * <p>相位由入口像素对节距取模得到，整局固定不变，扫门、透视和 HUD 都用同一套网格。
     */
    public record MapGrid(int phaseX, int phaseZ, int pitch, int size, int cells) {

        public int x(int cx) {
            return phaseX + cx * pitch;
        }

        public int z(int cz) {
            return phaseZ + cz * pitch;
        }

        /** 这一格完整落在地图里。 */
        public boolean fits(int x, int z) {
            return x >= 0 && z >= 0 && x + size <= MAP_SIZE && z + size <= MAP_SIZE;
        }
    }

    /** 根据入口像素和房间尺寸算出整张地图的房间网格。 */
    public static MapGrid roomGrid(int[] mapEntrance, int mapRoomSize) {
        int pitch = mapPitch(mapRoomSize);
        return new MapGrid(Math.floorMod(mapEntrance[0], pitch), Math.floorMod(mapEntrance[1], pitch),
                pitch, mapRoomSize, MAP_SIZE / pitch + 1);
    }
}
