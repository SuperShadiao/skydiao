package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import pers.XiaoShadiao.skydiao.utils.HypixelRewardClaimer;
import pers.XiaoShadiao.skydiao.utils.ToolList;

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
                getArgConstantInstance("想看看盔甲架的世界").executes(this::executeArmorStandWorld)
        );
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
        if(hrc != null && hrc.hasData && !hrc.claimed) {
            hrc.setTargetReward(IntegerArgumentType.getInteger(context, "index"));
            hrc.doClaim();
        }
        return 0;
    }

}
