package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.ReportType;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

@Mixin(ReportType.class)
public class MixinReportTypeCustomMessage {

    @Unique
    private static final List<String> msgs = List.of(
            "你已经是个成熟的Minecraft了, 应该学会自己处理崩溃。",
            "这个Minecraft就是逊啦, 老崩溃。",
            "你干~~嘛~~! 嗨害~~哟~~!",
            "『欸嘿』是什么意思啊…！！",
            "欸嘿。",
            "其实，每一位『神之眼』的拥有者，都是有资格成为神的人，因此被称作『原神』，拥有登上天空岛的资格。",
            "bw3/4 三个NOOB, 就缺你带领我们征战起床战争!",
            "嘿嘿，你看我是不是有点做生意的天赋呀？",
            "亻尔女子, 亻尔扌丁开了辶文个山月月氵贵报告",
            "From [MVP+] w99: freaking loser haha || [MVP+] w99 removed from their friends list!",
            "[MVP+] TellShe: duel 我 火速的 || [VIP] L_Daxian_L: 讨论组 fw",
            "[VIP] 1egendary_kind: Super_Shadiao is the hacker",
            "总会有地上的生灵, 敢于直面雷霆的微光",
            "Minecraft, 拒绝(咀嚼)了我...",
            "我看见爱的火焰在闪↑烁~",
            "Fuck this, delete.",
            "Minecraft, Launch( failed)!",
            "Minecraft更好的崩溃(报告)由小沙雕制作!",
            "Autofisher: hey 5i_XiaoShadiao i see you posted a very nice hypixelhelper autofish + rat mod on github",
            "There are so many Autofishers in main lobby #18!"
    );

    @Overwrite
    public String getErrorComment() {
        String msg;
        try {
            msg = msgs.get((int) (Util.getNanos() % msgs.size()));
        } catch (Throwable var2) {
            msg = "?!弓虽虽弓!?  何意味";
        }
        return msg + "\r\n\r\n// 加入小沙雕的QQ群https://xiaoshadiao.club/qqg小沙雕无偿为你分析崩溃报告";
    }

}
