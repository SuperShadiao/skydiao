package pers.XiaoShadiao.skydiao.commands;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.EasyFarmingScriptListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.ExecuteNode;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationPressMouseButton;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationPressMove;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationRotation;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.op.OperationSendCommand;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkydiaoFarmingScriptCommand extends BaseRootRunnableCommand {

    public final EasyFarmingScriptListener script = MacroManagerListener.farmingScript;

    @Override
    public String getCommandName() {
        return "skydiaofarmingscript";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("help").executes(this::printHelp),
                getArgConstantInstance("addnode").executes(this::addNode),
                getArgConstantInstance("removenode").executes(this::removeNode),
                getArgConstantInstance("editnearestnode").executes(this::editNearestNode),
                getArgConstantInstance("addoperation")
                        .then(getArgInstance("operation", StringArgumentType.string()).suggests((a, b) ->
                                b.suggest("move", new LiteralMessage("后面参数跟由wasd组成的字符串, 例如后面跟w就是按住前进键, 跟wa就是同时按住前进和向左键"))
                                        .suggest("rotation", new LiteralMessage("后面参数跟着yaw和pitch角度, 例如180 0, 将视角设置为yaw=180 pitch=0, 输入current将视角设置为当前视角"))
                                        .suggest("sendcommand", new LiteralMessage("后面参数跟着要发送的命令, 例如/warp garden"))
                                        .suggest("mouse", new LiteralMessage("后面参数跟着要按的鼠标键, 例如left right, 同时按左右键就一起输入就可以了")).buildFuture()
                        ).then(getArgInstance("arg", StringArgumentType.greedyString()).executes(this::addOperation))),
                getArgConstantInstance("removeoperation")
                        .then(getArgInstance("index", IntegerArgumentType.integer()).executes(this::removeOperation)),
                getArgConstantInstance("clone").executes(this::cloneCurrentEditing),
                getArgConstantInstance("listoperation").executes(this::listOperation),
                getArgConstantInstance("exitedit").executes(this::exitEditing),
                getArgConstantInstance("togglerendernode").executes(this::toggleRenderNode),
                getArgConstantInstance("setnodeexecmindelay").then(getArgInstance("delay", IntegerArgumentType.integer()).executes(this::setNodeExecMinDelay)),
                getArgConstantInstance("setnodeexecmaxdelay").then(getArgInstance("delay", IntegerArgumentType.integer()).executes(this::setNodeExecMaxDelay)),
                getArgConstantInstance("group")
                        .then(getArgInstance("operation", StringArgumentType.string()).suggests((a, b) ->
                                b.suggest("start", new LiteralMessage("标记组开始"))
                                        .suggest("end", new LiteralMessage("标记组结束"))
                                        .suggest("delete", new LiteralMessage("删除区域内所有节点"))
                                        .suggest("clone", new LiteralMessage("复制组，将当前位置作为组start")).buildFuture()
                        ).executes(this::group))
                );
    }

    private int setNodeExecMinDelay(CommandContext<FabricClientCommandSource> context) {
        script.setNodeExecMinDelay(IntegerArgumentType.getInteger(context, "delay"));
        return 0;
    }

    private int setNodeExecMaxDelay(CommandContext<FabricClientCommandSource> context) {
        script.setNodeExecMaxDelay(IntegerArgumentType.getInteger(context, "delay"));
        return 0;
    }

    private int toggleRenderNode(CommandContext<FabricClientCommandSource> context) {
        script.toggleRenderNode();
        return 0;
    }

    private int removeOperation(CommandContext<FabricClientCommandSource> context) {
        int index = IntegerArgumentType.getInteger(context, "index");
        script.removeOperationFromCurrentNode(index - 1);
        return 0;
    }

    private int exitEditing(CommandContext<FabricClientCommandSource> context) {
        script.setCurrentEditing(null);
        return 0;
    }

    private int listOperation(CommandContext<FabricClientCommandSource> context) {
        script.listOperation();
        return 0;
    }

    private int cloneCurrentEditing(CommandContext<FabricClientCommandSource> context) {
        script.cloneCurrentEditing();
        return 0;
    }

    private int addOperation(CommandContext<FabricClientCommandSource> context) {
        String arg = StringArgumentType.getString(context, "arg");
        switch(StringArgumentType.getString(context, "operation")) {
            case "move" -> {
                try {
                    List<OperationPressMove.Move> moves = new ArrayList<>();
                    for (char c : arg.toCharArray()) {
                        OperationPressMove.Move move = switch (c) {
                            case 'w' -> OperationPressMove.Move.FORWARD;
                            case 's' -> OperationPressMove.Move.BACKWARD;
                            case 'a' -> OperationPressMove.Move.LEFT;
                            case 'd' -> OperationPressMove.Move.RIGHT;
                            default -> throw new Exception("无效的移动指令: " + c);
                        };
                        moves.add(move);
                    }
                    OperationPressMove moveOp = new OperationPressMove();
                    moveOp.setMoves(moves.toArray(OperationPressMove.Move[]::new));
                    script.addOperationToCurrentEditing(moveOp);
                } catch (Exception e) {
                    e.printStackTrace();
                    context.getSource().sendError(Component.literal("§a[小沙雕] §c后面的参数应该是由wasd组成的字符串, 当前参数" + arg + "无效"));
                }
            }
            case "rotation" -> {
                try {
                    float yaw, pitch;
                    if(arg.equals("current")) {
                        yaw = InputSimulator.getPlayerYaw();
                        pitch = InputSimulator.getPlayerPitch();
                    } else {
                        String[] s = arg.split(" ");
                        if(s.length < 2) throw new IllegalArgumentException("错误的参数");
                        yaw = Float.parseFloat(s[0]);
                        pitch = Float.parseFloat(s[1]);
                    }
                    OperationRotation operationRotation = new OperationRotation();
                    operationRotation.pitch = pitch;
                    operationRotation.yaw = yaw;
                    script.addOperationToCurrentEditing(operationRotation);
                } catch (Exception e) {
                    e.printStackTrace();
                    context.getSource().sendError(Component.literal("§a[小沙雕] §c后面的参数应该是<yaw> <pitch>或者(current), 当前参数" + arg + "无效, 你可以输入current将视角设置为你当前的视角"));
                }
            }
            case "sendcommand" -> {
                OperationSendCommand op = new OperationSendCommand();
                op.command = arg;
                script.addOperationToCurrentEditing(op);
            }
            case "mouse" -> {
                OperationPressMouseButton op = new OperationPressMouseButton();
                List<String> list = Arrays.asList(arg.split(" "));
                if(list.contains("left")) op.isLeft = true;
                if(list.contains("right")) op.isRight = true;
                if(!op.isLeft && !op.isRight) {
                    context.getSource().sendError(Component.literal("§a[小沙雕] §c后面的参数应该是left或者right或者left right, 当前参数" + arg + "无效"));
                } else {
                    script.addOperationToCurrentEditing(op);
                }
            }
            default -> context.getSource().sendError(Component.literal("§a[小沙雕] §c无效的操作指令: " + StringArgumentType.getString(context, "operation")));
        }
        return 0;
    }

    private int editNearestNode(CommandContext<FabricClientCommandSource> context) {
        script.editNearestNode();
        return 0;
    }

    private int removeNode(CommandContext<FabricClientCommandSource> context) {
        script.removeCurrentEditingNode();
        return 0;
    }

    private int addNode(CommandContext<FabricClientCommandSource> context) {
        BlockPos pos = BlockPos.containing(mc.player.position());

        ExecuteNode node = new ExecuteNode();
        node.pos = pos;
        script.addNode(node);
        script.setCurrentEditing(node);
        return 0;
    }

    private int group(CommandContext<FabricClientCommandSource> context) {
        switch(StringArgumentType.getString(context, "operation")) {
            case "start" -> { script.groupStart(); }
            case "end" -> { script.groupEnd(); }
            case "delete" -> { script.groupDelete(); }
            case "clone" -> { script.groupClone(); }
            default -> context.getSource().sendError(Component.literal("§a[小沙雕] §c无效的操作指令: " + StringArgumentType.getString(context, "operation")));
        }
        return 0;
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {
        return 0;
    }

    @Override
    public void lastCallRootCmdNode(LiteralArgumentBuilder<FabricClientCommandSource> rootCmdNode) {
        rootCmdNode.executes(this::printHelp);
    }

    private int printHelp(CommandContext<FabricClientCommandSource> context) {

        List<String> helps = List.of(
                "§a[小沙雕] §e帮助:",
                "§a[小沙雕] §e/skydiaofs addnode §f- §b在你当前位置添加一个节点 (节点如果是空操作则称为空白节点, 到达该空白节点时脚本会提醒你并自动停止)",
                "§a[小沙雕] §e/skydiaofs removenode §f- §b删除当前正在编辑的节点",
                "§a[小沙雕] §e/skydiaofs clone §f- §b克隆当前正在编辑的节点到你当前的位置, 所有操作将保留",
                "§a[小沙雕] §e/skydiaofs editnearestnode §f- §b编辑你当前位置离你最近的节点",
                "§a[小沙雕] §e/skydiaofs removeoperation <index> §f- §b删除当前正在编辑的节点的操作, 其中<index>序号需要使用/skydiaofs listoperation查询",
                "§a[小沙雕] §e/skydiaofs listoperation §f- §b列出当前正在编辑的节点的所有操作",
                "§a[小沙雕] §e/skydiaofs addoperation move (wasd) §f- §b添加一个移动操作到当前正在编辑的节点, 其中如果要前进请输入w, 后退请输入s, 向左请输入a, 向右请输入d. 例如你要前进+向右, 则(wasd)部分输入wd",
                "§a[小沙雕] §e/skydiaofs addoperation rotation <yaw> <pitch> §f- §b添加一个转头操作到当前正在编辑的节点, 其中<yaw>和<pitch>是角度值, 例如90 0, 将<yaw> <pitch>替换成current将保存你当前的视角",
                "§a[小沙雕] §e/skydiaofs addoperation sendcommand <command> §f- §b添加一个发送命令操作到当前正在编辑的节点, 其中<command>是你要发送的命令, 例如/warp garden",
                "§a[小沙雕] §e/skydiaofs addoperation mouse (left/right) §f- §b添加一个鼠标键操作到当前正在编辑的节点, 其中(left/right)是你要按的鼠标键, 例如只按住左键就输入left, 同时按住两个键就输入left right",
                "§a[小沙雕] §e/skydiaofs exitedit §f- §b退出编辑节点编辑",
                "§a[小沙雕] §e/skydiaofs togglerendernodes §f- §b切换节点渲染",
                "§a[小沙雕] §e/skydiaofs setnodeexecmindelay <delay> §f- §b设置节点执行全局最小延迟, 即到达节点后不会立即执行, 而是经过该时间才会执行该节点的操作, 单位毫秒 (当前延迟为" + script.getNodeExecMinDelay() + "~" + script.getNodeExecMaxDelay() + "ms)",
                "§a[小沙雕] §e/skydiaofs setnodeexecmaxdelay <delay> §f- §b设置节点执行全局最大延迟, 即到达节点后不会立即执行, 而是经过该时间才会执行该节点的操作, 单位毫秒 (当前延迟为" + script.getNodeExecMinDelay() + "~" + script.getNodeExecMaxDelay() + "ms)",
                "§a[小沙雕] §e/skydiaofs group (start/end/delete/clone) §f- §b将开始和结束区域作为组进行复制和粘贴",
                "§a[小沙雕] §a",
                "§a[小沙雕] §a若要切换脚本开关, 请使用按键§e" + KeyBindsManager.toggleFarmingScript.getTranslatedKeyMessage().getString() + "§a切换 (可在控制设置里设置快捷键)",
                "§a[小沙雕] §a群内§e" + SkyDiaoModClient.CONST_QQGROUP_MAIN + "§a有教程awa"
        );
        for (String help : helps) {
            context.getSource().sendFeedback(Component.literal(help));
        }

        return 0;
    }

}
