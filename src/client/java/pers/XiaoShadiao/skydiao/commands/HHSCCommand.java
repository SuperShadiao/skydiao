package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.screen.ConfigScreen;

import java.util.Collections;
import java.util.List;

public class HHSCCommand extends OpenConfigMenuCommand {

    @Override
    public String getCommandName() {
        return "hhsc";
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        super.lastCallRootCmdNode(rootCmdNode);
        rootCmdNode.redirect(OPEN_CONFIG_MENU_COMMAND.getCommandNode());
    }
}
