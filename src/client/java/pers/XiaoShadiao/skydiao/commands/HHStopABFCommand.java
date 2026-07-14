package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class HHStopABFCommand extends SkydiaoStopABFCommand {
    @Override
    public String getCommandName() {
        return "hhstopabf";
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        rootCmdNode.redirect(SKYDIAO_STOP_AF_COMMAND.getCommandNode());
    }
}
