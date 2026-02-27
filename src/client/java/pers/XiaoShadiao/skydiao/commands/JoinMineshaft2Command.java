package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.List;

public class JoinMineshaft2Command extends JoinMineshaftCommand {
    @Override
    public String getCommandName() {
        return "skydiaojoinmineshaft";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of();
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        rootCmdNode.redirect(JOIN_MINESHAFT_COMMAND.getCommandNode());
    }
}
