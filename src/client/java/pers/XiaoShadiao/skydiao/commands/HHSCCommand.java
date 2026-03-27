package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon.DungeonF7BossbarListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.HypixelRewardClaimer;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.List;

public class HHSCCommand extends OpenConfigMenuCommand {

    @Override
    public String getCommandName() {
        return "hhsc";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {

        LiteralArgumentBuilder<FabricClientCommandSource> devcommand = getArgConstantInstance("devcommand");
        if(ToolList.getInstance().isDevEnvironment()) {
            devcommand.then(getArgConstantInstance("leftclick").then(getArgInstance("click", BoolArgumentType.bool()).executes((context) -> owo(() -> InputSimulator.isMouseLeftHolding = BoolArgumentType.getBool(context, "click")))));
            devcommand.then(getArgConstantInstance("rightclick").then(getArgInstance("click", BoolArgumentType.bool()).executes((context) -> owo(() -> InputSimulator.isMouseRightHolding = BoolArgumentType.getBool(context, "click")))));
            devcommand.then(getArgConstantInstance("leftfastclick").then(getArgInstance("count", IntegerArgumentType.integer(0)).then(getArgInstance("delay", IntegerArgumentType.integer(0)).executes((context) -> awa(() -> {
                try {
                    int count = IntegerArgumentType.getInteger(context, "count");
                    long delay = IntegerArgumentType.getInteger(context, "delay");
                    for(int i = 0; i < count; i++) {
                        Thread.sleep(delay);
                        InputSimulator.singleLeftClick();
                    }
                } catch (Exception e) {

                }
            })))));
            devcommand.then(getArgConstantInstance("rightfastclick").then(getArgInstance("count", IntegerArgumentType.integer(0)).then(getArgInstance("delay", IntegerArgumentType.integer(0)).executes((context) -> awa(() -> {
                try {
                    int count = IntegerArgumentType.getInteger(context, "count");
                    long delay = IntegerArgumentType.getInteger(context, "delay");
                    for(int i = 0; i < count; i++) {
                        Thread.sleep(delay);
                        InputSimulator.singleRightClick();
                    }
                } catch (Exception e) {

                }
            })))));
        } else {
            devcommand.executes((context) -> owo(() -> context.getSource().sendFeedback(Component.literal("§a[小沙雕] §c当前不是Dev环境..."))));
        }

        return List.of(
                getArgConstantInstance("listmobinfo").executes(this::executePrintMobInfo),
                getArgConstantInstance("claimreward").then(getArgInstance("index", IntegerArgumentType.integer(0, 2)).executes(this::executeClaimReward)),
                getArgConstantInstance("loadtestboss").executes(this::loadTestBoss),
                getArgConstantInstance("teststarrailmsg1").then(getArgInstance("msg", StringArgumentType.greedyString()).executes((context -> owo(() -> XSDHUD.starRailNotification.updateMessage(context.getArgument("msg", String.class), StarRailNotification.Type.success))))),
                getArgConstantInstance("teststarrailmsg2").then(getArgInstance("msg", StringArgumentType.greedyString()).executes((context -> owo(() -> XSDHUD.starRailNotification.updateMessage(context.getArgument("msg", String.class), StarRailNotification.Type.warning))))),
                getArgConstantInstance("translate").redirect(HHT_COMMAND.getCommandNode()),
                devcommand
        );
    }

    private int owo(Runnable runnable) {
        runnable.run();
        return 0;
    }

    private int awa(Runnable runnable) {
        new Thread(runnable).start();
        return 0;
    }

    private int loadTestBoss(CommandContext<FabricClientCommandSource> context) {
        XSDHUD.customBossbar.loadStarRailBossBar(new CustomBossbar.IStarRailBossBar() {
            @Override
            public int getStage() {
                return 1;
            }

            @Override
            public int getMaxStage() {
                return 2;
            }

            @Override
            public double getHealth() {
                return 100;
            }

            @Override
            public double getMaxHealth() {
                return 100;
            }

            @Override
            public boolean hasWeakness() {
                return true;
            }

            @Override
            public int getWeakness() {
                return 100;
            }

            @Override
            public int getMaxWeakness() {
                return 100;
            }

            @Override
            public ResourceLocation getHeadIcon() {
                return DungeonF7BossbarListener.F7_BOSS_ICON;
            }

            @Override
            public boolean isImmune() {
                return true;
            }

            @Override
            public boolean isPowerUpAvaliable() {
                return true;
            }

            @Override
            public boolean isPowerUp() {
                return false;
            }

            @Override
            public ResourceLocation getPowerUpPotionIcon() {
                return Gui.getMobEffectSprite(MobEffects.ABSORPTION);
            }

            @Override
            public int getPowerUp() {
                return 2;
            }

            @Override
            public CustomBossbar.PowerUpStyle getPowerUpStyle() {
                return CustomBossbar.PowerUpStyle.CHARGING;
            }

            @Override
            public int getMaxPowerUp() {
                return 5;
            }

            @Override
            public CustomBossbar.PowerUpTextState getPowerUpTextState() {
                return CustomBossbar.PowerUpTextState.NUMBER_WITH_MAX;
            }

            @Override
            public boolean isBattleOver() {
                return false;
            }

            @Override
            public void onBattleOver() {

            }

            @Override
            public LivingEntity getTargetEntity() {
                return mc.player;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Test");
            }

            @Override
            public void onStageEnter(int stage) {

            }

            @Override
            public boolean shouldNotRenderOtherBoss(LivingEntity e) {
                return false;
            }

            @Override
            public boolean shouldXRayBoss() {
                return false;
            }
        });
        return 0;
    }

    private int executeClaimReward(CommandContext<FabricClientCommandSource> context) {
        HypixelRewardClaimer hrc = HypixelRewardClaimer.getCurrent();
        if(hrc != null && hrc.hasData && !hrc.claimed) {
            hrc.setTargetReward(IntegerArgumentType.getInteger(context, "index"));
            hrc.doClaim();
        }
        return 0;
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        super.lastCallRootCmdNode(rootCmdNode);
        // rootCmdNode.redirect(OPEN_CONFIG_MENU_COMMAND.getCommandNode());
    }

    private int executePrintMobInfo(CommandContext<FabricClientCommandSource> context) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            E2AMappingListener.MobInfo mobInfo = AbstractListener.e2AMappingListener.getMobInfo(entity.asLivingEntity());
            if(mobInfo != null) {
                ToolList.printChatMessage(Component.literal(entity.toString()));
                ToolList.printChatMessage(Component.literal(mobInfo.armorStand.toString()));
                ToolList.printChatMessage(Component.empty());
            }
        }
        return 1;
    }

}
