package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.AutoBloodfiendListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.Optional;

public abstract class BFTask {

    public static final int
            priorityDefault = 0,
            priorityAttacker = 0,
            priorityAttackerSteakStake = 9,
            priorityHeal = 12,
            priorityImpel = 12,
            priorityImpelJump = 10,
            priorityClotgoyleAttacker = 8,
            priorityMania = 2,
            priorityDMGBoosting = 14,
            priorityManiaRunning = 11,
            priorityStonewrath = 4,
            priorityTwinclaws = 13,
            priorityKillerSpring = 7,
            priorityBloodIchor = 6;



    public static final Minecraft mc = ToolList.mc;
    public static final AutoBloodfiendListener slayerInstance = MacroManagerListener.autoBloodfiendListener;
    public abstract void tickUpdate();
    public abstract void onTaskResume();
    public abstract void onTaskStart(BFTask lastTask);
    public abstract boolean isTaskFinished();
    public abstract String getTaskName();
    public void onFinished() {}
    public int priority() {
        return priorityDefault;
    }
    public void sleepAlsoTickUpdate() {}
    public void render(RenderUtils.WorldRender wrLine, RenderUtils.WorldRender wrFill) {}
    public void onChat(Component chat) {}

    public boolean checkBossHasntSkill(String skill) {
        E2AMappingListener.MobInfo bloodfiendInstance = slayerInstance.getBloodfiendInstance();
        if(bloodfiendInstance != null) return Optional.of(bloodfiendInstance).map(e -> e.armorStandForBoss).map(e -> !e.getName().getString().toLowerCase().contains(skill.toLowerCase())).orElse(false);
        return Optional.ofNullable(slayerInstance.debugSkills).map(e -> !e.toLowerCase().contains(skill.toLowerCase())).orElse(false);
    }
}
