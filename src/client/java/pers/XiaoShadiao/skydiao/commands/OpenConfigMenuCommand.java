package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.screen.ConfigScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Collections;
import java.util.List;

public class OpenConfigMenuCommand extends BaseRootRunnableCommand {

    @Override
    public String getCommandName() {
        return "skydiao";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return Collections.emptyList();
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        ToolList.addThreadedTask(() -> mc.execute(() -> mc.setScreenAndShow(new ConfigScreen(mc.screen))), null);
        return 1;
    }

}
