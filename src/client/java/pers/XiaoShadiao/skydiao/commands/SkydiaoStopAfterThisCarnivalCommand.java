package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.carnival.ICarnival;

import java.util.List;

public class SkydiaoStopAfterThisCarnivalCommand extends BaseRootRunnableCommand {
    @Override
    public String getCommandName() {
        return "skydiaostopafterthiscarnival";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of();
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        ICarnival.stopAfterThis.setTrue();
        return 0;
    }
}
