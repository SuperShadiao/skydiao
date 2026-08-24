package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.calc.Calc;

import java.util.List;

public class SkydiaoCalcCommand extends BaseCommand {
    @Override
    public String getCommandName() {
        return "skydiaocalc";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgInstance("formula", StringArgumentType.greedyString()).executes(this::executeCommand)
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        try {
            String s = StringArgumentType.getString(context, "formula");
            double result = new Calc(s).result;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §d结果: §b" + s + " = " + result));
        } catch (Exception e) {
            e.printStackTrace();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请输入正确的计算算式: " + e.getLocalizedMessage()));
        }
        return 0;
    }

}
