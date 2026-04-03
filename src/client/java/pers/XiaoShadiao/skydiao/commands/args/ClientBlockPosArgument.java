package pers.XiaoShadiao.skydiao.commands.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class ClientBlockPosArgument implements ArgumentType<ClientBlockPosArgument.Coordinates> {

    public interface Coordinates {
        Vec3 getPosition(FabricClientCommandSource commandSourceStack);

        Vec2 getRotation(FabricClientCommandSource commandSourceStack);

        default BlockPos getBlockPos(FabricClientCommandSource commandSourceStack) {
            return BlockPos.containing(this.getPosition(commandSourceStack));
        }

        boolean isXRelative();

        boolean isYRelative();

        boolean isZRelative();
    }

    private static final Collection<String> EXAMPLES = Arrays.asList("0 0 0", "~ ~ ~", "^ ^ ^", "^1 ^ ^-5", "~0.5 ~1 ~-5");
    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(Component.translatable("argument.pos.unloaded"));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(Component.translatable("argument.pos.outofworld"));
    public static final SimpleCommandExceptionType ERROR_OUT_OF_BOUNDS = new SimpleCommandExceptionType(Component.translatable("argument.pos.outofbounds"));

    public static ClientBlockPosArgument blockPos() {
        return new ClientBlockPosArgument();
    }

    public static BlockPos getLoadedBlockPos(CommandContext<FabricClientCommandSource> commandContext, String string) throws CommandSyntaxException {
        return getLoadedBlockPos(commandContext, ToolList.mc.level, string);
    }

    public static BlockPos getLoadedBlockPos(CommandContext<FabricClientCommandSource> commandContext, ClientLevel serverLevel, String string) throws CommandSyntaxException {
        BlockPos blockPos = getBlockPos(commandContext, string);
        if (!serverLevel.hasChunkAt(blockPos)) {
            throw ERROR_NOT_LOADED.create();
        } else if (!serverLevel.isInWorldBounds(blockPos)) {
            throw ERROR_OUT_OF_WORLD.create();
        } else {
            return blockPos;
        }
    }

    public static BlockPos getBlockPos(CommandContext<FabricClientCommandSource> commandContext, String string) {
        return commandContext.getArgument(string, Coordinates.class).getBlockPos(commandContext.getSource());
    }

    public static BlockPos getSpawnablePos(CommandContext<FabricClientCommandSource> commandContext, String string) throws CommandSyntaxException {
        BlockPos blockPos = getBlockPos(commandContext, string);
        if (!Level.isInSpawnableBounds(blockPos)) {
            throw ERROR_OUT_OF_BOUNDS.create();
        } else {
            return blockPos;
        }
    }

    public Coordinates parse(StringReader stringReader) throws CommandSyntaxException {
        return WorldCoordinates.parseInt(stringReader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        if (!(commandContext.getSource() instanceof SharedSuggestionProvider)) {
            return Suggestions.empty();
        } else {
            String string = suggestionsBuilder.getRemaining();
            Collection<SharedSuggestionProvider.TextCoordinates> collection;
            if (!string.isEmpty() && string.charAt(0) == '^') {
                collection = Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_LOCAL);
            } else {
                collection = ((SharedSuggestionProvider)commandContext.getSource()).getRelevantCoordinates();
            }

            return SharedSuggestionProvider.suggestCoordinates(string, collection, suggestionsBuilder, Commands.createValidator(this::parse));
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public record WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) implements Coordinates {
        public static final WorldCoordinates ZERO_ROTATION = absolute(new Vec2(0.0F, 0.0F));

        @Override
        public Vec3 getPosition(FabricClientCommandSource commandSourceStack) {
            Vec3 vec3 = commandSourceStack.getPosition();
            return new Vec3(this.x.get(vec3.x), this.y.get(vec3.y), this.z.get(vec3.z));
        }

        @Override
        public Vec2 getRotation(FabricClientCommandSource commandSourceStack) {
            Vec2 vec2 = commandSourceStack.getRotation();
            return new Vec2((float)this.x.get(vec2.x), (float)this.y.get(vec2.y));
        }

        @Override
        public boolean isXRelative() {
            return this.x.isRelative();
        }

        @Override
        public boolean isYRelative() {
            return this.y.isRelative();
        }

        @Override
        public boolean isZRelative() {
            return this.z.isRelative();
        }

        public static WorldCoordinates parseInt(StringReader stringReader) throws CommandSyntaxException {
            int i = stringReader.getCursor();
            WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(stringReader);
            if (stringReader.canRead() && stringReader.peek() == ' ') {
                stringReader.skip();
                WorldCoordinate worldCoordinate2 = WorldCoordinate.parseInt(stringReader);
                if (stringReader.canRead() && stringReader.peek() == ' ') {
                    stringReader.skip();
                    WorldCoordinate worldCoordinate3 = WorldCoordinate.parseInt(stringReader);
                    return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
                } else {
                    stringReader.setCursor(i);
                    throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
                }
            } else {
                stringReader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
            }
        }

        public static WorldCoordinates parseDouble(StringReader stringReader, boolean bl) throws CommandSyntaxException {
            int i = stringReader.getCursor();
            WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(stringReader, bl);
            if (stringReader.canRead() && stringReader.peek() == ' ') {
                stringReader.skip();
                WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(stringReader, false);
                if (stringReader.canRead() && stringReader.peek() == ' ') {
                    stringReader.skip();
                    WorldCoordinate worldCoordinate3 = WorldCoordinate.parseDouble(stringReader, bl);
                    return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
                } else {
                    stringReader.setCursor(i);
                    throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
                }
            } else {
                stringReader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(stringReader);
            }
        }

        public static WorldCoordinates absolute(double d, double e, double f) {
            return new WorldCoordinates(new WorldCoordinate(false, d), new WorldCoordinate(false, e), new WorldCoordinate(false, f));
        }

        public static WorldCoordinates absolute(Vec2 vec2) {
            return new WorldCoordinates(new WorldCoordinate(false, vec2.x), new WorldCoordinate(false, vec2.y), new WorldCoordinate(true, 0.0));
        }
    }
}
