package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

public class XSDCBlockCommand extends BaseCommand {

    @Override
    public String getCommandName() {
        return "xsdcblock";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("add").then(getArgInstance("player", StringArgumentType.string()).executes(this::executeAdd)),
                getArgConstantInstance("remove").then(getArgInstance("player", StringArgumentType.string()).suggests((context, builder) -> {
                    String value = ConfigManager.ircBlockPlayers.getValue();
                    String[] players = value.split(" ");
                    for(String player : players) {
                        builder.suggest(player);
                    }
                    return builder.buildFuture();
                }).executes(this::executeRemove))
        );
    }

    private int executeRemove(CommandContext<FabricClientCommandSource> context) {
        String player = StringArgumentType.getString(context, "player");
        String value = ConfigManager.ircBlockPlayers.getValue();
        String[] players = value.split(" ");
        List<String> list = new ArrayList<>(List.of(players));
        if(list.remove(player)) {
            list.removeIf(String::isBlank);
            ConfigManager.ircBlockPlayers.setValue(String.join(" ", list));
            ConfigManager.saveConfig();
            context.getSource().sendFeedback(Component.literal("§a[小沙雕] 已将§e" + player + "§a从IRC屏蔽列表中移除"));
        } else {
            context.getSource().sendFeedback(Component.literal("§a[小沙雕] §c玩家§e" + player + "§c不在IRC屏蔽列表中"));
        }
        return 0;
    }

    private int executeAdd(CommandContext<FabricClientCommandSource> context) {
        String player = StringArgumentType.getString(context, "player");
        String value = ConfigManager.ircBlockPlayers.getValue();
        String[] players = value.split(" ");
        List<String> list = new ArrayList<>(List.of(players));
        list.add(player);
        list.removeIf(String::isBlank);
        ConfigManager.ircBlockPlayers.setValue(String.join(" ", list));

        context.getSource().sendFeedback(Component.literal("§a[小沙雕] 已将§e" + player + "§a添加到IRC屏蔽列表中"));
        ConfigManager.saveConfig();
        return 0;
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        return 0;
    }

}
