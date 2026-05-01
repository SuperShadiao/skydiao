package pers.XiaoShadiao.skydiao.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class SimpleStringReader implements ArgumentType<String> {

    public static String readSimpleString(StringReader reader, boolean saveSpace) throws CommandSyntaxException {

        reader.skipWhitespace();

        if (!reader.canRead()) return "";

        StringBuilder sb = new StringBuilder();

        while (reader.canRead()) {
            char c = reader.peek();
            if(c == ' ') {
                if(!saveSpace) reader.skip();
                break;
            }
            reader.skip();
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return readSimpleString(reader, false);
    }

    public static String getString(CommandContext<FabricClientCommandSource> commandContext, String argName) {
        return commandContext.getArgument(argName, String.class);
    }

}
