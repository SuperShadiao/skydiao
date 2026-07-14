package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class BFTaskImpelHandler extends BFTask {

    private final ImpelType type;
    private int tick = 5;
    private boolean isResponded = false;

    private boolean clickDownSpecialMode = false;

    public BFTaskImpelHandler(ImpelType type) {
        this.type = type;
    }

    @Override
    public void onChat(Component chat) {
        if (type == ImpelType.JUMP) {
            String message = ToolList.getInstance().deleteColorCode(chat.getString());
            if(message.toLowerCase().contains("from twinclaws")) {
                tick = -1;
            }
        }
    }

    @Override
    public void tickUpdate() {
        if(MacroManagerListener.pathFinderExecutor.isRunning()) {
            MacroManagerListener.pathFinderExecutor.pausePF();
            MacroManagerListener.pathFinderExecutor.stopExecution();
            return;
        }

        tick--;
        if(type == ImpelType.JUMP && slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) != null && (mc.player.getDeltaMovement().x() >= 0.00001 || mc.player.getDeltaMovement().z() >= 0.00001)) tick = 5;

        if(tick == 4) {
            switch (type) {
                case CLICKUP:
                    InputSimulator.setPlayerPitch(-89);
                    break;
                case CLICKDOWN:
                    if(!clickDownSpecialMode) InputSimulator.setPlayerPitch(80);
                    break;
                case JUMP:
                    InputSimulator.setJump(true);
                    tick = 2;
                    break;
                case SNEAK:
                    InputSimulator.setShift(true);
                    tick = 2;
                    break;
            }
            isResponded = true;
        }
        if(tick == 2) {
            switch (type) {
                case CLICKDOWN:
                    if(mc.hitResult instanceof BlockHitResult hitResult) {
                        BlockPos pos = hitResult.getBlockPos();
                        slayerInstance.maniaIgnoreChangePos.add(pos);
                    }
                case CLICKUP:
                    if(!clickDownSpecialMode) InputSimulator.singleLeftClick();
                    break;
            }
        }
        if(tick == 0) {
            switch (type) {
                case CLICKUP:
                case CLICKDOWN:
                    if(!clickDownSpecialMode) {
                        InputSimulator.setPlayerPitch(0);
                    } else {
                        slayerInstance.tasks.add(new BFTaskTwinclawsHandler());
                    }
                    break;
            }
        }
    }

    @Override
    public void onFinished() {
        InputSimulator.setShift(false);
        InputSimulator.setJump(false);
    }

    @Override
    public void onTaskResume() {

    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        if(type == ImpelType.JUMP) tick = 5;
        if(type == ImpelType.CLICKDOWN && slayerInstance.getRunningTaskByClass(BFTaskStonewrath.class) != null) clickDownSpecialMode = true;
    }

    @Override
    public boolean isTaskFinished() {
        return tick <= 0;
    }

    @Override
    public String getTaskName() {
        return "Impel Handler";
    }

    @Override
    public int priority() {
        return type == ImpelType.JUMP && !isResponded ? (slayerInstance.getTwinclawsCD() > 0 ? -1 : priorityImpelJump) : priorityImpel;
    }

    public ImpelType getType() {
        return type;
    }

    public enum ImpelType {
        CLICKUP,
        CLICKDOWN,
        JUMP,
        SNEAK
    }

    public static ImpelType getType(String info) {
        info = info.toLowerCase();
        if (info.contains("click up")) {
            return ImpelType.CLICKUP;
        } else if (info.contains("click down")) {
            return ImpelType.CLICKDOWN;
        } else if (info.contains("jump")) {
            return ImpelType.JUMP;
        } else if (info.contains("sneak")) {
            return ImpelType.SNEAK;
        } else {
            return null;
        }
    }
}
