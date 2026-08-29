package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.ICustomSkinModelLoader;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicListManager;

public class ClientReceiveHandler {

    public void handle(ChatPacket packet, ClientListener sender) {
        switch(packet.packetType) {
            case "chat":
                Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("点击来@" + packet.sender))).withClickEvent(new ClickEvent.SuggestCommand("/xsdc @" + packet.sender));
                ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + "§7: §f" + packet.message).withStyle(style));
                if((packet.message.toLowerCase().contains("@" + ToolList.mc.getUser().getName().toLowerCase()) || (packet.message.toLowerCase().contains("@all") && packet.sender.equals("5i_XiaoShadiao"))) && !packet.getRank(false).contains("[离线]")) {
                    ToolList.getInstance().playSound(SoundEvents.EXPERIENCE_ORB_PICKUP);
                    ToolList.printChatMessage(Component.literal("§a[XSDChat] §e" + CrowdinI18nManager.translate("xsdchat.at")));
                }
                if(ConfigManager.enablexsdccommandtip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] " + CrowdinI18nManager.translate("xsdchat.xsdctip")));
                break;
            case "system":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] §c[SYSTEM] §f" + packet.message));
                break;
            case "join":
                if(ConfigManager.enableircjointip.getValue()) {
                    sender.announceAuthors();
                    ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§b+§7] " + packet.getRank(true) + "§b" + packet.sender + "..."));
                    if(ConfigManager.enablexsdccommandtip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] " + CrowdinI18nManager.translate("xsdchat.xsdctip")));
                }
                break;
            case "leave":
                if(ConfigManager.enableircjointip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§c-§7] " + packet.getRank(true) + "§c" + packet.sender + "..."));
                break;
            case "heartbeat":
                sender.flagHeartbeat();
                break;
            case "afk":
                if(ConfigManager.enableircafktip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + (Boolean.parseBoolean(packet.message) ? " §7" + CrowdinI18nManager.translate("xsdchat.afk.in") : " §e" + CrowdinI18nManager.translate("xsdchat.afk.out"))));
                break;
            case "glacite_mineshaft_share":
                AbstractListener.mineshaftShareListener.onIRCMineshaftSharePacket(packet);
                break;
            case "macro_check":
                if(ConfigManager.enableircmacrochecktip.getValue()) {
                    MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §c触发了Macro Check警报! §e[HOVER]");
                    style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(packet.message)));
                    ToolList.printChatMessage(component.setStyle(style));
                }
                break;
            case "hyp_party":
                ToolList.getInstance().updatePartyInfo();
                break;
            case "slayer_together":
                AbstractListener.slayerTogetherListener.onSlayerTogetherPacket(packet);
                break;
            case "showitem":
                if(ToolList.mc.level != null) {
                    try {
                        ItemStack is = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, ToolList.mc.level.registryAccess()), JsonParser.parseString(packet.message)).getOrThrow(JsonSyntaxException::new);
                        MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §a展示容错 §e[HOVER]")
                                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(is))));
                        ToolList.printChatMessage(component);
                    } catch (JsonSyntaxException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "ysm":
                for (ICustomSkinModelLoader modelLoaderAdapter : AbstractListener.modelLoaderAdapters) {
                    if(modelLoaderAdapter.isSupportYSM()) modelLoaderAdapter.handleIRCPacket(packet);
                }
                break;
            case "sharemusic":
                MusicInfo musicInfo = MusicListManager.jsonToMI(JsonParser.parseString(packet.message).getAsJsonObject());
                int shareId = MusicListManager.storeTempSharingMusic(musicInfo);
                MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §a分享了音乐 §6" + musicInfo.name + " - " + musicInfo.singer + "§a, §e点击这里§a可以收听!");
                Style style1 = Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/skydiao listensharemusic " + shareId))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("/skydiao listensharemusic " + shareId)));
                ToolList.printChatMessage(component.withStyle(style1));
                break;
        }
    }

}
