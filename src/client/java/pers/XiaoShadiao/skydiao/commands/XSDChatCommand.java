package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;

import java.util.List;

public class XSDChatCommand extends BaseRootRunnableCommand {

    public static boolean inXSDChatChannel = false;

    @Override
    public String getCommandName() {
        return "xsdc";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
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

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        rootCmdNode.executes(context -> {
            inXSDChatChannel = !inXSDChatChannel;
            context.getSource().sendError(Component.literal(
                    inXSDChatChannel ?
                            "§a[小沙雕] §a你已进入小沙雕聊天频道, 现在直接发送的消息会发送到小沙雕聊天室." :
                            "§a[小沙雕] §e你已退出小沙雕聊天频道, 现在直接发送的消息会不会被发送到小沙雕聊天室."
            ));
            return 1;
        });
    }
}
