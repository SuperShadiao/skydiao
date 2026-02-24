package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;

import java.util.List;

public class JoinMineshaftCommand extends BaseCommand {

    private long lastRun;

    @Override
    public String getCommandName() {
        return "hhjoinmineshaft";
    }

    @Override
    public List<RequiredArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgInstanceAndRunNode("player", StringArgumentType.word())
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {

        if(System.currentTimeMillis() - lastRun < 5000) {
            context.getSource().sendError(Component.literal("§a[小沙雕] §c呜, 太快了, 会坏掉的..."));
            return -1;
        }
        lastRun = System.currentTimeMillis();
        String player = StringArgumentType.getString(context, "player");

        AbstractListener.mineshaftShareListener.tryToJoinPlayersMineshaft(player);
        // 工具列表.mc.thePlayer.sendChatMessage("/p leave");
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] §a尝试加入玩家§e" + player + "§a的§bGlacite Mineshaft§a, 请等待其响应!"));
        context.getSource().sendFeedback(Component.literal("§a[小沙雕] §a(如果没有响应请检查目标玩家大小写是否正确)"));

        return 0;

    }

}
