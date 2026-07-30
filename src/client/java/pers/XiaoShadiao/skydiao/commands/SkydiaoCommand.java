package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.screen.ConfigScreen;
import pers.XiaoShadiao.skydiao.utils.Banned;
import pers.XiaoShadiao.skydiao.utils.HypixelRewardClaimer;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Arrays;
import java.util.List;

public class SkydiaoCommand extends BaseRootRunnableCommand {

    @Override
    public String getCommandName() {
        return "skydiao";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("translate").redirect(HHT_COMMAND.getCommandNode()),
                getArgConstantInstance("playalert").executes(this::playAlertSound),
                getArgConstantInstance("claimreward").then(getArgInstance("index", IntegerArgumentType.integer(0, 2)).executes(this::executeClaimReward)),
                getArgConstantInstance("editcape").executes(this::executeEditCape),
                getArgConstantInstance("copyitemnbt").executes(this::executeCopyNBT),
                getArgConstantInstance("getblivelistenercode").executes(this::executeGetCode),
                getArgConstantInstance("afk").executes(this::executeAFK),
                getArgConstantInstance("viewblp").executes((_) -> AbstractListener.blacklistRenderer.printBLP()),
                getArgConstantInstance("ban").then(getArgInstance("type", StringArgumentType.string()).suggests(((commandContext, builder) -> {
                    Arrays.stream(Banned.BanReason.values()).map(v -> v.name().toLowerCase()).forEach(builder::suggest);
                    return builder.buildFuture();
                })).then(getArgInstance("time", StringArgumentType.string()).suggests(((commandContext, builder) -> {
                    Arrays.stream(Banned.BanTime.values()).map(v -> v.day).forEach(builder::suggest);
                    return builder.buildFuture();
                })).executes(this::executeBan))),
                getArgConstantInstance("想看看盔甲架的世界").executes(this::executeArmorStandWorld),
                getArgConstantInstance("autoclick").then(getArgInstance("action", StringArgumentType.string()).suggests((c, b) -> b.suggest("addleft").suggest("addright").suggest("addleftright").suggest("remove").buildFuture()).executes(this::executeAutoClicker)),
                getArgConstantInstance("loto").then(getArgInstance("index", IntegerArgumentType.integer()).executes(this::openAndChangeLoadout)),
                getArgConstantInstance("fastclearminingstash").executes(_ -> awa(AbstractListener.fastClearMiningStash::startClearTask))
        );
    }

    private int executeAutoClicker(CommandContext<FabricClientCommandSource> context) {
        switch (StringArgumentType.getString(context, "action")) {
            case "addleft":
                AbstractListener.autoClickerListener.add(true, false);
                break;
            case "addright":
                AbstractListener.autoClickerListener.add(false, true);
                break;
            case "addleftright":
                AbstractListener.autoClickerListener.add(true, true);
                break;
            case "remove":
                AbstractListener.autoClickerListener.remove();
                break;
        }
        return 0;
    }

    private int executeBan(CommandContext<FabricClientCommandSource> context) {
        String type = StringArgumentType.getString(context, "type");
        String time = StringArgumentType.getString(context, "time");
        try {
            Banned.BanReason b = Banned.BanReason.valueOf(type.toUpperCase());
            for (Banned.BanTime t : Banned.BanTime.values()) {
                if (String.valueOf(t.day).equals(time)) {
                    Banned.ban(b, t, 0);
                }
            }
        } catch (IllegalArgumentException e) {

        }
        return 0;
    }

    private int playAlertSound(CommandContext<FabricClientCommandSource> context) {
        ToolList.getInstance().playSound(CustomSounds.ALERT_MACRO_CHECK);
        return 0;
    }

    private int executeAFK(CommandContext<FabricClientCommandSource> context) {
        AbstractListener.basicListener.flagAsAFK();
        return 0;
    }

    private int executeArmorStandWorld(CommandContext<FabricClientCommandSource> context) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            entity.setInvisible(false);
        }
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] §e好。给你看看盔甲架的世界。"));
        return 0;
    }

    private int executeGetCode(CommandContext<FabricClientCommandSource> context) {
        ToolList.addThreadedTask(() -> {
            mc.execute(() -> context.getSource().sendFeedback(Component.literal("§c咕→咕→嘎→嘎↓!")));
            Thread.sleep(2000);
            mc.execute(() -> context.getSource().sendFeedback(Component.literal("§c咕↓咕↓嘎→嘎↑!!")));
            Thread.sleep(2000);
            mc.execute(() -> context.getSource().sendFeedback(Component.literal("§c咕↑咕↑嘎↑嘎↑!!!!")));
            Util.getPlatform().openUri("https://play-live.bilibili.com/");
            return null;
        });
        return 0;
    }

    private int executeCopyNBT(CommandContext<FabricClientCommandSource> context) {
        mc.keyboardHandler.setClipboard(mc.player.getItemHeldByArm(HumanoidArm.RIGHT).getComponents().toString());
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] 成功复制NBT到剪切板!"));
        return 0;
    }

    private int executeEditCape(CommandContext<FabricClientCommandSource> context) {
        Util.getPlatform().openFile(ConfigManager.capeFolder);
        return 0;
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        mc.schedule(() -> mc.setScreenAndShow(new ConfigScreen(mc.screen)));
        return 1;
    }

    protected int owo(Runnable runnable) {
        runnable.run();
        return 0;
    }

    protected int awa(Runnable runnable) {
        new Thread(runnable).start();
        return 0;
    }

    protected int executeClaimReward(CommandContext<FabricClientCommandSource> context) {
        HypixelRewardClaimer hrc = HypixelRewardClaimer.getCurrent();
        if (hrc != null && hrc.hasData && !hrc.claimed) {
            hrc.setTargetReward(IntegerArgumentType.getInteger(context, "index"));
            hrc.doClaim();
        }
        return 0;
    }

    private int openAndChangeLoadout(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        if (index <= 0 || index > 27) {
            context.getSource().sendError(Component.literal("§a[小沙雕] §cloadout序号必须为1-27"));
            return 1;
        }
        AbstractListener.autoLoadoutListener.switchLoadout(index, null);
        return 0;
    }
}
