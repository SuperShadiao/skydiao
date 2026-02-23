package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;

public abstract class BaseCommand {

    public abstract String getCommandName();
    public abstract List<RequiredArgumentBuilder<FabricClientCommandSource, ?>> getArgs();

    public int executeCommand0(CommandContext<FabricClientCommandSource> context) {
        try {
            return executeCommand(context);
        } catch (Exception e) {
            context.getSource().sendError(Component.literal("§a[小沙雕] §c执行指令时发生错误, 详情请查看日志...").append("\n").append("§a[小沙雕] §c" + e));
            e.printStackTrace();
            return -1;
        }
    }

    public abstract int executeCommand(CommandContext<FabricClientCommandSource> context);

    public <T> RequiredArgumentBuilder<FabricClientCommandSource, T> getArgInstance(String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type);
    }

    public <T> RequiredArgumentBuilder<FabricClientCommandSource, T> getArgInstanceAndRunNode(String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type).executes(this::executeCommand0);
    }

}
