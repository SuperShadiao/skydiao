package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.commands.args.ClientBlockPosArgument;
import pers.XiaoShadiao.skydiao.commands.args.SimpleStringReader;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.pathfinder.EntityFollower;
import pers.XiaoShadiao.skydiao.utils.pathfinder.PathFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SkydiaoPFCommand extends BaseCommand {
    @Override
    public String getCommandName() {
        return "skydiaopf";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("goto").then(getArgInstance("pos", ClientBlockPosArgument.blockPos()).executes(this::handleGoto)),
                getArgConstantInstance("stopgoal").executes(this::handleStop),
                getArgConstantInstance("debugmode").executes((context -> {
                    PathFinder.pathFinderDebug = !PathFinder.pathFinderDebug;
                    return 0;
                })),
                getArgConstantInstance("debugnext").then(getArgInstance("count", IntegerArgumentType.integer()).executes((context -> {
                    PathFinder.pathFinderDebugCount += IntegerArgumentType.getInteger(context, "count");
                    return 0;
                }))),
                getArgConstantInstance("mineblock").then(getArgInstance("blocks", new MineBlockArgument()).executes(this::handleMineBlock)),
                getArgConstantInstance("followentity").then(getArgInstance("entities", new AttackEntityArgument()).executes(c -> handleFollowEntity(c, false))),
                getArgConstantInstance("attackentity").then(getArgInstance("entities", new AttackEntityArgument()).executes(c -> handleFollowEntity(c, true))),
                getArgConstantInstance("pausegoal").executes(c -> handlePause(c, true)),
                getArgConstantInstance("resumegoal").executes(c -> handlePause(c, false))
        );
    }

    private int handlePause(CommandContext<FabricClientCommandSource> context, boolean flag) {
        if(flag) {
            MacroManagerListener.pathFinderExecutor.pausePF();
        } else {
            MacroManagerListener.pathFinderExecutor.resumePF();
        }
        return 0;
    }

    private int handleFollowEntity(CommandContext<FabricClientCommandSource> context, boolean attackMode) {
        AttackEntityArgument.Data entities = AttackEntityArgument.getArgData(context, "entities");
        MacroManagerListener.pathFinderExecutor.startFollowEntity(entities.targets(), entities.avoid(), attackMode, true);
        return 0;
    }

    private int handleMineBlock(CommandContext<FabricClientCommandSource> context) {
        MacroManagerListener.pathFinderExecutor.startMine(MineBlockArgument.getBlocks(context, "blocks"));
        return 0;
    }

    private int handleStop(CommandContext<FabricClientCommandSource> context) {
        MacroManagerListener.pathFinderExecutor.stopExecution();
        return 0;
    }

    private int handleGoto(CommandContext<FabricClientCommandSource> context) {
        BlockPos pos = ClientBlockPosArgument.getBlockPos(context, "pos");
        MacroManagerListener.pathFinderExecutor.startExecution(Vec3.atCenterOf(pos), true);
        return 0;
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        return 0;
    }

    public class MineBlockArgument implements ArgumentType<List<Map.Entry<Block, Integer>>> {

        private final BlockStateArgument blockStateArgument = BlockStateArgument.block(getCommandBuildContext());
        private final IntegerArgumentType integerArgumentType = IntegerArgumentType.integer();

        @Override
        public List<Map.Entry<Block, Integer>> parse(StringReader reader) throws CommandSyntaxException {
            ArrayList<Map.Entry<Block, Integer>> entries = new ArrayList<>();

            while(reader.canRead()) {
                Block block = blockStateArgument.parse(reader).getState().getBlock();
                reader.expect(' ');
                Integer p = integerArgumentType.parse(reader);
                reader.skipWhitespace();
                entries.add(Map.entry(
                        block,
                        p
                ));
            }
            return entries;
        }

        @Override
        public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());

            boolean flag = true;
            while(reader.canRead()) {
                if(flag) {
                    try {
                        blockStateArgument.parse(reader);
                        reader.expect(' ');
                    } catch (CommandSyntaxException e) {
                        break;
                    }
                } else {
                    try {
                        integerArgumentType.parse(reader);
                        reader.expect(' ');
                    } catch (CommandSyntaxException e) {
                        return Suggestions.empty();
                    }
                }
                flag = !flag;
            }
            if(flag) {
                return blockStateArgument.listSuggestions(context, builder.createOffset(reader.getCursor()));
            } else {
                return builder.createOffset(reader.getCursor()).suggest(1).buildFuture();
            }
        }

        public static List<Map.Entry<Block, Integer>> getBlocks(final CommandContext<?> context, final String name) {
            return context.getArgument(name, List.class);
        }

    }

    public class AttackEntityArgument implements ArgumentType<AttackEntityArgument.Data> {

        @Override
        public Data parse(StringReader reader) throws CommandSyntaxException {
            List<EntityFollower.NameInfo> targets = new ArrayList<>();
            List<EntityFollower.NameInfo> avoid = new ArrayList<>();
            while(reader.canRead()) {
                boolean isEqual;
                boolean isAvoid;
                try {
                    reader.expect('=');
                    isEqual = true;
                } catch (CommandSyntaxException e) {
                    isEqual = false;
                }
                try {
                    reader.expect('!');
                    isAvoid = true;
                } catch (CommandSyntaxException e) {
                    isAvoid = false;
                }

                (isAvoid ? avoid : targets).add(new EntityFollower.NameInfo(SimpleStringReader.readSimpleString(reader, false), isEqual));
            }

            return new Data(targets, avoid);
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            boolean hasEqual = false;
            boolean hasAvoid = false;
            boolean hasArg = false;
            do {
                reader.skipWhitespace();
                try {
                    reader.expect('=');
                    hasEqual = true;
                } catch (CommandSyntaxException e) {
                }
                try {
                    reader.expect('!');
                    hasAvoid = true;
                } catch (CommandSyntaxException e) {
                }
                try {
                    hasArg = !SimpleStringReader.readSimpleString(reader, true).isEmpty();
                } catch (CommandSyntaxException e) {
                }
                try {
                    reader.expect(' ');
                    hasAvoid = false;
                    hasEqual = false;
                    hasArg = false;
                } catch (CommandSyntaxException e) {
                }
            } while(reader.canRead());
            if(!hasArg) {
                if(!hasEqual && !hasAvoid) {
                    return builder.createOffset(reader.getCursor()).suggest("=").suggest("!").suggest("=!").buildFuture();
                } else if(hasEqual && !hasAvoid) {
                    return builder.createOffset(reader.getCursor()).suggest("!").buildFuture();
                }
            }
            return ArgumentType.super.listSuggestions(context, builder);
        }

        public record Data(List<EntityFollower.NameInfo> targets, List<EntityFollower.NameInfo> avoid) {}

        public static AttackEntityArgument.Data getArgData(CommandContext<?> context, String name) {
            return context.getArgument(name, AttackEntityArgument.Data.class);
        }

    }


}
