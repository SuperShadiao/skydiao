package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.mojang.text2speech.Narrator;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.TagValueOutput;
import pers.XiaoShadiao.skydiao.commands.args.ClientBlockPosArgument;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon.DungeonF7BossbarListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.GenshinImpactHeatCold;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatClientManager;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;
import pers.XiaoShadiao.skydiao.screen.FastCommandMenuScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.WindowsUtils;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.Base64;
import java.util.List;

public class HHSCCommand extends SkydiaoCommand {

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
            devcommand.then(getArgConstantInstance("aim").then(getArgInstance("pos", ClientBlockPosArgument.blockPos()).then(getArgInstance("keepTime", LongArgumentType.longArg(0)).executes((context) -> awa(() -> {
                try {
                    BlockPos pos = ClientBlockPosArgument.getBlockPos(context, "pos");
                    AimHelper aimHelper = new AimHelper();
                    long keepTime = LongArgumentType.getLong(context, "keepTime");
                    for(long i = 0; i < keepTime; i++) {
                        Thread.sleep(1);
                        AimHelper.getYawPitchByBlockPos(pos).updateToAimHelper(aimHelper);
                    }
                } catch (Exception e) {

                }
            })))));
            devcommand.then(getArgConstantInstance("triggerfakeshaftannounce").executes((context) -> owo(AbstractListener.mineshaftShareListener::debugFoundShaft)));
            devcommand.then(getArgConstantInstance("sendfakemacrocheck").executes((context) -> {
                ChatPacket packet = new ChatPacket();
                packet.initSender();
                packet.packetType = "macro_check";
                packet.message = "test msg";
                ChatClientManager.trySend(packet, false);
                return 0;
            }));
            devcommand.then(getArgConstantInstance("fetchskulldata").executes(context -> {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    entity.setInvisible(false);
                    if(entity instanceof LivingEntity armorStand) {
                        if(armorStand == mc.player) continue;
                        if(armorStand.distanceTo(mc.player) > 10) continue;
                        System.out.println(armorStand);
                        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                            ItemStack is = armorStand.getItemBySlot(equipmentSlot);
                            System.out.println(is.getItem());
                            if(is.getItem() == Items.PLAYER_HEAD) {
                                ResolvableProfile profile = is.getComponents().get(DataComponents.PROFILE);
                                System.out.println(equipmentSlot + " " + armorStand);
                                System.out.println(profile.partialProfile().properties().asMap());
                                System.out.println(new String(Base64.getDecoder().decode(profile.partialProfile().properties().get("textures").iterator().next().value())));
                            }
                        }
                    }
                }
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testmacrocheckalert").executes(context -> {
                ToolList.addThreadedTask(() -> {
                    Thread.sleep(2500);
                    AbstractListener.mml.triggerAlert("测试警报🚨");
                    return null;
                });
                return 0;
            }));
            devcommand.then(getArgConstantInstance("restartcnscanner").executes(context -> {
                AbstractListener.crystalHollowHelperListener.inCN = false;
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testheat").executes(context -> {
                XSDHUD.genshinImpactHeatCold.debug1 = GenshinImpactHeatCold.ColorType.heat;
                XSDHUD.genshinImpactHeatCold.debugHeatOrColdValue = Math.min(ToolList.getInstance().random.nextInt(150), 100);
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testnoheat").executes(context -> {
                XSDHUD.genshinImpactHeatCold.debugHeatOrColdValue = null;
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testcold").executes(context -> {
                XSDHUD.genshinImpactHeatCold.debug1 = GenshinImpactHeatCold.ColorType.cold;
                XSDHUD.genshinImpactHeatCold.debugHeatOrColdValue = ToolList.getInstance().random.nextInt(100);
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testnocold").executes(context -> {
                XSDHUD.genshinImpactHeatCold.debugHeatOrColdValue = null;
                return 0;
            }));
            devcommand.then(getArgConstantInstance("focuswindows").executes(context -> {
                ToolList.addThreadedTask(() -> {
                    Thread.sleep(3000);
                    WindowsUtils.focusWindows();
                    return null;
                });
                return 0;
            }));
            devcommand.then(getArgConstantInstance("say").then(getArgInstance("msg", StringArgumentType.greedyString()).executes(context -> {
                Narrator.getNarrator().say(StringArgumentType.getString(context, "msg"), false, 1);
                return 0;
            })));
            devcommand.then(getArgConstantInstance("testfastmenuload").executes(context -> {
                FastCommandMenuScreen.load();
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testfastmenuload").executes(context -> {
                FastCommandMenuScreen.load();
                return 0;
            }));
            devcommand.then(getArgConstantInstance("testswitchitem").executes(context -> {
                long startTime = System.currentTimeMillis();
                ToolList.addThreadedTask(() -> {
                    while(System.currentTimeMillis() - startTime < 10000) {
                        InputSimulator.switchItem(ToolList.getInstance().random.nextInt(9));
                        Thread.sleep(10);
                    }
                    return null;
                });
                return 0;
            }));
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
                getArgConstantInstance("oomtest").executes(context -> owo(AbstractListener.basicListener::throwOOMNextTick)),
                getArgConstantInstance("getskyblockitemid").executes(context -> owo(() -> context.getSource().sendFeedback(Component.literal(ToolList.getInstance().tryGetSkyblockItemId(mc.player.getItemHeldByArm(HumanoidArm.RIGHT)))))),
                getArgConstantInstance("printscoreboard").executes(this::executePrintScoreboard),
                getArgConstantInstance("sethiddensomethingindex").then(getArgInstance("index", IntegerArgumentType.integer(0, AbstractListener.hiddenSomething.getTotalGifCount())).executes(this::setHiddenSomethingIndex)),
                getArgConstantInstance("togglee2arecord").executes(_ -> owo(() -> AbstractListener.e2AMappingListener.toggleRecord = !AbstractListener.e2AMappingListener.toggleRecord)),
                getArgConstantInstance("togglefpsdebug").executes(context -> owo(() -> {
                    ConfigManager.debugFPSIssue.setValue(!ConfigManager.debugFPSIssue.getValue());
                    context.getSource().sendFeedback(Component.literal("§a[小沙雕] " + (ConfigManager.debugFPSIssue.getValue() ? "§a已开启" : "§c已关闭") + "FPS调试"));
                })),
                getArgConstantInstance("dumpentitynbt")
                        .executes(this::executePrintTargetEntities)
                        .then(getArgInstance("entityid", IntegerArgumentType.integer()).executes(this::executeDumpEntityNBT)),
                getArgConstantInstance("dumpallentitynbt").executes(this::executeDumpAllEntityNBT),
                devcommand
        );
    }

    private int executeDumpEntityNBT(CommandContext<FabricClientCommandSource> context) {
        int entityid = IntegerArgumentType.getInteger(context, "entityid");
        Entity entity = mc.level.getEntity(entityid);
        if (entity == null) {
            context.getSource().sendFeedback(Component.literal("§a[小沙雕] §c实体不存在"));
            return 0;
        }
        TagValueOutput output = TagValueOutput.createWithContext(new ProblemReporter.ScopedCollector(LogUtils.getLogger()), mc.level.registryAccess());
        entity.save(output);
        mc.keyboardHandler.setClipboard(output.buildResult().toString());
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] 实体NBT已复制到剪切板"));

        for (Entity entity1 : mc.level.entitiesForRendering()) {
            LivingEntity living = entity1.asLivingEntity();
            if(living != null) {
                if(living.getMaxHealth() >= 100_000_000) {
                    System.out.println(living);
                }
            }
        }

        return 0;
    }

    private int executeDumpAllEntityNBT(CommandContext<FabricClientCommandSource> context) {
        if (mc.level == null) {
            context.getSource().sendFeedback(Component.literal("§a[小沙雕] §c世界不存在"));
            return 0;
        }
        var logger = LogUtils.getLogger();
        int count = 0;
        logger.info("[ENTITYDUMP] ---- begin ----");
        for (Entity entity : mc.level.entitiesForRendering()) {
            try {
                double health = -1;
                double maxHealth = -1;
                if (entity instanceof LivingEntity livingEntity) {
                    health = livingEntity.getHealth();
                    maxHealth = livingEntity.getMaxHealth();
                }
                E2AMappingListener.MobInfo mobInfo = AbstractListener.e2AMappingListener.getMobInfo(entity.asLivingEntity());
                String armorStandName = mobInfo != null && mobInfo.armorStand != null ? mobInfo.armorStand.getName().getString() : "null";
                String bossArmorStandName = mobInfo != null && mobInfo.armorStandForBoss != null ? mobInfo.armorStandForBoss.getName().getString() : "null";

                TagValueOutput output = TagValueOutput.createWithContext(new ProblemReporter.ScopedCollector(logger), mc.level.registryAccess());
                entity.save(output);
                logger.info("[ENTITYDUMP] #{} class={} type={} name={} pos={} health={}/{} armorStand={} bossArmorStand={} nbt={}",
                        entity.getId(), entity.getClass().getSimpleName(), entity.getType(), entity.getName().getString(), entity.position(),
                        health, maxHealth, armorStandName, bossArmorStandName, output.buildResult());
            } catch (Exception e) {
                logger.error("[ENTITYDUMP] failed: {}", entity, e);
            }
            count++;
        }
        logger.info("[ENTITYDUMP] ---- end, count={} ----", count);
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] 已导出 " + count + " 个实体NBT到 latest.log"));
        return count;
    }

    private int executePrintTargetEntities(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] 以下是你半径5格内的实体, 选择一个复制其NBT"));
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(5, 5, 5))) {
            context.getSource().sendFeedback(Component.literal("§a[小沙雕] §f- §e" + entity.getName().getString() + " (" + entity.getId() + ")")
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand("/hhsc dumpentitynbt " + entity.getId())).withHoverEvent(new HoverEvent.ShowText(Component.literal("§a点击复制其NBT")))));
        }
        return 0;
    }

    private int setHiddenSomethingIndex(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        AbstractListener.hiddenSomething.setIndex(index);
        return 0;
    }

    private int executePrintScoreboard(CommandContext<FabricClientCommandSource> context) {
        ToolList.getInstance().fetchScoreboardLinesNoColor().forEach(System.out::println);
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
                return mc.player.getHealth() * 50 * 100000000000000000d;
            }

            @Override
            public double getMaxHealth() {
                return mc.player.getMaxHealth() * 50 * 100000000000000000d;
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
            public double getHealthFlagScale() {
                return getHealth() / getMaxHealth() - 0.08;
            }

            @Override
            public Identifier getHeadIcon() {
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
                return getMaxPowerUp() == getPowerUp();
            }

            @Override
            public Identifier getPowerUpPotionIcon() {
                return Hud.getMobEffectSprite(MobEffects.ABSORPTION);
            }

            @Override
            public int getPowerUp() {
                int i = (int) (1 + 4 * getHealth() / getMaxHealth());
                if(!true) return ToolList.getInstance().random.nextInt(i);
                return i;
            }

            @Override
            public CustomBossbar.PowerUpStyle getPowerUpStyle() {
                return getMaxPowerUp() == getPowerUp() ? CustomBossbar.PowerUpStyle.READY : CustomBossbar.PowerUpStyle.CHARGING;
            }

            @Override
            public int getMaxPowerUp() {
                return 5;
            }

            @Override
            public CustomBossbar.PowerUpTextState getPowerUpTextState() {
                return getPowerUp() == 2 ? CustomBossbar.PowerUpTextState.PERCENT : getMaxPowerUp() == getPowerUp() ? CustomBossbar.PowerUpTextState.NUMBER : CustomBossbar.PowerUpTextState.NUMBER_WITH_MAX;
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
