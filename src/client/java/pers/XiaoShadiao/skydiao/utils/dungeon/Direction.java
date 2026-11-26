package pers.XiaoShadiao.skydiao.utils.dungeon;

/**
 * 房间朝向：模板相对坐标与世界的四种旋转关系，和 Skyblocker 的 {@code Room.Direction} 一致。
 *
 * <p>命名指的是“地图上房间的西北角落在世界的哪个角”，例如 {@link #NW} 表示模板不做旋转。
 * {@link #NE}、{@link #SW} 会交换模板的长宽（1x2 的模板在世界上占 2x1 格）。
 */
public enum Direction {

    NW(false),
    NE(true),
    SW(true),
    SE(false);

    /** 该朝向下模板占地的 x/z 格数是否互换。 */
    public final boolean swapped;

    Direction(boolean swapped) {
        this.swapped = swapped;
    }

    /**
     * 世界坐标 -> 模板相对坐标。返回数组为 {@code {relX, relZ}}，y 不参与旋转。
     *
     * @param cornerX 该朝向下用于换算的角点物理 x
     * @param cornerZ 该朝向下用于换算的角点物理 z
     */
    public int[] toRelative(int x, int z, int cornerX, int cornerZ) {
        return switch (this) {
            case NW -> new int[]{x - cornerX, z - cornerZ};
            case NE -> new int[]{z - cornerZ, cornerX - x};
            case SW -> new int[]{cornerZ - z, x - cornerX};
            case SE -> new int[]{cornerX - x, cornerZ - z};
        };
    }

    /**
     * 模板相对坐标 -> 世界坐标，{@link #toRelative} 的逆变换。返回数组为 {@code {x, z}}，y 不参与旋转。
     */
    public int[] toActual(int relX, int relZ, int cornerX, int cornerZ) {
        return switch (this) {
            case NW -> new int[]{cornerX + relX, cornerZ + relZ};
            case NE -> new int[]{cornerX - relZ, cornerZ + relX};
            case SW -> new int[]{cornerX + relZ, cornerZ - relX};
            case SE -> new int[]{cornerX - relX, cornerZ - relZ};
        };
    }

    /**
     * 用房间的西北角与占地格数算出本朝向的换算角点。
     *
     * <p>NE / SE 用的是“最后一格的格点 + 30”，这是 Skyblocker {@code getPhysicalCornerPos} 的写法，
     * 因为一格实际只有 31 格宽（相邻房间共用一道墙）。
     */
    public int cornerX(int minX, int cellsX) {
        return (this == NE || this == SE) ? minX + (cellsX - 1) * 32 + 30 : minX;
    }

    public int cornerZ(int minZ, int cellsZ) {
        return (this == SW || this == SE) ? minZ + (cellsZ - 1) * 32 + 30 : minZ;
    }
}
