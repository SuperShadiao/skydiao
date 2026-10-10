package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;

import java.util.List;

public class HHChallengeMCCommand extends BaseCommand {
    @Override
    public String getCommandName() {
        return "hhch";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgInstance("qq", LongArgumentType.longArg(0, Long.MAX_VALUE)).executes(this::executeCommand)
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        ChatPacket packet = new ChatPacket();
        packet.packetType = "challenge_mc";
        packet.message = String.valueOf(LongArgumentType.getLong(context, "qq"));
        packet.initSender();
        ChatClientManager.trySendOrWarning(packet);

        context.getSource().sendFeedback(Component.literal("§a[小沙雕] 已尝试进行验证, 请再次尝试加入群聊"));

        return 0;
    }
}
