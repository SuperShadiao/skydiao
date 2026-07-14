package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class BFTaskTwinclawsHandler extends BFTask {

    private int tick = 4;
    private int sleepTick;

    public BFTaskTwinclawsHandler() {
        this(false);
    }
    public BFTaskTwinclawsHandler(boolean noSleep) {
        sleepTick = 8;
        if(slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) == null) sleepTick = 4;
        if(noSleep) sleepTick = 0;
    }

    @Override
    public void tickUpdate() {
        if(MacroManagerListener.pathFinderExecutor.isRunning()) {
            MacroManagerListener.pathFinderExecutor.pausePF();
            MacroManagerListener.pathFinderExecutor.stopExecution();
            return;
        }
        tick--;
        if(tick >= 2) {
            for (int i = 0; i < 9; i++) {
                if (ToolList.getInstance().deleteColorCode(mc.player.getInventory().getItem(i).getDisplayName().getString()).contains("Holy Ice")) {
                    InputSimulator.switchItem(i);
                    break;
                }
            }
        }
        if (tick < 2) {
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
        InputSimulator.setJump(false);
        InputSimulator.setShift(false);
    }

    @Override
    public boolean isTaskFinished() {
        return tick <= 0;
    }

    @Override
    public String getTaskName() {
        return "Twinclaws Handle";
    }

    @Override
    public int priority() {
        return sleepTick <= 0 ? priorityTwinclaws : -1;
    }

    @Override
    public void sleepAlsoTickUpdate() {
        sleepTick--;
    }
}
