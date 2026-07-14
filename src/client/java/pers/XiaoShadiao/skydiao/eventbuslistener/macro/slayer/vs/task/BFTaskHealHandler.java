package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class BFTaskHealHandler extends BFTask {

    private int tick = 4;

    @Override
    public void tickUpdate() {
        if(MacroManagerListener.pathFinderExecutor.isRunning()) {
            MacroManagerListener.pathFinderExecutor.pausePF();
            MacroManagerListener.pathFinderExecutor.stopExecution();
            return;
        }
        tick--;
        if(tick == 2) {
            for (int i = 0; i < 9; i++) {
                if (ToolList.getInstance().deleteColorCode(mc.player.getInventory().getItem(i).getDisplayName().getString()).contains("Healing Melon")) {
                    InputSimulator.switchItem(i);
                    break;
                }
            }
        }
        if (tick == 1) {
            InputSimulator.singleRightClick();
        }
    }

    @Override
    public void onTaskResume() {
        tick = 4;
    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        MacroManagerListener.pathFinderExecutor.pausePF();
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public boolean isTaskFinished() {
        return tick <= 0;
    }

    @Override
    public String getTaskName() {
        return "Healing";
    }

    @Override
    public int priority() {
        return priorityHeal;
    }
}
