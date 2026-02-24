package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class HHMCommand extends OpenConfigMenuCommand {

    @Override
    public String getCommandName() {
        return "hhm";
    }

    @Override
    public List<RequiredArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return !FabricLoader.getInstance().isModLoaded("modmenu") ? super.getArgs() : List.of(
                getArgInstance("extra", StringArgumentType.word()).executes(this::onExecute)
        );
    }

    private int onExecute(CommandContext<FabricClientCommandSource> context) {
        String extra = StringArgumentType.getString(context, "extra");
        if(extra.equals("modmenumod")) {
            ToolList.addThreadedTask(() -> {
                mc.execute(() -> {
                    try {
                        mc.setScreen((Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(mc.screen));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return null;
            });
            return 0;
        }

        return super.executeCommand(context);
    }

}
