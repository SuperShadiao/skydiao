package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PosLine {
    public static final PosLine DEFAULT = new PosLine(-1, 1, 1, -1);
    public final double x1, z1; // 点1坐标
    public final double x2, z2; // 点2坐标
    private final double lineLength; // 直线长度（缓存优化）
    private final double dx, dz; // 方向向量分量
    private final double lengthSq; // 方向向量长度平方（优化计算）

    /**
     * 通过两点构造直线
     * @param point1 第一个点的XZ坐标
     * @param point2 第二个点的XZ坐标
     */
    public PosLine(BlockPos point1, BlockPos point2) {
        this.x1 = point1.getX();
        this.z1 = point1.getZ();
        this.x2 = point2.getX();
        this.z2 = point2.getZ();
        this.lineLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(z2 - z1, 2));
        this.dx = x2 - x1;
        this.dz = z2 - z1;
        this.lengthSq = dx * dx + dz * dz;
    }
    public PosLine(double x1, double z1, double x2, double z2) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.lineLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(z2 - z1, 2));
        this.dx = x2 - x1;
        this.dz = z2 - z1;
        this.lengthSq = dx * dx + dz * dz;
    }

    /**
     * 计算点到直线的垂直距离
     * @param x 待测点X坐标
     * @param z 待测点Z坐标
     * @return 垂直距离（始终≥0）
     */
    public double distanceTo(double x, double z) {
        if (lineLength == 0) return Math.sqrt(Math.pow(x - x1, 2) + Math.pow(z - z1, 2)); // 两点重合时退化为点距

        // 向量叉积法计算距离
        return Math.abs((z2 - z1) * x - (x2 - x1) * z + x2 * z1 - z2 * x1)
                / lineLength;
    }

    /**
     * 判断点是否在直线上（允许误差）
     * @param x 待测点X坐标
     * @param z 待测点Z坐标
     * @param tolerance 最大允许误差（建议0.5）
     * @return 是否共线
     */
    public boolean isOnLine(double x, double z, double tolerance) {
        return distanceTo(x, z) <= tolerance;
    }

    /**
     * 获取直线的单位方向向量
     * @return 长度为1的方向数组[x, z]
     */
    public double[] getDirectionVector() {
        if (lineLength == 0) return new double[]{0, 0};
        return new double[]{(x2 - x1) / lineLength, (z2 - z1) / lineLength};
    }

    /**
     * 获取无限直线上离玩家最近的方块位置
     * @param world 用于边界检查（可空）
     * @param player 玩家实体
     * @param maxDistance 最大允许距离（防止远程计算）
     * @return 最近的方块坐标 | null（超出maxDistance时）
     */
    public BlockPos getBlockPosNearestToPlayer(ClientLevel world, Player player, int maxDistance) {
        // 1. 获取玩家XZ坐标
        Vec3 playerPos = new Vec3(player.getX(), 0, player.getZ());

        // 2. 计算垂足（玩家到直线的投影点）
        Vec3 foot = getFootOfPerpendicular(playerPos);

        // 3. 检查距离是否在允许范围内
        if (foot.distanceTo(playerPos) > maxDistance) {
            return null;
        }

        // 4. 转换为方块坐标（保持玩家Y坐标）
        return new BlockPos(
                Mth.floor(foot.x()),
                (int) player.getY(),
                Mth.floor(foot.z())
        );
    }

    /**
     * 计算玩家到直线的垂足（投影点）
     */
    private Vec3 getFootOfPerpendicular(Vec3 point) {
        if (lengthSq == 0) return new Vec3(x1, 0, z1); // 两点重合时返回任意一点

        // 计算投影参数 t = [(P-A)·(B-A)] / |B-A|²
        double t = ((point.x() - x1) * dx + (point.z() - z1) * dz) / lengthSq;

        // 计算垂足坐标
        return new Vec3(
                x1 + t * dx,
                0,
                z1 + t * dz
        );
    }
    /**
     * 生成与当前直线垂直且经过指定点的直线
     * @param point 经过的点
     * @return 新的垂直线
     */
    public PosLine getPerpendicularLine(BlockPos point) {
        return getPerpendicularLine(point.getX(), point.getZ());
    }

    /**
     * 生成与当前直线垂直且经过指定点的直线（坐标版）
     * @param x 经过点的X坐标
     * @param z 经过点的Z坐标
     * @return 新的垂直线
     */
    public PosLine getPerpendicularLine(double x, double z) {
        // 当前直线的方向向量 (dx, dz)
        // 垂直向量为 (-dz, dx) 或 (dz, -dx)
        double perpDx = -this.dz;
        double perpDz = this.dx;

        // 新直线通过(x,z)，方向为垂直方向
        return new PosLine(x, z, (x + perpDx), (z + perpDz));
    }
    /**
     * 判断两点是否在直线的同一侧
     * @param point1 第一个点
     * @param point2 第二个点
     * @return 是否同侧
     */
    public boolean isSameSide(BlockPos point1, BlockPos point2) {
        return isSameSide(
                point1.getX(), point1.getZ(),
                point2.getX(), point2.getZ()
        );
    }

    /**
     * 判断两点是否在直线的同一侧（坐标版）
     * @param x1 第一个点X坐标
     * @param z1 第一个点Z坐标
     * @param x2 第二个点X坐标
     * @param z2 第二个点Z坐标
     * @return 是否同侧
     */
    public boolean isSameSide(double x1, double z1, double x2, double z2) {
        // 直线方程: (z2-z1)(x-x1) - (x2-x1)(z-z1) = 0
        // 将点代入方程计算符号
        double value1 = (z2 - z1) * (x1 - this.x1) - (x2 - x1) * (z1 - this.z1);
        double value2 = (z2 - z1) * (x2 - this.x1) - (x2 - x1) * (z2 - this.z1);

        // 同侧判定：两个结果同号
        return (value1 * value2) >= 0;
    }

    public PosLine expandLength(double distance) {
        if (distance < 0) {
            throw new IllegalArgumentException("延长距离不能为负数");
        }

        // 计算单位方向向量
        double length = Math.sqrt(dx * dx + dz * dz);
        double unitDx = dx / length;
        double unitDz = dz / length;

        // 计算延长后的端点
        double newX1 = x1 - unitDx * distance;
        double newZ1 = z1 - unitDz * distance;
        double newX2 = x2 + unitDx * distance;
        double newZ2 = z2 + unitDz * distance;

        return new PosLine(newX1, newZ1, newX2, newZ2);
    }
}