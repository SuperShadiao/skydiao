package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.lang.reflect.Field;

public interface CommandManager {

    public static final XSDChatCommand XSD_CHAT_COMMAND = new XSDChatCommand();
    public static final OpenConfigMenuCommand OPEN_CONFIG_MENU_COMMAND = new OpenConfigMenuCommand();
    public static final HHSCCommand HHSC_COMMAND = new HHSCCommand();
    public static final HHMCommand HHM_COMMAND = new HHMCommand();
    public static final JoinMineshaftCommand JOIN_MINESHAFT_COMMAND = new JoinMineshaftCommand();
    public static final JoinMineshaft2Command JOIN_MINESHAFT2_COMMAND = new JoinMineshaft2Command();

    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            for (Field field : CommandManager.class.getDeclaredFields()) {
                if(BaseCommand.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        BaseCommand cmd = (BaseCommand) field.get(null);

                        LiteralArgumentBuilder<FabricClientCommandSource> register = ClientCommandManager.literal(cmd.getCommandName());
                        for(RequiredArgumentBuilder<FabricClientCommandSource, ?> arg : cmd.getArgs()) {
                            register = register.then(arg);
                        }
                        cmd.lastCallRootCmdNode(register);

                        cmd.setCommandNode(dispatcher.register(register));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException("Error registering command", e);
                    }
                }
            }
        });
    }

}
