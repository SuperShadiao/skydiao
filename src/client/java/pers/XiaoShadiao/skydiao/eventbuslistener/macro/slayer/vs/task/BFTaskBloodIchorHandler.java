package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class BFTaskBloodIchorHandler extends BFTask {

    private int tickCount;

    private final long startTime = System.currentTimeMillis();
    private final boolean shouldApproach;
    public BlockPos ichorPos = slayerInstance.getBloodIchorPos();

    public BFTaskBloodIchorHandler(boolean shouldApproach) {
        this.shouldApproach = shouldApproach;
    }

    @Override
    public void tickUpdate() {
        tickCount++;
        if(slayerInstance.getBloodIchorEntity() != null) {
            BlockPos bp = ichorPos = slayerInstance.getBloodIchorPos();
            if(!MacroManagerListener.pathFinderExecutor.isRunning()) {
//                if(/*bp.distanceSq(mc.thePlayer.getPosition()) < 16 && */slayerInstance.debugSkills == null && slayerInstance.getRunningTaskByClass(BFTaskClotgoyleAttacker.class) == null) {
                BlockPos down = bp.below();
                if(mc.level.getBlockState(down).getBlock().equals(Blocks.AIR)) bp = ichorPos = down;
                MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(bp), false);
//                }
//                else {
//                    PosLine line = new PosLine(bp.getX() + 0.5, bp.getZ() + 0.5, slayerInstance.getBloodfiendEntityInstance().posX, slayerInstance.getBloodfiendEntityInstance().posZ);
//                    MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(line.getBlockPosNearestToPlayer(mc.theWorld, mc.thePlayer, 100000)), false);
//                }
            }

            if(tickCount % 3 == 0 && ToolList.getInstance().random.nextInt(7) < 6) {
                InputSimulator.singleLeftClick();
            }
        }
    }

    @Override
    public void onTaskResume() {

    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        BlockPos bp = ichorPos = slayerInstance.getBloodIchorPos();
        if(bp != null) {
            MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(bp), false);
        }
    }

    @Override
    public boolean isTaskFinished() {
        return System.currentTimeMillis() - startTime > 5000 || slayerInstance.getBloodIchorEntity() == null || (shouldApproach && !checkBossHasntSkill("stonewrath")) || (checkBossHasntSkill("twinclaws") && (ichorPos == null || !shouldApproach || Math.sqrt(ichorPos.distToCenterSqr(mc.player.position())) < 3)) || slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) != null;
    }

    @Override
    public void onFinished() {
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public String getTaskName() {
        return "Blood Ichor Handler";
    }

    @Override
    public int priority() {
        return priorityBloodIchor;
    }

}
