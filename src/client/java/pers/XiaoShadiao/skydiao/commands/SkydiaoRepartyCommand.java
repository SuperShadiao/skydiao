package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Util;
import pers.XiaoShadiao.skydiao.utils.PartyManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class SkydiaoRepartyCommand extends BaseRootRunnableCommand {

    @Override
    public String getCommandName() {
        return "skydiaoreparty";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgInstance("excludePlayers", StringArgumentType.greedyString()).executes(this::executeCommand)
        );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        if(PartyManager.isInParty()) {
            ToolList.addThreadedTask(() -> {
                List<String> excludePlayers = Util.make(() -> {
                    try {
                        return List.of(StringArgumentType.getString(context, "excludePlayers").split(" "));
                    } catch (Exception e) {
                        return List.of();
                    }
                });

                List<String> playerNames = PartyManager.getPartyMembers().stream().map(PartyManager.Member::name).filter(name -> !excludePlayers.contains(name)).toList();

                ToolList.sendChatMessage("/p disband");
                int step = 5;
                for (int i = 0; i < playerNames.size(); i += step) {
                    Thread.sleep(1000);
                    List<String> subList = playerNames.subList(i, Math.min(i + step, playerNames.size()));
                    ToolList.sendChatMessage("/p invite " + String.join(" ", subList));
                }

                return null;
            });
        }
        return 0;
    }

}
