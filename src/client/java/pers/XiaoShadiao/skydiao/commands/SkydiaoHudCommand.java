package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.screen.HudConfigScreen;

import java.util.List;

public class SkydiaoHudCommand extends BaseRootRunnableCommand {
    @Override
    public String getCommandName() {
        return "skydiaohud";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of();
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        mc.schedule(() -> mc.setScreen(new HudConfigScreen(mc.screen)));
        return 0;
    }
}
