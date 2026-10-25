package pers.XiaoShadiao.skydiao.utils;

public class RandomTips {

    private static final String[] TIPS = {
            "可以使用/skydiao swaprevivehead来在地牢中快速切换Spirit/Bonzo Mask复活头, 可以使用/skydiao swapgoldordiamondhead来在地牢中快速切换金/钻头",
            "可以使用/skydiaohud来打开HUD设置页面",
            "可以使用/skydiao autoclick来对指定的物品进行左右键连点设置, 也可以在快捷键设置中设置全局连点快捷键",
            "可以使用/skydiao autofillbottle来自动装水瓶填充自己岛屿上的炼药机箱子",
            "可以使用/skydiao entityesp来对指定的实体开启ESP功能",
            "可以使用/skydiao calc (简称/skydiaocalc) 来使用小沙雕的计算器功能",
            "可以使用/skydiao translate (简称/skydiaot) 来翻译指定的文本, 也可以通过安装Chatpatches Mod后打开SkyDiao Mod的聊天按钮功能快速翻译聊天栏",
            "可以使用/skydiao fastclearminingstash来快速清除挖矿后Stash中上万的物品",
            "可以使用/skydiao loto <loadout序号>来快速切换指定的loadout!",
            "可以使用/skydiao petto <pet>快速切换指定的宠物, 其中pet名字可以简写, 例如Golden Dragon可以简写成golden (即Pet名字包含你输入的名字即可, 不区分大小写)",
            "可以使用/skydiao isleautofindvolcano来快速寻找Crismon Isle正在喷发的火山",
            "可以使用/skydiao lookat令自己面向指定的角度 (警告: 该指令不适用于Macro, 若应用于Macro造成的任何后果自负)",
            "可以使用/skydiao nodegraph打开节点图编辑器功能 (功能稍不完善)",
            "可以使用/skydiao ban/wipe整蛊的好伙伴...",
            "可以使用/skydiao editfastcommand来编辑快速命令菜单, 可以在快捷键设置中打开该快捷菜单并使用其快速输入指令 (例如快速切复活头等)",
            "可以使用/skydiaomusic来在客户端内听音乐!",
            "可以使用/skydiaofp打开Party Finder菜单来查找或开放你的地牢/钓鱼等队伍, 也可以使用快捷键(设置)快速打开Party Finder菜单!"
    };

    public static String getRandomTip() {
        return TIPS[ToolList.getInstance().random.nextInt(TIPS.length)];
    }

}
