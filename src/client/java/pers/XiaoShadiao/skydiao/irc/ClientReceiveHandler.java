package pers.XiaoShadiao.skydiao.irc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;

import java.util.Optional;

public class ClientReceiveHandler {

    public void handle(ChatPacket packet, ClientListener sender) {

        // {"message":"SB","packetType":"chat","sender":"5j_XiaoShadiao","senderUUID":"Player0"}
        // System.out.println("Received packet " + jo + " from " + socket.getInetAddress());
        switch(packet.packetType) {
            case "chat":

                ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + "§7: §f" + packet.message));
                ToolList.printChatMessage(Component.literal("§a[XSDChat] 请使用/xsdc message聊天!"));

                // 小沙雕音乐分享码: b1ZQ
//                if(packet.message.startsWith("小沙雕音乐分享码:")) {
//                    事件类型.事件列表.eventMusicShare.onChatEventMessage(packet.message);
//                }
                break;
            case "system":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] §c[SYSTEM] §f" + packet.message));
                // 工具列表.mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("§a[XSDChat] 请使用/xsdc message聊天!"));
                break;
            case "join":
                sender.announceAuthors();
                ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§b+§7] " + packet.getRank(true) + "§b" + packet.sender + "..."));
                ToolList.printChatMessage(Component.literal("§a[XSDChat] 请使用/xsdc message聊天!"));
                break;
            case "leave":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§c-§7] " + packet.getRank(true) + "§c" + packet.sender + "..."));
                break;
            case "heartbeat":
                sender.flagHeartbeat();
                break;
            case "afk":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + (Boolean.parseBoolean(packet.message) ? " §7" + CrowdinI18nManager.translate("xsdchat.afk.in") : " §e" + CrowdinI18nManager.translate("xsdchat.afk.out"))));
                break;
            case "glacite_mineshaft_share":
                // 抽象の监听器.监听器_GlaciteMineshaftShare.onIRCMineshaftSharePacket(packet);
                break;
            case "lps":
//                JsonObject jo = 工具列表.getInstance().解析Json(packet.message).getAsJsonObject();
//                Map.Entry<String, JsonElement> entry = jo.entrySet().iterator().next();
//                String name = entry.getKey();
//                String value = entry.getValue().toString();
//                ChatClient.log.info("Received IRC stats lookup for " + name);
//                for (StatsManager.IRCStatsLookup ircStatsLookuper : StatsManager.ircStatsLookupers) {
//                    if(ircStatsLookuper.getName().equals(name)) {
//                        ChatClient.log.info("Apply " + name + " lookup result to " + ircStatsLookuper);
//                        ircStatsLookuper.setResult(value);
//                    }
//                }
                break;
//            case "rc":
//                提示消息管理.addMessage("§e从IRC中获取到指令: " + packet.message, 消息类型.info, 5000, true);
//                工具列表.mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("§a[XSDChat] §e从IRC中获取到指令: " + packet.message));
//                工具列表.getInstance().sendChatMessage(packet.message);
//                break;
        }
    }

}
