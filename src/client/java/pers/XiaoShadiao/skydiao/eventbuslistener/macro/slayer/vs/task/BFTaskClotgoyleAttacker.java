package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.pathfinder.EntityFollower;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.Collections;
import java.util.stream.StreamSupport;

public class BFTaskClotgoyleAttacker extends BFTask {

    private boolean maniaRedstoneFlag = false;

    private int swordIndex;

    @Override
    public void tickUpdate() {
        if(!MacroManagerListener.pathFinderExecutor.isFollowerAlive()) {
            launchAttacker();
        }
        if(slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) != null) {
            BlockPos pos = BlockPos.containing(mc.player.position());
            boolean hasRedstone = StreamSupport.stream(BlockPos.betweenClosedStream(pos.offset(5, 5, 5), pos.offset(-5, -5, -5)).spliterator(), false)
                    .anyMatch(blockPos -> mc.level.getBlockState(blockPos).getBlock().equals(Blocks.REDSTONE_BLOCK));
            if (!maniaRedstoneFlag && hasRedstone) {
                maniaRedstoneFlag = true;
                slayerInstance.tasks.add(new BFTaskTwinclawsHandler(true));
            } else if (maniaRedstoneFlag && !hasRedstone) {
                maniaRedstoneFlag = false;
            }
        } else maniaRedstoneFlag = false;
    }

    @Override
    public void onTaskResume() {
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        MacroManagerListener.pathFinderExecutor.stopExecution();
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem().getDescriptionId().endsWith("_sword")) {
                InputSimulator.switchItem(i);
                swordIndex = i;
                break;
            }
        }
        launchAttacker();
    }

    @Override
    public boolean isTaskFinished() {
        return false;
    }

    @Override
    public String getTaskName() {
        return "Clotgoyle Attacker";
    }

    private void launchAttacker() {
        if(!slayerInstance.getClotgoyleList().isEmpty()) MacroManagerListener.pathFinderExecutor.startFollowEntity(Collections.singletonList(
                new EntityFollower.NameInfo("clotgoyle", false)
        ), true, false, new EntityFollower.EntityFollowerConfig().setSwordIndex(swordIndex));
    }

    private int skillDelay;

    @Override
    public int priority() {
//        boolean flag = !checkBossHasntSkill("clotting");
//        if(flag) {
//            skillDelay = 40;
//        }
//        if(skillDelay > 0) {
//            skillDelay--;
//        }

        return !slayerInstance.getClotgoyleList().isEmpty() || skillDelay > 0 ? priorityClotgoyleAttacker : -1;
    }
}
