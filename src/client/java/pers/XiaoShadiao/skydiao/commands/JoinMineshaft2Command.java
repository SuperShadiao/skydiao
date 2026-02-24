package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

import java.util.List;

public class JoinMineshaft2Command extends JoinMineshaftCommand {
    @Override
    public String getCommandName() {
        return "skydiaojoinmineshaft";
    }

    @Override
    public List<RequiredArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of();
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        rootCmdNode.redirect(JOIN_MINESHAFT_COMMAND.getCommandNode());
    }
}
