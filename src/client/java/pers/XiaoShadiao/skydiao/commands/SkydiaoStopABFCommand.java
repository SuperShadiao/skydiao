package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;

import java.util.List;

public class SkydiaoStopABFCommand extends BaseRootRunnableCommand {
    @Override
    public String getCommandName() {
        return "skydiaostopabf";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of();
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] §e已停止本次脚本!"));
        MacroManagerListener.autoBloodfiendListener.disableThisBoss();
        return 0;
    }
}
