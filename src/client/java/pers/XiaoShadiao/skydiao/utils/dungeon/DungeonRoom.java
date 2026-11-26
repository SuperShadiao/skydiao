package pers.XiaoShadiao.skydiao.utils.dungeon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 一个房间模板。
 *
 * <p>{@link #blocks()} 是排序后的编码方块数组，编码为 {@code x << 24 | y << 16 | z << 8 | blockId}：
 * x/z 是相对房间西北角的 0..127，y 是绝对世界 y。排序是为了能直接 {@code Arrays.binarySearch}。
 */
public final class DungeonRoom {

    /** 秘密编号解析结果缓存：名字是模板里的常量，整局不变。 */
    private static final ConcurrentMap<String, Set<String>> SECRET_GROUPS_CACHE = new ConcurrentHashMap<>();

    /** 房间里的秘密点，x/z 相对西北角，y 是绝对坐标。 */
    public record Secret(String name, String category, int x, int y, int z) {
    }

    /**
     * 秘密名前缀里的编号集合，比如 {@code "2/3 - Lever"} 返回 {@code ["2", "3"]}。
     * 一个秘密可能由多个节点组成，所以编号用来把节点归到同一组；没有编号时返回空集。
     */
    public static Set<String> secretGroups(String name) {
        return SECRET_GROUPS_CACHE.computeIfAbsent(name, DungeonRoom::parseSecretGroups);
    }

    private static Set<String> parseSecretGroups(String name) {
        int index = name.indexOf(" - ");
        if (index < 0) return Set.of();
        Set<String> groups = new HashSet<>();
        for (String token : name.substring(0, index).split("/")) {
            token = token.trim();
            if (!token.isEmpty()) groups.add(token);
        }
        return groups;
    }

    private final String id;
    private final String name;
    private final RoomShape shape;
    private final List<Secret> secrets;
    private final int[] blocks;
    private final int maxX;
    private final int maxZ;
    private final int cellsX;
    private final int cellsZ;
    private final boolean[][] occupied;

    public DungeonRoom(String id, String name, RoomShape shape, List<Secret> secrets, int[] blocks) {
        this.id = id;
        this.name = name;
        this.shape = shape;
        this.secrets = List.copyOf(secrets);
        this.blocks = blocks;
        int mx = 0;
        int mz = 0;
        for (int block : blocks) {
            mx = Math.max(mx, (block >> 24) & 0xFF);
            mz = Math.max(mz, (block >> 8) & 0xFF);
        }
        this.maxX = mx;
        this.maxZ = mz;
        this.cellsX = mx / 32 + 1;
        this.cellsZ = mz / 32 + 1;
        this.occupied = new boolean[cellsX][cellsZ];
        for (int block : blocks) {
            occupied[((block >> 24) & 0xFF) >> 5][((block >> 8) & 0xFF) >> 5] = true;
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public RoomShape shape() {
        return shape;
    }

    public List<Secret> secrets() {
        return secrets;
    }

    public int[] blocks() {
        return blocks;
    }

    public int maxX() {
        return maxX;
    }

    public int maxZ() {
        return maxZ;
    }

    /** 模板不做旋转时的占地格数。 */
    public int cellsX() {
        return cellsX;
    }

    /** 模板不做旋转时的占地格数。 */
    public int cellsZ() {
        return cellsZ;
    }

    /** L 形房间的模板里，某些格子是没有方块的，需要排除。 */
    public boolean isOccupied(int cellX, int cellZ) {
        return cellX >= 0 && cellZ >= 0 && cellX < cellsX && cellZ < cellsZ && occupied[cellX][cellZ];
    }
}
