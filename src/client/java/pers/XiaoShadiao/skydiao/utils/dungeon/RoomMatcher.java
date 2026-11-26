package pers.XiaoShadiao.skydiao.utils.dungeon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用玩家周围的方块反查当前站在哪个房间模板里。
 *
 * <p>做法和 Skyblocker 一致：以玩家为中心取 11x11x11 的方块盒（跳过 y 66~73 的门口走廊带），
 * 只比较落在房间格子内的方块，把每个已知方块旋转到模板相对坐标后与模板的编码方块数组做二分查找。
 *
 * <p>和 Skyblocker 的严格淘汰不同，这里允许相当一部分方块对不上：陷阱房的“推挤柱”整根柱子都是
 * 实心方块而且在移动，普通房间也可能有可交互方块被改变，实测错块能占到验证块数的两三成。
 * 容错上限与验证块数成正比，排序时先比错块数再比验证块数；再用“明显领先次优候选”来避免误判。
 */
public final class RoomMatcher {

    /** 采样半径：5 即 11x11x11。 */
    private static final int RADIUS = 5;
    /** 至少要验证过多少块才承认匹配，太少说明玩家还卡在墙里/门外。 */
    private static final int MIN_CHECKED = 8;
    /** 容错下限：动态方块通常只会影响个位数的方块。 */
    private static final int MIN_TOLERANCE = 4;
    /** 容错上限比例：最多允许验证块数的 1/10 对不上。 */
    private static final int TOLERANCE_DIVISOR = 10;
    /** 最佳候选至少要比次优候选少这么多错块，才算唯一命中。 */
    private static final int MIN_LEAD = 4;
    /** 透视扫描盲匹配时，32x32 的格子上每隔几格取一个采样点。 */
    private static final int BLIND_STEP = 4;

    private RoomMatcher() {
    }

    /**
     * @param segments 房间覆盖的每个物理格西北角，直接取自地图上的同色连通格
     * @return 按“错块数升序、验证块数降序”排好的候选，第一个就是最佳匹配
     */
    public static List<Match> match(ClientLevel level, BlockPos playerPos, int[][] segments, List<DungeonRoom> rooms) {
        if (segments.length == 0 || rooms.isEmpty()) return List.of();
        List<Sample> samples = samples(level, playerPos);
        if (samples.isEmpty()) return List.of();

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        Set<Long> covered = new HashSet<>();
        for (int[] segment : segments) {
            minX = Math.min(minX, segment[0]);
            minZ = Math.min(minZ, segment[1]);
            covered.add(DungeonMapUtils.cellKey(segment[0], segment[1]));
        }

        List<Match> matches = new ArrayList<>();
        for (DungeonRoom room : rooms) {
            for (Direction direction : Direction.values()) {
                int cellsX = direction.swapped ? room.cellsZ() : room.cellsX();
                int cellsZ = direction.swapped ? room.cellsX() : room.cellsZ();
                int[][] footprint = footprint(room, direction, minX, minZ, cellsX, cellsZ);
                if (!sameCells(footprint, covered)) continue;

                int checked = 0;
                int mismatched = 0;
                for (Sample sample : samples) {
                    if (!covered.contains(sample.cell)) continue;
                    checked++;
                    int[] relative = direction.toRelative(sample.x, sample.z,
                            direction.cornerX(minX, cellsX), direction.cornerZ(minZ, cellsZ));
                    int encoded = (relative[0] << 24) | (sample.y << 16) | (relative[1] << 8) | sample.id;
                    if (Arrays.binarySearch(room.blocks(), encoded) < 0) mismatched++;
                }
                if (checked < MIN_CHECKED) continue;
                if (mismatched > Math.max(MIN_TOLERANCE, checked / TOLERANCE_DIVISOR)) continue;
                matches.add(new Match(room, direction, minX, minZ, cellsX, cellsZ, checked, mismatched));
            }
        }
        // 只保留真正有威胁的候选：次优候选错块比最佳多出一截时，列表里就只剩最佳匹配，
        // 调用方据此可以直接判定“唯一命中”，不必因为容错放宽而放弃识别。
        return rank(matches);
    }

    private static int[][] footprint(DungeonRoom room, Direction direction, int minX, int minZ,
                                     int cellsX, int cellsZ) {
        List<int[]> cells = new ArrayList<>(cellsX * cellsZ);
        appendCells(cells, room, direction, minX, minZ, cellsX, cellsZ);
        return cells.toArray(new int[0][]);
    }

    /**
     * 透视扫描用的“盲匹配”：不靠地图连通格，直接把某一格当成房间锚点，用这一格范围内的方块
     * 把所有模板都试一遍，看这里到底长着什么房间。
     *
     * <p>和 {@link #match} 的区别只在房间占地格是从模板反推的；为了避免把没加载的区块当成
     * “对不上”，采样时跳过未加载的区块。采样点比玩家周围的 11x11x11 稀，所以错块一超限就立刻放弃，
     * 否则几百个模板全跑一遍太重。
     */
    public static List<Match> matchBlind(ClientLevel level, int originX, int originZ, int y, List<DungeonRoom> rooms) {
        if (rooms.isEmpty()) return List.of();
        List<Sample> samples = blindSamples(level, originX, originZ, y);
        if (samples.isEmpty()) return List.of();

        List<Match> matches = new ArrayList<>();
        for (DungeonRoom room : rooms) {
            for (Direction direction : Direction.values()) {
                int cellsX = direction.swapped ? room.cellsZ() : room.cellsX();
                int cellsZ = direction.swapped ? room.cellsX() : room.cellsZ();
                int[][] footprint = footprint(room, direction, originX, originZ, cellsX, cellsZ);
                Set<Long> covered = new HashSet<>(footprint.length);
                for (int[] cell : footprint) covered.add(DungeonMapUtils.cellKey(cell[0], cell[1]));

                int checked = 0;
                int mismatched = 0;
                boolean aborted = false;
                for (Sample sample : samples) {
                    if (!covered.contains(sample.cell)) continue;
                    checked++;
                    int[] relative = direction.toRelative(sample.x, sample.z,
                            direction.cornerX(originX, cellsX), direction.cornerZ(originZ, cellsZ));
                    int encoded = (relative[0] << 24) | (sample.y << 16) | (relative[1] << 8) | sample.id;
                    if (Arrays.binarySearch(room.blocks(), encoded) >= 0) continue;
                    mismatched++;
                    if (mismatched > Math.max(MIN_TOLERANCE, checked / TOLERANCE_DIVISOR)) {
                        aborted = true;
                        break;
                    }
                }
                if (aborted || checked < MIN_CHECKED) continue;
                matches.add(new Match(room, direction, originX, originZ, cellsX, cellsZ, checked, mismatched));
            }
        }
        return rank(matches);
    }

    /** 按“错块数升序、验证块数降序”排序，并去掉明显不如最佳的候选。 */
    private static List<Match> rank(List<Match> matches) {
        matches.sort(Comparator.comparingInt((Match match) -> match.mismatched())
                .thenComparingInt(match -> -match.checked()));
        if (matches.isEmpty()) return matches;

        Match best = matches.get(0);
        int lead = Math.max(MIN_LEAD, best.mismatched() / 2);
        for (int i = matches.size() - 1; i >= 1; i--) {
            if (matches.get(i).mismatched() >= best.mismatched() + lead) matches.remove(i);
        }
        return matches;
    }

    /** 覆盖一整格 32x32、上下几层、隔 {@value #BLIND_STEP} 格取一个点；跳过未加载区块和门口走廊。 */
    private static List<Sample> blindSamples(ClientLevel level, int originX, int originZ, int centerY) {
        List<Sample> samples = new ArrayList<>();
        for (int y = centerY - 3; y <= centerY + 3; y++) {
            for (int x = originX; x < originX + DungeonMapUtils.ROOM_PITCH; x += BLIND_STEP) {
                for (int z = originZ; z < originZ + DungeonMapUtils.ROOM_PITCH; z += BLIND_STEP) {
                    if (!DungeonMapUtils.hasChunk(level, x, z)) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (DungeonMapUtils.inDoorway(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    int id = DungeonRoomData.blockId(state.getBlock());
                    if (id < 0) continue;
                    int[] cell = DungeonMapUtils.physicalRoomPos(x, z);
                    samples.add(new Sample(x, y, z, id, DungeonMapUtils.cellKey(cell[0], cell[1])));
                }
            }
        }
        return samples;
    }

    /**
     * 逐个世界格判断模板在这一格里有没有方块。
     *
     * <p>必须用 {@link Direction#toRelative} 换算，不能只按“要不要交换 x/z”来判断：NE/SW/SE 相对 NW
     * 还带有镜像翻转，L 形房间的缺角只有翻转后才能和模板对上。
     */
    static void appendCells(List<int[]> cells, DungeonRoom room, Direction direction,
                            int minX, int minZ, int cellsX, int cellsZ) {
        int cornerX = direction.cornerX(minX, cellsX);
        int cornerZ = direction.cornerZ(minZ, cellsZ);
        for (int a = 0; a < cellsX; a++) {
            for (int b = 0; b < cellsZ; b++) {
                int x = minX + a * DungeonMapUtils.ROOM_PITCH;
                int z = minZ + b * DungeonMapUtils.ROOM_PITCH;
                int[] relative = direction.toRelative(x, z, cornerX, cornerZ);
                if (!room.isOccupied(relative[0] >> 5, relative[1] >> 5)) continue;
                cells.add(new int[]{x, z});
            }
        }
    }

    private static boolean sameCells(int[][] cells, Set<Long> expected) {
        if (cells.length != expected.size()) return false;
        for (int[] cell : cells) {
            if (!expected.contains(DungeonMapUtils.cellKey(cell[0], cell[1]))) return false;
        }
        return true;
    }

    private static List<Sample> samples(ClientLevel level, BlockPos center) {
        List<Sample> samples = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-RADIUS, -RADIUS, -RADIUS),
                center.offset(RADIUS, RADIUS, RADIUS))) {
            if (DungeonMapUtils.inDoorway(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            int id = DungeonRoomData.blockId(state.getBlock());
            if (id < 0) continue;
            int[] cell = DungeonMapUtils.physicalRoomPos(pos.getX(), pos.getZ());
            samples.add(new Sample(pos.getX(), pos.getY(), pos.getZ(), id, DungeonMapUtils.cellKey(cell[0], cell[1])));
        }
        return samples;
    }

    private record Sample(int x, int y, int z, int id, long cell) {
    }

    /**
     * @param minX / minZ 房间西北角的物理坐标
     * @param cellsX / cellsZ 该朝向下房间的占地格数
     * @param checked 落在房间格内、参与比较的方块数
     * @param mismatched 与模板对不上的方块数
     */
    public record Match(DungeonRoom room, Direction direction, int minX, int minZ,
                        int cellsX, int cellsZ, int checked, int mismatched) {

        /** 房间覆盖的每个物理格西北角，用于在地图上找勾叉。 */
        public int[][] segments() {
            List<int[]> segments = new ArrayList<>(cellsX * cellsZ);
            appendCells(segments, room, direction, minX, minZ, cellsX, cellsZ);
            return segments.toArray(new int[0][]);
        }

        /** 模板里的秘密点（相对坐标）换算成世界坐标。 */
        public BlockPos secretPos(DungeonRoom.Secret secret) {
            return relativeToActual(new BlockPos(secret.x(), secret.y(), secret.z()));
        }

        /** 模板相对坐标（y 直接就是地牢里的绝对高度）换算成世界坐标。 */
        public BlockPos relativeToActual(BlockPos relative) {
            int cornerX = direction.cornerX(minX, cellsX);
            int cornerZ = direction.cornerZ(minZ, cellsZ);
            int[] actual = direction.toActual(relative.getX(), relative.getZ(), cornerX, cornerZ);
            return new BlockPos(actual[0], relative.getY(), actual[1]);
        }

        /** 世界坐标换算成模板相对坐标。 */
        public BlockPos actualToRelative(BlockPos actual) {
            int cornerX = direction.cornerX(minX, cellsX);
            int cornerZ = direction.cornerZ(minZ, cellsZ);
            int[] relative = direction.toRelative(actual.getX(), actual.getZ(), cornerX, cornerZ);
            return new BlockPos(relative[0], actual.getY(), relative[1]);
        }

        /** 连续坐标版本的世界 -> 相对换算，用于射线检测。 */
        public Vec3 actualToRelative(Vec3 actual) {
            int cornerX = direction.cornerX(minX, cellsX);
            int cornerZ = direction.cornerZ(minZ, cellsZ);
            return switch (direction) {
                case NW -> new Vec3(actual.x - cornerX, actual.y, actual.z - cornerZ);
                case NE -> new Vec3(actual.z - cornerZ, actual.y, cornerX - actual.x);
                case SW -> new Vec3(cornerZ - actual.z, actual.y, actual.x - cornerX);
                case SE -> new Vec3(cornerX - actual.x, actual.y, cornerZ - actual.z);
            };
        }

        /** 连续坐标版本的相对 -> 世界换算。 */
        public Vec3 relativeToActual(Vec3 relative) {
            int cornerX = direction.cornerX(minX, cellsX);
            int cornerZ = direction.cornerZ(minZ, cellsZ);
            return switch (direction) {
                case NW -> new Vec3(cornerX + relative.x, relative.y, cornerZ + relative.z);
                case NE -> new Vec3(cornerX - relative.z, relative.y, cornerZ + relative.x);
                case SW -> new Vec3(cornerX + relative.z, relative.y, cornerZ - relative.x);
                case SE -> new Vec3(cornerX - relative.x, relative.y, cornerZ - relative.z);
            };
        }
    }
}
