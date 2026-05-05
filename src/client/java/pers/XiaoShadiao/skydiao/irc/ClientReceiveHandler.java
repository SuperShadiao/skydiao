package pers.XiaoShadiao.skydiao.irc;

import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;

import java.util.Optional;

public class ClientReceiveHandler {

    public void handle(ChatPacket packet, ClientListener sender) {
        switch(packet.packetType) {
            case "chat":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + "§7: §f" + packet.message));
                if(ConfigManager.enablexsdccommandtip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] 请使用/xsdc message聊天!"));

                break;
            case "system":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] §c[SYSTEM] §f" + packet.message));
                break;
            case "join":
                if(ConfigManager.enableircjointip.getValue()) {
                    sender.announceAuthors();
                    ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§b+§7] " + packet.getRank(true) + "§b" + packet.sender + "..."));
                }
                if(ConfigManager.enablexsdccommandtip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] 请使用/xsdc message聊天!"));
                break;
            case "leave":
                if(ConfigManager.enableircjointip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§c-§7] " + packet.getRank(true) + "§c" + packet.sender + "..."));
                break;
            case "heartbeat":
                sender.flagHeartbeat();
                break;
            case "afk":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + (Boolean.parseBoolean(packet.message) ? " §7" + CrowdinI18nManager.translate("xsdchat.afk.in") : " §e" + CrowdinI18nManager.translate("xsdchat.afk.out"))));
                break;
            case "glacite_mineshaft_share":
                AbstractListener.mineshaftShareListener.onIRCMineshaftSharePacket(packet);
                break;
            case "macro_check":
                MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §c触发了Macro Check警报! §e[HOVER]");
                Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(packet.message)));
                ToolList.printChatMessage(component.setStyle(style));
                break;
            case "hyp_party":
                HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
                break;
        }
    }

}
