package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

public class BFTaskStonewrath extends BFTask {

    private final long startTime = System.currentTimeMillis();
    private int tick = 20 + 80;
    private Vec3 goal;

    public int getTick() {
        return tick;
    }

    @Override
    public void tickUpdate() {
        // tick--;

        if(tick > 1 && tick <= 80) {
            if(!MacroManagerListener.pathFinderExecutor.isRunning()) MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(slayerInstance.getClotgoyleDeadPos()), false);
        }
        if(tick <= 1 && goal == null) {
            MacroManagerListener.pathFinderExecutor.pausePF();
            MacroManagerListener.pathFinderExecutor.stopExecution();
            if(slayerInstance.getBloodIchorEntity() != null) {
                goal = slayerInstance.getBloodIchorEntity().position();
            } else {
                List<BlockPos> list = SafePositionFinder.findSafePositions(mc.level, slayerInstance.getClotgoyleDeadPos());
                // System.out.println(list);
                if (!list.isEmpty()) {
                    goal = new Vec3(list.get(0));
                    // MacroManagerListener.pathFinderExecutor.startExecution(goal, false);
                } else {
                    list = SafePositionFinder.findSafePositions(mc.level, BlockPos.containing(mc.player.position()));
                    // System.out.println(list);
                    if (!list.isEmpty()) {
                        goal = new Vec3(list.get(0));
                    }
                }
            }
        }
        if(tick <= 1 && goal != null) {
            if(!MacroManagerListener.pathFinderExecutor.isRunning()) {
                MacroManagerListener.pathFinderExecutor.startExecution(goal, false);
            }
        }
    }

    public boolean getEscapeState() {
        return tick <= 1;
    }

    @Override
    public void onTaskResume() {

    }

    @Override
    public void onTaskStart(BFTask lastTask) {

    }

    @Override
    public boolean isTaskFinished() {
        if(slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) != null) return true;

        boolean flying = Optional.ofNullable(slayerInstance.getBloodfiendEntityInstance()).map(Entity::getY).orElse(999999.0) < mc.player.getY() + 4;
        return slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) != null || ((System.currentTimeMillis() - startTime > 17000 || tick <= 0) && flying);
    }

    @Override
    public String getTaskName() {
        return "Breaking Clotgoyle";
    }

    @Override
    public int priority() {
        return tick > 78 ? -1 : priorityStonewrath;
    }

    @Override
    public void sleepAlsoTickUpdate() {
        tick--;
    }


    static class SafePositionFinder {

        /**
         * 查找玩家周围可安全移动的位置（距离不小于15格）
         * @param world 世界对象
         * @param centerPos 中心位置(玩家位置)
         * @param searchRadius 搜索半径(应该大于minDistance)
         * @param minDistance 最小距离要求(默认15)
         * @return 可安全移动的位置列表，按距离排序
         */
        public static List<BlockPos> findSafePositions(Level world, BlockPos centerPos, int searchRadius, int minDistance) {
            List<BlockPos> safePositions = new ArrayList<>();
            int centerX = centerPos.getX();
            int centerY = centerPos.getY();
            int centerZ = centerPos.getZ();

            Entity entity = slayerInstance.getBloodfiendEntityInstance();
            if(entity != null) {
                if(new Vec3(entity.getX(), 0, entity.getZ()).distanceTo(Vec3.atCenterOf(centerPos)) < 10) {
                    return Collections.singletonList(entity.blockPosition());
                }
            }

            // 搜索以玩家为中心的立方体区域
            for (int x = centerX - searchRadius; x <= centerX + searchRadius; x++) {
                for (int z = centerZ - searchRadius; z <= centerZ + searchRadius; z++) {
                    for (int y = centerY - 2; y <= centerY + 2; y++) { // 上下各检查2格
                        BlockPos checkPos = new BlockPos(x, y, z);

                        // 首先检查距离是否满足最小要求
                        if (new Vec3(checkPos).horizontal().distanceTo(new Vec3(centerPos).horizontal()) < minDistance) {
                            continue; // 跳过距离太近的位置
                        }

                        // 检查是否满足安全位置条件
                        if (isSafePosition(world, checkPos)) {
                            safePositions.add(checkPos);
                        }
                    }
                }
            }

            // 按距离中心点的远近排序
            if(entity != null) {
                safePositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos) + pos.distSqr(entity.blockPosition()) * 2));
            } else {
                safePositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos)));
            }

            return safePositions;
        }

        /**
         * 检查一个位置是否安全可移动
         */
        private static boolean isSafePosition(Level world, BlockPos pos) {
            // 检查当前位置是否是空气(可以站立的位置)
            if (!world.getBlockState(pos).isAir()) {
                return false;
            }

            // 检查脚下方块是否是非空气(可以站立的基础)
            BlockPos belowPos = pos.below();
            BlockState belowState = world.getBlockState(belowPos);
            if (belowState.isAir()) {
                return false;
            }

            // 检查头顶是否有足够空间(至少2格高)
            BlockPos abovePos = pos.above();
            if (!world.getBlockState(abovePos).isAir()) {
                return false;
            }

            if (!hasClearPath(pos)) return false;

            return true;
        }

        private static boolean hasClearPath(BlockPos toPos) {

            // EntityLivingBase bossEntity = slayerInstance.getBloodfiendEntityInstance();
            // if(bossEntity != null && new PosLine(mc.thePlayer.posX, mc.thePlayer.posZ, toPos.getX() + 0.5, toPos.getZ() + 0.5).distanceTo(bossEntity.posX, bossEntity.posZ) < 0.7) return false;

            // 简单的直线检测，可以改为更复杂的路径检测
            Vec3 startVec = new Vec3(ToolList.mc.player.getX(), ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight(), ToolList.mc.player.getZ());
            Vec3 endVec1 = new Vec3(toPos.getX() + 0.5, toPos.getY() + 0.5, toPos.getZ() + 0.5);
            Vec3 endVec2 = new Vec3(toPos.getX() + 0.5, ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight(), toPos.getZ() + 0.5);

            // 进行光线追踪检测
            HitResult hitResult1 = ToolList.getInstance().predictPlayerAimBlock(startVec, endVec1);
            HitResult hitResult2 = ToolList.getInstance().predictPlayerAimBlock(startVec, endVec2);

            return Optional.ofNullable(hitResult1).map(h -> h.getType() == HitResult.Type.MISS).orElse(true) ||
                    Optional.ofNullable(hitResult2).map(h -> h.getType() == HitResult.Type.MISS).orElse(true) ||
                    (hitResult1 instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(toPos));
        }

        // 重载方法，默认搜索半径40，最小距离18
        public static List<BlockPos> findSafePositions(Level world, BlockPos centerPos) {
            return findSafePositions(world, centerPos, 45, 15);
        }

        // 重载方法，只指定最小距离
        public static List<BlockPos> findSafePositions(Level world, BlockPos centerPos, int minDistance) {
            return findSafePositions(world, centerPos, minDistance + 30, minDistance);
        }
    }
}
