package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.FillCommand;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class XSDChatCommand extends BaseCommand {
    @Override
    public String getCommandName() {
        return "xsdc";
    }

    @Override
    public List<RequiredArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgInstanceAndRunNode("chat", StringArgumentType.greedyString()).suggests((context, builder) -> builder.suggest("输入你想要发送的消息").buildFuture())
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        if(ChatClientManager.serverAvailable()) {
            ChatClientManager.getChatClient().sender.sendMessage(StringArgumentType.getString(context, "chat"));
        } else {
            context.getSource().sendError(Component.literal("§a[小沙雕] §c当前服务器暂时不可用, 请稍后再试! 若仍然没有解决, 请联系小沙雕!"));
        }
        return 1;
    }
}
