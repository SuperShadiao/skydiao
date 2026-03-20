package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.utils.TextTranslator;

import java.util.List;

public class HHTCommand extends BaseCommand {
    @Override
    public String getCommandName() {
        return "hht";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgInstanceAndRunNode("text", StringArgumentType.greedyString()).suggests((context, builder) -> builder.suggest("要翻译的文本").buildFuture())
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        new TextTranslator(StringArgumentType.getString(context, "text")).execute();
        return 0;
    }
}
