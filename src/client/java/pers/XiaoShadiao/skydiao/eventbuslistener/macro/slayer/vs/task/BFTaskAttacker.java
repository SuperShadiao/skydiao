package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinder.EntityFollower;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BFTaskAttacker extends BFTask {

    private boolean steakStakeFlag;
    private boolean steakStake404NotFound;
    private boolean bossLowHealthFlag;

    private int swordIndex;

    @Override
    public void tickUpdate() {
        if(!MacroManagerListener.pathFinderExecutor.isFollowerAlive()) {
            launchAttacker();
        }

        if(!steakStakeFlag && getBossHealthPercent() < 0.199) {
            steakStakeFlag = true;
            MacroManagerListener.pathFinderExecutor.stopExecution();
            onTaskStart(this);
        }
    }

    @Override
    public void onTaskResume() {
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        if(steakStakeFlag && !steakStake404NotFound) {

            boolean flag = true;
            for (int i = 0; i < 9; i++) {
                if (ToolList.getInstance().deleteColorCode(mc.player.getInventory().getItem(i).getDisplayName().getString()).contains("Steak Stake")) {
                    InputSimulator.switchItem(i);
                    swordIndex = i;
                    XSDHUD.bigTitle.updateTitleMsg("§a牛排! 给我吃!", 4000);
                    flag = false;
                    break;
                }
            }
            if (flag) {
                XSDHUD.bigTitle.updateTitleMsg("§c无法从物品栏找到牛排! 跳过喂牛排阶段...", 4000);
                steakStake404NotFound = true;
            }

        } else for (int i = 0; i < 9; i++) {
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

    private void launchAttacker() {

        List<EntityFollower.NameInfo> nameInfoList = new ArrayList<>();
        nameInfoList.add(new EntityFollower.NameInfo("bloodfiend", false));
        if(!steakStakeFlag && !steakStake404NotFound) nameInfoList.add(new EntityFollower.NameInfo("clotgoyle", false));

        MacroManagerListener.pathFinderExecutor.startFollowEntity(nameInfoList, true, false, new EntityFollower.EntityFollowerConfig().setShouldIgnore(e -> e != slayerInstance.getBloodfiendEntityInstance() && e.getName().getString().toLowerCase().contains("bloodfiend")).setSwordIndex(swordIndex));

    }

    @Override
    public String getTaskName() {
        return "Attacking Bloodfiend";
    }

    @Override
    public int priority() {
        E2AMappingListener.MobInfo boss = slayerInstance.getBloodfiendInstance();
        if(boss==null) return priorityAttacker;
        if(!bossLowHealthFlag && slayerInstance.getRunningTaskByClass(BFTaskKillerSpringHandler.class) == null && slayerInstance.getRunningTaskByClass(BFTaskManiaHandler.class) == null) {
            if(boss.health < Math.min(boss.maxHealth * 0.2 + 200, boss.maxHealth * 0.3)) {
                bossLowHealthFlag = true;

                boolean flag = true;
                for (int i = 0; i < 9; i++) {
                    if (ToolList.getInstance().deleteColorCode(mc.player.getInventory().getItem(i).getDisplayName().getString()).contains("Steak Stake")) {
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    XSDHUD.bigTitle.updateTitleMsg("§c无法从物品栏找到牛排! 跳过喂牛排阶段...", 4000);
                    steakStake404NotFound = true;
                } else XSDHUD.bigTitle.updateTitleMsg("§eBloodfiend已进入低血量状态, 攻击任务优先级提至最前并准备给他喂§c牛排", 4000);
            } else {
                if(Optional.ofNullable(slayerInstance.getBloodfiendInstance()).map(e -> e.armorStandForBoss).map(e -> e.getName().getString().toLowerCase().contains("00:28")).orElse(false)) {
                    bossLowHealthFlag = true;

                    boolean flag = true;
                    for (int i = 0; i < 9; i++) {
                        if (ToolList.getInstance().deleteColorCode(mc.player.getInventory().getItem(i).getDisplayName().getString()).contains("Steak Stake")) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        XSDHUD.bigTitle.updateTitleMsg("§c无法从物品栏找到牛排! 跳过喂牛排阶段...", 4000);
                        steakStake404NotFound = true;
                    } else XSDHUD.bigTitle.updateTitleMsg("§e哎呀, 看上去要没时间了, 攻击任务优先级提至最前!", 4000);
                }
            }
        }
        return bossLowHealthFlag && !steakStake404NotFound && slayerInstance.getRunningTaskByClass(BFTaskStonewrath.class) == null ? priorityAttackerSteakStake : priorityAttacker;
    }

    private double getBossHealthPercent() {
        E2AMappingListener.MobInfo boss = slayerInstance.getBloodfiendInstance();
        if(boss != null) {
            System.out.println(boss.health + "/" + boss.maxHealth);
            return boss.health / boss.maxHealth;
        } else return 1;
    }
}
