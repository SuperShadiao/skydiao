package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.screen.ConfigScreen;
import pers.XiaoShadiao.skydiao.screen.MusicPlayerScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class HHMusicCommand extends BaseRootRunnableCommand {
    @Override
    public String getCommandName() {
        return "hhmusic";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of();
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        mc.schedule(() -> mc.setScreenAndShow(new MusicPlayerScreen()));
        return 0;
    }
}
