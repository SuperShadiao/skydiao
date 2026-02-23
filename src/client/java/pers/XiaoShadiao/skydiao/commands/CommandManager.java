package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.MsgCommand;

import java.lang.reflect.Field;

public class CommandManager {

    public static final XSDChatCommand XSD_CHAT_COMMAND = new XSDChatCommand();

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

                        dispatcher.register(register);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException("Error registering command", e);
                    }
                }
            }
        });
    }

}
