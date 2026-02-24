package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public abstract class BaseRootRunnableCommand extends BaseCommand {

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        attachToRootCmdNode(rootCmdNode);
    }

}
