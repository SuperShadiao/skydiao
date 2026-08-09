package pers.XiaoShadiao.skydiao.appendage.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining.ObsidianWRListener;
import pers.XiaoShadiao.skydiao.commands.BaseRootRunnableCommand;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining.ObsidianListener;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.RecordPos;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

import java.util.*;

import static pers.XiaoShadiao.skydiao.appendage.utils.posrecord.RecordPos.getPListFileNames;

//APPEND
public class ApdCommand extends BaseRootRunnableCommand {

    private static final Logger logger =  LoggerFactory.getLogger(ApdCommand.class);

    @Override
    public String getCommandName() {
        return "apd";
    }

    @Override
    public List<ArgumentBuilder<FabricClientCommandSource, ?>> getArgs() {
        return List.of(
                getArgConstantInstance("recpos")
                        .then(
                        getArgConstantInstance("start").then(getArgInstance("filename", StringArgumentType.string()).executes(this::recposStart))
                )
                        .then(getArgConstantInstance("record").executes(this::recposAdd))
                        .then(getArgConstantInstance("save").executes(this::recposSave))
                        .then(getArgConstantInstance("listFiles").executes(this::recposList))
                        .then(getArgConstantInstance("load").then(
                                    getArgInstance("filename",StringArgumentType.string()).suggests(
                                            (context, builder) -> {
                                                Collection<String> fileNames = getPListFileNames();

                                                if (fileNames != null) {
                                                    for (String filename : fileNames) {
                                                        builder.suggest(filename);
                                                    }
                                                }
                                                return builder.buildFuture();
                                            }
                                    ).executes(this::recposLoad)
                                )
                        )
                        .then(getArgConstantInstance("exit").executes(this::recposExit))
                        .then(getArgConstantInstance("undo")
                                .then(getArgInstance("count", IntegerArgumentType.integer(1)).executes(this::recposUndo))
                                .executes(this::recposUndo2))
                        .then(getArgConstantInstance("del")
                                .then(getArgInstance("from", IntegerArgumentType.integer(1))
                                .executes(this::recposDelete2).then(getArgInstance("to", IntegerArgumentType.integer(1)).executes(this::recposDelete))
                        ))
                        .then(getArgConstantInstance("getall").executes(this::recposGetall))
                        .then(getArgConstantInstance("help").executes(this::recposHelp)).executes(this::recposHelp)
                ,
                //开关AutoObsidian或进行设置
                getArgConstantInstance("_om")
                        .then(
                                //设置钻头栏
                                getArgConstantInstance("DSlot").then(getArgInstance("slot", IntegerArgumentType.integer(0,8)).executes(this::setDrillSlot))
                        )
                        .then(
                                //设置灯笼栏
                                getArgConstantInstance("LSlot").then(getArgInstance("slot", IntegerArgumentType.integer(0,8)).executes(this::setLanternSlot))
                        )
                        .then(
                                //设置初始灯笼时间
                                getArgConstantInstance("LSec").then(getArgInstance("sec", IntegerArgumentType.integer(0)).executes(this::setLanternSec))
                        )
                        .then(
                                //设置存档
                                getArgConstantInstance("selectFile").then(getArgInstance("filename",StringArgumentType.string()).suggests(
                                                (context, builder) -> {
                                                    Collection<String> fileNames = getPListFileNames();

                                                    if (fileNames != null) {
                                                        for (String filename : fileNames) {
                                                            builder.suggest(filename);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                }
                                        ).executes(this::setrecpos)
                                )
                        )
                        .then(
                                getArgConstantInstance("playerCheck").executes(this::setPlayerCheck)
                        )
                        .then(
                                getArgConstantInstance("modeCheck").executes(this::setModeCheck)
                        )
                        .then(
                                getArgConstantInstance("setPlayerCheckRange").then(getArgInstance("range", IntegerArgumentType.integer(8)).executes(this::setPlayerCheckRange))
                        )
                        .then(
                                getArgConstantInstance("info").executes(this::info)
                        )
                        .executes(this::autoOb),
                //开关AutoObsidianWithRetry
                getArgConstantInstance("OM").executes(this::autoObWR),
                //获取ClientboundLocationPacket
                getArgConstantInstance("getCLP").executes(this::getCLP)
                );
    }

    @Override
    public int executeCommand(CommandContext<FabricClientCommandSource> context) {

        return 1;
    }


    private int executeEXAMPLE(CommandContext<FabricClientCommandSource> context) {
        return 0;
    }

    public int recposStart(CommandContext<FabricClientCommandSource> context){
        if(RecordPos.plist!=null) RecordPos.save();
        String filename = context.getArgument("filename", String.class);
        RecordPos.start(filename);
        context.getSource().sendFeedback(Component.literal("开始记录.文件名:"+filename).withColor(0xd672de));
        return 1;
    }

    private boolean recpos_check(CommandContext<FabricClientCommandSource> context){
        if(RecordPos.plist==null) {
            context.getSource().sendFeedback(Component.literal("未创建文件！").withColor(0xf22b30));
            return false;
        }
        return true;
    }

    private int recposAdd(CommandContext<FabricClientCommandSource> context) {
        if(!recpos_check(context)) return 0;
        if (Minecraft.getInstance().player != null) {
            RecordPos.add(Minecraft.getInstance().player);
            context.getSource().sendFeedback(Component.literal("已记录:").withColor(0xd672de).append(
                    Component.literal(Minecraft.getInstance().player.position().toString()).withColor(0x69eb79)
            ));
        }
        return 1;
    }

    private int recposSave(CommandContext<FabricClientCommandSource> context) {
        if(!recpos_check(context)) return 0;
        RecordPos.save();
        context.getSource().sendFeedback(Component.literal("已保存！").withColor(0xd672de));
        return 1;
    }

    private int recposExit(CommandContext<FabricClientCommandSource> context) {
        if(!recpos_check(context)) return 0;
        RecordPos.plist = null;
        context.getSource().sendFeedback(Component.literal("已退出！").withColor(0xd672de));
        return 1;
    }

    private int recposList(CommandContext<FabricClientCommandSource> context) {
        List<String> filenames = getPListFileNames();

        if (filenames == null || filenames.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("未找到文件.").withColor(0xf22b30));
            return 0;
        }
        context.getSource().sendFeedback(Component.literal("所有文件:").withColor(0xd672de));

        for (String filename : filenames) {
            context.getSource().sendFeedback(Component.literal("- " + filename).withColor(0x7d78dc));
        }

        return 1;

    }



    private int setrecpos(CommandContext<FabricClientCommandSource> context) {
        ConfigManager.autoObsidianPositionsFile.setValue(context.getArgument("filename", String.class));
        context.getSource().sendFeedback(Component.literal("已选择文件:"+context.getArgument("filename", String.class)).withColor(0xd672de));
        logger.info(ObsidianListener.plist.positions().toString());

        //context.getSource().sendFeedback(Component.literal(RecordPos.gson.toJson(ObsidianListener.plist)));
        return 0;
    }

    private int recposUndo(CommandContext<FabricClientCommandSource> context){
        if(!recpos_check(context)) return 0;
        recpos_Undo(context,context.getArgument("count", Integer.class));
        return 1;
    }

    private int recposUndo2(CommandContext<FabricClientCommandSource> context){
        if(!recpos_check(context)) return 0;
        int c = 1;
        recpos_Undo(context,c);
        return 1;
    }

    private void recpos_Undo(CommandContext<FabricClientCommandSource> context, int c){
        for (int i=0; i<c; i++) {
            RecordPos.plist.positions().removeLast();
        }
        context.getSource().sendFeedback(Component.literal("已撤销最后"+c+"次操作.").withColor(0xd672de));
    }



    private int recposDelete(CommandContext<FabricClientCommandSource> context) {
        if(!recpos_check(context)) return 0;
        recpos_Del(context, context.getArgument("from", Integer.class), context.getArgument("to", Integer.class));
        return 1;
    }

    private int recposDelete2(CommandContext<FabricClientCommandSource> context) {
        if(!recpos_check(context)) return 0;
        recpos_Del(context, context.getArgument("from", Integer.class), context.getArgument("from", Integer.class));
        return 1;
    }

    private void recpos_Del(CommandContext<FabricClientCommandSource> context, int from, int to){
        if(from > RecordPos.plist.positions().size()){
            context.getSource().sendFeedback(Component.literal("最大值超过了点个数！").withColor(0xf22b30));
            return;
        }
        int to2 = Math.clamp(to, from, RecordPos.plist.positions().size());
        for (int i=to2; i<=to2; i++) {
            RecordPos.plist.positions().remove(from-1);
        }
        context.getSource().sendFeedback(Component.literal("已删除第"+from+"至"+to2+"个点.").withColor(0xd672de));

    }

    private int recposGetall(CommandContext<FabricClientCommandSource> context){
        if(!recpos_check(context)) return 0;
        context.getSource().sendFeedback(Component.literal("已记录数目:"+RecordPos.plist.positions().size()).withColor(0xd672de));
        for(Vec3 vec3 : RecordPos.plist.positions()){
            context.getSource().sendFeedback(Component.literal(vec3.toString()).withColor(0x69eb79));
        }
        return 1;
    }

    private int recposLoad(CommandContext<FabricClientCommandSource> context) {
        if(RecordPos.plist!=null) RecordPos.save();
        RecordPos.plist = RecordPos.getLocal(context.getArgument("filename", String.class));
        context.getSource().sendFeedback(Component.literal("已加载文件:"+RecordPos.plist.name()).withColor(0xd672de));
        return 1;
    }

    private int recposHelp(CommandContext<FabricClientCommandSource> context){
        List<String> list = List.of(
                "recpos命令指南",
                "recpos start <文件名>       --开始记录点列",
                "recpos load <文件名>        --加载点列存档",
                "recpos record              --记录所在位置",
                "recpos undo [<数量>]        --撤回最后几次操作(默认1次)",
                "recpos del <from> [<to>]   --删除存档中指定序号的点",
                "recpos getall              --给出存档中所有点的数据",
                "recpos save                --保存",
                "recpos exit                --不保存,直接退出",
                "recpos listFiles           --列出所有文件名",
                "注:点列存档保存在config/Ipositions中."
        );
        for(String str:list) context.getSource().sendFeedback(Component.literal(str).withColor(0xd672de));
        return 1;
    }

    private int autoOb(CommandContext<FabricClientCommandSource> context){
        boolean b =  ConfigManager.iAutoObsidian.getValue();

        if(b){
            ConfigManager.iAutoObsidian.setValue(false);
        }
        else ConfigManager.iAutoObsidian.setValue(true);
        context.getSource().sendFeedback(Component.literal("已将自动黑曜石设置为:"+!b).withColor(0xd672de));
        return 1;
    }

    private int autoObWR(CommandContext<FabricClientCommandSource> context){
        boolean b =  ConfigManager.iAutoObsidianWR.getValue();

        if(b){
            ConfigManager.iAutoObsidianWR.setValue(false);
        }
        else ConfigManager.iAutoObsidianWR.setValue(true);
        context.getSource().sendFeedback(Component.literal("已将自动黑曜石(+Retry)设置为:"+!b).withColor(0xd672de));
        return 1;
    }

    private int setDrillSlot(CommandContext<FabricClientCommandSource> context){
        int slot = context.getArgument("slot", Integer.class);
        int slot2 = ConfigManager.drillSlot.getValue();
        ConfigManager.drillSlot.setValue(slot);
        context.getSource().sendFeedback(Component.literal("已将钻头栏原先的值"+slot2+"替换为"+slot).withColor(0xd672de));
        return 1;
    }

    private int setLanternSlot(CommandContext<FabricClientCommandSource> context){
        int slot = context.getArgument("slot", Integer.class);
        int slot2 = ConfigManager.lanternSlot.getValue();
        ConfigManager.lanternSlot.setValue(slot);
        context.getSource().sendFeedback(Component.literal("已将灯笼栏原先的值"+slot2+"替换为"+slot).withColor(0xd672de));
        return 1;
    }

    private int setLanternSec(CommandContext<FabricClientCommandSource> context){
        int sec = context.getArgument("sec", Integer.class);
        ObsidianListener.lanternTimer = sec*20;
        context.getSource().sendFeedback(Component.literal("已将灯笼冷却替换为"+sec+"秒").withColor(0xd672de));
        return 1;
    }

    private int getCLP(CommandContext<FabricClientCommandSource> context){
        String str = StatusManager.get().getHypLocation()==null? "hypLocation==null" : StatusManager.get().getHypLocation().toString();
        context.getSource().sendFeedback(Component.literal(str).withColor(0xd672de));
        return 1;
    }

    private int setPlayerCheck(CommandContext<FabricClientCommandSource> context){
        boolean b = ConfigManager.obsidianPlayerCheck.getValue();

        if(b){
            ConfigManager.obsidianPlayerCheck.setValue(false);
        }
        else ConfigManager.obsidianPlayerCheck.setValue(true);
        context.getSource().sendFeedback(Component.literal("已将玩家检测设为:"+!b).withColor(0xd672de));
        return 1;
    }

    private int setModeCheck(CommandContext<FabricClientCommandSource> context){
        boolean b = ConfigManager.obsidianTheEndCheck.getValue();

        if(b){
            ConfigManager.obsidianTheEndCheck.setValue(false);
        }
        else ConfigManager.obsidianTheEndCheck.setValue(true);
        context.getSource().sendFeedback(Component.literal("已将末地检测设为:"+!b).withColor(0xd672de));
        return 1;
    }

    private int setPlayerCheckRange(CommandContext<FabricClientCommandSource> context){
        int range = context.getArgument("range", Integer.class);
        int range2 = ConfigManager.obsidianPlayerCheckRange.getValue();
        ConfigManager.obsidianPlayerCheckRange.setValue(range);
        context.getSource().sendFeedback(Component.literal("已将范围"+ range2 +"替换为"+ range).withColor(0xd672de));
        return 1;
    }

    private int info(CommandContext<FabricClientCommandSource> context){
        List<String> list = List.of(
                "自动黑曜石(+Retry)信息如下:",
                "点列存档:"+ConfigManager.autoObsidianPositionsFile,
                "钻头栏:"+ConfigManager.drillSlot.getValue(),
                "灯笼栏:"+ConfigManager.lanternSlot.getValue(),
                "是否开启玩家检测:"+ConfigManager.obsidianPlayerCheck.getValue(),
                "玩家检测范围(大):"+ConfigManager.obsidianPlayerCheckRange.getValue(),
                "是否开启末地检测:"+ConfigManager.obsidianTheEndCheck.getValue()
        );
        for(String str: list){
            context.getSource().sendFeedback(Component.literal(str).withColor(0xd672de));
        }
        return 1;
    }
}
