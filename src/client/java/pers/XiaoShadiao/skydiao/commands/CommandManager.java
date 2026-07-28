package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import pers.XiaoShadiao.skydiao.utils.Register;

public interface CommandManager {

    public static final XSDChatCommand XSD_CHAT_COMMAND = new XSDChatCommand();
    public static final HHMCommand HHM_COMMAND = new HHMCommand();
    public static final JoinMineshaftCommand JOIN_MINESHAFT_COMMAND = new JoinMineshaftCommand();
    public static final JoinMineshaft2Command JOIN_MINESHAFT2_COMMAND = new JoinMineshaft2Command();
    public static final HHTCommand HHT_COMMAND = new HHTCommand();
    public static final SkydiaoTCommand SKYDIAO_T_COMMAND = new SkydiaoTCommand();
    public static final HHSCCommand HHSC_COMMAND = new HHSCCommand();
    public static final SkydiaoCommand OPEN_CONFIG_MENU_COMMAND = new SkydiaoCommand();
    public static final SkydiaoPFCommand SKYDIAO_PF_COMMAND = new SkydiaoPFCommand();
    public static final HHPFCommand HHPF_COMMAND = new HHPFCommand();
    public static final HHMusicCommand HH_MUSIC_COMMAND = new HHMusicCommand();
    public static final SkydiaoMusicCommand SKYDIAO_MUSIC_COMMAND = new SkydiaoMusicCommand();
    public static final SkydiaoFarmingScriptCommand SKYDIAO_FARMING_SCRIPT_COMMAND = new SkydiaoFarmingScriptCommand();
    public static final HHFSCommand HHFS_COMMAND = new HHFSCommand();
    public static final HHFarmingScriptCommand HH_FARMING_SCRIPT_COMMAND = new HHFarmingScriptCommand();
    public static final SkydiaoFSCommand SKYDIAO_FS_COMMAND = new SkydiaoFSCommand();
    public static final XSDCShowCommand XSDC_SHOW_COMMAND = new XSDCShowCommand();
    public static final SkydiaoStopABFCommand SKYDIAO_STOP_AF_COMMAND = new SkydiaoStopABFCommand();
    public static final HHStopABFCommand HH_STOP_AF_COMMAND = new HHStopABFCommand();
    public static final SkydiaoHudCommand SKYDIAO_HUD_COMMAND = new SkydiaoHudCommand();
    public static final HHHudCommand HH_HUD_COMMAND = new HHHudCommand();

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Register.execRegister(CommandManager.class, BaseCommand.class, cmd -> {
                cmd.setCommandBuildContext(registryAccess);
                try {
                    LiteralArgumentBuilder<FabricClientCommandSource> register = ClientCommands.literal(cmd.getCommandName());
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
