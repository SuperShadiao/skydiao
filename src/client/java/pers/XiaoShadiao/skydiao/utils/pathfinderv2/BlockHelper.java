package pers.XiaoShadiao.skydiao.utils.pathfinderv2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class BlockHelper {

    public static double getBlockY(BlockPos pos) {
        VoxelShape shape = ToolList.mc.level.getBlockState(pos).getCollisionShape(ToolList.mc.level, pos);
        if (shape.isEmpty()) return 0;
        return shape.max(Direction.Axis.Y) - shape.min(Direction.Axis.Y);
    }

    public static double getBlockX(BlockPos pos) {
        VoxelShape shape = ToolList.mc.level.getBlockState(pos).getCollisionShape(ToolList.mc.level, pos);
        if (shape.isEmpty()) return 0;
        return shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
    }

    public static double getBlockZ(BlockPos pos) {
        VoxelShape shape = ToolList.mc.level.getBlockState(pos).getCollisionShape(ToolList.mc.level, pos);
        if (shape.isEmpty()) return 0;
        return shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
    }

    public static boolean isCloseToFullBlock(BlockPos pos) {
        return getBlockX(pos) >= 0.85 && getBlockZ(pos) >= 0.85 && getBlockY(pos) >= 0.85;
    }

    public static boolean hasCollision(BlockPos pos) {
        return getBlockY(pos) > 0 || getBlockX(pos) > 0 || getBlockZ(pos) > 0;
    }

    public static boolean areaHasCollision(BlockPos pos1, BlockPos pos2) {
        return areaHasCollision(pos1, pos2, List.of());
    }

    public static boolean areaHasCollision(BlockPos pos1, BlockPos pos2, List<BlockPos> exceptPos) {
        for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2)) {
            if (!exceptPos.contains(pos) && hasCollision(pos)) return true;
        }
        return false;
    }

    public static boolean areaNoCollision(BlockPos pos1, BlockPos pos2) {
        return areaNoCollision(pos1, pos2, List.of());
    }

    public static boolean areaNoCollision(BlockPos pos1, BlockPos pos2, List<BlockPos> exceptPos) {
        for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2)) {
            if (!exceptPos.contains(pos) && hasCollision(pos)) return false;
        }
        return true;
    }

    public static boolean canStepOn(BlockPos pos) {
        return hasCollision(pos) && getBlockY(pos) >= 0.5;
    }

}
