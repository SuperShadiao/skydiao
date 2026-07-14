package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinder.EntityFollower;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.Collections;

public class BFTaskKillerSpringHandler extends BFTask {

    private int tickCounter;
    private int tick = 60;
    public ArmorStand boom;

    @Override
    public void tickUpdate() {
        tickCounter++;
        if(tick > 0) tick--;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem().getDescriptionId().endsWith("_sword")) {
                boolean flag = mc.player.getInventory().getSelectedSlot() != i;
                InputSimulator.switchItem(i);
                if(flag) return; else break;
            }
        }

        if(boom == null) {
            mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(50)).stream().filter(e -> e.getName().getString().contains("BOOM ")).findAny().ifPresent(entityArmorStand -> boom = entityArmorStand);
        } else {
            if(!MacroManagerListener.pathFinderExecutor.isRunning()) {
                MacroManagerListener.pathFinderExecutor.startFollowEntity(Collections.singletonList(new EntityFollower.NameInfo("boom ", false)), true, false);
            }
            if(new Vec3(boom.getX(), 0, boom.getZ()).distanceTo(new Vec3(mc.player.getX(), 0, mc.player.getZ())) < 3 || (mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof ArmorStand)) {
                if((tickCounter / 2) % 2 == 0 && ToolList.getInstance().random.nextInt(7) < 6) {
                    InputSimulator.singleRightClick();
                } else if((tickCounter / 2) % 2 == 0 && ToolList.getInstance().random.nextInt(7) < 6) {
                    InputSimulator.singleLeftClick();
                }
            }
            if(boom.isDeadOrDying() || !ToolList.getInstance().isEntityOnWorld(boom)) boom = null;
        }

    }

    @Override
    public void onTaskResume() {

    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public boolean isTaskFinished() {
        if(slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) != null) return true;
        return (tick <= 0 && boom == null) || (boom != null && (boom.isDeadOrDying() || !ToolList.getInstance().isEntityOnWorld(boom)));
    }

    @Override
    public String getTaskName() {
        return "Killing Spring Handler";
    }

    @Override
    public int priority() {
//        if(boom == null) return priorityKillerSpringKeepNear;
//        double distanceXZ = 工具列表.getInstance().算距离(mc.thePlayer.posX, 0, mc.thePlayer.posZ, boom.posX, 0, boom.posZ);
//        return distanceXZ > 6 || distanceXZ < 3 ? priorityKillerSpringKeepNear : priorityKillerSpring;
        return priorityKillerSpring;
    }

    @Override
    public void onFinished() {
        slayerInstance.tasks.add(new BFTaskBloodIchorHandler(true));
    }
}
