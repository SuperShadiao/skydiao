package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.utils.Register;

import java.lang.reflect.Field;

public interface CommandManager {

    public static final XSDChatCommand XSD_CHAT_COMMAND = new XSDChatCommand();
    public static final HHMCommand HHM_COMMAND = new HHMCommand();
    public static final JoinMineshaftCommand JOIN_MINESHAFT_COMMAND = new JoinMineshaftCommand();
    public static final JoinMineshaft2Command JOIN_MINESHAFT2_COMMAND = new JoinMineshaft2Command();
    public static final HHTCommand HHT_COMMAND = new HHTCommand();
    public static final SkydiaoTCommand SKYDIAO_T_COMMAND = new SkydiaoTCommand();
    public static final HHSCCommand HHSC_COMMAND = new HHSCCommand();
    public static final OpenConfigMenuCommand OPEN_CONFIG_MENU_COMMAND = new OpenConfigMenuCommand();
    public static final SkydiaoPFCommand SKYDIAO_PF_COMMAND = new SkydiaoPFCommand();
    public static final HHPFCommand HHPF_COMMAND = new HHPFCommand();
    public static final HHMusicCommand HH_MUSIC_COMMAND = new HHMusicCommand();
    public static final SkydiaoMusicCommand SKYDIAO_MUSIC_COMMAND = new SkydiaoMusicCommand();

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Register.execRegister(CommandManager.class, BaseCommand.class, cmd -> {
                cmd.setCommandBuildContext(registryAccess);
                try {
                    LiteralArgumentBuilder<FabricClientCommandSource> register = ClientCommandManager.literal(cmd.getCommandName());
                    for(ArgumentBuilder<FabricClientCommandSource, ?> arg : cmd.getArgs()) {
                        register = register.then(arg);
                    }
                    cmd.lastCallRootCmdNode(register);

                    cmd.setCommandNode(dispatcher.register(register));
                } catch (Throwable e) {
                    throw new RuntimeException("Error registering command", e);
                }
            });
        });
    };

}
