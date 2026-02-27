package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public abstract class BaseCommand implements CommandManager {

    public static final Minecraft mc = ToolList.mc;

    private LiteralCommandNode<FabricClientCommandSource> commandNode;

    public abstract String getCommandName();
    public abstract List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs();

    public final int executeCommand0(CommandContext<FabricClientCommandSource> context) {
        try {
            return executeCommand(context);
        } catch (Exception e) {
            context.getSource().sendError(Component.literal("§a[小沙雕] §c执行指令时发生错误, 详情请查看日志...").append("\n").append("§a[小沙雕] §c" + e));
            e.printStackTrace();
            return -1;
        }
    }

    public abstract int executeCommand(CommandContext<FabricClientCommandSource> context);

    public final <T> RequiredArgumentBuilder<FabricClientCommandSource, T> getArgInstance(String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type);
    }

    public final <T> RequiredArgumentBuilder<FabricClientCommandSource, T> getArgInstanceAndRunNode(String name, ArgumentType<T> type) {
        return ClientCommandManager.argument(name, type).executes(this::executeCommand0);
    }

    public final LiteralArgumentBuilder<FabricClientCommandSource> getArgConstantInstance(String name) {
        return ClientCommandManager.literal(name);
    }

    public final LiteralArgumentBuilder<FabricClientCommandSource> getArgConstantInstanceAndRunNode(String name) {
        return ClientCommandManager.literal(name).executes(this::executeCommand0);
    }

    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {}

    public final void attachToRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        rootCmdNode.executes(this::executeCommand0);
    }

    public final void setCommandNode(LiteralCommandNode<FabricClientCommandSource> register) {
        commandNode = register;
    }

    public final LiteralCommandNode<FabricClientCommandSource> getCommandNode() {
        return commandNode;
    }
}
